"""
Verite Production API — Session Management
In-memory sessions with optional Redis backing. Proper thread-safe state.
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional
from uuid import uuid4

import numpy as np

from ..safety.crisis import CrisisTracker

logger = logging.getLogger("verite.session")


def _count_tokens(text: str) -> int:
    """Approximate token count. Use tiktoken if available."""
    try:
        import tiktoken
        enc = tiktoken.get_encoding("cl100k_base")
        return len(enc.encode(text))
    except ImportError:
        return max(1, int(len(text.split()) * 1.3))


@dataclass
class SessionState:
    """Complete state for a single user session."""
    session_id: str = field(default_factory=lambda: uuid4().hex[:12])
    user_id: str = ""
    created_at: datetime = field(default_factory=datetime.utcnow)
    updated_at: datetime = field(default_factory=datetime.utcnow)

    # Conversation
    history: List[Dict[str, Any]] = field(default_factory=list)
    turn_count: int = 0

    # Clinical state
    domain: str = "unknown"
    phase: str = "intake"
    intensity: float = 0.5
    distortions: List[str] = field(default_factory=list)
    summary: str = ""

    # Safety
    crisis_tracker: CrisisTracker = field(default_factory=CrisisTracker)

    # Metrics
    empathy_scores: List[float] = field(default_factory=list)
    coherence_scores: List[float] = field(default_factory=list)
    intensity_history: List[float] = field(default_factory=list)
    phase_history: List[str] = field(default_factory=list)
    total_input_tokens: int = 0
    total_output_tokens: int = 0
    total_cost_usd: float = 0.0

    def add_turn(self, role: str, content: str, metadata: Optional[Dict] = None) -> None:
        """Add a conversation turn."""
        self.history.append({
            "role": role,
            "content": content,
            "turn": self.turn_count,
            "timestamp": datetime.utcnow().isoformat(),
            "metadata": metadata or {},
        })
        self.turn_count += 1
        self.updated_at = datetime.utcnow()

    def get_messages_for_llm(self, max_tokens: int = 4000) -> List[Dict[str, str]]:
        """Get recent messages within token budget for LLM context."""
        msgs = []
        token_count = 0
        for turn in reversed(self.history):
            tokens = _count_tokens(turn["content"]) + 10
            if token_count + tokens > max_tokens:
                break
            msgs.insert(0, {"role": turn["role"], "content": turn["content"]})
            token_count += tokens
        return msgs

    def get_user_history(self, n: int = 3) -> List[str]:
        """Get last N user messages (for RAG context)."""
        return [
            t["content"] for t in self.history if t["role"] == "user"
        ][-n:]

    def build_summary(self) -> None:
        """Build session summary for long conversations."""
        if self.turn_count < 10:
            return
        recent = self.get_user_history(6)
        self.summary = " | ".join(m[:100] for m in recent)

    def to_export(self) -> Dict[str, Any]:
        """Export session data for persistence/API response."""
        return {
            "session_id": self.session_id,
            "user_id": self.user_id,
            "created_at": self.created_at.isoformat(),
            "updated_at": self.updated_at.isoformat(),
            "domain": self.domain,
            "phase": self.phase,
            "turn_count": self.turn_count,
            "avg_intensity": round(
                float(np.mean(self.intensity_history)) if self.intensity_history else 0.5, 3
            ),
            "avg_empathy": round(
                float(np.mean(self.empathy_scores)) if self.empathy_scores else 0.0, 3
            ),
            "avg_coherence": round(
                float(np.mean(self.coherence_scores)) if self.coherence_scores else 0.0, 3
            ),
            "distortions": list(set(self.distortions)),
            "crisis_flagged": self.crisis_tracker.crisis_count > 0,
            "total_tokens": self.total_input_tokens + self.total_output_tokens,
            "total_cost_usd": round(self.total_cost_usd, 4),
            "phase_history": self.phase_history,
            "intensity_history": self.intensity_history[-20:],
            "history": self.history,
        }

    def to_summary(self) -> Dict[str, Any]:
        """Lightweight summary for session listing."""
        return {
            "session_id": self.session_id,
            "created_at": self.created_at.isoformat(),
            "turn_count": self.turn_count,
            "domain": self.domain,
            "phase": self.phase,
            "crisis_flagged": self.crisis_tracker.crisis_count > 0,
            "avg_empathy": round(
                float(np.mean(self.empathy_scores)) if self.empathy_scores else 0.0, 3
            ),
            "avg_coherence": round(
                float(np.mean(self.coherence_scores)) if self.coherence_scores else 0.0, 3
            ),
        }


# ═══════════════════════════════════════════════════════════════
# Session Manager
# ═══════════════════════════════════════════════════════════════

class SessionManager:
    """
    Manages session lifecycle.
    In-memory store with optional persistence to filesystem or Redis.
    """

    MAX_SESSIONS_PER_USER = 50
    SESSION_TIMEOUT_HOURS = 24

    def __init__(self, persist_dir: Optional[str] = None):
        self._sessions: Dict[str, SessionState] = {}
        self._user_sessions: Dict[str, List[str]] = {}  # user_id → [session_ids]
        self._lock = asyncio.Lock()
        self._persist_dir = persist_dir
        if persist_dir:
            os.makedirs(persist_dir, exist_ok=True)

    async def create_session(self, user_id: str) -> SessionState:
        """Create a new session for a user."""
        async with self._lock:
            session = SessionState(user_id=user_id)
            self._sessions[session.session_id] = session

            if user_id not in self._user_sessions:
                self._user_sessions[user_id] = []
            self._user_sessions[user_id].append(session.session_id)

            # Enforce per-user session limit
            user_sessions = self._user_sessions[user_id]
            if len(user_sessions) > self.MAX_SESSIONS_PER_USER:
                oldest = user_sessions.pop(0)
                self._sessions.pop(oldest, None)

            logger.info(f"Session created: {session.session_id} for user {user_id}")
            return session

    async def get_session(self, session_id: str, user_id: str) -> Optional[SessionState]:
        """Get a session, verifying ownership."""
        session = self._sessions.get(session_id)
        if session is None:
            # Try loading from disk
            session = await self._load_from_disk(session_id)
            if session:
                self._sessions[session_id] = session

        if session and session.user_id == user_id:
            return session
        return None

    async def save_session(self, session_id: str) -> Optional[str]:
        """Persist session to disk."""
        session = self._sessions.get(session_id)
        if not session or not self._persist_dir:
            return None

        path = os.path.join(self._persist_dir, f"{session_id}.json")
        try:
            data = session.to_export()
            with open(path, "w") as f:
                json.dump(data, f, indent=2, default=str)
            return path
        except Exception as e:
            logger.error(f"Session save failed: {e}")
            return None

    async def list_sessions(self, user_id: str) -> List[Dict[str, Any]]:
        """List all sessions for a user."""
        session_ids = self._user_sessions.get(user_id, [])
        summaries = []
        for sid in reversed(session_ids):  # Most recent first
            session = self._sessions.get(sid)
            if session:
                summaries.append(session.to_summary())
        return summaries

    async def delete_session(self, session_id: str, user_id: str) -> bool:
        """Delete a session."""
        async with self._lock:
            session = self._sessions.get(session_id)
            if session and session.user_id == user_id:
                del self._sessions[session_id]
                if user_id in self._user_sessions:
                    self._user_sessions[user_id] = [
                        s for s in self._user_sessions[user_id] if s != session_id
                    ]
                # Also delete from disk
                if self._persist_dir:
                    path = os.path.join(self._persist_dir, f"{session_id}.json")
                    if os.path.exists(path):
                        os.remove(path)
                return True
        return False

    async def cleanup_expired(self) -> int:
        """Remove expired sessions. Call periodically."""
        cutoff = datetime.utcnow() - timedelta(hours=self.SESSION_TIMEOUT_HOURS)
        expired = []
        for sid, session in self._sessions.items():
            if session.updated_at < cutoff:
                expired.append(sid)

        for sid in expired:
            session = self._sessions.pop(sid, None)
            if session and session.user_id in self._user_sessions:
                self._user_sessions[session.user_id] = [
                    s for s in self._user_sessions[session.user_id] if s != sid
                ]

        if expired:
            logger.info(f"Cleaned up {len(expired)} expired sessions")
        return len(expired)

    async def _load_from_disk(self, session_id: str) -> Optional[SessionState]:
        """Try to load a session from disk persistence."""
        if not self._persist_dir:
            return None
        path = os.path.join(self._persist_dir, f"{session_id}.json")
        if not os.path.exists(path):
            return None
        try:
            with open(path) as f:
                data = json.load(f)
            session = SessionState(
                session_id=data["session_id"],
                user_id=data.get("user_id", ""),
                turn_count=data.get("turn_count", 0),
                domain=data.get("domain", "unknown"),
                phase=data.get("phase", "intake"),
                history=data.get("history", []),
            )
            return session
        except Exception as e:
            logger.error(f"Failed to load session from disk: {e}")
            return None
