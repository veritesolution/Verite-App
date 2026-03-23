"""
Verite Production API — Crisis Detection
Multi-layer crisis detection: regex → semantic FAISS → behavioral signals.
Includes de-escalation logic (the original had permanent crisis lock with no exit).
"""
from __future__ import annotations

import logging
import os
import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

logger = logging.getLogger("verite.safety.crisis")


# ═══════════════════════════════════════════════════════════════
# Crisis Resources — Internationalized
# ═══════════════════════════════════════════════════════════════

CRISIS_RESOURCES = [
    {"name": "988 Suicide & Crisis Lifeline (US)", "contact": "988", "type": "phone", "available": "24/7"},
    {"name": "Crisis Text Line (US)", "contact": "Text HOME to 741741", "type": "text", "available": "24/7"},
    {"name": "Samaritans (UK/IE)", "contact": "116 123", "type": "phone", "available": "24/7"},
    {"name": "Lifeline (Australia)", "contact": "13 11 14", "type": "phone", "available": "24/7"},
    {"name": "iCall (India)", "contact": "9152987821", "type": "phone", "available": "Mon-Sat 8am-10pm"},
    {"name": "Vandrevala Foundation (India)", "contact": "1860-2662-345", "type": "phone", "available": "24/7"},
    {"name": "Sumithrayo (Sri Lanka)", "contact": "011-2682535", "type": "phone", "available": "24/7"},
    {"name": "International Association for Suicide Prevention",
     "contact": "https://www.iasp.info/resources/Crisis_Centres/", "type": "web", "available": "24/7"},
]

CRISIS_MESSAGE = (
    "I hear that you're going through something really painful right now, "
    "and I'm genuinely glad you reached out. "
    "What you're feeling matters, and you deserve real, immediate human support.\n\n"
    "Please reach out to one of these trained professionals who can help:\n"
    "• 988 Suicide & Crisis Lifeline (US): Call or text 988\n"
    "• Crisis Text Line (US): Text HOME to 741741\n"
    "• Samaritans (UK): 116 123\n"
    "• Sumithrayo (Sri Lanka): 011-2682535\n"
    "• International: https://www.iasp.info/resources/Crisis_Centres/\n\n"
    "You don't have to go through this alone. A trained counselor can provide "
    "the support you need right now."
)


# ═══════════════════════════════════════════════════════════════
# Layer 1: Regex-Based Crisis Detection (expanded from 14 → 30+)
# ═══════════════════════════════════════════════════════════════

# HIGH confidence — direct statements of suicidal intent
_CRISIS_HIGH: List[str] = [
    r"\b(kill|end|take)\s+(my|myself|my\s*life|it\s*all|everything)\b",
    r"\b(want\s+to\s+die|wanna\s+die|wish\s+i\s+was\s+dead)\b",
    r"\b(suicide|suicidal|self[\s\-.]?harm|cutting\s*myself|hurt\s*myself)\b",
    r"\b(planning\s+to\s+(?:hurt|kill|end))\b",
    r"\b((?:how|ways?)\s+to\s+(?:kill|end|hurt)\s+(?:my|your)?\s*self)\b",
    r"\b(overdose|hang\s*(?:ing)?\s*myself|jump\s+(?:off|from))\b",
    r"\b((?:wrote|writing|have)\s+(?:a\s+)?(?:suicide|goodbye)\s+(?:note|letter))\b",
    r"\b((?:collecting|saving|hoarding)\s+(?:pills?|medication|meds))\b",
    r"\b((?:bought|got|have)\s+(?:a\s+)?(?:gun|rope|razor|blade)\s+(?:to|for))\b",
    r"\b(slit\s+(?:my\s+)?(?:wrists?|throat))\b",
]

# MEDIUM confidence — indirect expressions of suicidal ideation
_CRISIS_MEDIUM: List[str] = [
    r"\b(don[']?t\s+want\s+to\s+(?:live|be\s+here|exist|wake\s+up|go\s+on))\b",
    r"\b(no\s+(?:reason|point|will)\s+to\s+live)\b",
    r"\b(rather\s+be\s+dead|life\s+(?:is\s+)?not\s+worth)\b",
    r"\b(can[']?t\s+(?:take|do|handle)\s+(?:it|this)\s+anymore)\b",
    r"\b((?:everything|world)\s+(?:would\s+be)?\s*better\s+without\s+me)\b",
    r"\b(no\s+one\s+(?:would\s+)?(?:care|notice|miss)\s+(?:if\s+i))\b",
    r"\b(goodbye.{0,30}(?:forever|everyone|world))\b",
    r"\b((?:just\s+)?want\s+(?:it|the\s+pain|everything)\s+to\s+(?:stop|end|be\s+over))\b",
    r"\b(i\s+(?:am|'m)\s+(?:a\s+)?burden\s+to\s+(?:everyone|everybody|my\s+family))\b",
    r"\b((?:gave|giving)\s+away\s+(?:my\s+)?(?:things|belongings|possessions|stuff))\b",
]

