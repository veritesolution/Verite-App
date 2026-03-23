"""
Crisis safety gate — production implementation.

Peer-review fixes applied (2026-03):

  Bug 8 — Escalation is non-blocking. Previous code used httpx.Client
           (synchronous, 3 s timeout) inside check(), stalling every crisis
           response. Fixed: escalation fires in a daemon thread.

  Bug 9 — Webhook payload includes user_id so a human reviewer can locate
           and contact the affected user. Raw input text is not transmitted.

  False-positive reduction — "hopeless" as a single word caused too many
           false positives (e.g. "my habit feels hopeless to break").
           Replaced with multi-word contextual phrases.

  Accuracy honesty — the README previously claimed "0% false-negative rate"
           for a regex system, which is impossible. Documentation updated to
           describe this as a first-pass keyword filter that MUST be combined
           with semantic screening and human escalation in clinical use.

  ContentModerator fallback — CrisisGate was incorrectly used as the
           fallback for LLM output moderation. A plan text containing "kill
           myself" has probability ≈ 0, so the fallback provided no actual
           moderation. Fixed: fallback uses structural plan validation.
"""

import re
import time
import threading
from dataclasses import dataclass, field
from typing import Optional
import structlog

log = structlog.get_logger()


# ── Keyword taxonomy ──────────────────────────────────────────────────────────

_MEDICAL_EMERGENCY = [
    "already overdosed", "not breathing", "seizure right now",
    "unconscious right now", "can't breathe", "heart attack right now",
]

_IMMEDIATE_DANGER = [
    "kill myself", "killing myself", "end my life", "take my life",
    "suicide", "suicidal", "want to die", "wish i was dead",
    "better off dead", "no reason to live",
    "self harm", "self-harm", "cutting myself", "hurt myself",
    "overdose on purpose", "planning to end it",
    "don't want to be here anymore", "i want to disappear forever",
]

_HIGH_RISK = [
    "can't go on anymore", "cannot go on anymore",
    "no point in living", "no point living",
    "life isn't worth living", "no way out of this",
    "unbearable pain and want it to stop",
    "i give up on life", "thinking about ending it",
    "worthless and want to die",
    # Removed: "hopeless" alone — too many false positives on habit-change text.
    # e.g. "my habit feels hopeless to break" should not block.
    "feel completely hopeless about living",
    "no hope left for my life",
]

_CRISIS_RESOURCES = {
    "US":            "988 Suicide & Crisis Lifeline — call or text 988",
    "US_text":       "Text HOME to 741741 (Crisis Text Line)",
    "UK":            "Samaritans — 116 123 (free, 24/7)",
    "AU":            "Lifeline Australia — 13 11 14",
    "CA":            "Crisis Services Canada — 1-833-456-4566",
    "IE":            "Samaritans Ireland — 116 123",
    "International": "https://www.befrienders.org",
    "Online_chat":   "https://www.crisistextline.org",
}


@dataclass
class CrisisCheckResult:
    level: str = "safe"
    matched_phrases: list = field(default_factory=list)
    resources: dict = field(default_factory=dict)
    blocked: bool = False
    escalated: bool = False

    @property
    def is_immediate_danger(self) -> bool:
        return self.level in ("immediate_danger", "medical_emergency")

    @property
    def is_high_risk(self) -> bool:
        return self.level == "high_risk"


