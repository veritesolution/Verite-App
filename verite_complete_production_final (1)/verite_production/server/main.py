"""
Vérité TMR v10.5.2 — Production API Server (Final)
===================================================
Full FastAPI wrapper with study/flashcard/quiz/audio routes.

Bug fixes applied:
  #3: _ALLOWED_CONFIG_KEYS is now lazy — won't crash on import if verite_tmr missing
  Study routes mounted: /study/*, /quiz/*, /audio/*
  app.state.active_session synced for study_routes access
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import secrets
import threading
import time
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import numpy as np
from fastapi import (
    Depends, FastAPI, File, Header, HTTPException, Request,
    UploadFile, WebSocket, WebSocketDisconnect, status,
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from verite_tmr import Config
from verite_tmr.audio import TMRAudioEngine
from verite_tmr.detection import (
    ArousalPredictor, ArtefactDetector, KComplexDetector,
    SOSpindleCoupling, SpindleCNN,
)
from verite_tmr.document import ConceptExtractor, DocumentProcessor
from verite_tmr.hardware import BrainStateSnapshot, HardwareInterface, ModelRegistry
from verite_tmr.memory import MemoryStrengthAssessor
from verite_tmr.orchestrator import AdaptiveCueOrchestrator, CueEvent
from verite_tmr.phase import create_phase_estimator
from verite_tmr.safety import GDPRDataManager, validate_pipeline

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("verite.server")

API_KEY          = os.environ.get("API_KEY", "verite-dev-key-change-in-production")
UPLOAD_DIR       = Path(os.environ.get("UPLOAD_DIR", "/tmp/verite_uploads"))
TICK_INTERVAL    = 0.05
MAX_UPLOAD_BYTES = int(os.environ.get("MAX_UPLOAD_MB", "50")) * 1024 * 1024
ENVIRONMENT      = os.environ.get("ENVIRONMENT", "development")
CORS_ORIGINS = [
    o.strip() for o in
    os.environ.get("CORS_ORIGINS", "http://localhost:3000,http://localhost:8080").split(",")
    if o.strip()
]

# BUG FIX #3: Lazy config key whitelist — computed on first use, not at import time.
# If verite_tmr is not installed, module-level access to Config.__dataclass_fields__
# crashes before FastAPI starts, producing a confusing error that hides the real problem.
_allowed_config_keys: frozenset | None = None

def _get_allowed_config_keys() -> frozenset:
    global _allowed_config_keys
    if _allowed_config_keys is None:
        _allowed_config_keys = frozenset(Config.__dataclass_fields__.keys())
    return _allowed_config_keys


def _validate_startup() -> None:
    if ENVIRONMENT == "production":
        if API_KEY == "verite-dev-key-change-in-production":
            raise RuntimeError(
                "⛔ PRODUCTION MODE: API_KEY is still the default. "
                "Set a strong random key (≥32 chars) in .env before starting."
            )
        if len(API_KEY) < 32:
            log.warning("⚠ API_KEY is shorter than 32 characters.")


def _json_default(obj: Any) -> Any:
    if isinstance(obj, np.floating): return float(obj)
    if isinstance(obj, np.integer): return int(obj)
    if isinstance(obj, np.ndarray): return obj.tolist()
    raise TypeError(f"Not JSON serialisable: {type(obj)}")


# ─────────────────────────────────────────────────────────────────────────────
# SessionState
# ─────────────────────────────────────────────────────────────────────────────

class SessionState:
    def __init__(self, config: Config, mode: str, ws_uri: str) -> None:
        self.config = config; self.mode = mode; self.ws_uri = ws_uri
        self.session_id = str(uuid.uuid4()); self.started_at = time.time()
        self.phase_est = create_phase_estimator(config.phase_predictor, fs=250)
        self.spindle_cnn = SpindleCNN()
        self.kcomplex = KComplexDetector(
            amplitude_threshold_uv=config.kcomplex_amplitude_uv,
            duration_min_s=config.kcomplex_duration_min_s,
            duration_max_s=config.kcomplex_duration_max_s,
        )
        self.arousal = ArousalPredictor()
        self.artefact_det = ArtefactDetector(
            amplitude_threshold_uv=config.artefact_amplitude_uv,
            flatline_threshold=config.artefact_flatline_threshold,
        )
        self.coupling = SOSpindleCoupling(min_coupling=config.pac_min_coupling)
        self.assessor = MemoryStrengthAssessor(config=config)
        self.audio_engine = TMRAudioEngine(config=config)
        self.orchestrator = AdaptiveCueOrchestrator(config=config)
        self.doc_proc = DocumentProcessor()
        self.concept_ext = ConceptExtractor(
            groq_key=os.environ.get("GROQ_API_KEY", ""),
            gemini_key=os.environ.get("GEMINI_API_KEY", ""),
        )
        self.gdpr = GDPRDataManager(config)
        self.hardware = HardwareInterface(
            mode=mode, ws_uri=ws_uri, model_registry=ModelRegistry(),
            time_warp=config.time_warp,
        )
        self._ws_queues: dict[str, asyncio.Queue] = {}
        self._ws_loop: asyncio.AbstractEventLoop | None = None
        self._lock = threading.Lock()
        self._running = False; self._thread: threading.Thread | None = None
        self.concepts: list[dict] = []; self.cue_log: list[dict] = []

    def register_ws(self, client_id: str, loop: asyncio.AbstractEventLoop) -> asyncio.Queue:
        q: asyncio.Queue = asyncio.Queue(maxsize=64)
        with self._lock:
            self._ws_queues[client_id] = q; self._ws_loop = loop
        return q

    def unregister_ws(self, client_id: str) -> None:
        with self._lock: self._ws_queues.pop(client_id, None)

    def _broadcast(self, payload: dict) -> None:
        msg = json.dumps(payload, default=_json_default)
        with self._lock:
            loop = self._ws_loop; queues = list(self._ws_queues.values())
        if not loop or not loop.is_running(): return
        for q in queues:
            try: loop.call_soon_threadsafe(q.put_nowait, msg)
            except (asyncio.QueueFull, Exception): pass

    def _tick_loop(self) -> None:
        log.info(f"[{self.session_id[:8]}] Tick loop started — mode={self.mode}")
        while self._running:
            snap = self.hardware.get_snapshot()
            if snap is None: time.sleep(TICK_INTERVAL); continue

            eeg_sample = snap.eeg_channels.get("C3") or snap.eeg_channels.get("C4")
            if eeg_sample is not None:
                s = float(eeg_sample)
                self.spindle_cnn.push(s); self.kcomplex.push(s)
                self.arousal.push(s); self.artefact_det.push(s); self.coupling.push(s)

            spindle_prob = (snap.spindle_prob if snap.spindle_source == "hardware"
                           else self.spindle_cnn.predict())
            arousal_risk = (snap.arousal_risk
                           if snap.spindle_source == "hardware" and snap.arousal_risk > 0.0
                           else self.arousal.predict(hrv_rmssd=snap.hrv_rmssd, accel_rms=snap.accel_rms))
            kc_event = self.kcomplex.detect() if self.config.kcomplex_enabled else None
            artefact = self.artefact_det.is_artefact()
            coupling_mi = self.coupling.compute_mi() if self.config.pac_enabled else 0.0

            if snap.phase_source != "hardware" and eeg_sample is not None:
                so_phase = self.phase_est.push_and_estimate(float(eeg_sample))
            else:
                so_phase = snap.so_phase

            enriched = BrainStateSnapshot(
                timestamp=snap.timestamp, hw_timestamp=snap.hw_timestamp,
                sleep_stage=snap.sleep_stage, so_phase=so_phase,
                so_amplitude=snap.so_amplitude, spindle_prob=spindle_prob,
                arousal_risk=arousal_risk, spindle_just_fired=snap.spindle_just_fired,
                hrv_rmssd=snap.hrv_rmssd, accel_rms=snap.accel_rms,
                artefact_detected=artefact,
            )
            cue_evt = self.orchestrator.step(enriched, coupling_mi=coupling_mi,
                                             kcomplex_detected=kc_event is not None)

            phase_deg = float(np.degrees(so_phase))
            payload: dict[str, Any] = {
                "type": "tick", "ts": snap.timestamp, "stage": enriched.sleep_stage,
                "phase_deg": round(phase_deg, 1), "spindle_prob": round(spindle_prob, 3),
                "arousal_risk": round(arousal_risk, 3), "coupling_mi": round(coupling_mi, 4),
                "kcomplex": kc_event is not None, "artefact": artefact,
                "delivery_window": enriched.is_delivery_window, "cue": None,
            }
            if cue_evt:
                cue_dict = {
                    "concept": cue_evt.concept_name, "cue_type": cue_evt.cue_type,
                    "phase_deg": round(float(np.degrees(cue_evt.so_phase_at_delivery)), 1),
                    "spindle_prob": round(cue_evt.spindle_prob_at_delivery, 3),
                    "coupling_mi": round(cue_evt.coupling_mi, 4),
                }
                payload["cue"] = cue_dict
                self.cue_log.append({**cue_dict, "ts": snap.timestamp})
                log.info(f"[{self.session_id[:8]}] CUE '{cue_evt.concept_name}' phase={phase_deg:.1f}°")
            self._broadcast(payload)
            time.sleep(TICK_INTERVAL)
        log.info(f"[{self.session_id[:8]}] Tick loop stopped")

    def start(self) -> None:
        self.hardware.start(); self._running = True
        self._thread = threading.Thread(target=self._tick_loop, daemon=True, name="verite-tick")
        self._thread.start()

    def stop(self) -> None:
        self._running = False
        self._broadcast({"type": "session_ended", "ts": time.time()})
        self.hardware.stop()
        if self._thread: self._thread.join(timeout=3); self._thread = None

    def load_document(self, path: str) -> dict:
        try: text = self.doc_proc.extract_text(path)
        except Exception as exc:
            log.warning(f"Document extraction failed: {exc}"); return {"error": str(exc)}
        concepts = self.concept_ext.extract_concepts(text, self.config.max_concepts)
        self.concepts = concepts
        self.assessor.assess_simulation(concepts)
        cue_packages = []
        for c in concepts:
            name = c.get("concept", c.get("term", ""))
            if not name: continue
            if self.assessor.get_tier(self.assessor.get_strength(name)) == "sweet_spot":
                cue_packages.append(self.audio_engine.generate_whispered(name, concept_id=name))
        self.orchestrator.load_queue(cue_packages)
        log.info(f"Loaded {len(concepts)} concepts, {len(cue_packages)} cues queued")
        return {"n_concepts": len(concepts), "n_cues_queued": len(cue_packages),
                "concepts": [c.get("concept", "") for c in concepts],
                "audio_backend": self.audio_engine.backend}

    def get_report(self) -> dict:
        return {
            "session_id": self.session_id, "mode": self.mode,
            "started_at": self.started_at,
            "elapsed_s": round(time.time() - self.started_at, 1),
            "running": self._running, "n_concepts": len(self.concepts),
            "stats": self.orchestrator.session_stats(),
            "gate_summary": self.orchestrator.gate_rejection_summary(),
            "memory_profile": self.assessor.get_session_profile(),
            "cue_log": self.cue_log[-20:], "hardware": self.hardware.latency_report(),
            "spindle_mode": self.spindle_cnn.mode, "arousal_mode": self.arousal.mode,
        }


# ─────────────────────────────────────────────────────────────────────────────
# Global state + FastAPI app
# ─────────────────────────────────────────────────────────────────────────────

_active_session: SessionState | None = None
_session_lock = threading.Lock()

@asynccontextmanager
async def lifespan(app: FastAPI):
    _validate_startup()
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    # Sync app.state for study_routes access
    app.state.active_session = None
    log.info(f"Vérité TMR server started (env={ENVIRONMENT})")
    yield
    global _active_session
    with _session_lock:
        if _active_session:
            _active_session.stop(); _active_session = None
    app.state.active_session = None
    log.info("Vérité TMR server shut down")

app = FastAPI(title="Vérité TMR API", version="10.5.2",
              description="Production API for Closed-Loop TMR", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=CORS_ORIGINS, allow_credentials=True,
                   allow_methods=["GET", "POST"], allow_headers=["X-API-Key", "Content-Type", "Accept"])

# Rate limiting
_rate_limit_store: dict[str, list[float]] = {}
_rate_limit_lock = threading.Lock()
RATE_LIMIT_PER_MINUTE = int(os.environ.get("RATE_LIMIT_PER_MINUTE", "120"))

def _check_rate_limit(client_ip: str) -> bool:
    now = time.time()
    with _rate_limit_lock:
        ts = [t for t in _rate_limit_store.get(client_ip, []) if now - t < 60]
        if len(ts) >= RATE_LIMIT_PER_MINUTE:
            _rate_limit_store[client_ip] = ts; return False
        ts.append(now); _rate_limit_store[client_ip] = ts; return True

@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(status_code=exc.status_code, content={
        "error": exc.detail, "status_code": exc.status_code,
        "request_id": request.headers.get("X-Request-ID", str(uuid.uuid4())[:8]),
    })

def require_api_key(request: Request, x_api_key: str = Header(...)) -> str:
    client_ip = request.client.host if request.client else "unknown"
    if not _check_rate_limit(client_ip):
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Rate limit exceeded")
    if not secrets.compare_digest(x_api_key, API_KEY):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid API key")
    return x_api_key


# ── Pydantic models ──────────────────────────────────────────────────────────

class StartSessionRequest(BaseModel):
    mode: str = Field("simulation")
    ws_uri: str = Field("")
    hours: float = Field(8.0, ge=0.1, le=12.0)
    config: dict = Field(default_factory=dict)

class ConfigRequest(BaseModel):
    config: dict

class SessionStatusResponse(BaseModel):
    active: bool; session_id: str | None; mode: str | None
    elapsed_s: float | None; n_cues: int | None


def _sanitize_config(raw: dict) -> dict:
    return {k: v for k, v in raw.items() if k in _get_allowed_config_keys()}


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health", tags=["system"])
async def health() -> dict:
    return {"status": "ok", "version": "10.5.2", "ts": time.time(),
            "session": _active_session.session_id if _active_session else None}

@app.post("/session/start", tags=["session"], dependencies=[Depends(require_api_key)])
async def start_session(req: StartSessionRequest, request: Request) -> dict:
    global _active_session
    with _session_lock:
        if _active_session and _active_session._running:
            raise HTTPException(409, "Session already running. POST /session/stop first.")
        cfg = Config()
        for k, v in _sanitize_config(req.config).items():
            if hasattr(cfg, k): setattr(cfg, k, v)
        if "simulate_hours" not in req.config: cfg.simulate_hours = req.hours
        if "time_warp" not in req.config:
            cfg.time_warp = 3000.0 if req.mode == "simulation" else 1.0
        errors = cfg.validate()
        if errors: raise HTTPException(422, {"config_errors": errors})
        if req.mode not in ("simulation", "live", "replay"):
            raise HTTPException(422, f"Invalid mode '{req.mode}'")
        try: safety = validate_pipeline(cfg, req.mode)
        except RuntimeError as exc: raise HTTPException(503, {"safety_block": str(exc)})
        session = SessionState(cfg, req.mode, req.ws_uri)
        session.start(); _active_session = session
        # Sync to app.state for study_routes
        request.app.state.active_session = session
    return {"session_id": session.session_id, "mode": req.mode,
            "safety": {k: v["status"] for k, v in safety.items() if not k.startswith("_")},
            "spindle_mode": session.spindle_cnn.mode, "arousal_mode": session.arousal.mode,
            "audio_backend": session.audio_engine.backend}

@app.post("/session/stop", tags=["session"], dependencies=[Depends(require_api_key)])
async def stop_session(request: Request) -> dict:
    global _active_session
    with _session_lock:
        session = _active_session
        if not session or not session._running: raise HTTPException(404, "No active session")
        report = session.get_report(); session.stop()
        _active_session = None
        request.app.state.active_session = None
    return {"status": "stopped", "final_report": report}

@app.get("/session/status", tags=["session"], dependencies=[Depends(require_api_key)])
async def session_status() -> SessionStatusResponse:
    with _session_lock: session = _active_session
    if not session:
        return SessionStatusResponse(active=False, session_id=None, mode=None, elapsed_s=None, n_cues=None)
    return SessionStatusResponse(
        active=session._running, session_id=session.session_id, mode=session.mode,
        elapsed_s=round(time.time() - session.started_at, 1), n_cues=len(session.cue_log))

@app.get("/config", tags=["config"], dependencies=[Depends(require_api_key)])
async def get_config() -> dict:
    with _session_lock: cfg = _active_session.config if _active_session else Config()
    return cfg.to_dict()

@app.post("/config", tags=["config"], dependencies=[Depends(require_api_key)])
async def update_config(req: ConfigRequest) -> dict:
    with _session_lock:
        if _active_session and _active_session._running:
            raise HTTPException(409, "Stop session before changing config.")
    cfg = Config.from_dict(_sanitize_config(req.config))
    errors = cfg.validate()
    if errors: raise HTTPException(422, {"config_errors": errors})
    return {"ok": True, "config": cfg.to_dict()}

@app.post("/document", tags=["document"], dependencies=[Depends(require_api_key)])
async def upload_document(file: UploadFile = File(...)) -> dict:
    with _session_lock: session = _active_session
    if not session: raise HTTPException(404, "Start a session first.")
    if not file.filename: raise HTTPException(422, "Missing filename.")
    content = await file.read()
    if len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, f"File too large ({len(content)//1024//1024}MB). Max: {MAX_UPLOAD_BYTES//1024//1024}MB")
    ext = Path(file.filename).suffix.lower()
    if ext not in {".pdf", ".docx", ".pptx", ".txt", ".md"}:
        raise HTTPException(422, f"Unsupported file type '{ext}'")
    dest = UPLOAD_DIR / f"{uuid.uuid4()}{ext}"; dest.write_bytes(content)
    result = session.load_document(str(dest))
    result["filename"] = file.filename; result["size_bytes"] = len(content)
    return result

@app.get("/report", tags=["report"], dependencies=[Depends(require_api_key)])
async def get_report() -> dict:
    with _session_lock: session = _active_session
    if not session: raise HTTPException(404, "No active session")
    return session.get_report()

@app.websocket("/ws/stream")
async def ws_stream(ws: WebSocket) -> None:
    ws_key = ws.query_params.get("api_key", "")
    if not secrets.compare_digest(ws_key, API_KEY):
        await ws.close(code=4001, reason="Unauthorized"); return
    await ws.accept()
    with _session_lock: session = _active_session
    if not session:
        await ws.send_text(json.dumps({"type": "error", "message": "No active session"}))
        await ws.close(); return
    client_id = str(uuid.uuid4())
    loop = asyncio.get_running_loop()
    q = session.register_ws(client_id, loop)
    log.info(f"WS {client_id[:8]} connected")
    try:
        while True:
            try:
                msg = await asyncio.wait_for(q.get(), timeout=5.0)
                await ws.send_text(msg)
            except asyncio.TimeoutError:
                await ws.send_text(json.dumps({"type": "ping", "ts": time.time()}))
    except WebSocketDisconnect: log.info(f"WS {client_id[:8]} disconnected")
    except Exception as exc: log.warning(f"WS {client_id[:8]} error: {exc}")
    finally: session.unregister_ws(client_id)


# ── Mount study/quiz/audio routers ───────────────────────────────────────────

from study_routes import study_router, quiz_router, audio_router

# Apply API key auth to all study routes
for router in (study_router, quiz_router, audio_router):
    app.include_router(router, dependencies=[Depends(require_api_key)])


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0",
                port=int(os.environ.get("PORT", 8080)), reload=False, log_level="info")