# BEHAVIORAL signals — warning signs that don't use explicit language
_CRISIS_BEHAVIORAL: List[str] = [
    r"\b(said\s+(?:my\s+)?(?:goodbyes?|farewell))\b",
    r"\b((?:made|making|writing)\s+(?:a\s+)?(?:will|final\s+arrangements?))\b",
    r"\b((?:drove|went|going)\s+to\s+(?:the\s+)?(?:bridge|cliff|edge|roof(?:top)?))\b",
    r"\b((?:researching|looking\s+up|googled?)\s+(?:how\s+to\s+)?(?:die|kill|methods?))\b",
    r"\b((?:tied|tying)\s+(?:a\s+)?(?:noose|rope))\b",
    r"\b(put\s+(?:my\s+)?affairs\s+in\s+order)\b",
    r"\b((?:this\s+is\s+)?(?:my\s+)?(?:last|final)\s+(?:message|goodbye|words?))\b",
    r"\b((?:tonight|today)\s+(?:is|will\s+be)\s+(?:the|my)\s+(?:last|end))\b",
]

_crisis_high_re = [re.compile(p, re.IGNORECASE | re.DOTALL) for p in _CRISIS_HIGH]
_crisis_medium_re = [re.compile(p, re.IGNORECASE | re.DOTALL) for p in _CRISIS_MEDIUM]
_crisis_behavioral_re = [re.compile(p, re.IGNORECASE | re.DOTALL) for p in _CRISIS_BEHAVIORAL]


@dataclass
class CrisisResult:
    is_crisis: bool = False
    confidence: str = "none"  # none | low | medium | high
    method: str = "none"  # regex_high | regex_medium | regex_behavioral | semantic | combined
    semantic_score: float = 0.0
    matched_pattern: Optional[str] = None

    @property
    def needs_immediate_resources(self) -> bool:
        return self.is_crisis and self.confidence in ("high", "medium")


def detect_crisis_regex(text: str) -> CrisisResult:
    """Layer 1: Pattern matching with confidence levels."""
    text_lower = text.lower()

    # HIGH confidence patterns
    for p in _crisis_high_re:
        m = p.search(text_lower)
        if m:
            return CrisisResult(
                is_crisis=True, confidence="high",
                method="regex_high", matched_pattern=m.group(0)
            )

    # MEDIUM confidence patterns
    for p in _crisis_medium_re:
        m = p.search(text_lower)
        if m:
            return CrisisResult(
                is_crisis=True, confidence="medium",
                method="regex_medium", matched_pattern=m.group(0)
            )

    # BEHAVIORAL signals
    for p in _crisis_behavioral_re:
        m = p.search(text_lower)
        if m:
            return CrisisResult(
                is_crisis=True, confidence="medium",
                method="regex_behavioral", matched_pattern=m.group(0)
            )

    return CrisisResult()


# ═══════════════════════════════════════════════════════════════
# Layer 2: Semantic Crisis Detection via FAISS
# ═══════════════════════════════════════════════════════════════

# Expanded from 12 → 25 reference phrases
_CRISIS_SEMANTIC_REFS: List[str] = [
    # Direct suicidal ideation
    "I want to end my life",
    "I don't see any point in living anymore",
    "Everyone would be better off without me",
    "I've been thinking about suicide",
    "I just want the pain to stop permanently",
    "I'm planning to hurt myself tonight",
    "I've written my goodbye letters",
    "I can't go on like this, I want to die",
    "Nobody would even notice if I was gone",
    "I've been collecting pills to end it all",
    "I just want to disappear forever",
    "I'm a burden to everyone around me",
    # Indirect / behavioral
    "I've been giving away all my things",
    "I drove to the bridge last night",
    "I've made peace with dying",
    "I just want the suffering to end for good",
    "There's no future for me anymore",
    "I keep thinking about how to do it",
    "I've already decided what I'm going to do",
    "Tonight is going to be my last night",
    "I can't take this pain any longer",
    "Life has no meaning for me now",
    "I feel completely empty and hopeless with no way out",
    "I've been researching methods to end it",
    "I don't want to wake up tomorrow",
]

_crisis_faiss_index = None
_sem_model = None


