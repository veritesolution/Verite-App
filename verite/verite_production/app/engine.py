"""
Verite Production API — Core Engine v2.1
Orchestrates: validation → crisis → RAG → LLM → screening → response.

v2.1 peer-review fixes:
- #1: Empathy/coherence demoted to debug_metrics (not quality indicators)
- #8: Added relevance + repetition + length gates before response reaches user
- #9: Session summary via LLM distillation, not pipe-delimited fragments
"""
from __future__ import annotations

import asyncio
import logging
import time
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

from .config import Settings
from .llm import LLMRouter, parse_llm_response
from .models import (
    AnalysisInfo,
    ChatMessageResponse,
    CrisisResponse,
    DebugMetrics,
    MetricsInfo,
    SafetyInfo,
)
from .prompts import build_system_prompt
from .rag import RAGRetriever
from .safety import (
    CRISIS_MESSAGE,
    CRISIS_RESOURCES,
    SAFE_FALLBACK_RESPONSE,
    detect_crisis,
    screen_output,
    validate_input,
)
from .session import SessionState

logger = logging.getLogger("verite.engine")


# ═══════════════════════════════════════════════════════════════
# Debug-Only Style Similarity
# These measure cosine similarity to canned phrases. They do NOT
# measure actual empathy or therapeutic quality. Exposed only in
# debug_metrics for internal monitoring.
# ═══════════════════════════════════════════════════════════════

_STYLE_EMPATHY_REFS = [
    "I hear you and understand how difficult that must be.",
    "Your feelings are completely valid.",
    "That sounds incredibly hard, and I'm glad you shared that.",
    "It makes sense you feel that way given what you're going through.",
    "Thank you for trusting me with something so personal.",
]
_STYLE_THERAPEUTIC_REFS = [
    "Let's explore what might be driving that feeling.",
    "I notice a pattern here that might be worth examining together.",
    "What evidence do you have for and against that thought?",
    "What would you tell a close friend in this same situation?",
    "What's one small, concrete step you could take this week?",
]

_emp_emb = None
_ther_emb = None
_eval_model = None


def init_eval_metrics(sem_model: Any) -> None:
    global _emp_emb, _ther_emb, _eval_model
    _eval_model = sem_model
    if sem_model is None:
        return
    try:
        _emp_emb = sem_model.encode(
            _STYLE_EMPATHY_REFS, convert_to_numpy=True, normalize_embeddings=True
        ).astype("float32")
        _ther_emb = sem_model.encode(
            _STYLE_THERAPEUTIC_REFS, convert_to_numpy=True, normalize_embeddings=True
        ).astype("float32")
    except Exception as e:
        logger.error(f"Eval metrics init failed: {e}")


def _style_sim(text: str, refs: Any) -> float:
    """Cosine similarity to reference phrases — debug only, not a quality score."""
    if _eval_model is None or refs is None:
        return -1.0
    try:
        emb = _eval_model.encode(
            [text[:500]], convert_to_numpy=True, normalize_embeddings=True
        ).astype("float32")
        return float(np.max(emb @ refs.T))
    except Exception:
        return -1.0


# ═══════════════════════════════════════════════════════════════
# FIX #8: Real Output Quality Gates (pass/fail, not fake scores)
# ═══════════════════════════════════════════════════════════════

def _check_relevance(user_text: str, response_text: str) -> bool:
    """Is the response about what the user actually said?"""
    if _eval_model is None:
        return True
    try:
        embs = _eval_model.encode(
            [user_text[:500], response_text[:500]],
            convert_to_numpy=True, normalize_embeddings=True,
        ).astype("float32")
        sim = float(embs[0] @ embs[1].T)
        if sim < 0.15:
            logger.warning(f"Relevance FAIL: sim={sim:.3f}")
            return False
        return True
    except Exception:
        return True


def _check_not_repetitive(response_text: str, recent_responses: List[str]) -> bool:
    """Is the response different from the last few responses?"""
    if _eval_model is None or not recent_responses:
        return True
    try:
        texts = [response_text[:300]] + [r[:300] for r in recent_responses[-3:]]
        embs = _eval_model.encode(
            texts, convert_to_numpy=True, normalize_embeddings=True
        ).astype("float32")
        for i in range(1, len(embs)):
            if float(embs[0] @ embs[i].T) > 0.95:
                logger.warning("Repetition detected")
                return False
        return True
    except Exception:
        return True


