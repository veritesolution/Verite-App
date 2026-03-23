"""
Hardware interface — WebSocket client, JSON input, and model plugin pipeline.

Model Plugin System:
    registry = ModelRegistry()
    registry.register("sleep_stage", my_eeg_model, input_key="eeg_raw")
    registry.register("hrv", my_hrv_model, input_key="hrv_rr_intervals")
    registry.register("emg", my_emg_model, input_key="emg_raw")
    hw = HardwareInterface(ws_uri="ws://...", model_registry=registry)
"""
from __future__ import annotations
import json, queue, threading, time, warnings
from collections import deque
from dataclasses import dataclass, field
from typing import Any, Callable, Protocol
import numpy as np

class PredictionModel(Protocol):
    def predict(self, data: np.ndarray, **ctx: Any) -> dict[str, Any]: ...

class ModelRegistry:
    """Registry for pluggable ML models (EEG sleep staging, HRV, EMG, etc.)."""
    def __init__(self):
        self._models: dict[str, dict] = {}
        self._order: list[str] = []

    def register(self, name: str, model: Any, input_key: str = "eeg_raw",
                 priority: int = 50, required: bool = False):
        if not hasattr(model, "predict"):
            raise TypeError(f"Model '{name}' must have predict()")
        self._models[name] = {"model": model, "input_key": input_key,
                              "priority": priority, "required": required}
        self._order = sorted(self._models, key=lambda n: self._models[n]["priority"])

    def unregister(self, name: str):
        self._models.pop(name, None)
        self._order = [n for n in self._order if n != name]

    def run_all(self, json_data: dict) -> dict[str, Any]:
        results: dict[str, Any] = {}
        for name in self._order:
            info = self._models[name]
            raw = json_data.get(info["input_key"])
            if raw is None:
                if info["required"]:
                    raise ValueError(f"Model '{name}' requires '{info['input_key']}'")
                continue
            try:
                data = np.array(raw, dtype=np.float64)
                preds = info["model"].predict(data, **results)
                if isinstance(preds, dict):
                    results.update(preds)
            except Exception as e:
                warnings.warn(f"Model '{name}' failed: {e}")
        return results

    @property
    def registered_models(self) -> list[str]: return list(self._order)
    def summary(self) -> dict:
        return {n: {"input_key": i["input_key"], "priority": i["priority"],
                     "type": type(i["model"]).__name__} for n, i in self._models.items()}

@dataclass
class BrainStateSnapshot:
    timestamp: float; hw_timestamp: float; sleep_stage: str
    so_phase: float; so_amplitude: float; spindle_prob: float
    arousal_risk: float; spindle_just_fired: bool
    eeg_channels: dict = field(default_factory=dict)
    hrv_rmssd: float = 0.0; accel_rms: float = 0.0; emg_level: float = 0.0
    phase_source: str = "hardware"; spindle_source: str = "hardware"
    artefact_detected: bool = False; model_outputs: dict = field(default_factory=dict)

    @property
    def is_delivery_window(self) -> bool:
        return (self.sleep_stage in ("N2","N3") and
                (0.75*np.pi) <= self.so_phase <= (1.25*np.pi) and
                self.spindle_prob >= 0.15 and self.arousal_risk <= 0.25 and
                not self.artefact_detected)

