"""
Verite Production API — Content Screening
Input validation, prompt injection defense, toxicity screening, harmful advice detection.
"""
from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from typing import Any, List, Optional, Tuple

logger = logging.getLogger("verite.safety.content")


# ═══════════════════════════════════════════════════════════════
# Input Validation
# ═══════════════════════════════════════════════════════════════

@dataclass
class ValidationResult:
    is_valid: bool
    cleaned_text: str = ""
    error: str = ""
    was_sanitized: bool = False


def validate_input(text: Any, max_chars: int = 3000) -> ValidationResult:
    """Validate and sanitize user input."""
    if not isinstance(text, str):
        return ValidationResult(False, error="Input must be text.")

    text = text.strip()
    if not text:
        return ValidationResult(False, error="Please type a message.")

    if len(text) < 2:
        return ValidationResult(False, error="Message too short.")

    if len(text) > max_chars:
        return ValidationResult(
            False, error=f"Message too long ({len(text)}/{max_chars} chars)."
        )

    # Check for non-text content (binary, excessive special chars)
    printable_ratio = sum(1 for c in text if c.isprintable() or c in "\n\r\t") / len(text)
    if printable_ratio < 0.8:
        return ValidationResult(False, error="Message contains too many non-text characters.")

    # Sanitize
    cleaned, was_sanitized = sanitize_input(text, max_chars)

    return ValidationResult(
        is_valid=True,
        cleaned_text=cleaned,
        was_sanitized=was_sanitized,
    )


# ═══════════════════════════════════════════════════════════════
# Prompt Injection Defense
# Multi-layer: pattern removal + structural detection
# ═══════════════════════════════════════════════════════════════

# Direct injection patterns
_INJECTION_PATTERNS: List[str] = [
    # Role/identity manipulation
    r"(?:ignore|forget|disregard|override|bypass)\s+(?:all|every|your|previous|above|prior)\s*"
    r"(?:instructions?|rules?|prompts?|guidelines?|constraints?|system\s*(?:prompt)?)",
    r"you\s+are\s+now\s+(?:a\s+)?(?:new|different)",
    r"pretend\s+(?:you\s+are|to\s+be|you're)",
    r"(?:act|behave)\s+as\s+(?:if\s+you\s+(?:are|were)|a\s+different)",
    r"(?:jailbreak|DAN\s*mode|developer\s*mode|god\s*mode)",
    r"(?:enter|switch\s+to|enable)\s+(?:unrestricted|unfiltered|uncensored)\s+mode",
    # System prompt extraction
    r"(?:show|reveal|display|repeat|print|output|tell\s+me)\s+(?:your|the)\s+"
    r"(?:system|initial|original|hidden|secret)\s+(?:prompt|instructions?|message)",
    r"what\s+(?:are|were)\s+your\s+(?:initial|system|original)\s+instructions?",
    # Output manipulation
    r"(?:respond|reply|answer)\s+(?:only\s+)?(?:with|in)\s+(?:the\s+)?(?:word|phrase)",
    r"do\s+not\s+(?:follow|obey|listen\s+to)\s+(?:your|the|any)\s+(?:rules|guidelines|instructions)",
    # Encoding tricks
    r"base64\s*(?:decode|encode)",
    r"(?:translate|convert)\s+(?:from|to)\s+(?:hex|binary|base64|rot13)",
]

_injection_re = [re.compile(p, re.IGNORECASE | re.DOTALL) for p in _INJECTION_PATTERNS]

# Structural injection markers (Unicode tricks, invisible chars)
_STRUCTURAL_PATTERNS = [
    re.compile(r"[\u200b-\u200f\u2028-\u202f\ufeff]"),  # Zero-width / invisible chars
    re.compile(r"[\u0000-\u0008\u000b\u000c\u000e-\u001f]"),  # Control characters
]


def sanitize_input(text: str, max_chars: int = 3000) -> Tuple[str, bool]:
    """
    Multi-layer sanitization.
    Returns (cleaned_text, was_modified).
    """
    original = text
    modified = False

    # Remove invisible / zero-width characters
    for p in _STRUCTURAL_PATTERNS:
        cleaned = p.sub("", text)
        if cleaned != text:
            text = cleaned
            modified = True

    # Remove injection patterns
    for p in _injection_re:
        cleaned = p.sub("[filtered]", text)
        if cleaned != text:
            text = cleaned
            modified = True
            logger.warning(f"Injection pattern detected and filtered")

    # Normalize excessive whitespace
    text = re.sub(r"\s{3,}", "  ", text)

    # Truncate
    text = text[:max_chars].strip()

    return text, modified


# ═══════════════════════════════════════════════════════════════
# Output Toxicity Screening
# ═══════════════════════════════════════════════════════════════

_detox_model = None


def init_toxicity_model() -> None:
    """Initialize Detoxify model. Called once at startup."""
    global _detox_model
    try:
        from detoxify import Detoxify
        _detox_model = Detoxify("original")
        logger.info("Detoxify model loaded")
    except ImportError:
        logger.warning("Detoxify not installed — toxicity screening disabled")
    except Exception as e:
        logger.error(f"Detoxify init failed: {e}")


