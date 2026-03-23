"""
Pydantic v2 schemas for all API endpoints.
Input validation, serialization, and OpenAPI documentation live here.
"""

import uuid
from datetime import datetime
from typing import Optional, List, Any
from pydantic import BaseModel, EmailStr, Field, field_validator, model_validator


# ── Auth ──────────────────────────────────────────────────────────────────────

class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=12, description="Minimum 12 characters")
    full_name: Optional[str] = Field(None, max_length=256)
    locale: str = Field("en", max_length=10)

    @field_validator("password")
    @classmethod
    def password_strength(cls, v: str) -> str:
        if not any(c.isdigit() for c in v):
            raise ValueError("Password must contain at least one digit")
        if not any(c.isupper() for c in v):
            raise ValueError("Password must contain at least one uppercase letter")
        return v


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int


class OAuthCallbackRequest(BaseModel):
    provider: str = Field(..., pattern="^(google|apple)$")
    code: str
    redirect_uri: str


# ── User ──────────────────────────────────────────────────────────────────────

class UserResponse(BaseModel):
    id: uuid.UUID
    email: str
    full_name: Optional[str]
    locale: str
    is_active: bool
    created_at: datetime

    model_config = {"from_attributes": True}


class ConsentRequest(BaseModel):
    irb_protocol: Optional[str] = None
    consent_version: str = "1.0"


class ConsentResponse(BaseModel):
    status: str
    given_at: Optional[datetime]
    consent_version: str

    model_config = {"from_attributes": True}


# ── Profile intake ────────────────────────────────────────────────────────────

class ProfileCreateRequest(BaseModel):
    age: int = Field(..., ge=13, le=100)
    profession: str = Field(..., min_length=2, max_length=256)
    ailment_description: str = Field(
        ..., min_length=10, max_length=2000,
        description="Description of the habit or behaviour to address",
    )
    duration_years: str = Field(..., max_length=64)
    frequency: str = Field(..., max_length=128)
    intensity: int = Field(..., ge=1, le=10, description="Severity 1–10")
    origin_story: str = Field(..., min_length=10, max_length=2000)
    primary_emotion: str = Field(..., max_length=256)
    what_it_gives: str = Field(..., max_length=512)
    trigger_emotions: str = Field(..., max_length=512)
    past_attempts: Optional[str] = Field(None, max_length=1000)
    voice_emotion_data: Optional[dict] = Field(None, description="Hume AI emotion scores from voice intake")
    locale: str = Field("en", max_length=10)
    input_mode: str = Field("text", pattern="^(text|voice)$")


class ProfileResponse(BaseModel):
    id: uuid.UUID
    age: int
    profession: str
    locale: str
    input_mode: str
    created_at: datetime

    model_config = {"from_attributes": True}


# ── Habit Plan ────────────────────────────────────────────────────────────────

class PlanCreateRequest(BaseModel):
    profile_id: uuid.UUID


class PlanStatusResponse(BaseModel):
    id: uuid.UUID
    status: str
    llm_provider: Optional[str]
    cue_audio_cdn_url: Optional[str]
    created_at: datetime
    completed_at: Optional[datetime]
    error_message: Optional[str]

    model_config = {"from_attributes": True}


class PlanContentResponse(PlanStatusResponse):
    plan_content: Optional[dict]   # decrypted on read


# ── TMR Session ───────────────────────────────────────────────────────────────

class TMRSessionCreateRequest(BaseModel):
    plan_id: Optional[uuid.UUID] = None
    hardware: str = Field("synthetic", pattern="^(synthetic|muse|openbci)$")
    max_duration_minutes: int = Field(480, ge=60, le=720)
    max_cues: int = Field(10, ge=1, le=20)


class TMRSessionResponse(BaseModel):
    id: uuid.UUID
    status: str
    hardware: str
    cues_delivered: int
    cues_planned: int
    sleep_efficiency: Optional[float]
    n2_minutes: Optional[float]
    n3_minutes: Optional[float]
    spindles_detected: int
    started_at: Optional[datetime]
    ended_at: Optional[datetime]

    model_config = {"from_attributes": True}


# ── Safety / Crisis ───────────────────────────────────────────────────────────

class SafetyCheckRequest(BaseModel):
    text: str = Field(..., max_length=5000)


class SafetyCheckResponse(BaseModel):
    is_safe: bool
    crisis_level: str   # safe | high_risk | immediate_danger | medical_emergency
    resources: dict
    blocked: bool       # whether API should block the downstream action


# ── Voice ─────────────────────────────────────────────────────────────────────

class TTSRequest(BaseModel):
    text: str = Field(..., max_length=5000)
    locale: str = Field("en-US", max_length=10)
    voice_name: Optional[str] = Field("en-US-Journey-F", max_length=64)


class STTResponse(BaseModel):
    transcript: str
    confidence: float
    locale_detected: str
    emotion_scores: Optional[dict]


# ── Researcher / Analytics ────────────────────────────────────────────────────

class SessionSummaryResponse(BaseModel):
    total_users: int
    total_plans: int
    total_tmr_sessions: int
    avg_cues_per_session: float
    avg_sleep_efficiency: Optional[float]
    crisis_events_flagged: int