class HardwareInterface:
    """WebSocket hardware interface with model plugin pipeline."""
    FS = 250; MAX_RECONNECT = 5; RECONNECT_DELAY_S = 3.0

    def __init__(self, mode="simulation", ws_uri="", model_registry=None,
                 time_warp=1.0, psg_path=""):
        self.mode = mode; self.ws_uri = ws_uri
        self._registry = model_registry or ModelRegistry()
        self._time_warp = max(1.0, float(time_warp)); self._psg_path = psg_path
        self._running = False; self._threads = []; self._callbacks = []
        self._snapshot_lock = threading.Lock(); self._snapshot = None
        self._msg_queue = queue.Queue(maxsize=50)
        self._hw_latency_offset_s = 0.0; self._latency_log = []
        self._sim_t = 0.0; self._sim_stage_idx = 0; self._sim_stage_elapsed = 0.0
        self._stage_schedule = [("N1",5),("N2",20),("N3",40),("N2",15),("N3",35),
                                ("N2",20),("REM",15),("N2",25),("N3",30),("REM",20)]
        # Realistic SO phase via CausalPhaseEstimator (Issue 3 fix)
        try:
            from verite_tmr.phase.echt import CausalPhaseEstimator
            self._sim_phase_est = CausalPhaseEstimator(fs=250, buffer_s=4.0)
            # Pre-warm: fill the buffer so phase output is valid from tick 1.
            # Random phase offset ensures the simulation explores full [0, 2pi] phase
            # space across runs, not always the same ~57 deg sector of the SO cycle.
            _prewarm_offset = np.random.uniform(0, 2 * np.pi)
            for _i in range(1500):
                self._sim_phase_est.push_and_estimate(
                    50.0 * np.cos(2 * np.pi * 0.75 * _i / 250.0 + _prewarm_offset)
                )
            # Verify pre-warm succeeded
            assert self._sim_phase_est.last_phase != 0.0, (
                "CausalPhaseEstimator pre-warm failed — buffer not filled"
            )
        except Exception:
            self._sim_phase_est = None

    def register_callback(self, fn): self._callbacks.append(fn)
    @property
    def current_snapshot(self):
        with self._snapshot_lock: return self._snapshot
    def get_snapshot(self): return self.current_snapshot
    def _set_snapshot(self, s):
        with self._snapshot_lock: self._snapshot = s

    def sync_hardware_clock(self, hw_time):
        self._hw_latency_offset_s = time.time() - hw_time
    def record_cue_latency(self, ts):
        self._latency_log.append((time.time()-ts-self._hw_latency_offset_s)*1000)
    def latency_report(self):
        if not self._latency_log: return {"n":0,"p95_ms":None,"hw_synced":self._hw_latency_offset_s!=0}
        a = np.array(self._latency_log)
        return {"n":len(a),"mean_ms":round(float(np.mean(a)),2),
                "p95_ms":round(float(np.percentile(a,95)),2),"hw_synced":self._hw_latency_offset_s!=0}

    def start(self):
        self._running = True
        if self.mode == "live":
            self._threads = [threading.Thread(target=self._live_loop, daemon=True),
                             threading.Thread(target=self._consumer_loop, daemon=True)]
        elif self.mode == "replay":
            self._threads = [threading.Thread(target=self._replay_loop, daemon=True)]
        else:
            self._threads = [threading.Thread(target=self._sim_loop, daemon=True)]
        for t in self._threads: t.start()

    def stop(self):
        self._running = False
        for t in self._threads: t.join(timeout=2)
        self._threads.clear()

    def process_json(self, j: dict) -> BrainStateSnapshot:
        """Process a JSON packet through the model pipeline."""
        hw_ts = float(j.get("timestamp", time.time()))
        mo = self._registry.run_all(j)
        snap = BrainStateSnapshot(
            timestamp=time.time(), hw_timestamp=hw_ts,
            sleep_stage=mo.get("sleep_stage", j.get("stage","N2")),
            so_phase=float(mo.get("so_phase", j.get("so_phase",0.0))),
            so_amplitude=float(j.get("so_amplitude",100.0)),
            spindle_prob=float(mo.get("spindle_prob", j.get("spindle_prob",0.0))),
            arousal_risk=float(mo.get("arousal_risk", j.get("arousal_risk",0.1))),
            spindle_just_fired=bool(j.get("spindle_fired",False)),
            eeg_channels=j.get("eeg_channels",{}),
            hrv_rmssd=float(mo.get("hrv_rmssd", j.get("hrv_rmssd",45.0))),
            accel_rms=float(j.get("accel_rms",0.01)),
            emg_level=float(mo.get("emg_level",0.0)),
            phase_source=mo.get("phase_source","hardware"),
            spindle_source=mo.get("spindle_source","hardware"),
            artefact_detected=bool(mo.get("artefact_detected",False)),
            model_outputs=mo,
        )
        self._set_snapshot(snap)
        for cb in self._callbacks:
            try: cb(snap)
            except Exception: pass
        return snap

    def _live_loop(self):
        try: import websocket
        except ImportError: self._sim_loop(); return
        attempt = 0
        while self._running and attempt < self.MAX_RECONNECT:
            try:
                evt = threading.Event()
                ws_app = websocket.WebSocketApp(self.ws_uri,
                    on_message=lambda ws,m: self._msg_queue.put_nowait(m) if not self._msg_queue.full() else None,
                    on_error=lambda ws,e: evt.set(), on_close=lambda ws,c,m: evt.set())
                t = threading.Thread(target=lambda: ws_app.run_forever(ping_interval=15), daemon=True)
                t.start()
                while self._running and not evt.is_set(): time.sleep(0.1)
                ws_app.close(); t.join(timeout=2)
            except Exception: pass
            if not self._running: break
            attempt += 1
            if attempt < self.MAX_RECONNECT: time.sleep(self.RECONNECT_DELAY_S)
        if self._running: self._sim_loop()

    def _consumer_loop(self):
        while self._running:
            try: self.process_json(json.loads(self._msg_queue.get(timeout=0.1)))
            except queue.Empty: pass
            except Exception: pass

    def _replay_loop(self):
        if not self._psg_path: self._sim_loop(); return
        try:
            import mne
            raw = mne.io.read_raw_edf(self._psg_path, preload=True, verbose=False)
            data = raw.get_data()[0]; fs = raw.info["sfreq"]
            epoch_len = int(fs * 30)
            for s in range(0, len(data)-epoch_len, epoch_len):
                if not self._running: break
                self.process_json({"timestamp":time.time(),"eeg_raw":data[s:s+epoch_len].tolist()})
                time.sleep(0.1)
        except ImportError:
            try:
                import csv
                with open(self._psg_path) as f:
                    for row in csv.DictReader(f):
                        if not self._running: break
                        self.process_json(dict(row)); time.sleep(0.1)
            except Exception: self._sim_loop()

    def _sim_loop(self):
        dt = 0.05
        # Generate synthetic SO-frequency noise for realistic phase trajectory
        _so_freq = 0.75
        _sim_sample_idx = 0
        while self._running:
            self._sim_t += dt * self._time_warp
            self._sim_stage_elapsed += dt * self._time_warp
            if self._sim_stage_idx < len(self._stage_schedule):
                stage, dur = self._stage_schedule[self._sim_stage_idx]
                if self._sim_stage_elapsed >= dur*60:
                    self._sim_stage_elapsed = 0
                    self._sim_stage_idx = (self._sim_stage_idx+1) % len(self._stage_schedule)
            else: stage = "N2"
            is_nrem = stage in ("N2","N3")
            # Realistic phase from CausalPhaseEstimator on synthetic SO signal
            if self._sim_phase_est is not None and is_nrem:
                # Push synthetic SO-frequency sample with noise
                t_s = _sim_sample_idx / 250.0
                so_sample = 50.0 * np.cos(2*np.pi*_so_freq*t_s) + np.random.randn()*10
                so_phase = self._sim_phase_est.push_and_estimate(so_sample)
                _sim_sample_idx += 1
            else:
                so_phase = float(np.random.uniform(0, 2*np.pi))
            # Realistic physiology from phase estimator (v10.3 fix)
            # Spindle probability: peaks during SO up-state (phase ~π), not random
            # Arousal: lower during deep SO troughs, rises near transitions
            if is_nrem and self._sim_phase_est is not None:
                # Spindle probability correlated with SO phase (peaks at up-state)
                phase_factor = max(0, np.cos(so_phase - np.pi))  # peaks at π
                spindle_base = 0.05 + 0.45 * phase_factor  # 0.05-0.50
                spindle_noise = np.random.normal(0, 0.05)
                spindle_prob = float(np.clip(spindle_base + spindle_noise, 0, 1))
                # Arousal: low during stable NREM, higher near stage transitions
                hours_elapsed = self._sim_t / 3600
                fatigue = min(1.0, hours_elapsed / 8.0)
                arousal_base = 0.05 + 0.10 * fatigue  # rises overnight
                arousal_noise = np.random.exponential(0.03)
                arousal_risk = float(np.clip(arousal_base + arousal_noise, 0, 1))
            elif is_nrem:
                spindle_prob = float(np.random.beta(2, 5))
                arousal_risk = float(np.random.beta(1.5, 8))
            else:
                spindle_prob = 0.05
                arousal_risk = 0.4
            self.process_json({
                "timestamp": time.time(), "stage": stage,
                "so_phase": so_phase,
                "spindle_prob": spindle_prob,
                "arousal_risk": arousal_risk,
                "eeg_channels": {"C3":float(np.random.randn()*50)},
                "hrv_rmssd": float(np.random.normal(45,10)),
                "accel_rms": float(abs(np.random.normal(0.01,0.005))),
            })
            time.sleep(dt)

__all__ = ["HardwareInterface", "BrainStateSnapshot", "ModelRegistry", "PredictionModel"]
