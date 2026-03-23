"""
Verite Production API — Data Models
Pydantic schemas for API requests, responses, and internal data.
"""
from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional
from uuid import uuid4

from pydantic import BaseModel, Field, field_validator


# ═══════════════════════════════════════════════════════════════
# Enums
# ═══════════════════════════════════════════════════════════════

class Domain(str, Enum):
    PERSONAL = "personal"
    RELATIONSHIP = "relationship"
    HEALTH = "health"
    ACADEMIC = "academic"
    FAMILY = "family"
    WORK_CAREER = "work_career"
    UNKNOWN = "unknown"


class Phase(str, Enum):
    INTAKE = "intake"
    ASSESSMENT = "assessment"
    ANALYSIS = "analysis"
    SUPPORT = "support"
    ACTION_PLAN = "action_plan"
    FOLLOW_UP = "follow_up"
    CRISIS = "crisis"


class TherapeuticMove(str, Enum):
    VALIDATE = "validate"
    EXPLORE = "explore"
    REFRAME = "reframe"
    PSYCHOEDUCATE = "psychoeducate"
    SKILL_TEACH = "skill_teach"
    SUMMARIZE = "summarize"
    ACTION_PLAN = "action_plan"
    CHECK_IN = "check_in"


class CognitiveDistortion(str, Enum):
    CATASTROPHIZING = "catastrophizing"
    ALL_OR_NOTHING = "all_or_nothing_thinking"
    MIND_READING = "mind_reading"
    FORTUNE_TELLING = "fortune_telling"
    EMOTIONAL_REASONING = "emotional_reasoning"
    SHOULD_STATEMENTS = "should_statements"
    PERSONALIZATION = "personalization"
    OVERGENERALIZATION = "overgeneralization"
    MENTAL_FILTERING = "mental_filtering"
    DISQUALIFYING_POSITIVE = "disqualifying_the_positive"
    MAGNIFICATION = "magnification"
    LABELING = "labeling"


# ═══════════════════════════════════════════════════════════════
# Auth Models
# ═══════════════════════════════════════════════════════════════

class AuthRegisterRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=50, pattern=r"^[a-zA-Z0-9_]+$")
    password: str = Field(..., min_length=8, max_length=128)
    display_name: Optional[str] = Field(None, max_length=100)


class AuthLoginRequest(BaseModel):
    username: str
    password: str


class AuthTokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int


class AuthRefreshRequest(BaseModel):
    refresh_token: str


# ═══════════════════════════════════════════════════════════════
# Chat Models
# ═══════════════════════════════════════════════════════════════

class ChatMessageRequest(BaseModel):
    message: str = Field(..., min_length=1, max_length=3000)
    session_id: Optional[str] = None

    @field_validator("message")
    @classmethod
    def strip_message(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("Message cannot be empty")
        return v


class SafetyInfo(BaseModel):
    is_crisis: bool = False
    crisis_method: Optional[str] = None
    toxicity_score: float = 0.0
    was_sanitized: bool = False
    harmful_advice_blocked: bool = False


class AnalysisInfo(BaseModel):
    domain: Domain = Domain.UNKNOWN
    phase: Phase = Phase.INTAKE
    emotional_intensity: float = Field(0.5, ge=0.0, le=1.0)
    distortions_detected: List[str] = Field(default_factory=list)
    therapeutic_move: TherapeuticMove = TherapeuticMove.VALIDATE
    reasoning: str = ""


class MetricsInfo(BaseModel):
    json_parsed_ok: bool = False
    input_tokens: int = 0
    output_tokens: int = 0
    latency_ms: int = 0
    provider_used: str = ""


class DebugMetrics(BaseModel):
    """
    Internal monitoring metrics. NOT quality indicators.
    Style similarity scores measure cosine distance to canned phrases.
    They do NOT measure actual empathy, coherence, or therapeutic quality.
    """
    style_empathy_similarity: float = Field(-1.0, description="Cosine sim to empathy phrases. NOT a quality score.")
    style_therapeutic_similarity: float = Field(-1.0, description="Cosine sim to therapeutic phrases. NOT a quality score.")
    relevance_passed: bool = True
    repetition_passed: bool = True
    note: str = "Debug metrics only. Not validated clinical measures."


class ChatMessageResponse(BaseModel):
    session_id: str
    turn: int
    response: str
    safety: SafetyInfo
    analysis: AnalysisInfo
    metrics: MetricsInfo
    debug_metrics: Optional[DebugMetrics] = None
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat())


