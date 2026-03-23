"""
Verite Production API — LLM Provider Clients
Async-first, properly typed, with timeout and structured error handling.
"""
from __future__ import annotations

import json
import logging
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger("verite.llm.providers")


@dataclass
class LLMResponse:
    """Standardized response from any LLM provider."""
    raw_text: str
    input_tokens: int = 0
    output_tokens: int = 0
    cost_usd: float = 0.0
    latency_ms: int = 0
    provider: str = ""
    model: str = ""


class LLMProviderError(Exception):
    """Base error for LLM providers."""
    def __init__(self, message: str, is_retryable: bool = True, provider: str = ""):
        super().__init__(message)
        self.is_retryable = is_retryable
        self.provider = provider


class AuthenticationError(LLMProviderError):
    """API key is invalid or expired — do NOT retry."""
    def __init__(self, message: str, provider: str = ""):
        super().__init__(message, is_retryable=False, provider=provider)


class RateLimitError(LLMProviderError):
    """Rate limited — retry after backoff."""
    def __init__(self, message: str, provider: str = "", retry_after: float = 0):
        super().__init__(message, is_retryable=True, provider=provider)
        self.retry_after = retry_after


class BaseLLMProvider(ABC):
    """Abstract base for all LLM providers."""

    name: str = "base"
    model: str = ""

    @abstractmethod
    async def call(self, system_prompt: str, messages: List[Dict[str, str]]) -> LLMResponse:
        """Send a request to the LLM provider."""
        ...

    def _classify_error(self, error: Exception) -> LLMProviderError:
        """Classify an error as retryable or not."""
        msg = str(error).lower()
        if any(k in msg for k in ["401", "403", "invalid api key", "authentication", "unauthorized"]):
            return AuthenticationError(str(error), provider=self.name)
        if any(k in msg for k in ["429", "rate", "quota", "resource_exhausted"]):
            return RateLimitError(str(error), provider=self.name)
        if any(k in msg for k in ["500", "502", "503", "504", "timeout", "connection"]):
            return LLMProviderError(str(error), is_retryable=True, provider=self.name)
        return LLMProviderError(str(error), is_retryable=True, provider=self.name)


class GroqProvider(BaseLLMProvider):
    name = "groq"
    model = "llama-3.3-70b-versatile"
    # FIX #10: Llama 3.3 70B has 128K context — use reasonable portion
    MAX_SYSTEM_CHARS = 10000
    MAX_MESSAGES = 10

    def __init__(self, api_key: str, temperature: float = 0.7, max_tokens: int = 1024):
        from groq import AsyncGroq
        self.client = AsyncGroq(api_key=api_key)
        self.temperature = temperature
        self.max_tokens = max_tokens

    async def call(self, system_prompt: str, messages: List[Dict[str, str]]) -> LLMResponse:
        start = time.monotonic()
        try:
            sys_prompt = system_prompt[:self.MAX_SYSTEM_CHARS]
            sys_prompt += "\n\nCRITICAL: Respond with ONLY a valid JSON object. No other text."
            recent = messages[-self.MAX_MESSAGES:] if len(messages) > self.MAX_MESSAGES else messages
            all_msgs = [{"role": "system", "content": sys_prompt}] + recent

            resp = await self.client.chat.completions.create(
                model=self.model,
                messages=all_msgs,
                max_tokens=self.max_tokens,
                temperature=self.temperature,
            )

            text = resp.choices[0].message.content or ""
            latency = int((time.monotonic() - start) * 1000)

            return LLMResponse(
                raw_text=text.strip(),
                input_tokens=resp.usage.prompt_tokens if resp.usage else 0,
                output_tokens=resp.usage.completion_tokens if resp.usage else 0,
                cost_usd=0.0,  # Free tier
                latency_ms=latency,
                provider=self.name,
                model=self.model,
            )
        except Exception as e:
            raise self._classify_error(e)


