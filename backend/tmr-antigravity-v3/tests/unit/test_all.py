"""
Unit tests for TMR-ANTIGRAVITY v3.0.0 production system.
Run with: pytest tests/ -v --cov=services --cov-report=term-missing

v3.0.0 additions:
  - ArousalRiskEstimator tests
  - AR lock read-side consistency tests
  - _AsyncDBWriter asyncio.Queue tests
  - Arousal risk gate enforcement tests
"""

import time
import asyncio
import threading
import numpy as np
import pytest
from unittest.mock import patch, MagicMock, AsyncMock


# ═══════════════════════════════════════════════════════════════════════════════
# CRISIS GATE TESTS — 100% coverage required, zero false negatives allowed
# ═══════════════════════════════════════════════════════════════════════════════

class TestCrisisGate:

    @pytest.fixture(autouse=True)
    def gate(self):
        from services.safety.crisis_gate import CrisisGate
        self.gate = CrisisGate()

    def test_safe_input_passes(self):
        result = self.gate.check("I want to stop checking my phone so much")
        assert not result.blocked
        assert result.level == "safe"
        assert not result.is_immediate_danger

    def test_immediate_danger_detected(self):
        result = self.gate.check("I want to kill myself")
        assert result.blocked
        assert result.is_immediate_danger
        assert "988" in result.resources.get("US", "")

    def test_medical_emergency_detected(self):
        result = self.gate.check("I already overdosed and I can't breathe")
        assert result.blocked
        assert result.level == "medical_emergency"

    def test_high_risk_detected(self):
        result = self.gate.check("I can't go on anymore, there's no way out")
        assert result.blocked
        assert result.level == "high_risk"

    def test_empty_string_is_safe(self):
        result = self.gate.check("")
        assert not result.blocked

    def test_case_insensitive_matching(self):
        result = self.gate.check("I WANT TO END MY LIFE")
        assert result.blocked
        assert result.is_immediate_danger

    def test_partial_match_still_detected(self):
        result = self.gate.check("My therapist said I've been having suicidal thoughts")
        assert result.blocked

    def test_resources_include_multiple_countries(self):
        result = self.gate.check("I want to die")
        assert "US" in result.resources
        assert "UK" in result.resources
        assert "AU" in result.resources

    def test_safe_habit_context_not_blocked(self):
        result = self.gate.check("My social media habit feels hopeless to break")
        assert not result.blocked, (
            "'hopeless' alone should not block; only multi-word contextual "
            "phrases like 'feel completely hopeless about living' should match."
        )

    def test_specific_hopeless_phrase_still_blocked(self):
        result = self.gate.check("I feel completely hopeless about living")
        assert result.blocked
        assert result.level == "high_risk"

    def test_user_id_passed_to_result(self):
        result = self.gate.check("I want to kill myself", user_id="user-uuid-123")
        assert result.blocked
        assert result.is_immediate_danger

    @pytest.mark.parametrize("phrase", [
        "self harm", "self-harm", "cutting myself", "hurt myself",
        "better off dead", "no reason to live",
    ])
    def test_all_immediate_danger_phrases(self, phrase):
        result = self.gate.check(f"I have been {phrase} for weeks")
        assert result.blocked, f"Phrase '{phrase}' should be blocked"


# ═══════════════════════════════════════════════════════════════════════════════
# SYNTHETIC EEG DRIVER TESTS
# ═══════════════════════════════════════════════════════════════════════════════

