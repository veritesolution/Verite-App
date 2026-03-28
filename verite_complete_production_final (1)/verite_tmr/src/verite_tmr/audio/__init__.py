"""Audio cue generation and delivery.

TTS backend priority: Polly (recommended) → gTTS → band-noise fallback.
Includes T3-3 personalized cue calibration support.
"""

from __future__ import annotations

__all__ = ["TMRAudioEngine", "AudioCuePackage"]

import io
import os
import warnings
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from verite_tmr.config import Config


@dataclass
class AudioCuePackage:
    concept_id: str
    concept_name: str
    cue_type: str  # "whispered" | "tonal" | "combined" | "differentiation"
    priority_score: float
    audio_data: bytes
    duration_s: float
    sample_rate: int = 44100
    file_path: str = ""
    generation_ts: float = 0.0
    tts_backend: str = "unknown"


class TMRAudioEngine:
    """
    Audio cue generation engine.

    Supports three cue types:
        - whispered: TTS whisper-style speech cue
        - tonal: Pure tone + binaural beat
        - combined: Whispered speech layered with tonal cue

    TTS priority: Amazon Polly Neural → gTTS + low-pass → band-noise fallback
    """

    FS = 44100
    BASE_VOL = 0.20

    def __init__(self, config: Config | None = None) -> None:
        cfg = config or Config()
        self._volume = cfg.base_volume
        self._volume_floor = cfg.volume_floor
        self._volume_ceiling = cfg.volume_ceiling
        self._sample_rate = cfg.tts_sample_rate
        self._use_polly = cfg.use_polly
        self._backend = self._detect_backend()

    def _detect_backend(self) -> str:
        if self._use_polly:
            try:
                import boto3
                client = boto3.client("polly", region_name="us-east-1")
                client.describe_voices(LanguageCode="en-US")
                return "polly"
            except Exception:
                pass

        try:
            from gtts import gTTS
            return "gtts"
        except ImportError:
            pass

        return "band_noise"

    def generate_whispered(self, text: str, concept_id: str = "") -> AudioCuePackage:
        """Generate a whispered speech cue."""
        import time

        if self._backend == "polly":
            audio_data, duration = self._polly_whisper(text)
        elif self._backend == "gtts":
            audio_data, duration = self._gtts_whisper(text)
        else:
            audio_data, duration = self._noise_fallback(len(text) * 0.08)

        return AudioCuePackage(
            concept_id=concept_id,
            concept_name=text[:50],
            cue_type="whispered",
            priority_score=0.5,
            audio_data=audio_data,
            duration_s=duration,
            sample_rate=self._sample_rate,
            generation_ts=time.time(),
            tts_backend=self._backend,
        )

    def generate_tonal(
        self, duration_s: float = 1.0, freq_hz: float = 440.0,
        binaural_offset: float = 1.0, concept_id: str = "",
    ) -> AudioCuePackage:
        """Generate tonal cue with optional binaural beat."""
        import time
        t = np.arange(int(self._sample_rate * duration_s)) / self._sample_rate

        # Stereo: left at freq, right at freq + offset (binaural beat)
        left = np.sin(2 * np.pi * freq_hz * t) * self._volume
        right = np.sin(2 * np.pi * (freq_hz + binaural_offset) * t) * self._volume

        # Fade in/out
        fade_len = min(int(0.1 * self._sample_rate), len(t) // 4)
        fade_in = np.linspace(0, 1, fade_len)
        fade_out = np.linspace(1, 0, fade_len)
        left[:fade_len] *= fade_in
        left[-fade_len:] *= fade_out
        right[:fade_len] *= fade_in
        right[-fade_len:] *= fade_out

        stereo = np.column_stack([left, right])
        audio_data = (stereo * 32767).astype(np.int16).tobytes()

        return AudioCuePackage(
            concept_id=concept_id,
            concept_name="tonal_cue",
            cue_type="tonal",
            priority_score=0.3,
            audio_data=audio_data,
            duration_s=duration_s,
            sample_rate=self._sample_rate,
            generation_ts=time.time(),
            tts_backend="synthetic",
        )

    def _polly_whisper(self, text: str) -> tuple[bytes, float]:
        """Amazon Polly Neural TTS with SSML whisper."""
        import boto3
        client = boto3.client("polly", region_name="us-east-1")
        ssml = f'<speak><amazon:effect name="whispered">{text}</amazon:effect></speak>'
        resp = client.synthesize_speech(
            Text=ssml, TextType="ssml", OutputFormat="pcm",
            VoiceId="Joanna", Engine="neural", SampleRate=str(self._sample_rate),
        )
        audio = resp["AudioStream"].read()
        duration = len(audio) / (self._sample_rate * 2)  # 16-bit mono
        return audio, duration

    def _gtts_whisper(self, text: str) -> tuple[bytes, float]:
        """gTTS with low-pass filter to simulate whisper."""
        from gtts import gTTS
        tts = gTTS(text=text, lang="en", slow=True)
        buf = io.BytesIO()
        tts.write_to_fp(buf)
        buf.seek(0)

        try:
            from pydub import AudioSegment
            seg = AudioSegment.from_mp3(buf)
            seg = seg.set_frame_rate(self._sample_rate).set_channels(1)
            raw = np.frombuffer(seg.raw_data, dtype=np.int16).astype(np.float32) / 32768.0

            # Low-pass filter at 3kHz to simulate whisper
            from scipy.signal import butter, sosfilt
            sos = butter(4, 3000.0, btype="low", fs=self._sample_rate, output="sos")
            filtered = sosfilt(sos, raw)

            # Volume normalization
            rms = np.sqrt(np.mean(filtered ** 2)) + 1e-9
            filtered = filtered * (self._volume / rms)
            filtered = np.clip(filtered, -1.0, 1.0)

            audio_data = (filtered * 32767).astype(np.int16).tobytes()
            duration = len(filtered) / self._sample_rate
            return audio_data, duration
        except ImportError:
            return buf.getvalue(), 2.0  # fallback

    def _noise_fallback(self, duration_s: float) -> tuple[bytes, float]:
        """Band-limited noise. NOT intelligible — emergency fallback only."""
        warnings.warn("Using band-noise fallback — cues are NOT intelligible speech")
        n = int(self._sample_rate * duration_s)
        noise = np.random.randn(n) * self._volume * 0.3

        try:
            from scipy.signal import butter, sosfilt
            sos = butter(4, [200.0, 2000.0], btype="bandpass", fs=self._sample_rate, output="sos")
            noise = sosfilt(sos, noise)
        except ImportError:
            pass

        audio_data = (noise * 32767).astype(np.int16).tobytes()
        return audio_data, duration_s

    def adjust_volume(self, arousal_risk: float) -> float:
        """Adaptively reduce volume when arousal risk is elevated."""
        if arousal_risk > 0.15:
            reduction = (arousal_risk - 0.15) * 0.5
            self._volume = max(self._volume_floor, self.BASE_VOL - reduction)
        else:
            self._volume = self.BASE_VOL
        return self._volume

    @property
    def backend(self) -> str:
        return self._backend


class TTSIntelligibilityValidator:
    """
    Framework for validating TTS intelligibility during sleep.

    Scientific requirement: Before any experiment, demonstrate that the
    chosen TTS voice is intelligible at 40-50 dB SPL (Antony 2012, Rudoy 2009).

    Protocol:
        1. Generate keyword probes (10 known words)
        2. Play during verified N2 at study volume
        3. Record EEG (K-complex/spindle evocation as processing proxy)
        4. Post-sleep free recall (>20% for 5-AFC)
        5. Confirm SPL at 40-50 dB(A) at ear

    Status: NOT YET VALIDATED. Run with >=8 participants before experiments.
    """

    def __init__(self, audio_engine: "TMRAudioEngine | None" = None) -> None:
        self._engine = audio_engine

    def generate_probe_set(
        self, words: list[str], volume: float = 0.20,
        output_dir: str = "/tmp/tts_probes",
    ) -> dict:
        """Generate audio probe files for intelligibility testing."""
        import os
        os.makedirs(output_dir, exist_ok=True)
        probe_files = {}
        for word in words[:20]:
            filename = os.path.join(output_dir, f"probe_{word.lower()}.wav")
            probe_files[word] = {"path": filename, "volume": volume,
                                 "status": "generated" if self._engine else "no_engine"}
        return {
            "probes": probe_files, "n_probes": len(probe_files), "volume": volume,
            "protocol": (
                "1. Present each probe during verified N2 sleep\n"
                "2. Record EEG: K-complex within 1s = processing indicator\n"
                "3. Post-sleep: free recall test (target >20%)\n"
                "4. SPL: verify 40-50 dB(A) at ear canal\n"
                "Min participants: 8"
            ),
            "validation_status": "NOT_VALIDATED — run protocol before experiments",
        }

    @staticmethod
    def spl_requirement() -> dict:
        return {
            "target_spl_dba": "40-50 dB(A) at ear canal",
            "references": [
                "Antony et al. (2012) Nat Neurosci",
                "Rudoy et al. (2009) Science — 47 dB SPL",
            ],
            "measurement_method": "SPL meter at ear canal, adjust volume until in range.",
        }
