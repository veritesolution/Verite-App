"""
Verite Production API — Configuration
Loads from environment variables with validation. No hardcoded secrets.
"""
from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path
from typing import List, Optional

from pydantic_settings import BaseSettings
from pydantic import Field, field_validator


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # ── App ────────────────────────────────────────────────
    app_env: str = Field("production", alias="APP_ENV")
    app_debug: bool = Field(False, alias="APP_DEBUG")
    app_host: str = Field("0.0.0.0", alias="APP_HOST")
    app_port: int = Field(8000, alias="APP_PORT")
    app_workers: int = Field(4, alias="APP_WORKERS")
    app_log_level: str = Field("INFO", alias="APP_LOG_LEVEL")
    secret_key: str = Field(..., alias="SECRET_KEY")

    # ── JWT ────────────────────────────────────────────────
    jwt_secret: str = Field(..., alias="JWT_SECRET")
    jwt_algorithm: str = Field("HS256", alias="JWT_ALGORITHM")
    jwt_expiry_hours: int = Field(24, alias="JWT_EXPIRY_HOURS")
    jwt_refresh_expiry_days: int = Field(30, alias="JWT_REFRESH_EXPIRY_DAYS")

    # ── LLM Providers ─────────────────────────────────────
    groq_api_key: str = Field("", alias="GROQ_API_KEY")
    gemini_api_key: str = Field("", alias="GEMINI_API_KEY")
    together_api_key: str = Field("", alias="TOGETHER_API_KEY")
    openai_api_key: str = Field("", alias="OPENAI_API_KEY")
    anthropic_api_key: str = Field("", alias="ANTHROPIC_API_KEY")

    llm_primary_provider: str = Field("groq", alias="LLM_PRIMARY_PROVIDER")
    llm_temperature: float = Field(0.7, alias="LLM_TEMPERATURE")
    llm_max_tokens: int = Field(1024, alias="LLM_MAX_TOKENS")
    llm_timeout_seconds: int = Field(30, alias="LLM_TIMEOUT_SECONDS")

    # ── Database ──────────────────────────────────────────
    database_url: str = Field(
        "sqlite+aiosqlite:///./verite.db", alias="DATABASE_URL"
    )

    # ── Redis ─────────────────────────────────────────────
    redis_url: Optional[str] = Field(None, alias="REDIS_URL")

    # ── Safety ────────────────────────────────────────────
    crisis_semantic_threshold: float = Field(0.68, alias="CRISIS_SEMANTIC_THRESHOLD")
    toxicity_threshold: float = Field(0.35, alias="TOXICITY_THRESHOLD")
    max_input_chars: int = Field(3000, alias="MAX_INPUT_CHARS")
    max_history_tokens: int = Field(4000, alias="MAX_HISTORY_TOKENS")

    # ── RAG ───────────────────────────────────────────────
    rag_top_k_techniques: int = Field(3, alias="RAG_TOP_K_TECHNIQUES")
    rag_top_k_examples: int = Field(3, alias="RAG_TOP_K_EXAMPLES")
    rag_history_window: int = Field(3, alias="RAG_HISTORY_WINDOW")
    faiss_cache_dir: str = Field("./data/faiss_cache", alias="FAISS_CACHE_DIR")

    # ── Rate Limiting ─────────────────────────────────────
    rate_limit_per_minute: int = Field(20, alias="RATE_LIMIT_PER_MINUTE")
    rate_limit_per_hour: int = Field(200, alias="RATE_LIMIT_PER_HOUR")

    # ── CORS ──────────────────────────────────────────────
    cors_origins: str = Field("http://localhost:3000", alias="CORS_ORIGINS")

    # ── Monitoring ────────────────────────────────────────
    sentry_dsn: Optional[str] = Field(None, alias="SENTRY_DSN")
    prometheus_enabled: bool = Field(False, alias="PROMETHEUS_ENABLED")

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "case_sensitive": False,
        "extra": "ignore",
    }

    @property
    def is_production(self) -> bool:
        return self.app_env == "production"

    @property
    def cors_origin_list(self) -> List[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @property
    def available_providers(self) -> List[str]:
        """Return providers with valid keys, primary first."""
        mapping = {
            "groq": self.groq_api_key,
            "gemini": self.gemini_api_key,
            "together": self.together_api_key,
            "openai": self.openai_api_key,
            "anthropic": self.anthropic_api_key,
        }
        providers = []
        # Primary first
        if mapping.get(self.llm_primary_provider):
            providers.append(self.llm_primary_provider)
        # Then others as fallbacks
        for name, key in mapping.items():
            if key and name not in providers:
                providers.append(name)
        return providers

    def get_api_key(self, provider: str) -> str:
        return {
            "groq": self.groq_api_key,
            "gemini": self.gemini_api_key,
            "together": self.together_api_key,
            "openai": self.openai_api_key,
            "anthropic": self.anthropic_api_key,
        }.get(provider, "")

    @field_validator("secret_key", "jwt_secret")
    @classmethod
    def validate_secrets(cls, v: str) -> str:
        if v in ("CHANGE_ME_TO_A_RANDOM_64_CHAR_STRING",
                 "CHANGE_ME_TO_A_DIFFERENT_RANDOM_STRING", ""):
            if os.getenv("APP_ENV", "production") == "production":
                raise ValueError(
                    "SECRET_KEY and JWT_SECRET must be set to random values in production. "
                    "Generate with: python -c \"import secrets; print(secrets.token_hex(32))\""
                )
            # Allow defaults only in dev
            return "dev-insecure-" + v
        return v


@lru_cache()
def get_settings() -> Settings:
    """Cached settings singleton."""
    return Settings()


def ensure_directories(settings: Settings) -> None:
    """Create required data directories."""
    for d in [
        settings.faiss_cache_dir,
        "./data/sessions",
        "./data/feedback",
        "./data/logs",
    ]:
        Path(d).mkdir(parents=True, exist_ok=True)
