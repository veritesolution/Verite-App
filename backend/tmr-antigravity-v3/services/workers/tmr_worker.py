"""
TMR session Celery worker — production real-time loop.

v3.0.0 production fixes:
  ✅ Bug 5  — Electrode selection hardware-aware (from v2.0.1)
  ✅ Bug 6  — asyncio.run()/DB I/O removed from hot loop (from v2.0.1)
  🆕 Fix 1  — _AsyncDBWriter uses asyncio.Queue (not blocking queue.Queue.get)
  🆕 Fix 2  — DB writes batched into single transaction per drain cycle
  🆕 Feature — Arousal risk gate enforced before every cue delivery

Safety constraints (Antony 2012, Oudiette & Paller 2013):
  - TMR only during confirmed N2 or N3
  - Minimum 30 s between cues
  - Stop if arousal risk > 25%
  - Stop if cumulative cues >= session maximum
  - Immediate stop on hardware disconnect
"""

import asyncio
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional
import numpy as np
import structlog

from .celery_app import celery_app
from ..eeg.synthetic_driver import get_driver
from ..eeg.sleep_stager import YASAStager
from ..eeg.signal_processing import (
    SpindleDetector, PhaseEstimator, ArtifactRejector,
    ArousalRiskEstimator, AROUSAL_RISK_MAX,
)
from ..eeg.audio_delivery import AudioPlayer

log = structlog.get_logger()

SPINDLE_THRESHOLD   = 0.15
MIN_INTERVAL_S      = 30.0
UPSTATE_HALF_WINDOW = np.pi / 4
STATUS_LOG_INTERVAL = 60.0
PHASE_CHECK_SAMPLES = 16
DRAIN_BATCH_MAX     = 50

_FRONTAL_CHANNEL = {
    "muse":     1,   # AF7
    "openbci":  0,
    "synthetic": 0,
}


@dataclass
class _TMREvent:
    session_id: str
    event_type: str
    timestamp_unix: float
    sleep_stage: Optional[str] = None
    spindle_prob: Optional[float] = None
    phase_rad: Optional[float] = None
    arousal_risk: Optional[float] = None
    extra: Optional[dict] = None


class _AsyncDBWriter(threading.Thread):
    """
    Daemon thread with its own asyncio event loop.

    v3.0.0 fixes:
      1. Uses asyncio.Queue so _drain() never blocks its event loop.
      2. Batches events into a single DB transaction per drain cycle.

    The hot EEG loop enqueues events via enqueue(), which is thread-safe:
    it uses loop.call_soon_threadsafe to schedule the put.
    """

    def __init__(self):
        super().__init__(daemon=True, name="tmr-db-writer")
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._queue: Optional[asyncio.Queue] = None
        self._ready = threading.Event()

    def run(self) -> None:
        self._loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        self._queue = asyncio.Queue(maxsize=500)
        self._ready.set()
        self._loop.run_until_complete(self._drain())

    def wait_ready(self, timeout: float = 5.0) -> None:
        self._ready.wait(timeout=timeout)

    def enqueue(self, event: Optional[_TMREvent]) -> None:
        """Thread-safe enqueue from the EEG hot loop."""
        if self._loop is None or self._queue is None:
            return
        try:
            self._loop.call_soon_threadsafe(self._queue.put_nowait, event)
        except (asyncio.QueueFull, RuntimeError):
            if event is not None:
                log.warning("event_queue_full_dropped", event_type=event.event_type)

    def stop(self) -> None:
        """Send sentinel and wait for thread to finish."""
        self.enqueue(None)
        self.join(timeout=10.0)

    async def _drain(self) -> None:
        while True:
            batch: list[_TMREvent] = []
            try:
                item = await asyncio.wait_for(self._queue.get(), timeout=1.0)
            except asyncio.TimeoutError:
                continue

            if item is None:
                break
            batch.append(item)

            while len(batch) < DRAIN_BATCH_MAX:
                try:
                    item = self._queue.get_nowait()
                except asyncio.QueueEmpty:
                    break
                if item is None:
                    await self._write_batch(batch)
                    return
                batch.append(item)

            if batch:
                await self._write_batch(batch)

    @staticmethod
    async def _write_batch(events: list[_TMREvent]) -> None:
        try:
            from ..api.database import AsyncSessionLocal
            from ..api.models import TMREvent as TMREventModel

            async with AsyncSessionLocal() as db:
                for evt in events:
                    db.add(TMREventModel(
                        session_id=uuid.UUID(evt.session_id),
                        event_type=evt.event_type,
                        timestamp_unix=evt.timestamp_unix,
                        sleep_stage=evt.sleep_stage,
                        spindle_prob=evt.spindle_prob,
                        phase_rad=evt.phase_rad,
                        arousal_risk=evt.arousal_risk,
                        extra=evt.extra,
                    ))
                await db.commit()
        except Exception as exc:
            log.error("db_writer_batch_error", n_events=len(events), error=str(exc))


