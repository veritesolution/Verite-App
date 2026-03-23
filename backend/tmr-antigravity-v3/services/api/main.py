"""
TMR-ANTIGRAVITY v3.0.0 — FastAPI Application Entry Point
Production-ready backend for the TMR habit change platform.

v3.0.0 enhancements:
  - Deep health check with DB connectivity probe
  - Request ID middleware for distributed tracing
  - Rate limiting via sliding window (Redis-backed in production)
  - Graceful startup/shutdown lifecycle
"""

import uuid
import time
from contextlib import asynccontextmanager
from collections import defaultdict

from fastapi import FastAPI, Request, Response, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse
from prometheus_fastapi_instrumentator import Instrumentator
from starlette.middleware.base import BaseHTTPMiddleware
import structlog
import sentry_sdk
from sentry_sdk.integrations.fastapi import FastApiIntegration
from sentry_sdk.integrations.sqlalchemy import SqlalchemyIntegration

from .config import settings
from .database import engine, Base
from .routers import auth, users, plans, tmr, safety, voice
from ..safety.crisis_gate import CrisisGate

log = structlog.get_logger()


# ── Sentry (error tracking) ──────────────────────────────────────────────────
if settings.SENTRY_DSN:
    sentry_sdk.init(
        dsn=settings.SENTRY_DSN,
        integrations=[FastApiIntegration(), SqlalchemyIntegration()],
        traces_sample_rate=0.1,
        environment=settings.ENVIRONMENT,
    )


# ── Lifespan (startup / shutdown) ────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("startup", env=settings.ENVIRONMENT, version=settings.APP_VERSION)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    CrisisGate()        # warm up keyword trie
    yield
    await engine.dispose()
    log.info("shutdown")


# ── Application ───────────────────────────────────────────────────────────────
app = FastAPI(
    title="TMR-ANTIGRAVITY API",
    version=settings.APP_VERSION,
    description=(
        "Evidence-based AI habit change platform with optional TMR sleep protocol. "
        "⚠ Not a medical device. Not a substitute for professional clinical care."
    ),
    docs_url="/docs" if settings.ENVIRONMENT != "production" else None,
    redoc_url="/redoc" if settings.ENVIRONMENT != "production" else None,
    lifespan=lifespan,
)


# ── Request ID Middleware ─────────────────────────────────────────────────────
class RequestIDMiddleware(BaseHTTPMiddleware):
    """Adds X-Request-ID header for distributed tracing."""
    async def dispatch(self, request: Request, call_next):
        request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
        structlog.contextvars.clear_contextvars()
        structlog.contextvars.bind_contextvars(request_id=request_id)
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        return response


# ── In-Memory Rate Limiter (use Redis in production cluster) ──────────────────
class RateLimitMiddleware(BaseHTTPMiddleware):
    """
    Sliding window rate limiter per client IP.
    In a multi-instance deployment, replace with Redis-backed limiter.
    """
    def __init__(self, app, requests_per_minute: int = 60, burst: int = 10):
        super().__init__(app)
        self._rpm = requests_per_minute
        self._burst = burst
        self._windows: dict[str, list[float]] = defaultdict(list)

    async def dispatch(self, request: Request, call_next):
        # Skip rate limiting for health/metrics endpoints
        if request.url.path in ("/health", "/metrics"):
            return await call_next(request)

        client_ip = request.client.host if request.client else "unknown"
        now = time.time()
        window = self._windows[client_ip]

        # Prune entries older than 60s
        cutoff = now - 60.0
        self._windows[client_ip] = window = [t for t in window if t > cutoff]

        if len(window) >= self._rpm + self._burst:
            return JSONResponse(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                content={"detail": "Rate limit exceeded. Please slow down."},
                headers={"Retry-After": "60"},
            )

        window.append(now)
        response = await call_next(request)

        response.headers["X-RateLimit-Limit"] = str(self._rpm)
        response.headers["X-RateLimit-Remaining"] = str(max(0, self._rpm - len(window)))
        return response


# ── Middleware Stack ──────────────────────────────────────────────────────────
app.add_middleware(RequestIDMiddleware)
app.add_middleware(
    RateLimitMiddleware,
    requests_per_minute=settings.RATE_LIMIT_REQUESTS_PER_MINUTE,
    burst=settings.RATE_LIMIT_BURST,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(GZipMiddleware, minimum_size=1000)

# ── Prometheus metrics ────────────────────────────────────────────────────────
Instrumentator().instrument(app).expose(app, endpoint="/metrics")


# ── Global exception handlers ─────────────────────────────────────────────────
@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    log.error("unhandled_exception", path=request.url.path, exc=str(exc))
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "Internal server error. Please try again later."},
    )


# ── Routers ───────────────────────────────────────────────────────────────────
app.include_router(auth.router,    prefix="/auth",    tags=["authentication"])
app.include_router(users.router,   prefix="/users",   tags=["users"])
app.include_router(plans.router,   prefix="/plans",   tags=["habit plans"])
app.include_router(tmr.router,     prefix="/tmr",     tags=["TMR sessions"])
app.include_router(safety.router,  prefix="/safety",  tags=["safety"])
app.include_router(voice.router,   prefix="/voice",   tags=["voice interface"])


# ── Health check (deep — includes DB probe) ───────────────────────────────────
@app.get("/health", include_in_schema=False)
async def health():
    """
    Deep health check — verifies DB connectivity, not just process liveness.
    Returns 503 if the database is unreachable.
    """
    checks = {"api": "ok", "version": settings.APP_VERSION}
    try:
        from sqlalchemy import text
        async with engine.connect() as conn:
            await conn.execute(text("SELECT 1"))
        checks["database"] = "ok"
    except Exception as exc:
        checks["database"] = f"error: {str(exc)[:100]}"
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"status": "degraded", **checks},
        )
    return {"status": "ok", **checks}


@app.get("/health/live", include_in_schema=False)
async def liveness():
    """Kubernetes liveness probe — lightweight, no external deps."""
    return {"status": "ok"}