class TestSleepStager:

    @pytest.fixture
    def stager(self):
        from services.eeg.sleep_stager import YASAStager
        return YASAStager(sf=256.0)

    def test_returns_unknown_before_buffer_full(self, stager):
        for _ in range(100):
            stager.push_sample(10.0)
        assert stager.get_stage() == "unknown"

    def test_ambiguous_signal_returns_unknown_not_n2(self, stager):
        SR = 256
        n_samples = int(30 * SR * 5)
        flat = np.ones(n_samples) * 2.0 + np.random.randn(n_samples) * 0.5
        for v in flat:
            stager.push_sample(float(v))
        stage = stager._score()
        assert stage != "N2", (
            f"Ambiguous signal returned '{stage}'; expected 'unknown'. "
            "Bug 3: ambiguous cases must not default to a TMR-permissive stage."
        )

    @pytest.fixture
    def driver(self):
        from services.eeg.synthetic_driver import SyntheticDriver
        d = SyntheticDriver(realtime=False)
        d.connect()
        return d

    def test_connect_returns_true(self):
        from services.eeg.synthetic_driver import SyntheticDriver
        d = SyntheticDriver(realtime=False)
        assert d.connect() is True

    def test_read_sample_returns_correct_shape(self, driver):
        sample = driver.read_sample()
        assert sample.channels.shape == (len(driver.channel_names),)

    def test_sample_rate_correct(self, driver):
        assert driver.sample_rate == 256.0

    def test_impedance_check_passes(self, driver):
        report = driver.check_impedance()
        assert report.all_ok is True

    def test_samples_are_in_physiological_range(self, driver):
        samples = [driver.read_sample().channels[0] for _ in range(100)]
        arr = np.array(samples)
        assert np.abs(arr).max() < 500.0

    def test_timestamps_monotonically_increasing(self, driver):
        t1 = driver.read_sample().timestamp
        t2 = driver.read_sample().timestamp
        assert t2 >= t1


# ═══════════════════════════════════════════════════════════════════════════════
# PHASE ESTIMATOR TESTS
# ═══════════════════════════════════════════════════════════════════════════════

class TestPhaseEstimator:

    @pytest.fixture
    def estimator(self):
        from services.eeg.signal_processing import PhaseEstimator
        return PhaseEstimator(sf=256.0)

    def test_returns_none_when_buffer_empty(self, estimator):
        assert estimator.get_phase() is None

    def test_phase_within_range_after_fill(self, estimator):
        SR = 256
        t = np.linspace(0, 7, 7 * SR)
        signal = 75.0 * np.sin(2 * np.pi * 0.75 * t)
        for v in signal:
            estimator.push_sample(float(v))
        phase = estimator.get_phase()
        assert phase is not None
        assert -np.pi <= phase <= np.pi

    def test_phase_convention_positive_peak_near_zero(self, estimator):
        """
        Verify the phase convention: at the positive peak of the slow oscillation
        after bandpass filtering, the instantaneous phase should be near 0.
        """
        from scipy.signal import butter, sosfilt
        SR = 256
        buf_len = int(estimator.BUFFER_SECONDS * SR)
        freq = 0.75

        total = 4 * buf_len
        t = np.arange(total) / SR
        raw = 75.0 * np.cos(2 * np.pi * freq * t)

        sos = butter(4, [0.5, 4.0], btype="bandpass", fs=SR, output="sos")
        filtered = sosfilt(sos, raw)

        search = filtered[buf_len: 2 * buf_len]
        peak_offset = int(np.argmax(search))
        peak_idx = buf_len + peak_offset

        assert peak_idx >= buf_len

        buf_slice = raw[peak_idx - buf_len + 1: peak_idx + 1]
        assert len(buf_slice) == buf_len
        for v in buf_slice:
            estimator.push_sample(float(v))

        phase = estimator.get_phase()
        assert phase is not None
        assert abs(phase) < np.pi / 2, (
            f"At the SO positive peak, phase should be in (-pi/2, pi/2), "
            f"got {phase:.3f} rad."
        )

    def test_ar_lock_read_side_consistency(self):
        """
        v3.0.0 fix: verify that _extend_with_ar uses the lock on read.
        We confirm that concurrent refit and extend do not raise exceptions.
        """
        from services.eeg.signal_processing import PhaseEstimator
        est = PhaseEstimator(sf=256.0)
        SR = 256

        # Fill buffer
        t = np.linspace(0, 7, 7 * SR)
        signal = 75.0 * np.sin(2 * np.pi * 0.75 * t)
        for v in signal:
            est.push_sample(float(v))

        errors = []

        def concurrent_refit():
            try:
                from scipy.signal import sosfilt
                data = np.array(est._buf, dtype=np.float64)
                filtered = sosfilt(est._sos, data)
                for _ in range(100):
                    est._refit_ar(filtered)
            except Exception as e:
                errors.append(e)

        def concurrent_get_phase():
            try:
                for _ in range(100):
                    est.get_phase()
            except Exception as e:
                errors.append(e)

        threads = [
            threading.Thread(target=concurrent_refit),
            threading.Thread(target=concurrent_get_phase),
        ]
        for t_obj in threads:
            t_obj.start()
        for t_obj in threads:
            t_obj.join(timeout=5.0)

        assert len(errors) == 0, f"Concurrent AR access raised errors: {errors}"


