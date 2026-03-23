"""
pytest configuration for TMR-ANTIGRAVITY unit tests.

Sets required environment variables before any imports happen, so
pydantic-settings does not raise ValidationError for missing fields.

Optional hardware/service dependencies (celery, pyaudio, boto3) are
skipped gracefully — unit tests must not require a full stack.
"""

import os
import sys
import pytest

# ── Inject test environment before any app code imports settings ──────────────
os.environ.setdefault("SECRET_KEY",    "test-secret-key-minimum-32-characters-long!!")
os.environ.setdefault("DATABASE_URL",  "postgresql+asyncpg://test:test@localhost/tmr_test")
os.environ.setdefault("FERNET_KEY",    "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=")
os.environ.setdefault("ENVIRONMENT",   "test")
os.environ.setdefault("REDIS_URL",     "redis://localhost:6379/0")
os.environ.setdefault("CELERY_BROKER_URL",      "redis://localhost:6379/1")
os.environ.setdefault("CELERY_RESULT_BACKEND",  "redis://localhost:6379/2")
os.environ.setdefault("CRISIS_HUMAN_HANDOFF_WEBHOOK", "")

# ── Add project root to sys.path ──────────────────────────────────────────────
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)


# ── Skip markers for optional dependencies ────────────────────────────────────

def pytest_collection_modifyitems(config, items):
    """Auto-skip tests whose class or module needs an unavailable package."""
    skip_celery  = pytest.mark.skip(reason="celery not installed in test env")
    skip_pyaudio = pytest.mark.skip(reason="pyaudio not installed in test env")

    for item in items:
        # Tests that import from workers (need celery)
        if "PlanParsing" in item.nodeid or "ModelRouter" in item.nodeid:
            try:
                import celery  # noqa: F401
            except ImportError:
                item.add_marker(skip_celery)

        # Tests that use AudioPlayer (need pyaudio)
        if "AudioPlayer" in item.nodeid:
            try:
                import pyaudio  # noqa: F401
            except ImportError:
                item.add_marker(skip_pyaudio)