@dataclass
class _SessionState:
    session_id: str
    cues_planned: int
    cues_delivered: int = 0
    cues_suppressed_arousal: int = 0
    spindles_detected: int = 0
    n2_samples: int = 0
    n3_samples: int = 0
    total_clean_samples: int = 0
    last_cue_time: float = field(default_factory=lambda: -MIN_INTERVAL_S - 1)
    last_status_log: float = field(default_factory=time.time)
    current_stage: str = "unknown"


@celery_app.task(
    bind=True,
    name="workers.run_tmr_session",
    queue="eeg",
    max_retries=0,
    acks_late=True,
    reject_on_worker_lost=False,
    time_limit=8 * 3600,
    soft_time_limit=8 * 3600 - 60,
)
def run_tmr_session(
    self,
    session_id: str,
    hardware: str = "synthetic",
    max_duration_s: float = 28800.0,
    max_cues: int = 10,
):
    asyncio.run(_update_session_status(session_id, "running",
                                       started_at=datetime.now(timezone.utc)))

    writer = _AsyncDBWriter()
    writer.start()
    writer.wait_ready()

    def enqueue(event_type: str, **kwargs) -> None:
        writer.enqueue(_TMREvent(
            session_id=session_id,
            event_type=event_type,
            timestamp_unix=time.time(),
            **kwargs,
        ))

    state  = _SessionState(session_id=session_id, cues_planned=max_cues)
    source = None
    player = None

    try:
        source = get_driver(hardware)
        source.connect()

        impedance = source.check_impedance()
        if not impedance.all_ok:
            bad = {k: v for k, v in impedance.values_kohm.items()
                   if v >= impedance.threshold_kohm}
            log.warning("high_impedance", channels=bad)
            enqueue("impedance_warning", extra={"channels": bad})

        sr     = source.sample_rate
        ch_idx = _FRONTAL_CHANNEL.get(hardware, 0)
        ch_name = (source.channel_names[ch_idx]
                   if ch_idx < len(source.channel_names) else str(ch_idx))
        log.info("eeg_ready", hardware=hardware, sr=sr, channel=ch_name)

        stager      = YASAStager(sf=sr)
        spindle     = SpindleDetector(sf=sr)
        phase_est   = PhaseEstimator(sf=sr)
        rejector    = ArtifactRejector(sf=sr)
        arousal_est = ArousalRiskEstimator(sf=sr)

        cue_wav = asyncio.run(_fetch_cue_audio(session_id))
        player  = AudioPlayer(wav_bytes=cue_wav, volume=0.35)

        t_start  = time.time()
        sample_n = 0

        while (
            time.time() - t_start < max_duration_s
            and state.cues_delivered < max_cues
        ):
            try:
                sample = source.read_sample()
            except TimeoutError:
                log.warning("eeg_timeout")
                continue
            except Exception as exc:
                log.error("eeg_read_error", error=str(exc))
                break

            eeg_uv = float(sample.channels[ch_idx])
            sample_n += 1

            if not rejector.push_and_check(eeg_uv):
                continue
            state.total_clean_samples += 1

            stager.push_sample(eeg_uv)
            spindle.push_sample(eeg_uv)
            phase_est.push_sample(eeg_uv)
            arousal_est.push_sample(eeg_uv)

            stage = stager.get_stage()
            if stage != state.current_stage:
                state.current_stage = stage
                enqueue("stage_change", sleep_stage=stage)
                log.info("stage_change", stage=stage,
                         elapsed_s=int(time.time() - t_start))

            if stage == "N2":
                state.n2_samples += 1
            elif stage == "N3":
                state.n3_samples += 1

            if stage not in ("N2", "N3"):
                continue

            if sample_n % PHASE_CHECK_SAMPLES != 0:
                continue

            spindle_prob = spindle.get_spindle_probability()
            if spindle_prob >= 0.70:
                state.spindles_detected += 1
                enqueue("spindle_detected", sleep_stage=stage, spindle_prob=spindle_prob)

            in_upstate, phase_rad = phase_est.is_in_upstate(UPSTATE_HALF_WINDOW)
            if not in_upstate:
                continue
            if spindle_prob < SPINDLE_THRESHOLD:
                continue
            if time.time() - state.last_cue_time < MIN_INTERVAL_S:
                continue

            # ── Arousal risk gate (v3.0.0 — was phantom, now enforced) ────
            current_arousal = arousal_est.get_arousal_risk()
            if current_arousal > AROUSAL_RISK_MAX:
                state.cues_suppressed_arousal += 1
                enqueue("cue_suppressed_arousal", sleep_stage=stage,
                        arousal_risk=current_arousal, spindle_prob=spindle_prob,
                        phase_rad=phase_rad)
                log.info("cue_suppressed_arousal",
                         arousal_risk=round(current_arousal, 3),
                         threshold=AROUSAL_RISK_MAX)
                continue

            if player.play_async():
                state.cues_delivered += 1
                state.last_cue_time = time.time()
                log.info("cue_delivered", n=state.cues_delivered, stage=stage,
                         spindle_prob=round(spindle_prob, 3),
                         phase_rad=round(phase_rad, 3) if phase_rad else None,
                         arousal_risk=round(current_arousal, 3))
                enqueue("cue_delivered", sleep_stage=stage,
                        spindle_prob=spindle_prob, phase_rad=phase_rad,
                        arousal_risk=current_arousal)

            if time.time() - state.last_status_log > STATUS_LOG_INTERVAL:
                state.last_status_log = time.time()
                log.info("tmr_status", session_id=session_id,
                         cues=f"{state.cues_delivered}/{state.cues_planned}",
                         stage=state.current_stage,
                         n2_min=round(state.n2_samples / sr / 60, 1),
                         n3_min=round(state.n3_samples / sr / 60, 1),
                         spindles=state.spindles_detected,
                         arousal_suppressions=state.cues_suppressed_arousal,
                         artifact_rate=round(rejector.artifact_rate, 3))

    except Exception as exc:
        log.error("tmr_session_error", session_id=session_id, error=str(exc))
    finally:
        if source:
            source.disconnect()
        writer.stop()

        sr_f = source.sample_rate if source else 256.0
        n2_min = state.n2_samples / sr_f / 60.0
        n3_min = state.n3_samples / sr_f / 60.0
        sleep_eff = (
            (state.n2_samples + state.n3_samples) / max(state.total_clean_samples, 1)
        )
        asyncio.run(_update_session_complete(
            session_id=session_id,
            status="completed",
            cues_delivered=state.cues_delivered,
            spindles_detected=state.spindles_detected,
            n2_minutes=round(n2_min, 2),
            n3_minutes=round(n3_min, 2),
            sleep_efficiency=round(sleep_eff, 3),
            metadata={
                "cues_suppressed_arousal": state.cues_suppressed_arousal,
                "artifact_rate": round(rejector.artifact_rate, 3),
                "total_clean_samples": state.total_clean_samples,
            },
        ))


