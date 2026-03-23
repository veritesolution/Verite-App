"""
FastAPI dependency injection functions.
Handles JWT authentication, DB sessions, rate limiting, and audit logging.
"""

import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional, Annotated

import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
import structlog

from .config import settings
from .database import get_db
from .models import User, ConsentRecord, ConsentStatus, AuditLog

log = structlog.get_logger()
security = HTTPBearer()


# ── Token utilities ───────────────────────────────────────────────────────────

def create_access_token(user_id: uuid.UUID, extra: dict = {}) -> str:
    payload = {
        "sub": str(user_id),
        "exp": datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES),
        "iat": datetime.now(timezone.utc),
        "type": "access",
        **extra,
    }
    return jwt.encode(payload, settings.SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def create_refresh_token(user_id: uuid.UUID) -> str:
    payload = {
        "sub": str(user_id),
        "exp": datetime.now(timezone.utc) + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS),
        "iat": datetime.now(timezone.utc),
        "type": "refresh",
    }
    return jwt.encode(payload, settings.SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def decode_token(token: str, expected_type: str = "access") -> dict:
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.JWT_ALGORITHM])
        if payload.get("type") != expected_type:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token type")
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")


# ── Auth dependencies ─────────────────────────────────────────────────────────

async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials, Depends(security)],
    db: AsyncSession = Depends(get_db),
) -> User:
    payload = decode_token(credentials.credentials)
    user_id = uuid.UUID(payload["sub"])
    result = await db.execute(select(User).where(User.id == user_id, User.is_active == True))
    user = result.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found or inactive")
    return user


async def get_current_researcher(
    user: User = Depends(get_current_user),
) -> User:
    if not user.is_researcher:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Researcher access required")
    return user


async def require_consent(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> User:
    """Ensures user has given valid consent before accessing clinical features."""
    result = await db.execute(
        select(ConsentRecord).where(ConsentRecord.user_id == user.id)
    )
    consent = result.scalar_one_or_none()
    if not consent or consent.status != ConsentStatus.GIVEN:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Informed consent is required before using this feature. "
                   "Please complete the consent process at /users/consent.",
        )
    return user


# ── Audit logging ─────────────────────────────────────────────────────────────

async def log_action(
    db: AsyncSession,
    action: str,
    user_id: Optional[uuid.UUID] = None,
    resource_type: Optional[str] = None,
    resource_id: Optional[str] = None,
    request: Optional[Request] = None,
    outcome: str = "success",
    detail: Optional[dict] = None,
):
    """Write an audit log entry. Used throughout routers for HIPAA compliance."""
    entry = AuditLog(
        user_id=user_id,
        action=action,
        resource_type=resource_type,
        resource_id=str(resource_id) if resource_id else None,
        ip_address=request.client.host if request else None,
        user_agent=request.headers.get("user-agent") if request else None,
        outcome=outcome,
        detail=detail,
    )
    db.add(entry)
    # Flush immediately so audit entries are never lost on rollback of main txn
    await db.flush()
    log.info("audit", action=action, user_id=str(user_id), outcome=outcome)