# ═══════════════════════════════════════════════════════════════════════════════
# AROUSAL RISK ESTIMATOR TESTS (v3.0.0 — previously phantom feature)
# ═══════════════════════════════════════════════════════════════════════════════

class TestArousalRiskEstimator:

    @pytest.fixture
    def estimator(self):
        from services.eeg.signal_processing import ArousalRiskEstimator
        return ArousalRiskEstimator(sf=256.0)

    def test_returns_zero_before_buffer_full(self, estimator):
        assert estimator.get_arousal_risk() == 0.0

    def test_returns_float_in_0_1(self, estimator):
        SR = 256
        n = int(4.0 * SR)
        noise = np.random.randn(n) * 20.0
        for v in noise:
            estimator.push_sample(float(v))
        risk = estimator.get_arousal_risk()
        assert 0.0 <= risk <= 1.0

    def test_calm_nrem_signal_low_risk(self, estimator):
        """Pure delta signal (0.75 Hz) should produce low arousal risk."""
        SR = 256
        # Fill with enough for baseline to establish
        t = np.linspace(0, 30, 30 * SR)
        signal = 75.0 * np.sin(2 * np.pi * 0.75 * t)
        for v in signal:
            estimator.push_sample(float(v))
        risk = estimator.get_arousal_risk()
        # Pure delta → low beta/alpha, no EMG, no delta suppression
        assert risk < 0.5, f"Pure delta signal should have low arousal risk, got {risk:.3f}"

    def test_high_beta_signal_higher_risk(self):
        """Signal with strong beta component should produce higher risk."""
        from services.eeg.signal_processing import ArousalRiskEstimator
        SR = 256

        # Estimator 1: calm delta
        est_calm = ArousalRiskEstimator(sf=SR)
        t = np.linspace(0, 30, 30 * SR)
        calm = 75.0 * np.sin(2 * np.pi * 0.75 * t)
        for v in calm:
            est_calm.push_sample(float(v))
        risk_calm = est_calm.get_arousal_risk()

        # Estimator 2: delta + strong beta + noise (arousal signature)
        est_active = ArousalRiskEstimator(sf=SR)
        active = (
            30.0 * np.sin(2 * np.pi * 0.75 * t)     # weakened delta
            + 40.0 * np.sin(2 * np.pi * 22.0 * t)    # strong beta
            + 15.0 * np.random.randn(len(t))          # broadband noise
        )
        for v in active:
            est_active.push_sample(float(v))
        risk_active = est_active.get_arousal_risk()

        assert risk_active > risk_calm, (
            f"High-beta signal risk ({risk_active:.3f}) should exceed "
            f"calm delta risk ({risk_calm:.3f})"
        )

    def test_arousal_risk_max_constant_exists(self):
        from services.eeg.signal_processing import AROUSAL_RISK_MAX
        assert AROUSAL_RISK_MAX == 0.25


# ═══════════════════════════════════════════════════════════════════════════════
# ARTIFACT REJECTION TESTS
# ═══════════════════════════════════════════════════════════════════════════════

class TestArtifactRejector:

    @pytest.fixture
    def rejector(self):
        from services.eeg.signal_processing import ArtifactRejector
        return ArtifactRejector(amplitude_threshold_uv=150.0, sf=256.0)

    def test_normal_sample_accepted(self, rejector):
        assert rejector.push_and_check(50.0) is True

    def test_over_amplitude_threshold_rejected(self, rejector):
        assert rejector.push_and_check(200.0) is False

    def test_high_ptp_window_rejected(self, rejector):
        for _ in range(256):
            rejector.push_and_check(10.0)
        for _ in range(128):
            rejector.push_and_check(100.0)
        result = rejector.push_and_check(-150.0)
        assert result is False

    def test_artifact_rate_tracking(self, rejector):
        for _ in range(100):
            rejector.push_and_check(50.0)    # clean
        rejector.push_and_check(200.0)       # artifact
        rate = rejector.artifact_rate
        assert 0.0 < rate < 0.1


