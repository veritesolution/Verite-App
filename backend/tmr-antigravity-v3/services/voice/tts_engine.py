"""
Text-to-speech engine.
Primary: Google Cloud TTS (neural Journey voices).
Fallback: pyttsx3 (fully offline, system TTS).
"""

import asyncio
import io
import structlog

log = structlog.get_logger()


class TTSEngine:
    def __init__(self):
        from ..api.config import settings
        self._use_cloud = settings.VOICE_USE_CLOUD and bool(settings.GOOGLE_CLOUD_PROJECT)
        if self._use_cloud:
            try:
                from google.cloud import texttospeech
                self._client = texttospeech.TextToSpeechClient()
                self._tts_mod = texttospeech
                log.info("tts_engine", backend="google_cloud")
            except Exception as e:
                log.warning("google_tts_unavailable", error=str(e), fallback="pyttsx3")
                self._use_cloud = False

        if not self._use_cloud:
            log.info("tts_engine", backend="pyttsx3_offline")

    async def synthesize(
        self,
        text: str,
        locale: str = "en-US",
        voice_name: str = "en-US-Journey-F",
        speaking_rate: float = 0.95,
    ) -> bytes:
        """Returns raw WAV bytes."""
        if self._use_cloud:
            return await asyncio.to_thread(
                self._cloud_synthesize, text, locale, voice_name, speaking_rate
            )
        return await asyncio.to_thread(self._pyttsx3_synthesize, text)

    def _cloud_synthesize(
        self, text: str, locale: str, voice_name: str, speaking_rate: float
    ) -> bytes:
        tts = self._tts_mod
        synthesis_input = tts.SynthesisInput(text=text)
        voice = tts.VoiceSelectionParams(language_code=locale, name=voice_name)
        audio_config = tts.AudioConfig(
            audio_encoding=tts.AudioEncoding.LINEAR16,
            speaking_rate=speaking_rate,
            sample_rate_hertz=22050,
        )
        response = self._client.synthesize_speech(
            input=synthesis_input, voice=voice, audio_config=audio_config
        )
        return response.audio_content

    def _pyttsx3_synthesize(self, text: str) -> bytes:
        """Render TTS to a WAV file via pyttsx3, return as bytes."""
        import pyttsx3, tempfile, os
        engine = pyttsx3.init()
        engine.setProperty("rate", 150)
        engine.setProperty("volume", 0.9)
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
            tmp = f.name
        try:
            engine.save_to_file(text, tmp)
            engine.runAndWait()
            with open(tmp, "rb") as f:
                return f.read()
        finally:
            os.unlink(tmp)
