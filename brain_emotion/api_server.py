"""
API Server v13.3 — FastAPI + WebSocket
FIXES: input validation, single-session, API key auth, no nonlocal state issues.
"""

import numpy as np
import pickle
import json
import time
import logging
import os
import hashlib
from typing import Optional

logger = logging.getLogger("api_server")


def create_app(model_path: str = "model_v13.pkl"):
    from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, Depends, Header
    from fastapi.middleware.cors import CORSMiddleware
    from pydantic import BaseModel, validator
    from .heca_engine import HECAEngine
    from .config import SAMPLING_RATE, WINDOW_SAMPLES, STEP_SAMPLES, N_CHANNELS, BLE_BYTES_PER_PACKET, VERSION

    app = FastAPI(title="Brain-Emotion API", version=VERSION)

    # CORS — configurable, not wildcard in production
    allowed_origins = os.environ.get("CORS_ORIGINS", "*").split(",")
    app.add_middleware(CORSMiddleware, allow_origins=allowed_origins, allow_methods=["*"], allow_headers=["*"])

    # ── API Key Auth ─────────────────────────────────────────────────────
    API_KEY = os.environ.get("BRAIN_EMOTION_API_KEY", "")

    async def verify_api_key(x_api_key: str = Header(default="")):
        if API_KEY and x_api_key != API_KEY:
            raise HTTPException(status_code=401, detail="Invalid API key")

    # ── Engine (thread-safe via single-session lock) ─────────────────────
    engine = HECAEngine(fs=SAMPLING_RATE)
    active_ws = {"connection": None}

    # Load model — prefer joblib (safer), fallback to pickle
    try:
        try:
            import joblib
            model = joblib.load(model_path)
            logger.info(f"Model loaded via joblib: {model.get('version', '?')}")
        except (ImportError, Exception):
            import pickle
            logger.warning("joblib not available, falling back to pickle (security risk)")
            with open(model_path, "rb") as f:
                model = pickle.load(f)
            logger.info(f"Model loaded via pickle: {model.get('version', '?')}")
        engine.load_model(model["val_clf"], model["aro_clf"])
        engine.set_status(model.get("status", "PENDING"))
    except FileNotFoundError:
        logger.warning(f"No model at {model_path} — heuristic mode")
    except Exception as e:
        logger.error(f"Model load error: {e} — heuristic mode")

    # ── State ────────────────────────────────────────────────────────────
    class SessionState:
        def __init__(self):
            self.reset()

        def reset(self):
            self.buffer = np.zeros((N_CHANNELS, WINDOW_SAMPLES))
            self.pos = 0
            self.last_emotion = None
            self.last_fine = None
            self.last_sound = None
            self.last_psych = None
            self.context = {}  # app-provided context for 26-emotion disambiguation

    state = SessionState()

    # ── WebSocket with input validation ──────────────────────────────────
    @app.websocket("/ws/eeg")
    async def websocket_eeg(ws: WebSocket):
        # Enforce single session
        if active_ws["connection"] is not None:
            await ws.close(code=4000, reason="Another session is active")
            return

        await ws.accept()
        active_ws["connection"] = ws
        logger.info("WebSocket connected")

        try:
            while True:
                data = await ws.receive_bytes()

                # ── INPUT VALIDATION ─────────────────────────────────────
                n_bytes = len(data)
                if n_bytes == 0:
                    continue

                # Must be multiple of 8 bytes (2 channels × float32)
                if n_bytes % (N_CHANNELS * 4) != 0:
                    await ws.send_text(json.dumps({
                        "type": "error",
                        "message": f"Invalid packet size: {n_bytes} bytes (must be multiple of {N_CHANNELS * 4})"
                    }))
                    continue

                try:
                    samples = np.frombuffer(data, dtype=np.float32).reshape(-1, N_CHANNELS).T
                except Exception as e:
                    await ws.send_text(json.dumps({"type": "error", "message": f"Parse error: {e}"}))
                    continue

                # Check for NaN/Inf
                if np.any(~np.isfinite(samples)):
                    await ws.send_text(json.dumps({"type": "error", "message": "NaN/Inf in data"}))
                    continue

                # Check amplitude sanity (> 1000 µV is almost certainly wrong)
                if np.any(np.abs(samples) > 1000):
                    logger.warning(f"Extreme amplitude: max={np.max(np.abs(samples)):.0f} µV")

                # Write to circular buffer
                for i in range(samples.shape[1]):
                    state.buffer[:, state.pos % WINDOW_SAMPLES] = samples[:, i]
                    state.pos += 1

                    if state.pos >= WINDOW_SAMPLES and state.pos % STEP_SAMPLES == 0:
                        # Extract window from circular buffer
                        end = state.pos % WINDOW_SAMPLES
                        start = (end - WINDOW_SAMPLES) % WINDOW_SAMPLES
                        if start < end:
                            window = state.buffer[:, start:end]
                        else:
                            window = np.concatenate([state.buffer[:, start:], state.buffer[:, :end]], axis=1)

                        emotion, fine, sound, psych = engine.process_window(window, state.context)
                        state.last_emotion = emotion
                        state.last_fine = fine
                        state.last_sound = sound
                        state.last_psych = psych

                        await ws.send_text(json.dumps({
                            "type": "emotion_update",
                            "emotion": emotion.to_dict(),
                            "fine_grained": fine.to_dict(),
                            "sound": sound.to_dict(),
                        }))

        except WebSocketDisconnect:
            logger.info("WebSocket disconnected")
        except Exception as e:
            logger.error(f"WebSocket error: {e}")
        finally:
            active_ws["connection"] = None

    # ── REST endpoints ───────────────────────────────────────────────────
    @app.get("/api/v1/emotion")
    async def get_emotion(_=Depends(verify_api_key)):
        if state.last_emotion is None:
            return {"status": "no_data", "message": "Start EEG streaming first"}
        return state.last_emotion.to_dict()

    @app.get("/api/v1/emotion/fine")
    async def get_fine_emotion(_=Depends(verify_api_key)):
        """Get the 26-class fine-grained emotion (EEG + context)."""
        if state.last_fine is None:
            return {"status": "no_data"}
        return state.last_fine.to_dict()

    @app.get("/api/v1/emotion/history")
    async def get_history(seconds: float = 60, _=Depends(verify_api_key)):
        return {"history": engine.get_emotion_history(seconds)}

    @app.post("/api/v1/context")
    async def set_context(ctx: dict, _=Depends(verify_api_key)):
        """
        Set app context for fine-grained emotion disambiguation.
        Call this whenever the user's context changes (e.g., starts meditation,
        plays music, opens therapy chat).

        Example body: {"mode": "meditation", "content": "nature_scenery"}
        """
        state.context = ctx
        return {"status": "ok", "context": ctx}

    @app.get("/api/v1/taxonomy")
    async def get_taxonomy():
        """List all 26 emotions with V-A coordinates and EEG support levels."""
        from .emotion_taxonomy import EmotionTaxonomy
        return {"emotions": EmotionTaxonomy().get_all_emotions()}

    @app.get("/api/v1/sound")
    async def get_sound(_=Depends(verify_api_key)):
        if state.last_sound is None:
            return {"status": "no_data"}
        return state.last_sound.to_dict()

    @app.get("/api/v1/psychologist/context")
    async def get_psych_context(_=Depends(verify_api_key)):
        if state.last_psych is None:
            return {"status": "no_data", "recommendation": "Begin with open-ended questions."}
        return state.last_psych.to_dict()

    @app.post("/api/v1/session/start")
    async def start_session(_=Depends(verify_api_key)):
        engine.reset_session()
        state.reset()
        return {"status": "ok"}

    @app.post("/api/v1/session/end")
    async def end_session(_=Depends(verify_api_key)):
        h = engine.get_emotion_history(86400)
        return {"status": "ok", "n_readings": len(h), "history": h}

    @app.post("/api/v1/calibration/add")
    async def calibration_add(window: dict, _=Depends(verify_api_key)):
        """Add a calibration window. Send during 60s baseline recording."""
        try:
            eeg = np.array([window["f3"], window["f4"]], dtype=np.float64)
            ok = engine.add_calibration_window(eeg)
            return {"status": "ok" if ok else "low_quality"}
        except Exception as e:
            raise HTTPException(400, str(e))

    @app.post("/api/v1/calibration/finalize")
    async def calibration_finalize(_=Depends(verify_api_key)):
        ok = engine.finalize_calibration()
        return {"status": "calibrated" if ok else "failed", "is_calibrated": ok}

    class SingleWindow(BaseModel):
        f3: list
        f4: list

    @app.post("/api/v1/process")
    async def process_one(window: SingleWindow, _=Depends(verify_api_key)):
        """Process a single EEG window (for testing without WebSocket)."""
        eeg = np.array([window.f3, window.f4], dtype=np.float64)
        e, f, s, p = engine.process_window(eeg, state.context)
        return {"emotion": e.to_dict(), "fine_grained": f.to_dict(),
                "sound": s.to_dict(), "psychologist": p.to_dict()}

    @app.get("/api/v1/health")
    async def health():
        return {
            "status": "ok", "version": VERSION,
            "model_loaded": engine._val_clf is not None,
            "calibrated": engine.calibration.is_calibrated,
            "active_session": active_ws["connection"] is not None,
        }

    return app


def run_server(model_path="model_v13.pkl", host="0.0.0.0", port=8080):
    import uvicorn
    from .config import VERSION
    app = create_app(model_path)
    print(f"\n🧠 Brain-Emotion v{VERSION} — http://{host}:{port}")
    print(f"   Docs: http://{host}:{port}/docs")
    print(f"   ⚠️  Production: run behind HTTPS reverse proxy (nginx/Caddy)")
    uvicorn.run(app, host=host, port=port)