# ═══════════════════════════════════════════════════════════════
# FIX #9: Real Session Summary
# ═══════════════════════════════════════════════════════════════

async def _generate_summary(llm: LLMRouter, session: SessionState) -> str:
    """LLM-generated session summary. Falls back to theme extraction."""
    if session.turn_count < 8:
        return ""
    user_msgs = session.get_user_history(8)
    if not user_msgs:
        return ""
    try:
        prompt = (
            "Summarize this therapy session in 2-3 sentences. "
            "Focus on: main concerns, emotional themes, patterns. "
            "No advice. Plain text only, no JSON."
        )
        msg_text = "\n".join(f"- {m[:150]}" for m in user_msgs)
        resp = await llm.call(prompt, [{"role": "user", "content": msg_text}])
        raw = resp.raw_text.strip()
        if raw and not raw.startswith("{") and len(raw) < 500:
            return raw
    except Exception:
        pass

    # Fallback: keyword theme extraction
    themes = set()
    for msg in user_msgs:
        ml = msg.lower()
        for kw, theme in [
            ("anxious", "anxiety"), ("anxiety", "anxiety"), ("worried", "worry"),
            ("sad", "sadness"), ("depress", "low mood"), ("stress", "stress"),
            ("sleep", "sleep issues"), ("relationship", "relationship concerns"),
            ("work", "work stress"), ("family", "family dynamics"),
            ("lonely", "loneliness"), ("overwhelm", "feeling overwhelmed"),
        ]:
            if kw in ml:
                themes.add(theme)
    if themes:
        return f"Key themes: {', '.join(sorted(themes))}. Turn {session.turn_count}."
    return f"Session at turn {session.turn_count}, domain: {session.domain}."


# ═══════════════════════════════════════════════════════════════
# Core Engine
# ═══════════════════════════════════════════════════════════════

