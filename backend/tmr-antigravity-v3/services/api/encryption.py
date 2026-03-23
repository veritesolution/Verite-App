"""
Application-layer field encryption using Fernet (AES-128-CBC + HMAC-SHA256).
Used for sensitive database columns (ailment text, emotion data, plan content).
"""

import base64
import os
from typing import Optional
from cryptography.fernet import Fernet, InvalidToken
import structlog

log = structlog.get_logger()

_fernet: Optional[Fernet] = None


def _get_fernet() -> Fernet:
    global _fernet
    if _fernet is None:
        from .config import settings
        key = settings.FERNET_KEY
        if not key:
            # Auto-generate in dev. MUST be set in production.
            key = Fernet.generate_key().decode()
            log.warning("fernet_key_missing", action="auto_generated_for_dev")
        _fernet = Fernet(key.encode() if isinstance(key, str) else key)
    return _fernet


def encrypt_field(value: str) -> bytes:
    """Encrypt a string field for database storage."""
    if not value:
        return b""
    return _get_fernet().encrypt(value.encode("utf-8"))


def decrypt_field(value: Optional[bytes]) -> Optional[str]:
    """Decrypt a database field. Returns None on failure (never raises)."""
    if not value:
        return None
    try:
        return _get_fernet().decrypt(value).decode("utf-8")
    except (InvalidToken, Exception) as e:
        log.error("decryption_failed", error=str(e))
        return None


def generate_new_key() -> str:
    """Generate a new Fernet key. Run once and store in environment."""
    return Fernet.generate_key().decode()