# ═══════════════════════════════════════════════════════════════════════════════
# SPINDLE DETECTOR TESTS
# ═══════════════════════════════════════════════════════════════════════════════

class TestSpindleDetector:

    @pytest.fixture
    def detector(self):
        from services.eeg.signal_processing import SpindleDetector
        return SpindleDetector(sf=256.0, window_seconds=4.0)

    def test_zero_probability_with_empty_buffer(self, detector):
        assert detector.get_spindle_probability() == 0.0

    def test_returns_float_in_0_1(self, detector):
        SR = 256
        noise = np.random.randn(4 * SR) * 20.0
        for v in noise:
            detector.push_sample(float(v))
        prob = detector.get_spindle_probability()
        assert 0.0 <= prob <= 1.0

    def test_high_sigma_signal_yields_higher_probability(self, detector):
        SR = 256
        noise_warm = np.random.randn(4 * SR) * 20.0
        for v in noise_warm:
            detector.push_sample(float(v))
        prob_noise = detector.get_spindle_probability()

        from services.eeg.signal_processing import SpindleDetector
        detector2 = SpindleDetector(sf=256.0, window_seconds=4.0)
        for v in (np.random.randn(4 * SR) * 2.0):
            detector2.push_sample(float(v))
        t = np.linspace(0, 4.0, 4 * SR)
        spindle_signal = 50.0 * np.sin(2 * np.pi * 13.0 * t)
        for v in spindle_signal:
            detector2.push_sample(float(v))
        prob_spindle = detector2.get_spindle_probability()

        assert prob_spindle >= prob_noise


# ═══════════════════════════════════════════════════════════════════════════════
# ASYNC DB WRITER TESTS (v3.0.0 — asyncio.Queue fix)
# ═══════════════════════════════════════════════════════════════════════════════

class TestAsyncDBWriter:

    def test_writer_starts_and_stops_cleanly(self):
        """Verify the writer thread starts, accepts events, and stops on sentinel."""
        from services.workers.tmr_worker import _AsyncDBWriter, _TMREvent

        writer = _AsyncDBWriter()
        writer.start()
        writer.wait_ready()

        assert writer.is_alive()
        assert writer._queue is not None

        # Enqueue a test event (will fail to write since no DB, but shouldn't crash)
        writer.enqueue(_TMREvent(
            session_id="00000000-0000-0000-0000-000000000000",
            event_type="test",
            timestamp_unix=time.time(),
        ))

        # Stop cleanly
        writer.stop()
        assert not writer.is_alive()

    def test_writer_handles_queue_full_gracefully(self):
        """If the queue is full, events are dropped with a warning, not an exception."""
        from services.workers.tmr_worker import _AsyncDBWriter, _TMREvent

        writer = _AsyncDBWriter()
        writer.start()
        writer.wait_ready()

        # Fill the queue (maxsize=500) — this should not raise
        for i in range(510):
            writer.enqueue(_TMREvent(
                session_id="00000000-0000-0000-0000-000000000000",
                event_type=f"flood_{i}",
                timestamp_unix=time.time(),
            ))

        writer.stop()


# ═══════════════════════════════════════════════════════════════════════════════
# PLAN PARSING TESTS
# ═══════════════════════════════════════════════════════════════════════════════