class VeriteEngine:
    def __init__(self, llm_router: LLMRouter, rag_retriever: RAGRetriever, settings: Settings):
        self.llm = llm_router
        self.rag = rag_retriever
        self.settings = settings

    async def process_message(
        self, user_input: str, session: SessionState
    ) -> ChatMessageResponse | CrisisResponse:
        start_time = time.monotonic()
        relevance_ok = True
        repetition_ok = True

        # ── 1. Validate ───────────────────────────────────
        validation = validate_input(user_input, self.settings.max_input_chars)
        if not validation.is_valid:
            return ChatMessageResponse(
                session_id=session.session_id, turn=session.turn_count,
                response=validation.error,
                safety=SafetyInfo(), analysis=AnalysisInfo(), metrics=MetricsInfo(),
            )
        clean_text = validation.cleaned_text

        # ── 2. Crisis detection ───────────────────────────
        crisis_result = detect_crisis(clean_text, self.settings.crisis_semantic_threshold)
        session.crisis_tracker.record_turn(crisis_result, session.turn_count)
        if crisis_result.is_crisis or session.crisis_tracker.should_show_resources:
            session.add_turn("user", clean_text, {"crisis_detected": True})
            session.add_turn("assistant", CRISIS_MESSAGE, {"crisis": True})
            return CrisisResponse(
                session_id=session.session_id, is_crisis=True,
                message=CRISIS_MESSAGE, resources=CRISIS_RESOURCES,
            )

        # ── 3. Add to session ─────────────────────────────
        session.add_turn("user", clean_text)

        # FIX #9: Real summary
        if session.turn_count > 10 and session.turn_count % 5 == 0:
            try:
                session.summary = await _generate_summary(self.llm, session)
            except Exception:
                pass

        # ── 4. RAG retrieval ──────────────────────────────
        tech_ctx, few_shot = self.rag.retrieve(
            domain=session.domain, phase=session.phase, user_text=clean_text,
            history_texts=session.get_user_history(self.settings.rag_history_window),
            top_k_techniques=self.settings.rag_top_k_techniques,
            top_k_examples=self.settings.rag_top_k_examples,
        )

        # ── 5. Build prompt ───────────────────────────────
        system_prompt = build_system_prompt(
            domain=session.domain, rag_context=tech_ctx,
            session_summary=session.summary if session.turn_count > 10 else "",
            few_shot_examples=few_shot,
            crisis_context=session.crisis_tracker.should_be_cautious,
        )
        messages = session.get_messages_for_llm(self.settings.max_history_tokens)

        # ── 6. Call LLM ──────────────────────────────────
        llm_response = await self.llm.call(system_prompt, messages)
        session.total_input_tokens += llm_response.input_tokens
        session.total_output_tokens += llm_response.output_tokens
        session.total_cost_usd += llm_response.cost_usd

        # ── 7. Parse ──────────────────────────────────────
        parsed, json_ok = parse_llm_response(llm_response.raw_text)
        response_text = parsed.get("response", "") or SAFE_FALLBACK_RESPONSE

        # ── 8. Multi-layer output screening ───────────────
        # Layer 1: Toxicity + harmful advice
        screening = screen_output(response_text, self.settings.toxicity_threshold)
        if not screening.is_safe:
            logger.warning(f"Output screened: {screening.reason}")
            response_text = SAFE_FALLBACK_RESPONSE

        # FIX #8 Layer 2: Relevance
        relevance_ok = _check_relevance(clean_text, response_text)
        if not relevance_ok:
            response_text = (
                "I want to make sure I'm addressing what you shared. "
                "Could you help me understand a bit more about what you're experiencing?"
            )

        # FIX #8 Layer 3: Repetition
        recent = [t["content"] for t in session.history if t["role"] == "assistant"][-3:]
        repetition_ok = _check_not_repetitive(response_text, recent)
        if not repetition_ok:
            response_text += (
                " I want to make sure we're making progress — "
                "is there a different angle you'd like to explore?"
            )

        # FIX #8 Layer 4: Length
        if len(response_text.split()) < 5:
            response_text = SAFE_FALLBACK_RESPONSE

        # LLM-signaled crisis
        if parsed.get("crisis_signal"):
            from .safety.crisis import CrisisResult
            session.crisis_tracker.record_turn(
                CrisisResult(is_crisis=True, confidence="high"), session.turn_count,
            )
            session.add_turn("assistant", CRISIS_MESSAGE, {"crisis": True})
            return CrisisResponse(
                session_id=session.session_id, is_crisis=True,
                message=CRISIS_MESSAGE, resources=CRISIS_RESOURCES,
            )

        # ── 9. Update session ─────────────────────────────
        session.domain = parsed.get("domain", session.domain)
        session.phase = parsed.get("phase", session.phase)
        session.intensity = parsed.get("emotional_intensity", session.intensity)
        new_distortions = parsed.get("distortions_detected", [])
        if new_distortions:
            session.distortions.extend(new_distortions)
        session.intensity_history.append(session.intensity)
        session.phase_history.append(session.phase)

        latency_ms = int((time.monotonic() - start_time) * 1000)
        session.add_turn("assistant", response_text, {
            "domain": session.domain, "phase": session.phase,
            "provider": llm_response.provider,
        })

        # ── 10. Build response ────────────────────────────
        return ChatMessageResponse(
            session_id=session.session_id, turn=session.turn_count,
            response=response_text,
            safety=SafetyInfo(
                is_crisis=False, toxicity_score=screening.toxicity_score,
                was_sanitized=validation.was_sanitized,
                harmful_advice_blocked=screening.harmful_advice_detected,
            ),
            analysis=AnalysisInfo(
                domain=session.domain, phase=session.phase,
                emotional_intensity=session.intensity,
                distortions_detected=new_distortions,
                therapeutic_move=parsed.get("therapeutic_move", "validate"),
                reasoning=parsed.get("reasoning", ""),
            ),
            metrics=MetricsInfo(
                json_parsed_ok=json_ok,
                input_tokens=llm_response.input_tokens,
                output_tokens=llm_response.output_tokens,
                latency_ms=latency_ms,
                provider_used=llm_response.provider,
            ),
            debug_metrics=DebugMetrics(
                style_empathy_similarity=round(_style_sim(response_text, _emp_emb), 3),
                style_therapeutic_similarity=round(_style_sim(response_text, _ther_emb), 3),
                relevance_passed=relevance_ok,
                repetition_passed=repetition_ok,
                note=(
                    "Style similarities measure cosine distance to canned phrases. "
                    "They do NOT measure actual empathy or quality. Internal use only."
                ),
            ),
        )
