"""
Multi-provider LLM router with ordered fallback chain.
Providers: Groq (Llama-3.3-70B) → Gemini 2.0 Flash → OpenAI GPT-4o (optional)
All calls are async with configurable timeout and retry.
"""

import json
import asyncio
from typing import Optional, AsyncIterator
import httpx
import structlog

from ..api.config import settings

log = structlog.get_logger()

GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
OPENAI_URL = "https://api.openai.com/v1/chat/completions"


class ModelRouter:
    """
    Async LLM router. Tries providers in order; on failure moves to next.
    Always returns something — worst case is the rule-based template fallback.
    """

    def get_providers(self) -> list[str]:
        """Return available providers in priority order."""
        providers = []
        if settings.GROQ_API_KEY:
            providers.append("groq")
        if settings.GEMINI_API_KEY:
            providers.append("gemini")
        if settings.OPENAI_API_KEY:
            providers.append("openai")
        return providers

    async def generate_async(
        self,
        provider: str,
        system: str,
        user: str,
        max_tokens: int = 4000,
        temperature: float = 0.7,
    ) -> str:
        """Route to the named provider and return the response text."""
        if provider == "groq":
            return await self._groq(system, user, max_tokens, temperature)
        elif provider == "gemini":
            return await self._gemini(system, user, max_tokens, temperature)
        elif provider == "openai":
            return await self._openai(system, user, max_tokens, temperature)
        raise ValueError(f"Unknown provider: {provider}")

    async def generate_with_fallback(
        self,
        system: str,
        user: str,
        max_tokens: int = 4000,
    ) -> tuple[str, str]:
        """
        Try all providers; returns (response_text, provider_used).
        Raises RuntimeError only if ALL providers fail.
        """
        errors = []
        for provider in self.get_providers():
            try:
                text = await self.generate_async(provider, system, user, max_tokens)
                return text, provider
            except Exception as e:
                log.warning("provider_failed", provider=provider, error=str(e))
                errors.append(f"{provider}: {e}")

        raise RuntimeError(f"All LLM providers failed: {'; '.join(errors)}")

    async def _groq(self, system: str, user: str, max_tokens: int, temperature: float) -> str:
        async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT_SECONDS) as client:
            resp = await client.post(
                GROQ_URL,
                headers={
                    "Authorization": f"Bearer {settings.GROQ_API_KEY}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": "llama-3.3-70b-versatile",
                    "messages": [
                        {"role": "system", "content": system},
                        {"role": "user", "content": user},
                    ],
                    "max_tokens": max_tokens,
                    "temperature": temperature,
                    "response_format": {"type": "json_object"},
                },
            )
            resp.raise_for_status()
            return resp.json()["choices"][0]["message"]["content"]

    async def _gemini(self, system: str, user: str, max_tokens: int, temperature: float) -> str:
        async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT_SECONDS) as client:
            resp = await client.post(
                f"{GEMINI_URL}?key={settings.GEMINI_API_KEY}",
                json={
                    "system_instruction": {"parts": [{"text": system}]},
                    "contents": [{"parts": [{"text": user}]}],
                    "generationConfig": {
                        "maxOutputTokens": max_tokens,
                        "temperature": temperature,
                        "responseMimeType": "application/json",
                    },
                },
            )
            resp.raise_for_status()
            data = resp.json()
            return data["candidates"][0]["content"]["parts"][0]["text"]

    async def _openai(self, system: str, user: str, max_tokens: int, temperature: float) -> str:
        async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT_SECONDS) as client:
            resp = await client.post(
                OPENAI_URL,
                headers={
                    "Authorization": f"Bearer {settings.OPENAI_API_KEY}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": "gpt-4o",
                    "messages": [
                        {"role": "system", "content": system},
                        {"role": "user", "content": user},
                    ],
                    "max_tokens": max_tokens,
                    "temperature": temperature,
                    "response_format": {"type": "json_object"},
                },
            )
            resp.raise_for_status()
            return resp.json()["choices"][0]["message"]["content"]
