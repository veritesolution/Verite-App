"""
SQLAlchemy ORM models.
Sensitive fields (ailment, emotion, plan content) are encrypted at the
application layer using Fernet symmetric encryption before storage.
"""

import uuid
from datetime import datetime
from typing import Optional
from sqlalchemy import (
    String, Text, Integer, Float, Boolean, DateTime,
    ForeignKey, Enum, LargeBinary, JSON, func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.dialects.postgresql import UUID
import enum

from .database import Base
from .encryption import encrypt_field, decrypt_field


class ConsentStatus(str, enum.Enum):
    PENDING = "pending"
    GIVEN = "given"
    WITHDRAWN = "withdrawn"


class PlanStatus(str, enum.Enum):
    QUEUED = "queued"
    GENERATING = "generating"
    READY = "ready"
    FAILED = "failed"


class TMRSessionStatus(str, enum.Enum):
    SCHEDULED = "scheduled"
    RUNNING = "running"
    COMPLETED = "completed"
    ABORTED = "aborted"


class SleepStage(str, enum.Enum):
    WAKE = "W"
    N1 = "N1"
    N2 = "N2"
    N3 = "N3"
    REM = "REM"
    UNKNOWN = "unknown"


# ── User ──────────────────────────────────────────────────────────────────────

class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email: Mapped[str] = mapped_column(String(320), unique=True, nullable=False, index=True)
    email_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    hashed_password: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)  # null for OAuth-only
    google_sub: Mapped[Optional[str]] = mapped_column(String(128), unique=True, nullable=True)
    apple_sub: Mapped[Optional[str]] = mapped_column(String(128), unique=True, nullable=True)
    full_name: Mapped[Optional[str]] = mapped_column(String(256), nullable=True)
    locale: Mapped[str] = mapped_column(String(10), default="en")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    is_researcher: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    # Relationships
    consent: Mapped[Optional["ConsentRecord"]] = relationship(back_populates="user", uselist=False)
    profiles: Mapped[list["UserProfile"]] = relationship(back_populates="user")
    plans: Mapped[list["HabitPlan"]] = relationship(back_populates="user")
    tmr_sessions: Mapped[list["TMRSession"]] = relationship(back_populates="user")
    audit_logs: Mapped[list["AuditLog"]] = relationship(back_populates="user")


class ConsentRecord(Base):
    __tablename__ = "consent_records"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), unique=True)
    status: Mapped[ConsentStatus] = mapped_column(Enum(ConsentStatus), default=ConsentStatus.PENDING)
    consent_version: Mapped[str] = mapped_column(String(20), default="1.0")
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)  # GDPR: store for audit only
    user_agent: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    given_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    withdrawn_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    irb_protocol: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)

    user: Mapped["User"] = relationship(back_populates="consent")


# ── User Profile ──────────────────────────────────────────────────────────────

class UserProfile(Base):
    """Stores the intake questionnaire. Sensitive fields are Fernet-encrypted."""
    __tablename__ = "user_profiles"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), index=True)
    session_version: Mapped[int] = mapped_column(Integer, default=1)  # increments on each intake

    # Encrypted columns (stored as LargeBinary)
    _ailment_enc: Mapped[Optional[bytes]] = mapped_column("ailment_enc", LargeBinary, nullable=True)
    _emotion_enc: Mapped[Optional[bytes]] = mapped_column("emotion_enc", LargeBinary, nullable=True)
    _origin_story_enc: Mapped[Optional[bytes]] = mapped_column("origin_story_enc", LargeBinary, nullable=True)
    _voice_emotion_enc: Mapped[Optional[bytes]] = mapped_column("voice_emotion_enc", LargeBinary, nullable=True)

    # Non-sensitive metadata
    age: Mapped[int] = mapped_column(Integer)
    profession: Mapped[str] = mapped_column(String(256))
    duration_years: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    frequency: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    intensity: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    locale: Mapped[str] = mapped_column(String(10), default="en")
    input_mode: Mapped[str] = mapped_column(String(16), default="text")  # text | voice

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    user: Mapped["User"] = relationship(back_populates="profiles")

    @property
    def ailment(self) -> Optional[str]:
        return decrypt_field(self._ailment_enc)

    @ailment.setter
    def ailment(self, value: str):
        self._ailment_enc = encrypt_field(value)

    @property
    def primary_emotion(self) -> Optional[str]:
        return decrypt_field(self._emotion_enc)

    @primary_emotion.setter
    def primary_emotion(self, value: str):
        self._emotion_enc = encrypt_field(value)

    @property
    def origin_story(self) -> Optional[str]:
        return decrypt_field(self._origin_story_enc)

    @origin_story.setter
    def origin_story(self, value: str):
        self._origin_story_enc = encrypt_field(value)

    @property
    def voice_emotion_json(self) -> Optional[dict]:
        raw = decrypt_field(self._voice_emotion_enc)
        if raw:
            import json
            return json.loads(raw)
        return None

    @voice_emotion_json.setter
    def voice_emotion_json(self, value: dict):
        import json
        self._voice_emotion_enc = encrypt_field(json.dumps(value))


