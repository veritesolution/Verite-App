"""
Centralised configuration via environment variables.
All secrets come from the environment — never hardcode credentials.
"""

from functools import lru_cache
from typing import List, Optional
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # ── App ───────────────────────────────────────────────────────────────────
    APP_VERSION: str = "3.0.0"
    ENVIRONMENT: str = "development"          # development | staging | production
    SECRET_KEY: str                           # 32+ byte random secret
    CORS_ORIGINS: List[str] = ["http://localhost:3000", "http://localhost:8080"]

    # ── Database ──────────────────────────────────────────────────────────────
    DATABASE_URL: str                         # postgresql+asyncpg://user:pass@host/db
    DB_POOL_SIZE: int = 10
    DB_MAX_OVERFLOW: int = 20

    # ── Redis ─────────────────────────────────────────────────────────────────
    REDIS_URL: str = "redis://localhost:6379/0"
    REDIS_PASSWORD: Optional[str] = None

    # ── Celery ────────────────────────────────────────────────────────────────
    CELERY_BROKER_URL: str = "redis://localhost:6379/1"
    CELERY_RESULT_BACKEND: str = "redis://localhost:6379/2"

    # ── JWT Auth ──────────────────────────────────────────────────────────────
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30

    # ── OAuth2 ────────────────────────────────────────────────────────────────
    GOOGLE_CLIENT_ID: Optional[str] = None
    GOOGLE_CLIENT_SECRET: Optional[str] = None
    APPLE_CLIENT_ID: Optional[str] = None
    APPLE_TEAM_ID: Optional[str] = None
    APPLE_KEY_ID: Optional[str] = None
    APPLE_PRIVATE_KEY: Optional[str] = None

    # ── LLM providers ────────────────────────────────────────────────────────
    GROQ_API_KEY: Optional[str] = None
    GEMINI_API_KEY: Optional[str] = None
    OPENAI_API_KEY: Optional[str] = None       # optional tertiary fallback
    LLM_TIMEOUT_SECONDS: int = 30
    LLM_MAX_RETRIES: int = 3

    # ── Voice services ────────────────────────────────────────────────────────
    GOOGLE_CLOUD_PROJECT: Optional[str] = None
    GOOGLE_APPLICATION_CREDENTIALS: Optional[str] = None  # path to service account JSON
    WHISPER_MODEL_SIZE: str = "base"           # tiny|base|small|medium|large
    VOICE_USE_CLOUD: bool = True               # False = offline Whisper only

    # ── Hume AI (emotion analysis) ────────────────────────────────────────────
    HUME_API_KEY: Optional[str] = None

    # ── Storage ───────────────────────────────────────────────────────────────
    AWS_ACCESS_KEY_ID: Optional[str] = None
    AWS_SECRET_ACCESS_KEY: Optional[str] = None
    AWS_REGION: str = "us-east-1"
    S3_BUCKET_AUDIO: str = "tmr-audio-cues"
    S3_BUCKET_EEG: str = "tmr-eeg-raw"
    CLOUDFRONT_DOMAIN: Optional[str] = None    # CDN for audio cues

    # ── Monitoring ────────────────────────────────────────────────────────────
    SENTRY_DSN: Optional[str] = None
    LOG_LEVEL: str = "INFO"

    # ── Safety ────────────────────────────────────────────────────────────────
    PERSPECTIVE_API_KEY: Optional[str] = None  # Google Perspective for toxicity
    CRISIS_HUMAN_HANDOFF_WEBHOOK: Optional[str] = None  # Twilio/Slack webhook
    CRISIS_EMAIL: str = "safety@yourlab.org"

    # ── EEG hardware ─────────────────────────────────────────────────────────
    EEG_HARDWARE: str = "synthetic"            # synthetic | muse | openbci
    EEG_SERIAL_PORT: str = "/dev/ttyUSB0"     # OpenBCI
    EEG_BUFFER_SECONDS: float = 5.0
    TMR_MAX_CUES_PER_SESSION: int = 15
    TMR_MIN_INTERVAL_SECONDS: float = 30.0
    TMR_AROUSAL_RISK_MAX: float = 0.25     # suppress cues above this threshold

    # ── Rate limiting ─────────────────────────────────────────────────────────
    RATE_LIMIT_REQUESTS_PER_MINUTE: int = 60
    RATE_LIMIT_BURST: int = 10

    # ── Encryption ────────────────────────────────────────────────────────────
    FERNET_KEY: Optional[str] = None          # base64-encoded 32-byte key for field encryption


@lru_cache
def get_settings() -> Settings:
    return Settings()

settings = get_settings()