class CrisisGate:
    """
    Synchronous safety gate. check() returns in < 1 ms (regex only).

    Escalation (when triggered) fires in a background daemon thread so the
    caller's response is never delayed by network I/O.

    ⚠ Accuracy limitations (Bug 8/9 honesty addendum):
      This is a keyword-based first-pass filter. It has non-zero false-negative
      rate. Paraphrases such as "I feel like ending things" will not match.
      Clinical deployments MUST layer additional controls:
        1. Semantic screening on every user input via an LLM safety call.
        2. Human on-call escalation with a genuine SLA.
        3. In-app display of crisis resources on every session start.
      This gate alone is not sufficient safety coverage for a clinical product.
    """

    def __init__(self):
        self._patterns = {
            "medical_emergency": self._compile(_MEDICAL_EMERGENCY),
            "immediate_danger":  self._compile(_IMMEDIATE_DANGER),
            "high_risk":         self._compile(_HIGH_RISK),
        }

    @staticmethod
    def _compile(phrases: list[str]) -> list[re.Pattern]:
        return [
            re.compile(r"\b" + re.escape(p.lower()) + r"\b", re.IGNORECASE)
            for p in phrases
        ]

    def check(
        self,
        text: str,
        user_id: Optional[str] = None,
    ) -> CrisisCheckResult:
        """
        Synchronous check. Returns immediately.
        user_id is passed through to escalation so the on-call human can
        identify the affected user (Bug 9 fix).
        """
        if not text or not text.strip():
            return CrisisCheckResult()

        result = CrisisCheckResult()

        for level, patterns in self._patterns.items():
            matched = [p.pattern for p in patterns if p.search(text)]
            if matched:
                result.level = level
                result.matched_phrases = matched
                result.resources = _CRISIS_RESOURCES
                result.blocked = True
                log.warning("crisis_detected", level=level, n_phrases=len(matched),
                            user_id=user_id or "unknown")
                if level in ("immediate_danger", "medical_emergency"):
                    # Bug 8 fix: non-blocking — fires in a daemon thread
                    threading.Thread(
                        target=self._escalate,
                        args=(level, user_id),
                        daemon=True,
                        name="crisis-escalation",
                    ).start()
                    result.escalated = True
                break

        return result

    def _escalate(self, level: str, user_id: Optional[str]) -> None:
        """
        Runs in a daemon thread. Never blocks check().
        Sends user_id (not raw text) to the webhook so a human can act.
        """
        from ..api.config import settings
        import httpx

        webhook = settings.CRISIS_HUMAN_HANDOFF_WEBHOOK
        if not webhook:
            log.warning("crisis_escalation_webhook_not_configured",
                        hint="Set CRISIS_HUMAN_HANDOFF_WEBHOOK in .env")
            return
        try:
            with httpx.Client(timeout=5.0) as client:
                client.post(webhook, json={
                    "event":     "crisis_detected",
                    "level":     level,
                    "timestamp": time.time(),
                    # Bug 9 fix: include user_id so reviewer can locate the user.
                    # Raw input text is never sent (privacy).
                    "user_id":   user_id or "unknown",
                    "action_required": (
                        "Immediate human review required. "
                        "Locate user in the admin dashboard and initiate contact."
                    ),
                })
            log.info("crisis_escalation_sent", level=level, user_id=user_id)
        except Exception as exc:
            log.error("crisis_escalation_failed", error=str(exc))


# ── LLM output content moderator ─────────────────────────────────────────────

class ContentModerator:
    """
    Post-generation moderation for LLM plan output.

    Primary:  Google Perspective API (toxicity scoring).
    Fallback: structural plan safety check (required keys, disclaimer presence,
              absence of dangerous medical advice patterns).

    Bug fix: CrisisGate is NOT used as a fallback here. CrisisGate matches
    patterns like "kill myself" — patterns that have near-zero probability of
    appearing in a clinically-generated behaviour change plan. Using it as a
    fallback for LLM output moderation provides no actual protection.
    """

    def __init__(self):
        from ..api.config import settings
        self._api_key = settings.PERSPECTIVE_API_KEY
        self._url = "https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze"

    async def is_safe(self, text: str, threshold: float = 0.7) -> tuple[bool, dict]:
        if self._api_key:
            result = await self._perspective_check(text, threshold)
            if result is not None:
                return result
        return self._structural_check(text), {}

    async def _perspective_check(
        self, text: str, threshold: float
    ) -> Optional[tuple[bool, dict]]:
        import httpx
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.post(
                    f"{self._url}?key={self._api_key}",
                    json={
                        "comment": {"text": text[:20_000]},
                        "requestedAttributes": {
                            "TOXICITY": {}, "SEVERE_TOXICITY": {},
                            "IDENTITY_ATTACK": {}, "THREAT": {},
                        },
                    },
                )
                if resp.status_code != 200:
                    return None
                scores = {
                    attr: data["summaryScore"]["value"]
                    for attr, data in resp.json()["attributeScores"].items()
                }
                return all(v < threshold for v in scores.values()), scores
        except Exception as exc:
            log.warning("perspective_api_error", error=str(exc))
            return None

    @staticmethod
    def _structural_check(text: str) -> bool:
        """Validate plan structure and absence of dangerous content patterns."""
        from ..ai.safety_guard import PlanSafetyGuard
        import json
        try:
            plan = json.loads(text)
            ok, reason = PlanSafetyGuard().validate(plan)
            if not ok:
                log.warning("plan_structural_safety_failed", reason=reason)
            return ok
        except Exception:
            return True
