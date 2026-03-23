"""
Speech-to-text engine.
Primary: Google Cloud Speech-to-Text v1 (streaming or batch).
Fallback: OpenAI Whisper (offline, runs on CPU).
"""

import asyncio
import io
import struct
import wave
from typing import Optional, Tuple
import structlog

log = structlog.get_logger()


class STTEngine:
    def __init__(self):
        from ..api.config import settings
        self._use_cloud = settings.VOICE_USE_CLOUD and bool(settings.GOOGLE_CLOUD_PROJECT)

        if self._use_cloud:
            try:
                from google.cloud import speech_v1 as speech
                self._client = speech.SpeechClient()
                self._speech_mod = speech
                log.info("stt_engine", backend="google_cloud_speech")
            except Exception as e:
                log.warning("google_stt_unavailable", error=str(e), fallback="whisper")
                self._use_cloud = False

        if not self._use_cloud:
            try:
                import whisper
                from ..api.config import settings
                self._whisper = whisper.load_model(settings.WHISPER_MODEL_SIZE)
                log.info("stt_engine", backend="whisper", model=settings.WHISPER_MODEL_SIZE)
            except ImportError:
                log.warning("whisper_not_installed")
                self._whisper = None

    async def transcribe_bytes(self, audio_bytes: bytes) -> Tuple[str, float, str]:
        """
        Transcribe audio bytes (WAV/FLAC).
        Returns (transcript, confidence, detected_locale).
        """
        if self._use_cloud:
            return await asyncio.to_thread(self._google_transcribe, audio_bytes)
        return await asyncio.to_thread(self._whisper_transcribe, audio_bytes)

    def _google_transcribe(self, audio_bytes: bytes) -> Tuple[str, float, str]:
        speech = self._speech_mod
        config = speech.RecognitionConfig(
            encoding=speech.RecognitionConfig.AudioEncoding.LINEAR16,
            sample_rate_hertz=16000,
            enable_automatic_punctuation=True,
            enable_word_confidence=True,
            model="latest_long",
            language_code="en-US",
            alternative_language_codes=["es-US", "fr-FR", "de-DE", "zh", "ja-JP", "pt-BR"],
        )
        audio = speech.RecognitionAudio(content=audio_bytes)
        response = self._client.recognize(config=config, audio=audio)

        if not response.results:
            return "", 0.0, "en-US"

        result = response.results[0].alternatives[0]
        confidence = float(result.confidence) if result.confidence else 0.9
        detected_locale = getattr(response.results[0], "language_code", "en-US")
        return result.transcript, confidence, detected_locale

    def _whisper_transcribe(self, audio_bytes: bytes) -> Tuple[str, float, str]:
        if self._whisper is None:
            return "", 0.0, "en"
        import numpy as np, tempfile, os
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
            f.write(audio_bytes)
            tmp = f.name
        try:
            result = self._whisper.transcribe(tmp, word_timestamps=False)
            transcript = result.get("text", "").strip()
            locale = result.get("language", "en")
            return transcript, 0.85, locale
        finally:
            os.unlink(tmp)


class EmotionAnalyzer:
    """
    Extracts emotional dimensions from voice audio.
    Primary: Hume AI Expression API.
    Fallback: returns None (graceful degradation).
    """

    def __init__(self):
        from ..api.config import settings
        self._api_key = settings.HUME_API_KEY

    async def analyze_audio_bytes(self, audio_bytes: bytes) -> Optional[dict]:
        if not self._api_key:
            return None
        try:
            return await asyncio.to_thread(self._hume_analyze, audio_bytes)
        except Exception as e:
            log.warning("emotion_analysis_failed", error=str(e))
            return None

    def _hume_analyze(self, audio_bytes: bytes) -> Optional[dict]:
        import httpx, base64, time
        encoded = base64.b64encode(audio_bytes).decode()

        with httpx.Client(timeout=15.0) as client:
            # Submit job
            resp = client.post(
                "https://api.hume.ai/v0/batch/jobs",
                headers={"X-Hume-Api-Key": self._api_key, "Content-Type": "application/json"},
                json={
                    "models": {"prosody": {}},
                    "urls": [],
                    "files": [{"data": encoded, "filename": "audio.wav"}],
                },
            )
            resp.raise_for_status()
            job_id = resp.json()["job_id"]

            # Poll for completion (up to 30 s)
            for _ in range(30):
                time.sleep(1.0)
                status_resp = client.get(
                    f"https://api.hume.ai/v0/batch/jobs/{job_id}",
                    headers={"X-Hume-Api-Key": self._api_key},
                )
                status = status_resp.json().get("state", {}).get("status")
                if status == "COMPLETED":
                    break

            # Fetch predictions
            pred_resp = client.get(
                f"https://api.hume.ai/v0/batch/jobs/{job_id}/predictions",
                headers={"X-Hume-Api-Key": self._api_key},
            )
            pred_resp.raise_for_status()
            predictions = pred_resp.json()

        # Extract top emotions, averaged across utterances
        emotions: dict[str, list[float]] = {}
        try:
            for file_pred in predictions:
                for model_pred in file_pred.get("models", {}).get("prosody", {}).get("grouped_predictions", []):
                    for prediction in model_pred.get("predictions", []):
                        for em in prediction.get("emotions", []):
                            name = em["name"]
                            emotions.setdefault(name, []).append(em["score"])
            return {k: float(sum(v) / len(v)) for k, v in emotions.items()}
        except Exception:
            return None