async def _update_session_status(session_id: str, status: str, **kwargs):
    from ..api.database import AsyncSessionLocal
    from ..api.models import TMRSession, TMRSessionStatus
    from sqlalchemy import select
    async with AsyncSessionLocal() as db:
        r = await db.execute(select(TMRSession).where(TMRSession.id == uuid.UUID(session_id)))
        s = r.scalar_one_or_none()
        if s:
            s.status = TMRSessionStatus[status.upper()]
            for k, v in kwargs.items():
                setattr(s, k, v)
            await db.commit()


async def _update_session_complete(session_id: str, status: str, metadata: dict = None, **kwargs):
    from ..api.database import AsyncSessionLocal
    from ..api.models import TMRSession, TMRSessionStatus
    from sqlalchemy import select
    async with AsyncSessionLocal() as db:
        r = await db.execute(select(TMRSession).where(TMRSession.id == uuid.UUID(session_id)))
        s = r.scalar_one_or_none()
        if s:
            s.status = TMRSessionStatus[status.upper()]
            s.ended_at = datetime.now(timezone.utc)
            for k, v in kwargs.items():
                setattr(s, k, v)
            if metadata:
                s.session_metadata = {**(s.session_metadata or {}), **metadata}
            await db.commit()


async def _fetch_cue_audio(session_id: str) -> bytes:
    try:
        from ..api.database import AsyncSessionLocal
        from ..api.models import TMRSession, HabitPlan
        from ..api.config import settings
        from sqlalchemy import select
        import boto3

        async with AsyncSessionLocal() as db:
            sr = await db.execute(select(TMRSession).where(TMRSession.id == uuid.UUID(session_id)))
            sess = sr.scalar_one_or_none()
            if not sess or not sess.plan_id:
                return _default_cue_wav()
            pr = await db.execute(select(HabitPlan).where(HabitPlan.id == sess.plan_id))
            plan = pr.scalar_one_or_none()
            if not plan or not plan.cue_audio_s3_key:
                return _default_cue_wav()

        s3 = boto3.client("s3",
                          aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
                          aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
                          region_name=settings.AWS_REGION)
        obj = s3.get_object(Bucket=settings.S3_BUCKET_AUDIO, Key=plan.cue_audio_s3_key)
        return obj["Body"].read()
    except Exception as exc:
        log.warning("cue_audio_fetch_failed", error=str(exc))
        return _default_cue_wav()


def _default_cue_wav() -> bytes:
    import io, wave
    sr, dur, freq = 22050, 1.5, 440.0
    n = int(sr * dur)
    t = np.linspace(0, dur, n, endpoint=False)
    fd = int(0.05 * sr)
    env = np.ones(n)
    env[:fd] = np.linspace(0, 1, fd)
    env[-fd:] = np.linspace(1, 0, fd)
    s = (np.sin(2 * np.pi * freq * t) * 0.3 * env * 32767).astype(np.int16)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1); wf.setsampwidth(2); wf.setframerate(sr)
        wf.writeframes(s.tobytes())
    return buf.getvalue()