class GeminiProvider(BaseLLMProvider):
    name = "gemini"
    model = "gemini-1.5-flash"
    # FIX #10: Gemini Flash has 1M context — use a reasonable portion
    MAX_SYSTEM_CHARS = 12000
    MAX_MESSAGES = 12

    def __init__(self, api_key: str, temperature: float = 0.7, max_tokens: int = 1024):
        import google.generativeai as genai
        genai.configure(api_key=api_key)
        self._genai = genai
        self.client = genai.GenerativeModel(self.model)
        self.temperature = temperature
        self.max_tokens = max_tokens

    async def call(self, system_prompt: str, messages: List[Dict[str, str]]) -> LLMResponse:
        start = time.monotonic()
        try:
            # FIX #10: Use more of the context window
            sp = system_prompt[:self.MAX_SYSTEM_CHARS]
            recent = messages[-self.MAX_MESSAGES:] if len(messages) > self.MAX_MESSAGES else messages

            prompt_parts = [sp, "\nConversation:"]
            for m in recent:
                role = "User" if m["role"] == "user" else "Assistant"
                prompt_parts.append(f"{role}: {m['content']}")
            prompt_parts.append(
                "\nRespond as Assistant. Output ONLY valid JSON, nothing else. "
                "No markdown. Just the raw JSON object starting with { and ending with }."
            )

            # FIX #4: Keep safety filters active for a mental health product.
            # Only lower the threshold on harassment since therapeutic conversations
            # can be flagged incorrectly. Keep all others at default.
            safety = {
                "HARM_CATEGORY_HARASSMENT": "BLOCK_MEDIUM_AND_ABOVE",
                "HARM_CATEGORY_HATE_SPEECH": "BLOCK_MEDIUM_AND_ABOVE",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT": "BLOCK_MEDIUM_AND_ABOVE",
                "HARM_CATEGORY_DANGEROUS_CONTENT": "BLOCK_MEDIUM_AND_ABOVE",
            }

            # FIX #5: Run synchronous Gemini call in executor to avoid blocking event loop
            import asyncio
            loop = asyncio.get_event_loop()
            resp = await loop.run_in_executor(
                None,
                lambda: self.client.generate_content(
                    "\n".join(prompt_parts),
                    generation_config={
                        "temperature": self.temperature,
                        "max_output_tokens": self.max_tokens,
                        "response_mime_type": "application/json",
                    },
                    safety_settings=safety,
                ),
            )

            if not resp.candidates:
                raise LLMProviderError("Gemini returned no candidates", provider=self.name)

            text = ""
            try:
                text = resp.text
            except (ValueError, AttributeError):
                try:
                    text = resp.candidates[0].content.parts[0].text
                except Exception:
                    raise LLMProviderError("Gemini: no text extractable", provider=self.name)

            latency = int((time.monotonic() - start) * 1000)
            in_tok = getattr(resp.usage_metadata, "prompt_token_count", 0) if hasattr(resp, "usage_metadata") else 0
            out_tok = getattr(resp.usage_metadata, "candidates_token_count", 0) if hasattr(resp, "usage_metadata") else 0

            return LLMResponse(
                raw_text=text.strip(),
                input_tokens=in_tok,
                output_tokens=out_tok,
                cost_usd=0.0,
                latency_ms=latency,
                provider=self.name,
                model=self.model,
            )
        except LLMProviderError:
            raise
        except Exception as e:
            raise self._classify_error(e)


class TogetherProvider(BaseLLMProvider):
    name = "together"
    model = "meta-llama/Llama-3.3-70B-Instruct-Turbo"
    MAX_SYSTEM_CHARS = 10000
    MAX_MESSAGES = 10

    def __init__(self, api_key: str, temperature: float = 0.7, max_tokens: int = 1024):
        from openai import AsyncOpenAI
        self.client = AsyncOpenAI(api_key=api_key, base_url="https://api.together.xyz/v1")
        self.temperature = temperature
        self.max_tokens = max_tokens

    async def call(self, system_prompt: str, messages: List[Dict[str, str]]) -> LLMResponse:
        start = time.monotonic()
        try:
            sp = system_prompt[:self.MAX_SYSTEM_CHARS] + "\n\nCRITICAL: Respond with ONLY valid JSON."
            recent = messages[-self.MAX_MESSAGES:] if len(messages) > self.MAX_MESSAGES else messages
            all_msgs = [{"role": "system", "content": sp}] + recent

            resp = await self.client.chat.completions.create(
                model=self.model,
                messages=all_msgs,
                max_tokens=self.max_tokens,
                temperature=self.temperature,
            )

            text = resp.choices[0].message.content or ""
            latency = int((time.monotonic() - start) * 1000)

            return LLMResponse(
                raw_text=text.strip(),
                input_tokens=resp.usage.prompt_tokens if resp.usage else 0,
                output_tokens=resp.usage.completion_tokens if resp.usage else 0,
                cost_usd=0.0,
                latency_ms=latency,
                provider=self.name,
                model=self.model,
            )
        except Exception as e:
            raise self._classify_error(e)