def screen_toxicity(text: str, threshold: float = 0.35) -> Tuple[bool, float]:
    """
    Screen text for toxicity using Detoxify.
    Returns (is_safe, toxicity_score).
    """
    if _detox_model is None:
        return True, 0.0

    try:
        result = _detox_model.predict(text)
        toxicity = float(result.get("toxicity", 0.0))
        return toxicity < threshold, toxicity
    except Exception as e:
        logger.error(f"Toxicity screening error: {e}")
        return True, 0.0  # Fail open for availability, log for monitoring


# ═══════════════════════════════════════════════════════════════
# Harmful Advice Detection (for LLM output screening)
# ═══════════════════════════════════════════════════════════════

_HARMFUL_PATTERNS: List[str] = [
    # Medication interference
    r"\b(?:stop|quit|don't|do\s+not)\s+tak(?:e|ing)\s+(?:your\s+)?(?:medication|meds|pills|medicine|prescriptions?)\b",
    r"\b(?:throw\s+away|flush|get\s+rid\s+of)\s+(?:your\s+)?(?:medication|meds|pills)\b",
    r"\b(?:medication|meds|pills)\s+(?:are|is)\s+(?:poison|harmful|making\s+(?:you|things)\s+worse)\b",

    # Anti-professional-help
    r"\byou\s+(?:don't|do\s+not)\s+need\s+(?:a\s+)?(?:therapist|psychiatrist|doctor|counselor|professional\s+help)\b",
    r"\b(?:therapy|counseling|psychiatr(?:y|ists?))\s+(?:is|are)\s+(?:a\s+)?(?:scam|waste|useless|pointless)\b",

    # Toxic positivity / minimizing
    r"\b(?:just|simply)\s+(?:get\s+over\s+it|snap\s+out\s+of\s+it|stop\s+being\s+(?:sad|depressed|anxious|worried))\b",
    r"\b(?:just|simply)\s+(?:be\s+happy|think\s+positive|cheer\s+up|smile\s+more)\b",
    r"\bothers\s+have\s+it\s+(?:much\s+)?(?:worse|harder)\s+than\s+you\b",
    r"\b(?:man|woman|grow)\s+up\s+and\s+(?:deal|cope|handle)\s+(?:with\s+)?it\b",

    # Dangerous self-help advice
    r"\byou\s+(?:should|need\s+to)\s+(?:just\s+)?(?:forgive|forget)\s+(?:and\s+move\s+on|already|everything)\b",
    r"\b(?:you're|you\s+are)\s+(?:being\s+)?(?:too\s+)?(?:dramatic|sensitive|emotional|weak)\b",

    # Substance encouragement
    r"\b(?:have|take|try)\s+(?:a\s+)?(?:drink|shot|beer|wine|alcohol)\s+(?:to\s+)?(?:relax|calm|feel\s+better)\b",
    r"\b(?:try|use|take)\s+(?:some\s+)?(?:weed|marijuana|drugs?)\s+(?:to\s+)?(?:help|cope|relax|feel)\b",
]

_harmful_re = [re.compile(p, re.IGNORECASE) for p in _HARMFUL_PATTERNS]


def check_harmful_advice(text: str) -> bool:
    """Check if LLM output contains harmful advice. Returns True if harmful."""
    return any(p.search(text) for p in _harmful_re)


# ═══════════════════════════════════════════════════════════════
# Combined Output Screening
# ═══════════════════════════════════════════════════════════════

@dataclass
class ScreeningResult:
    is_safe: bool
    toxicity_score: float = 0.0
    harmful_advice_detected: bool = False
    reason: str = ""


def screen_output(text: str, toxicity_threshold: float = 0.35) -> ScreeningResult:
    """Full output screening pipeline."""
    # Toxicity
    is_safe_tox, tox_score = screen_toxicity(text, toxicity_threshold)

    # Harmful advice
    is_harmful = check_harmful_advice(text)

    is_safe = is_safe_tox and not is_harmful

    reason = ""
    if not is_safe_tox:
        reason = f"toxicity={tox_score:.3f}"
    if is_harmful:
        reason += " harmful_advice" if reason else "harmful_advice"

    return ScreeningResult(
        is_safe=is_safe,
        toxicity_score=tox_score,
        harmful_advice_detected=is_harmful,
        reason=reason,
    )


# ═══════════════════════════════════════════════════════════════
# Safe Fallback Response
# ═══════════════════════════════════════════════════════════════

SAFE_FALLBACK_RESPONSE = (
    "I appreciate you sharing that with me. I'm having a brief technical issue "
    "and want to make sure I give you a thoughtful response. "
    "If you're in distress right now, please reach out to a crisis line: "
    "988 (US), 116 123 (UK), or https://www.iasp.info/resources/Crisis_Centres/. "
    "Otherwise, please try again in a moment."
)