# ── Habit Plan ────────────────────────────────────────────────────────────────

class HabitPlan(Base):
    __tablename__ = "habit_plans"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), index=True)
    profile_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("user_profiles.id"))
    celery_task_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    status: Mapped[PlanStatus] = mapped_column(Enum(PlanStatus), default=PlanStatus.QUEUED)
    llm_provider: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    _plan_enc: Mapped[Optional[bytes]] = mapped_column("plan_enc", LargeBinary, nullable=True)
    cue_audio_s3_key: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    cue_audio_cdn_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    error_message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    user: Mapped["User"] = relationship(back_populates="plans")

    @property
    def plan_content(self) -> Optional[dict]:
        raw = decrypt_field(self._plan_enc)
        if raw:
            import json
            return json.loads(raw)
        return None

    @plan_content.setter
    def plan_content(self, value: dict):
        import json
        self._plan_enc = encrypt_field(json.dumps(value))


# ── TMR Session ───────────────────────────────────────────────────────────────

class TMRSession(Base):
    __tablename__ = "tmr_sessions"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), index=True)
    plan_id: Mapped[Optional[uuid.UUID]] = mapped_column(UUID(as_uuid=True), ForeignKey("habit_plans.id"), nullable=True)
    celery_task_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    status: Mapped[TMRSessionStatus] = mapped_column(Enum(TMRSessionStatus), default=TMRSessionStatus.SCHEDULED)
    hardware: Mapped[str] = mapped_column(String(32), default="synthetic")
    cues_delivered: Mapped[int] = mapped_column(Integer, default=0)
    cues_planned: Mapped[int] = mapped_column(Integer, default=0)
    sleep_efficiency: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    n2_minutes: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    n3_minutes: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    spindles_detected: Mapped[int] = mapped_column(Integer, default=0)
    eeg_s3_key: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    session_metadata: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    started_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    ended_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    user: Mapped["User"] = relationship(back_populates="tmr_sessions")
    events: Mapped[list["TMREvent"]] = relationship(back_populates="session")


class TMREvent(Base):
    """Every cue delivery, stage change, and arousal logged here."""
    __tablename__ = "tmr_events"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("tmr_sessions.id"), index=True)
    event_type: Mapped[str] = mapped_column(String(32))   # cue_delivered | stage_change | spindle | arousal_risk
    timestamp_unix: Mapped[float] = mapped_column(Float)
    sleep_stage: Mapped[Optional[str]] = mapped_column(String(8), nullable=True)
    spindle_prob: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    phase_rad: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    arousal_risk: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    extra: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)

    session: Mapped["TMRSession"] = relationship(back_populates="events")


# ── Audit Log ─────────────────────────────────────────────────────────────────

class AuditLog(Base):
    """HIPAA-compliant audit trail. Append-only — never delete rows."""
    __tablename__ = "audit_logs"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[Optional[uuid.UUID]] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    actor_id: Mapped[Optional[uuid.UUID]] = mapped_column(UUID(as_uuid=True), nullable=True)  # researcher if different
    action: Mapped[str] = mapped_column(String(128))    # e.g. "plan.create", "tmr.start", "crisis.flagged"
    resource_type: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    resource_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)
    user_agent: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    outcome: Mapped[str] = mapped_column(String(16), default="success")  # success | failure | blocked
    detail: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), index=True)

    user: Mapped[Optional["User"]] = relationship(back_populates="audit_logs")