def init_crisis_semantic(model: Any, cache_dir: str) -> None:
    """Initialize FAISS crisis index. Called once at startup."""
    global _crisis_faiss_index, _sem_model
    _sem_model = model

    if model is None:
        logger.warning("No sentence model — semantic crisis detection disabled")
        return

    try:
        import faiss
        cache_path = os.path.join(cache_dir, "crisis_v2.index")

        if os.path.exists(cache_path):
            _crisis_faiss_index = faiss.read_index(cache_path)
            logger.info(f"Crisis FAISS loaded from cache: {_crisis_faiss_index.ntotal} vectors")
            return

        embs = model.encode(
            _CRISIS_SEMANTIC_REFS,
            convert_to_numpy=True,
            normalize_embeddings=True,
        ).astype("float32")
        _crisis_faiss_index = faiss.IndexFlatIP(embs.shape[1])
        _crisis_faiss_index.add(embs)

        os.makedirs(cache_dir, exist_ok=True)
        faiss.write_index(_crisis_faiss_index, cache_path)
        logger.info(f"Crisis FAISS built and cached: {_crisis_faiss_index.ntotal} vectors")

    except Exception as e:
        logger.error(f"Crisis FAISS init failed: {e}")
        _crisis_faiss_index = None


def detect_crisis_semantic(text: str, threshold: float = 0.68) -> CrisisResult:
    """Layer 2: Semantic similarity to crisis reference phrases."""
    if _crisis_faiss_index is None or _sem_model is None:
        return CrisisResult()

    try:
        emb = _sem_model.encode(
            [text[:500]], convert_to_numpy=True, normalize_embeddings=True
        ).astype("float32")
        scores, indices = _crisis_faiss_index.search(emb, 3)

        top_score = float(scores[0][0])
        avg_top3 = float(np.mean(scores[0][:3]))

        # High confidence if top match is very close
        if top_score >= threshold + 0.12:
            return CrisisResult(
                is_crisis=True, confidence="high",
                method="semantic", semantic_score=top_score,
            )
        # Medium if above threshold
        if top_score >= threshold:
            return CrisisResult(
                is_crisis=True, confidence="medium",
                method="semantic", semantic_score=top_score,
            )
        # Low if average of top 3 is close (pattern of crisis-adjacent language)
        if avg_top3 >= threshold - 0.05:
            return CrisisResult(
                is_crisis=False, confidence="low",
                method="semantic", semantic_score=avg_top3,
            )

        return CrisisResult(semantic_score=top_score)

    except Exception as e:
        logger.error(f"Semantic crisis detection error: {e}")
        return CrisisResult()


# ═══════════════════════════════════════════════════════════════
# Combined Detection
# ═══════════════════════════════════════════════════════════════

def detect_crisis(text: str, threshold: float = 0.68) -> CrisisResult:
    """
    Multi-layer crisis detection.
    Layer 1: Regex (fast, high precision)
    Layer 2: Semantic FAISS (catches indirect expressions)
    Combined: If both layers show signal, boost confidence.
    """
    regex_result = detect_crisis_regex(text)
    if regex_result.is_crisis and regex_result.confidence == "high":
        return regex_result  # No need to check further

    semantic_result = detect_crisis_semantic(text, threshold)

    # If regex caught it at medium confidence, return
    if regex_result.is_crisis:
        # Boost to high if semantic also agrees
        if semantic_result.semantic_score > threshold - 0.05:
            regex_result.confidence = "high"
            regex_result.method = "combined"
            regex_result.semantic_score = semantic_result.semantic_score
        return regex_result

    # If only semantic caught it
    if semantic_result.is_crisis:
        return semantic_result

    return CrisisResult(semantic_score=semantic_result.semantic_score)


# ═══════════════════════════════════════════════════════════════
# Session-Level Crisis State (with de-escalation)
# ═══════════════════════════════════════════════════════════════

@dataclass
class CrisisTracker:
    """
    Tracks crisis state across a session with proper de-escalation.
    Original code permanently locked users out after one trigger — this fixes that.
    """
    crisis_count: int = 0
    last_crisis_turn: int = -1
    consecutive_safe_turns: int = 0
    is_active: bool = False
    escalation_level: int = 0  # 0=none, 1=watchful, 2=active crisis

    SAFE_TURNS_TO_DEESCALATE = 3  # Require 3 non-crisis turns before de-escalating

    def record_turn(self, result: CrisisResult, turn: int) -> None:
        """Update crisis state based on this turn's detection result."""
        if result.is_crisis:
            self.crisis_count += 1
            self.last_crisis_turn = turn
            self.consecutive_safe_turns = 0
            self.is_active = True
            self.escalation_level = 2
        else:
            if self.is_active:
                self.consecutive_safe_turns += 1
                if self.consecutive_safe_turns >= self.SAFE_TURNS_TO_DEESCALATE:
                    self.is_active = False
                    self.escalation_level = 1  # Stay watchful, don't go to 0
            elif self.escalation_level == 1 and self.consecutive_safe_turns > 6:
                self.escalation_level = 0

    @property
    def should_show_resources(self) -> bool:
        """Show crisis resources when actively in crisis."""
        return self.is_active

    @property
    def should_be_cautious(self) -> bool:
        """Be extra careful in responses when recently in crisis."""
        return self.escalation_level >= 1