class OpenAIProvider(BaseLLMProvider):
    name = "openai"
    model = "gpt-4o-mini"
    MAX_SYSTEM_CHARS = 16000  # 128K context
    MAX_MESSAGES = 16

    def __init__(self, api_key: str, temperature: float = 0.7, max_tokens: int = 1024):
        from openai import AsyncOpenAI
        self.client = AsyncOpenAI(api_key=api_key)
        self.temperature = temperature
        self.max_tokens = max_tokens

    async def call(self, system_prompt: str, messages: List[Dict[str, str]]) -> LLMResponse:
        start = time.monotonic()
        try:
            sp = system_prompt[:self.MAX_SYSTEM_CHARS] + "\n\nRespond with ONLY valid JSON."
            recent = messages[-self.MAX_MESSAGES:] if len(messages) > self.MAX_MESSAGES else messages
            all_msgs = [{"role": "system", "content": sp}] + recent

            resp = await self.client.chat.completions.create(
                model=self.model,
                messages=all_msgs,
                max_tokens=self.max_tokens,
                temperature=self.temperature,
                response_format={"type": "json_object"},
            )

            text = resp.choices[0].message.content or ""
            latency = int((time.monotonic() - start) * 1000)

            # Pricing for gpt-4o-mini
            in_tok = resp.usage.prompt_tokens if resp.usage else 0
            out_tok = resp.usage.completion_tokens if resp.usage else 0
            cost = (in_tok * 0.00000015) + (out_tok * 0.0000006)

            return LLMResponse(
                raw_text=text.strip(),
                input_tokens=in_tok,
                output_tokens=out_tok,
                cost_usd=cost,
                latency_ms=latency,
                provider=self.name,
                model=self.model,
            )
        except Exception as e:
            raise self._classify_error(e)


class AnthropicProvider(BaseLLMProvider):
    name = "anthropic"
    model = "claude-sonnet-4-20250514"
    MAX_SYSTEM_CHARS = 20000  # 200K context
    MAX_MESSAGES = 20

    def __init__(self, api_key: str, temperature: float = 0.7, max_tokens: int = 1024):
        from anthropic import AsyncAnthropic
        self.client = AsyncAnthropic(api_key=api_key)
        self.temperature = temperature
        self.max_tokens = max_tokens

    async def call(self, system_prompt: str, messages: List[Dict[str, str]]) -> LLMResponse:
        start = time.monotonic()
        try:
            recent = messages[-self.MAX_MESSAGES:] if len(messages) > self.MAX_MESSAGES else messages

            resp = await self.client.messages.create(
                model=self.model,
                system=system_prompt[:self.MAX_SYSTEM_CHARS] + "\n\nRespond with ONLY valid JSON.",
                messages=recent,
                max_tokens=self.max_tokens,
                temperature=self.temperature,
            )

            text = resp.content[0].text if resp.content else ""
            latency = int((time.monotonic() - start) * 1000)

            in_tok = resp.usage.input_tokens if resp.usage else 0
            out_tok = resp.usage.output_tokens if resp.usage else 0
            cost = (in_tok * 0.000003) + (out_tok * 0.000015)

            return LLMResponse(
                raw_text=text.strip(),
                input_tokens=in_tok,
                output_tokens=out_tok,
                cost_usd=cost,
                latency_ms=latency,
                provider=self.name,
                model=self.model,
            )
        except Exception as e:
            raise self._classify_error(e)


# ═══════════════════════════════════════════════════════════════
# Fallback (no API needed)
# ═══════════════════════════════════════════════════════════════

class FallbackProvider(BaseLLMProvider):
    """Rule-based fallback when all API providers fail."""
    name = "fallback"
    model = "rule-based"

    async def call(self, system_prompt: str, messages: List[Dict[str, str]]) -> LLMResponse:
        return LLMResponse(
            raw_text=json.dumps({
                "domain": "unknown",
                "phase": "intake",
                "emotional_intensity": 0.5,
                "distortions_detected": [],
                "therapeutic_move": "validate",
                "crisis_signal": False,
                "reasoning": "Using fallback — API unavailable",
                "response": (
                    "I'm here and I want to help. I'm experiencing a temporary issue "
                    "connecting to my language processing service, but I'm still listening. "
                    "Could you tell me a bit more about what's on your mind?"
                ),
            }),
            provider=self.name,
            model=self.model,
        )


# ═══════════════════════════════════════════════════════════════
# Provider Factory
# ═══════════════════════════════════════════════════════════════

def create_provider(
    name: str,
    api_key: str,
    temperature: float = 0.7,
    max_tokens: int = 1024,
) -> BaseLLMProvider:
    """Factory function to create an LLM provider."""
    providers = {
        "groq": GroqProvider,
        "gemini": GeminiProvider,
        "together": TogetherProvider,
        "openai": OpenAIProvider,
        "anthropic": AnthropicProvider,
        "fallback": FallbackProvider,
    }
    cls = providers.get(name)
    if cls is None:
        raise ValueError(f"Unknown provider: {name}")
    if name == "fallback":
        return cls()
    return cls(api_key=api_key, temperature=temperature, max_tokens=max_tokens)
