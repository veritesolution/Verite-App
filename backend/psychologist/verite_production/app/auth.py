"""
Verite Production API — Authentication v2.1
JWT auth with SQLite user store, login rate limiting, and refresh token revocation.

v2.1 peer-review fixes:
- #6a: SQLite user store instead of in-memory dict
- #6b: Login rate limiting (5 attempts per 15 min per username)
- #6c: Refresh token revocation on re-issue
- #6d: Password complexity requirements
"""
from __future__ import annotations

import logging
import re
import sqlite3
import time
import threading
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional

import jwt
from passlib.context import CryptContext

logger = logging.getLogger("verite.auth")

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


# ═══════════════════════════════════════════════════════════════
# SQLite User Store
# ═══════════════════════════════════════════════════════════════

_db_path: str = "./data/verite_users.db"
_db_lock = threading.Lock()


def init_user_db(db_path: str = "./data/verite_users.db") -> None:
    """Initialize SQLite user database."""
    global _db_path
    _db_path = db_path
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)

    with sqlite3.connect(db_path) as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS users (
                username TEXT PRIMARY KEY,
                password_hash TEXT NOT NULL,
                display_name TEXT,
                created_at TEXT NOT NULL,
                is_active INTEGER DEFAULT 1
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS revoked_tokens (
                token_jti TEXT PRIMARY KEY,
                revoked_at TEXT NOT NULL,
                expires_at TEXT NOT NULL
            )
        """)
        # Cleanup old revoked tokens periodically
        conn.execute("""
            DELETE FROM revoked_tokens WHERE expires_at < datetime('now')
        """)
        conn.commit()
    logger.info(f"User database initialized: {db_path}")


def _get_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(_db_path)
    conn.row_factory = sqlite3.Row
    return conn


# ═══════════════════════════════════════════════════════════════
# Password Validation
# ═══════════════════════════════════════════════════════════════

def validate_password(password: str) -> Optional[str]:
    """
    Validate password complexity. Returns error message or None if valid.
    Requirements: 8+ chars, at least 1 letter, at least 1 digit.
    """
    if len(password) < 8:
        return "Password must be at least 8 characters"
    if len(password) > 128:
        return "Password must be at most 128 characters"
    if not re.search(r"[a-zA-Z]", password):
        return "Password must contain at least one letter"
    if not re.search(r"\d", password):
        return "Password must contain at least one digit"
    return None


# ═══════════════════════════════════════════════════════════════
# Login Rate Limiting
# ═══════════════════════════════════════════════════════════════

_login_attempts: Dict[str, List[float]] = defaultdict(list)
_rate_lock = threading.Lock()

MAX_LOGIN_ATTEMPTS = 5
RATE_WINDOW_SECONDS = 900  # 15 minutes


def _check_login_rate(username: str) -> bool:
    """Returns True if login attempt is allowed, False if rate-limited."""
    now = time.monotonic()
    with _rate_lock:
        attempts = _login_attempts[username]
        # Remove old attempts
        _login_attempts[username] = [t for t in attempts if now - t < RATE_WINDOW_SECONDS]
        if len(_login_attempts[username]) >= MAX_LOGIN_ATTEMPTS:
            return False
        _login_attempts[username].append(now)
        return True


def _record_failed_login(username: str) -> None:
    """Record a failed login attempt (already added by _check_login_rate)."""
    pass  # Attempt was recorded in _check_login_rate


def _clear_login_attempts(username: str) -> None:
    """Clear login attempts on successful login."""
    with _rate_lock:
        _login_attempts.pop(username, None)


# ═══════════════════════════════════════════════════════════════
# User CRUD
# ═══════════════════════════════════════════════════════════════

async def create_user(username: str, password: str, display_name: str = "") -> Dict[str, Any]:
    """Create a new user. Raises ValueError on failure."""
    # Validate password
    pwd_error = validate_password(password)
    if pwd_error:
        raise ValueError(pwd_error)

    with _db_lock:
        conn = _get_conn()
        try:
            # Check if exists
            existing = conn.execute(
                "SELECT username FROM users WHERE username = ?", (username,)
            ).fetchone()
            if existing:
                raise ValueError("Username already exists")

            conn.execute(
                "INSERT INTO users (username, password_hash, display_name, created_at) VALUES (?, ?, ?, ?)",
                (username, pwd_context.hash(password), display_name or username, datetime.utcnow().isoformat()),
            )
            conn.commit()
            logger.info(f"User created: {username}")
            return {"username": username, "display_name": display_name or username}
        finally:
            conn.close()


async def authenticate_user(username: str, password: str) -> Optional[Dict[str, Any]]:
    """Authenticate user with rate limiting. Returns user dict or None."""
    # Rate limit check
    if not _check_login_rate(username):
        logger.warning(f"Login rate limited: {username}")
        return None

    with _db_lock:
        conn = _get_conn()
        try:
            row = conn.execute(
                "SELECT username, password_hash, display_name, is_active FROM users WHERE username = ?",
                (username,),
            ).fetchone()
            if not row:
                return None
            if not row["is_active"]:
                return None
            if pwd_context.verify(password, row["password_hash"]):
                _clear_login_attempts(username)
                return {"username": row["username"], "display_name": row["display_name"]}
            return None
        finally:
            conn.close()


# ═══════════════════════════════════════════════════════════════
# JWT Token Management
# ═══════════════════════════════════════════════════════════════

def create_access_token(
    data: Dict[str, Any],
    secret: str,
    algorithm: str = "HS256",
    expires_hours: int = 24,
) -> str:
    """Create a JWT access token."""
    import uuid
    payload = data.copy()
    payload["exp"] = datetime.utcnow() + timedelta(hours=expires_hours)
    payload["type"] = "access"
    payload["jti"] = uuid.uuid4().hex[:16]
    return jwt.encode(payload, secret, algorithm=algorithm)


def create_refresh_token(
    data: Dict[str, Any],
    secret: str,
    algorithm: str = "HS256",
    expires_days: int = 30,
) -> str:
    """Create a JWT refresh token with a unique ID for revocation."""
    import uuid
    payload = data.copy()
    exp = datetime.utcnow() + timedelta(days=expires_days)
    payload["exp"] = exp
    payload["type"] = "refresh"
    payload["jti"] = uuid.uuid4().hex[:16]
    return jwt.encode(payload, secret, algorithm=algorithm)


def decode_token(token: str, secret: str, algorithm: str = "HS256") -> Optional[Dict[str, Any]]:
    """Decode and validate a JWT token. Checks revocation for refresh tokens."""
    try:
        payload = jwt.decode(token, secret, algorithms=[algorithm])

        # Check if refresh token was revoked
        if payload.get("type") == "refresh" and payload.get("jti"):
            if _is_token_revoked(payload["jti"]):
                logger.debug("Refresh token was revoked")
                return None

        return payload
    except jwt.ExpiredSignatureError:
        return None
    except jwt.InvalidTokenError:
        return None


def revoke_refresh_token(token: str, secret: str, algorithm: str = "HS256") -> None:
    """Revoke a refresh token so it can't be used again."""
    try:
        # Decode without verification to get JTI even if expired
        payload = jwt.decode(token, secret, algorithms=[algorithm], options={"verify_exp": False})
        jti = payload.get("jti")
        if jti:
            exp = datetime.utcfromtimestamp(payload.get("exp", 0))
            with _db_lock:
                conn = _get_conn()
                try:
                    conn.execute(
                        "INSERT OR IGNORE INTO revoked_tokens (token_jti, revoked_at, expires_at) VALUES (?, ?, ?)",
                        (jti, datetime.utcnow().isoformat(), exp.isoformat()),
                    )
                    conn.commit()
                finally:
                    conn.close()
    except Exception as e:
        logger.error(f"Token revocation error: {e}")


def _is_token_revoked(jti: str) -> bool:
    """Check if a token JTI has been revoked."""
    with _db_lock:
        conn = _get_conn()
        try:
            row = conn.execute(
                "SELECT token_jti FROM revoked_tokens WHERE token_jti = ?", (jti,)
            ).fetchone()
            return row is not None
        finally:
            conn.close()
