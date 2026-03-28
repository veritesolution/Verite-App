"""
Verite Production API — FastAPI Application
REST API + WebSocket endpoints for Kotlin/Android mobile client.
"""
from __future__ import annotations

import asyncio
import logging
import time
from contextlib import asynccontextmanager
from typing import Any, Dict, Optional

from fastapi import (
    Depends,
    FastAPI,
    Header,
    HTTPException,
    WebSocket,
    WebSocketDisconnect,
    status,
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from . import __version__
from .auth import (
    authenticate_user,
    create_access_token,
    create_refresh_token,
    create_user,
    decode_token,
    init_user_db,
    revoke_refresh_token,
)
from .config import Settings, ensure_directories, get_settings
from .engine import VeriteEngine, init_eval_metrics
from .llm import LLMRouter, create_provider
from .models import (
    AuthLoginRequest,
    AuthRefreshRequest,
    AuthRegisterRequest,
    AuthTokenResponse,
    ChatMessageRequest,
    ChatMessageResponse,
    CrisisResponse,
    FeedbackRequest,
    FeedbackResponse,
    HealthResponse,
    SessionCreateResponse,
    SessionDetailResponse,
    SessionListResponse,
    WSIncomingMessage,
    WSOutgoingMessage,
)
from .rag import RAGRetriever
from .safety import init_crisis_semantic, init_toxicity_model
from .session import SessionManager

logger = logging.getLogger("verite.main")

# ═══════════════════════════════════════════════════════════════
# Global state (initialized at startup)
# ═══════════════════════════════════════════════════════════════

_engine: Optional[VeriteEngine] = None
_session_mgr: Optional[SessionManager] = None
_startup_time: float = 0.0


# ═══════════════════════════════════════════════════════════════
# Startup / Shutdown
# ═══════════════════════════════════════════════════════════════

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifecycle — initialize all components at startup."""
    global _engine, _session_mgr, _startup_time
    _startup_time = time.monotonic()

    settings = get_settings()
    ensure_directories(settings)

    # ── Initialize user database (Fix #6) ─────────────────
    init_user_db("./data/verite_users.db")

    logging.basicConfig(
        level=getattr(logging, settings.app_log_level.upper(), logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    logger.info(f"Starting Verite API v{__version__} ({settings.app_env})")

    # ── Initialize sentence transformer ───────────────────
    sem_model = None
    try:
        from sentence_transformers import SentenceTransformer
        sem_model = SentenceTransformer("all-MiniLM-L6-v2")
        logger.info("Sentence transformer loaded")
    except Exception as e:
        logger.warning(f"Sentence transformer unavailable: {e}")

    # ── Initialize safety systems ─────────────────────────
    init_crisis_semantic(sem_model, settings.faiss_cache_dir)
    init_toxicity_model()
    init_eval_metrics(sem_model)

    # ── Initialize RAG ────────────────────────────────────
    rag = RAGRetriever()
    rag.init(sem_model, settings.faiss_cache_dir)

    # Load datasets in background (can be slow)
    async def _load_datasets():
        try:
            await asyncio.get_event_loop().run_in_executor(
                None, rag.load_datasets, settings.faiss_cache_dir
            )
        except Exception as e:
            logger.warning(f"Background dataset loading failed: {e}")

    asyncio.create_task(_load_datasets())

    # ── Initialize LLM Router ─────────────────────────────
    llm_router = LLMRouter()
    for provider_name in settings.available_providers:
        try:
            provider = create_provider(
                name=provider_name,
                api_key=settings.get_api_key(provider_name),
                temperature=settings.llm_temperature,
                max_tokens=settings.llm_max_tokens,
            )
            llm_router.add_provider(provider)
        except Exception as e:
            logger.error(f"Failed to init provider {provider_name}: {e}")

    if not llm_router.providers:
        logger.warning("No LLM providers configured! Only fallback will work.")

    # ── Initialize Engine + Session Manager ───────────────
    _engine = VeriteEngine(llm_router, rag, settings)
    _session_mgr = SessionManager(persist_dir="./data/sessions")

    # ── Periodic session cleanup ──────────────────────────
    async def _cleanup_loop():
        while True:
            await asyncio.sleep(3600)  # Every hour
            if _session_mgr:
                await _session_mgr.cleanup_expired()

    asyncio.create_task(_cleanup_loop())

    logger.info(f"Verite API ready | LLM: {llm_router.active_provider} | RAG: {rag.health_status()}")

    yield  # Application is running

    # Shutdown
    logger.info("Verite API shutting down")


# ═══════════════════════════════════════════════════════════════
# FastAPI App
# ═══════════════════════════════════════════════════════════════

app = FastAPI(
    title="Verite Mental Health Support API",
    description=(
        "AI-powered mental health support companion API. "
        "Uses CBT, DBT, and MI principles with multi-layer safety. "
        "NOT a medical device. NOT a substitute for professional help."
    ),
    version=__version__,
    lifespan=lifespan,
)


def _setup_cors(app: FastAPI) -> None:
    """Configure CORS for mobile app access."""
    settings = get_settings()
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )


_setup_cors(app)


# ═══════════════════════════════════════════════════════════════
# Dependencies
# ═══════════════════════════════════════════════════════════════

async def get_current_user(authorization: str = Header(None)) -> Dict[str, Any]:
    """Extract and validate JWT from Authorization header."""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid authorization header",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = authorization.split(" ", 1)[1]
    settings = get_settings()
    payload = decode_token(token, settings.jwt_secret, settings.jwt_algorithm)

    if payload is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if payload.get("type") != "access":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token type",
        )

    return payload


# ═══════════════════════════════════════════════════════════════
# Auth Endpoints
# ═══════════════════════════════════════════════════════════════

@app.post("/api/v1/auth/register", response_model=AuthTokenResponse, tags=["Auth"])
async def register(req: AuthRegisterRequest):
    """Register a new user account."""
    try:
        user = await create_user(req.username, req.password, req.display_name)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    settings = get_settings()
    access = create_access_token(
        {"sub": user["username"]}, settings.jwt_secret,
        settings.jwt_algorithm, settings.jwt_expiry_hours,
    )
    refresh = create_refresh_token(
        {"sub": user["username"]}, settings.jwt_secret,
        settings.jwt_algorithm, settings.jwt_refresh_expiry_days,
    )

    return AuthTokenResponse(
        access_token=access,
        refresh_token=refresh,
        expires_in=settings.jwt_expiry_hours * 3600,
    )


@app.post("/api/v1/auth/login", response_model=AuthTokenResponse, tags=["Auth"])
async def login(req: AuthLoginRequest):
    """Authenticate and get tokens. Rate limited to 5 attempts per 15 minutes."""
    user = await authenticate_user(req.username, req.password)
    if not user:
        raise HTTPException(
            status_code=401,
            detail="Invalid credentials or too many login attempts. Try again later."
        )

    settings = get_settings()
    access = create_access_token(
        {"sub": user["username"]}, settings.jwt_secret,
        settings.jwt_algorithm, settings.jwt_expiry_hours,
    )
    refresh = create_refresh_token(
        {"sub": user["username"]}, settings.jwt_secret,
        settings.jwt_algorithm, settings.jwt_refresh_expiry_days,
    )

    return AuthTokenResponse(
        access_token=access,
        refresh_token=refresh,
        expires_in=settings.jwt_expiry_hours * 3600,
    )


@app.post("/api/v1/auth/refresh", response_model=AuthTokenResponse, tags=["Auth"])
async def refresh_token(req: AuthRefreshRequest):
    """Refresh an access token. Revokes the old refresh token."""
    settings = get_settings()
    payload = decode_token(req.refresh_token, settings.jwt_secret, settings.jwt_algorithm)

    if not payload or payload.get("type") != "refresh":
        raise HTTPException(status_code=401, detail="Invalid refresh token")

    # FIX #6c: Revoke the old refresh token so it can't be reused
    revoke_refresh_token(req.refresh_token, settings.jwt_secret, settings.jwt_algorithm)

    access = create_access_token(
        {"sub": payload["sub"]}, settings.jwt_secret,
        settings.jwt_algorithm, settings.jwt_expiry_hours,
    )
    refresh = create_refresh_token(
        {"sub": payload["sub"]}, settings.jwt_secret,
        settings.jwt_algorithm, settings.jwt_refresh_expiry_days,
    )

    return AuthTokenResponse(
        access_token=access,
        refresh_token=refresh,
        expires_in=settings.jwt_expiry_hours * 3600,
    )


# ═══════════════════════════════════════════════════════════════
# Chat Endpoints
# ═══════════════════════════════════════════════════════════════

@app.post(
    "/api/v1/chat/message",
    response_model=ChatMessageResponse | CrisisResponse,
    tags=["Chat"],
)
async def send_message(
    req: ChatMessageRequest,
    user: Dict = Depends(get_current_user),
):
    """
    Send a chat message and get a therapeutic response.
    Creates a new session if session_id is not provided.
    """
    username = user["sub"]

    # Get or create session
    session = None
    if req.session_id:
        session = await _session_mgr.get_session(req.session_id, username)
        if not session:
            raise HTTPException(status_code=404, detail="Session not found")
    else:
        session = await _session_mgr.create_session(username)

    # Process message
    response = await _engine.process_message(req.message, session)

    # Auto-save session
    await _session_mgr.save_session(session.session_id)

    return response


@app.post("/api/v1/chat/session", response_model=SessionCreateResponse, tags=["Chat"])
async def create_session(user: Dict = Depends(get_current_user)):
    """Create a new chat session."""
    session = await _session_mgr.create_session(user["sub"])
    return SessionCreateResponse(
        session_id=session.session_id,
        created_at=session.created_at.isoformat(),
    )


@app.get("/api/v1/chat/sessions", response_model=SessionListResponse, tags=["Chat"])
async def list_sessions(user: Dict = Depends(get_current_user)):
    """List all sessions for the current user."""
    sessions = await _session_mgr.list_sessions(user["sub"])
    return SessionListResponse(sessions=sessions, total=len(sessions))


@app.get("/api/v1/chat/session/{session_id}", tags=["Chat"])
async def get_session(session_id: str, user: Dict = Depends(get_current_user)):
    """Get detailed session data."""
    session = await _session_mgr.get_session(session_id, user["sub"])
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    return session.to_export()


@app.delete("/api/v1/chat/session/{session_id}", tags=["Chat"])
async def delete_session(session_id: str, user: Dict = Depends(get_current_user)):
    """Delete a session."""
    deleted = await _session_mgr.delete_session(session_id, user["sub"])
    if not deleted:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"status": "deleted"}


# ═══════════════════════════════════════════════════════════════
# Feedback Endpoint
# ═══════════════════════════════════════════════════════════════

@app.post("/api/v1/feedback", response_model=FeedbackResponse, tags=["Feedback"])
async def submit_feedback(
    req: FeedbackRequest,
    user: Dict = Depends(get_current_user),
):
    """Submit feedback for a specific turn in a session."""
    # In production, this would write to a database
    logger.info(
        f"Feedback: session={req.session_id} turn={req.turn} "
        f"rating={req.rating} user={user['sub']}"
    )
    return FeedbackResponse(status="recorded")


# ═══════════════════════════════════════════════════════════════
# WebSocket (for real-time chat from Kotlin app)
# ═══════════════════════════════════════════════════════════════

@app.websocket("/api/v1/ws/chat")
async def websocket_chat(ws: WebSocket):
    """
    WebSocket endpoint for real-time chat.
    Kotlin client connects, authenticates via first message, then streams.
    """
    await ws.accept()
    session = None
    username = None

    try:
        # First message must be auth
        auth_data = await ws.receive_json()
        token = auth_data.get("token", "")
        settings = get_settings()
        payload = decode_token(token, settings.jwt_secret, settings.jwt_algorithm)

        if not payload or payload.get("type") != "access":
            await ws.send_json({"type": "error", "error": "Authentication failed"})
            await ws.close(code=4001)
            return

        username = payload["sub"]
        session_id = auth_data.get("session_id")

        if session_id:
            session = await _session_mgr.get_session(session_id, username)
        if not session:
            session = await _session_mgr.create_session(username)

        await ws.send_json({
            "type": "session_created",
            "session_id": session.session_id,
        })

        # Message loop
        while True:
            data = await ws.receive_json()
            msg_type = data.get("type", "message")

            if msg_type == "ping":
                await ws.send_json({"type": "pong"})
                continue

            if msg_type == "end_session":
                await _session_mgr.save_session(session.session_id)
                await ws.send_json({"type": "session_ended"})
                break

            if msg_type == "message":
                content = data.get("content", "").strip()
                if not content:
                    await ws.send_json({"type": "error", "error": "Empty message"})
                    continue

                response = await _engine.process_message(content, session)

                if isinstance(response, CrisisResponse):
                    await ws.send_json({
                        "type": "crisis",
                        "content": response.message,
                        "session_id": session.session_id,
                        "resources": response.resources,
                    })
                else:
                    await ws.send_json({
                        "type": "response",
                        "content": response.response,
                        "session_id": response.session_id,
                        "analysis": response.analysis.model_dump() if response.analysis else None,
                        "safety": response.safety.model_dump() if response.safety else None,
                        "metrics": response.metrics.model_dump() if response.metrics else None,
                    })

    except WebSocketDisconnect:
        logger.info(f"WebSocket disconnected: user={username}")
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        try:
            await ws.send_json({"type": "error", "error": "Internal server error"})
        except Exception:
            pass
    finally:
        if session:
            await _session_mgr.save_session(session.session_id)


# ═══════════════════════════════════════════════════════════════
# Health Check
# ═══════════════════════════════════════════════════════════════

@app.get("/api/v1/health", response_model=HealthResponse, tags=["System"])
async def health_check():
    """System health check — use for monitoring and mobile app connectivity test."""
    from .safety.crisis import detect_crisis_regex, _crisis_faiss_index
    from .safety.content import _detox_model

    safety_checks = {}
    # Crisis regex
    try:
        r = detect_crisis_regex("I want to kill myself")
        safety_checks["crisis_regex"] = "PASS" if r.is_crisis else "FAIL"
    except Exception:
        safety_checks["crisis_regex"] = "ERROR"

    # Crisis negative
    try:
        r = detect_crisis_regex("I feel sad today")
        safety_checks["crisis_negative"] = "PASS" if not r.is_crisis else "FAIL"
    except Exception:
        safety_checks["crisis_negative"] = "ERROR"

    # Toxicity
    safety_checks["toxicity_model"] = "loaded" if _detox_model else "unavailable"
    safety_checks["crisis_faiss"] = "loaded" if _crisis_faiss_index else "unavailable"

    uptime = time.monotonic() - _startup_time

    return HealthResponse(
        status="healthy",
        version=__version__,
        llm_provider=_engine.llm.active_provider if _engine else "none",
        llm_available=bool(_engine and _engine.llm.providers),
        safety_checks=safety_checks,
        rag_status=_engine.rag.health_status() if _engine else {},
        uptime_seconds=round(uptime, 2),
    )


@app.get("/", tags=["System"])
async def root():
    """Root endpoint — API info."""
    return {
        "name": "Verite Mental Health Support API",
        "version": __version__,
        "status": "running",
        "docs": "/docs",
        "disclaimer": (
            "This is an AI support tool, NOT a medical device. "
            "NOT a substitute for professional mental health care."
        ),
    }
