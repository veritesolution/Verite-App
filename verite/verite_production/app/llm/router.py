"""
Verite Production API — LLM Router & Response Parser
Async retry with exponential backoff, provider fallback chain, robust JSON extraction.
"""
from __future__ import annotations

import asyncio
import json
import logging
import re
from typing import Any, Dict, List, Optional, Tuple

from .providers import (
    AuthenticationError,
    BaseLLMProvider,
    FallbackProvider,
    LLMProviderError,
    LLMResponse,
    RateLimitError,
    create_provider,
)

logger = logging.getLogger("verite.llm.router")


# ═══════════════════════════════════════════════════════════════
# LLM Router — Async retry + provider fallback
# ═══════════════════════════════════════════════════════════════

class LLMRouter:
    """
    Routes LLM calls through a fallback chain of providers.
    - Retries retryable errors with exponential backoff
    - Fails fast on auth errors (moves to next provider)
    - Falls back to rule-based response if all providers fail
    """

    MAX_RETRIES = 2
    BASE_DELAY = 1.5
    MAX_DELAY = 15.0

    def __init__(self):
        self.providers: List[BaseLLMProvider] = []
        self.fallback = FallbackProvider()
        self._last_error: str = ""
        self._provider_health: Dict[str, bool] = {}

    def add_provider(self, provider: BaseLLMProvider) -> None:
        self.providers.append(provider)
        self._provider_health[provider.name] = True
        logger.info(f"LLM provider registered: {provider.name} ({provider.model})")

    async def call(
        self, system_prompt: str, messages: List[Dict[str, str]]
    ) -> LLMResponse:
        """
        Call the LLM with automatic retry and fallback.
        """
        self._last_error = ""
        errors: List[str] = []

        for provider in self.providers:
            if not self._provider_health.get(provider.name, True):
                continue  # Skip providers that are marked as down

            try:
                response = await self._call_with_retry(provider, system_prompt, messages)
                self._provider_health[provider.name] = True
                return response
            except AuthenticationError as e:
                error_msg = f"{provider.name}: Auth error — {e}"
                errors.append(error_msg)
                logger.error(error_msg)
                self._provider_health[provider.name] = False  # Don't retry this provider
            except LLMProviderError as e:
                error_msg = f"{provider.name}: {e}"
                errors.append(error_msg)
                logger.error(error_msg)
            except Exception as e:
                error_msg = f"{provider.name}: Unexpected — {e}"
                errors.append(error_msg)
                logger.error(error_msg)

        # All providers failed
        self._last_error = " | ".join(errors)
        logger.error(f"All LLM providers failed: {self._last_error}")
        return await self.fallback.call(system_prompt, messages)

    async def _call_with_retry(
        self,
        provider: BaseLLMProvider,
        system_prompt: str,
        messages: List[Dict[str, str]],
    ) -> LLMResponse:
        """Retry a single provider with exponential backoff."""
        last_error: Optional[Exception] = None

        for attempt in range(self.MAX_RETRIES + 1):
            try:
                return await provider.call(system_prompt, messages)
            except AuthenticationError:
                raise  # Never retry auth errors
            except RateLimitError as e:
                last_error = e
                delay = max(e.retry_after, self.BASE_DELAY * (2 ** attempt))
                delay = min(delay, self.MAX_DELAY)
                logger.warning(
                    f"{provider.name}: Rate limited, retry {attempt+1}/{self.MAX_RETRIES} in {delay:.1f}s"
                )
                await asyncio.sleep(delay)
            except LLMProviderError as e:
                if not e.is_retryable:
                    raise
                last_error = e
                delay = self.BASE_DELAY * (2 ** attempt)
                logger.warning(
                    f"{provider.name}: Retryable error, retry {attempt+1}/{self.MAX_RETRIES} in {delay:.1f}s"
                )
                await asyncio.sleep(delay)

        raise last_error or LLMProviderError("Max retries exceeded", provider=provider.name)

    @property
    def last_error(self) -> str:
        return self._last_error

    @property
    def active_provider(self) -> str:
        for p in self.providers:
            if self._provider_health.get(p.name, True):
                return p.name
        return "fallback"

    def health_status(self) -> Dict[str, Any]:
        return {
            "providers": {p.name: self._provider_health.get(p.name, True) for p in self.providers},
            "active": self.active_provider,
            "total_providers": len(self.providers),
        }


# ═══════════════════════════════════════════════════════════════
# JSON Response Parser — 4-strategy extraction
# ═══════════════════════════════════════════════════════════════

def parse_llm_response(raw: str) -> Tuple[Dict[str, Any], bool]:
    """
    Parse LLM response into structured data.
    Tries 4 strategies in order of strictness.
    Returns (parsed_dict, json_ok).
    """
    defaults = {
        "domain": "unknown",
        "phase": "support",
        "emotional_intensity": 0.5,
        "distortions_detected": [],
        "therapeutic_move": "validate",
        "crisis_signal": False,
        "reasoning": "",
        "response": "",
    }

    if not raw or not raw.strip():
        defaults["response"] = "I'm here to listen. Could you tell me more about what's going on?"
        return defaults, False

    raw = raw.strip()

    # Strategy 1: Direct JSON parse
    parsed = _try_json_parse(raw)
    if parsed:
        defaults.update(parsed)
        return defaults, True

    # Strategy 2: Strip markdown code fences
    cleaned = re.sub(r"^```(?:json)?\s*", "", raw)
    cleaned = re.sub(r"\s*```$", "", cleaned).strip()
    parsed = _try_json_parse(cleaned)
    if parsed:
        defaults.update(parsed)
        return defaults, True

    # Strategy 3: Balanced-brace extraction (handles text before/after JSON)
    extracted = _extract_json_balanced(raw)
    if extracted:
        defaults.update(extracted)
        return defaults, True

    # Strategy 4: Greedy brace extraction (find first { and last })
    brace_start = raw.find("{")
    brace_end = raw.rfind("}")
    if brace_start >= 0 and brace_end > brace_start:
        parsed = _try_json_parse(raw[brace_start:brace_end + 1])
        if parsed:
            defaults.update(parsed)
            return defaults, True

    # Strategy 5: Raw text fallback
    logger.warning(f"JSON parse failed — using raw text. Start: {raw[:100]}")
    clean_text = re.sub(r"```.*?```", "", raw, flags=re.DOTALL).strip()
    # Try to extract just the response text
    resp_match = re.search(r'"response"\s*:\s*"([^"]*(?:\\.[^"]*)*)"', raw)
    if resp_match:
        defaults["response"] = resp_match.group(1).replace('\\"', '"').replace("\\n", "\n")
        return defaults, False

    defaults["response"] = clean_text[:800] if clean_text else raw[:800]
    return defaults, False


def _try_json_parse(text: str) -> Optional[Dict[str, Any]]:
    """Try to parse text as JSON, return None on failure."""
    try:
        result = json.loads(text)
        if isinstance(result, dict) and result.get("response"):
            return result
    except (json.JSONDecodeError, TypeError):
        pass
    return None


def _extract_json_balanced(text: str) -> Optional[Dict[str, Any]]:
    """
    Extract JSON using balanced brace matching.
    Handles nested objects correctly (original code had issues with this).
    """
    depth = 0
    start = -1

    for i, ch in enumerate(text):
        if ch == "{":
            if depth == 0:
                start = i
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0 and start >= 0:
                candidate = text[start:i + 1]
                parsed = _try_json_parse(candidate)
                if parsed:
                    return parsed
                # If this JSON block didn't work, keep looking
                start = -1
            elif depth < 0:
                depth = 0
                start = -1

    return None