class TestPlanParsing:

    def test_valid_json_parsed(self):
        from services.workers.plan_generator import _parse_and_validate
        plan = {
            "habit_target": "scrolling",
            "mechanism": "habit loop disruption",
            "tmr_cue_phrase": "calm and free",
            "safety_note": "Not a substitute for clinical care.",
            "weeks": [
                {
                    "week": i,
                    "theme": f"Week {i}",
                    "days": [
                        {
                            "day": d,
                            "technique": "urge surfing",
                            "intervention": "DBT",
                            "micro_action": "Do it",
                            "reflection": "Reflect",
                        }
                        for d in range(1, 8)
                    ],
                }
                for i in range(1, 4)
            ],
        }
        import json
        result = _parse_and_validate(json.dumps(plan))
        assert result is not None
        assert result["tmr_cue_phrase"] == "calm and free"

    def test_missing_weeks_returns_none(self):
        from services.workers.plan_generator import _parse_and_validate
        import json
        result = _parse_and_validate(json.dumps({"habit_target": "x"}))
        assert result is None

    def test_wrong_week_count_returns_none(self):
        from services.workers.plan_generator import _parse_and_validate
        import json
        plan = {
            "habit_target": "x", "mechanism": "y",
            "tmr_cue_phrase": "z", "safety_note": "s",
            "weeks": [{"week": 1, "theme": "t", "days": []}],
        }
        result = _parse_and_validate(json.dumps(plan))
        assert result is None

    def test_strips_markdown_fences(self):
        from services.workers.plan_generator import _parse_and_validate
        import json
        plan_dict = {
            "habit_target": "x", "mechanism": "y",
            "tmr_cue_phrase": "z", "safety_note": "s",
            "weeks": [
                {"week": i, "theme": "t", "days": [
                    {"day": d, "technique": "t", "intervention": "i",
                     "micro_action": "m", "reflection": "r"}
                    for d in range(1, 8)
                ]}
                for i in range(1, 4)
            ],
        }
        fenced = f"```json\n{json.dumps(plan_dict)}\n```"
        result = _parse_and_validate(fenced)
        assert result is not None


# ═══════════════════════════════════════════════════════════════════════════════
# LLM ROUTER TESTS (mocked)
# ═══════════════════════════════════════════════════════════════════════════════

class TestModelRouter:

    @patch("services.ai.router.settings")
    def test_provider_list_respects_api_keys(self, mock_settings):
        from services.ai.router import ModelRouter
        mock_settings.GROQ_API_KEY = "test"
        mock_settings.GEMINI_API_KEY = None
        mock_settings.OPENAI_API_KEY = None
        router = ModelRouter()
        assert router.get_providers() == ["groq"]

    @patch("services.ai.router.settings")
    def test_all_providers_available(self, mock_settings):
        from services.ai.router import ModelRouter
        mock_settings.GROQ_API_KEY = "key1"
        mock_settings.GEMINI_API_KEY = "key2"
        mock_settings.OPENAI_API_KEY = "key3"
        router = ModelRouter()
        providers = router.get_providers()
        assert "groq" in providers
        assert "gemini" in providers
        assert "openai" in providers


# ═══════════════════════════════════════════════════════════════════════════════
# KNOWLEDGE BASE TESTS
# ═══════════════════════════════════════════════════════════════════════════════

class TestKnowledgeBase:

    @pytest.fixture
    def kb(self):
        from services.ai.knowledge_base import KnowledgeBase
        return KnowledgeBase()

    def test_get_techniques_returns_list(self, kb):
        techs = kb.get_techniques("phone scrolling addiction", top_k=4)
        assert isinstance(techs, list)
        assert len(techs) <= 4
        assert all("name" in t and "framework" in t for t in techs)

    def test_fallback_template_valid_structure(self, kb):
        profile = {
            "age": 30,
            "profession": "engineer",
            "ailment": "excessive social media scrolling",
            "duration_years": "2 years",
            "frequency": "50x per day",
            "intensity": 7,
            "origin_story": "Started during COVID",
            "primary_emotion": "anxiety",
        }
        plan = kb.get_fallback_template(profile)
        assert "weeks" in plan
        assert len(plan["weeks"]) == 3
        assert "tmr_cue_phrase" in plan
        assert "safety_note" in plan

    def test_scrolling_addiction_matches_relevant_techniques(self, kb):
        techs = kb.get_techniques("compulsive phone scrolling", top_k=6)
        names = [t["name"] for t in techs]
        relevant = {"Habit loop interruption", "Stimulus control", "Urge surfing",
                    "Implementation intentions"}
        assert len(relevant.intersection(names)) >= 2


# ═══════════════════════════════════════════════════════════════════════════════
# AUDIO PLAYER TESTS
# ═══════════════════════════════════════════════════════════════════════════════