class CrisisResponse(BaseModel):
    """Returned when crisis is detected — separate schema for clarity."""
    session_id: str
    is_crisis: bool = True
    message: str
    resources: List[Dict[str, str]]
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat())


# ═══════════════════════════════════════════════════════════════
# Session Models
# ═══════════════════════════════════════════════════════════════

class SessionCreateResponse(BaseModel):
    session_id: str
    created_at: str


class SessionSummary(BaseModel):
    session_id: str
    created_at: str
    turn_count: int
    domain: Domain
    phase: Phase
    crisis_flagged: bool
    avg_empathy: float
    avg_coherence: float


class SessionDetailResponse(BaseModel):
    session_id: str
    created_at: str
    turn_count: int
    domain: Domain
    phase: Phase
    crisis_flagged: bool
    avg_empathy: float
    avg_coherence: float
    avg_intensity: float
    distortions: List[str]
    phase_history: List[str]
    intensity_history: List[float]


class SessionListResponse(BaseModel):
    sessions: List[SessionSummary]
    total: int


# ═══════════════════════════════════════════════════════════════
# Feedback
# ═══════════════════════════════════════════════════════════════

class FeedbackRequest(BaseModel):
    session_id: str
    turn: int
    rating: str = Field(..., pattern=r"^(helpful|not_helpful)$")
    comment: Optional[str] = Field(None, max_length=500)


class FeedbackResponse(BaseModel):
    status: str = "recorded"


# ═══════════════════════════════════════════════════════════════
# Health Check
# ═══════════════════════════════════════════════════════════════

class HealthResponse(BaseModel):
    status: str
    version: str
    llm_provider: str
    llm_available: bool
    safety_checks: Dict[str, str]
    rag_status: Dict[str, Any]
    uptime_seconds: float


# ═══════════════════════════════════════════════════════════════
# Internal: LLM parsed output
# ═══════════════════════════════════════════════════════════════

class LLMParsedResponse(BaseModel):
    """What we expect the LLM to return (after JSON parsing)."""
    domain: str = "unknown"
    phase: str = "support"
    emotional_intensity: float = 0.5
    distortions_detected: List[str] = Field(default_factory=list)
    therapeutic_move: str = "validate"
    reasoning: str = ""
    crisis_signal: bool = False
    response: str = ""

    @classmethod
    def with_defaults(cls, data: Dict[str, Any]) -> "LLMParsedResponse":
        """Safely create from dict, applying defaults for missing fields."""
        defaults = {
            "domain": "unknown",
            "phase": "support",
            "emotional_intensity": 0.5,
            "distortions_detected": [],
            "therapeutic_move": "validate",
            "reasoning": "",
            "crisis_signal": False,
            "response": "",
        }
        defaults.update({k: v for k, v in data.items() if v is not None})
        # Clamp intensity
        ei = defaults.get("emotional_intensity", 0.5)
        if isinstance(ei, (int, float)):
            defaults["emotional_intensity"] = max(0.0, min(1.0, float(ei)))
        else:
            defaults["emotional_intensity"] = 0.5
        return cls(**defaults)


# ═══════════════════════════════════════════════════════════════
# WebSocket Models
# ═══════════════════════════════════════════════════════════════

class WSIncomingMessage(BaseModel):
    type: str = "message"  # message | ping | end_session
    content: Optional[str] = None
    session_id: Optional[str] = None


class WSOutgoingMessage(BaseModel):
    type: str  # response | crisis | error | pong | session_created
    content: Optional[str] = None
    session_id: Optional[str] = None
    analysis: Optional[AnalysisInfo] = None
    safety: Optional[SafetyInfo] = None
    metrics: Optional[MetricsInfo] = None
    error: Optional[str] = None
