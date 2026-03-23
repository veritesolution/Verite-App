"""
Low-latency audio cue delivery for TMR.
Target: <20 ms from trigger to first audio sample reaching the DAC.

Uses PyAudio with a pre-loaded buffer. Audio is loaded at session start
so there is zero disk/network I/O on the critical delivery path.

Volume calibration:
  The cue is delivered at a level ~10–15 dB below a subjective wakefulness
  threshold, estimated from the user's self-reported hearing sensitivity.
  Default: 40 dB SPL equivalent (quiet conversation level).
"""

import io
import time
import wave
import threading
import numpy as np
import structlog

log = structlog.get_logger()

DEFAULT_CUE_VOLUME = 0.35     # 0.0–1.0 relative to full scale
FADE_SAMPLES = 256            # ~1 ms linear fade-in to avoid click


class AudioPlayer:
    """
    Pre-loads a WAV cue into memory at session init.
    play_async() is non-blocking and returns within <1 ms.
    """

    def __init__(
        self,
        wav_path: str = None,
        wav_bytes: bytes = None,
        volume: float = DEFAULT_CUE_VOLUME,
        output_device_index: int = None,
    ):
        if wav_path is None and wav_bytes is None:
            raise ValueError("Provide wav_path or wav_bytes")

        self._volume = volume
        self._device_index = output_device_index
        self._lock = threading.Lock()
        self._playing = False

        raw_bytes = wav_bytes if wav_bytes else open(wav_path, "rb").read()
        self._samples, self._sr, self._n_channels, self._sampwidth = self._load_wav(raw_bytes)
        log.info("audio_player_ready", sr=self._sr, duration_ms=len(self._samples) / self._sr * 1000)

    @staticmethod
    def _load_wav(raw: bytes):
        """Decode WAV bytes into a numpy array of int16 samples."""
        with wave.open(io.BytesIO(raw), "rb") as wf:
            sr = wf.getframerate()
            n_ch = wf.getnchannels()
            sw = wf.getsampwidth()
            frames = wf.readframes(wf.getnframes())
        samples = np.frombuffer(frames, dtype=np.int16).copy().astype(np.float32) / 32768.0
        return samples, sr, n_ch, sw

    def play_async(self) -> bool:
        """
        Trigger cue playback in a daemon thread. Returns immediately.
        Returns True if playback was started, False if already playing.
        """
        with self._lock:
            if self._playing:
                log.debug("audio_skip_already_playing")
                return False
            self._playing = True

        thread = threading.Thread(target=self._play, daemon=True)
        thread.start()
        return True

    def _play(self):
        try:
            import pyaudio
            data = self._samples.copy()

            # Apply volume and fade-in
            data *= self._volume
            fade = np.linspace(0.0, 1.0, min(FADE_SAMPLES, len(data)))
            data[: len(fade)] *= fade

            data_int16 = (data * 32767).astype(np.int16).tobytes()

            pa = pyaudio.PyAudio()
            stream = pa.open(
                format=pyaudio.paInt16,
                channels=self._n_channels,
                rate=self._sr,
                output=True,
                output_device_index=self._device_index,
                frames_per_buffer=256,
            )
            t_start = time.perf_counter()
            stream.write(data_int16)
            latency_ms = (time.perf_counter() - t_start) * 1000
            stream.stop_stream()
            stream.close()
            pa.terminate()
            log.info("audio_cue_delivered", latency_ms=round(latency_ms, 1))
        except Exception as e:
            log.error("audio_playback_error", error=str(e))
        finally:
            with self._lock:
                self._playing = False

    def set_volume(self, volume: float):
        self._volume = float(np.clip(volume, 0.0, 1.0))

    @property
    def is_playing(self) -> bool:
        return self._playing