class TestAudioPlayer:

    @pytest.fixture
    def wav_bytes(self):
        import io, wave
        buf = io.BytesIO()
        samples = (np.sin(2 * np.pi * 440 * np.linspace(0, 1, 22050)) * 16000).astype(np.int16)
        with wave.open(buf, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(22050)
            wf.writeframes(samples.tobytes())
        return buf.getvalue()

    def test_loads_wav_bytes(self, wav_bytes):
        from services.eeg.audio_delivery import AudioPlayer
        player = AudioPlayer(wav_bytes=wav_bytes)
        assert player._sr == 22050
        assert len(player._samples) == 22050

    def test_volume_setter(self, wav_bytes):
        from services.eeg.audio_delivery import AudioPlayer
        player = AudioPlayer(wav_bytes=wav_bytes)
        player.set_volume(0.5)
        assert player._volume == 0.5

    def test_volume_clamped(self, wav_bytes):
        from services.eeg.audio_delivery import AudioPlayer
        player = AudioPlayer(wav_bytes=wav_bytes)
        player.set_volume(2.5)
        assert player._volume == 1.0
        player.set_volume(-1.0)
        assert player._volume == 0.0

    @patch("pyaudio.PyAudio")
    def test_play_async_nonblocking(self, mock_pyaudio, wav_bytes):
        from services.eeg.audio_delivery import AudioPlayer
        mock_stream = MagicMock()
        mock_pyaudio.return_value.open.return_value = mock_stream

        player = AudioPlayer(wav_bytes=wav_bytes)
        t_start = time.time()
        player.play_async()
        elapsed = time.time() - t_start
        assert elapsed < 0.05, f"play_async blocked for {elapsed:.3f}s"


# ═══════════════════════════════════════════════════════════════════════════════
# PLAN SAFETY GUARD TESTS
# ═══════════════════════════════════════════════════════════════════════════════

class TestPlanSafetyGuard:

    @pytest.fixture
    def guard(self):
        from services.ai.safety_guard import PlanSafetyGuard
        return PlanSafetyGuard()

    def _valid_plan(self):
        return {
            "habit_target": "scrolling",
            "mechanism": "habit loop disruption",
            "tmr_cue_phrase": "calm and free",
            "safety_note": "This is not a substitute for professional clinical care.",
            "weeks": [
                {"week": i, "theme": f"Week {i}", "days": [
                    {"day": d, "technique": "t", "intervention": "i",
                     "micro_action": "m", "reflection": "r"}
                    for d in range(1, 8)
                ]}
                for i in range(1, 4)
            ],
        }

    def test_valid_plan_passes(self, guard):
        ok, reason = guard.validate(self._valid_plan())
        assert ok is True
        assert reason is None

    def test_blocked_content_fails(self, guard):
        plan = self._valid_plan()
        plan["weeks"][0]["days"][0]["micro_action"] = "Take a sedative before bed"
        ok, reason = guard.validate(plan)
        assert ok is False
        assert "sedative" in reason.lower()

    def test_missing_safety_note_fails(self, guard):
        plan = self._valid_plan()
        plan["safety_note"] = ""
        ok, reason = guard.validate(plan)
        assert ok is False

    def test_wrong_week_count_fails(self, guard):
        plan = self._valid_plan()
        plan["weeks"] = plan["weeks"][:2]
        ok, reason = guard.validate(plan)
        assert ok is False


# ═══════════════════════════════════════════════════════════════════════════════
# ENCRYPTION TESTS
# ═══════════════════════════════════════════════════════════════════════════════

class TestEncryption:

    def test_encrypt_decrypt_roundtrip(self):
        from services.api.encryption import encrypt_field, decrypt_field
        original = "This is sensitive patient data"
        encrypted = encrypt_field(original)
        assert encrypted != original.encode()
        decrypted = decrypt_field(encrypted)
        assert decrypted == original

    def test_empty_string_encrypt(self):
        from services.api.encryption import encrypt_field, decrypt_field
        encrypted = encrypt_field("")
        assert encrypted == b""
        assert decrypt_field(b"") is None

    def test_decrypt_none_returns_none(self):
        from services.api.encryption import decrypt_field
        assert decrypt_field(None) is None
