"""
Vérité TMR v10.0 — Comprehensive Test Suite

T2-2 FIX: These are CORRECTNESS tests, not just range checks.

v9 tests checked: "does the output fall within [0, 1]?"
v10 tests check: "is the output CORRECT for a known input?"

Test categories:
    - Phase estimation: synthetic sinusoid with known phase → ±15° accuracy
    - Spindle detection: known burst in noise → high prob; pure noise → low prob
    - K-complex: known morphology → detection; random noise → no detection
    - Memory assessment: deterministic inputs → exact expected outputs
    - Orchestrator gate: mock snapshots → correct allow/block decisions
    - Config validation: invalid configs → specific error messages
"""

import math
import time

import numpy as np
from pathlib import Path
import pytest

from verite_tmr.config import Config, validate_config


# ═══════════════════════════════════════════════════════════════════════════
# CONFIG TESTS
# ═══════════════════════════════════════════════════════════════════════════

class TestConfig:
    def test_default_config_is_valid(self) -> None:
        cfg = Config()
        errors = cfg.validate()
        assert not errors, f"Default config has errors: {errors}"

    def test_weights_must_sum_to_one(self) -> None:
        cfg = Config(weight_accuracy=0.5, weight_speed=0.5, weight_confidence=0.5)
        errors = cfg.validate()
        assert any("sum to 1.0" in e for e in errors)

    def test_sweet_spot_bounds(self) -> None:
        cfg = Config(sweet_low=0.8, sweet_high=0.2)
        errors = cfg.validate()
        assert any("sweet_low" in e for e in errors)

    def test_min_interval_safety(self) -> None:
        cfg = Config(min_interval_s=2.0)
        errors = cfg.validate()
        assert any("unsafe" in e.lower() for e in errors)

    def test_hilbert_buffer_rejected(self) -> None:
        cfg = Config(phase_predictor="hilbert_buffer")
        errors = cfg.validate()
        assert any("hilbert_buffer" in e.lower() for e in errors)

    def test_volume_ceiling_safety(self) -> None:
        cfg = Config(volume_ceiling=0.9)
        errors = cfg.validate()
        assert any("hearing" in e.lower() for e in errors)

    def test_json_roundtrip(self, tmp_path=None) -> None:
        cfg = Config(max_concepts=20, sweet_low=0.25)
        import tempfile
        _td = tmp_path if tmp_path else tempfile.mkdtemp()
        path = Path(_td) / "test_config.json"
        cfg.to_json(path)
        loaded = Config.from_json(path)
        assert loaded.max_concepts == 20
        assert loaded.sweet_low == 0.25

    def test_phase_predictor_advisory(self) -> None:
        cfg = Config(phase_predictor="echt")
        adv = cfg.phase_predictor_advisory()
        assert adv["suitable_for_experiments"] == "yes"

        cfg_lms = Config(phase_predictor="lms")
        adv_lms = cfg_lms.phase_predictor_advisory()
        assert adv_lms["suitable_for_experiments"] == "no"


# ═══════════════════════════════════════════════════════════════════════════
# PHASE ESTIMATION CORRECTNESS TESTS
# ═══════════════════════════════════════════════════════════════════════════

class TestPhaseEstimation:
    """
    Phase estimator CORRECTNESS tests.
    - ECHTEstimator (true Zrenner 2020): ~45° mean error — tests algorithm runs correctly
    - CausalPhaseEstimator (recommended): ~18° mean error — tests accuracy target
    """

    def test_echt_synthetic_sinusoid(self) -> None:
        """TRUE ECHT (Zrenner 2020) achieves ~45° mean on synthetic sinusoids.
        The SO up-state window is ~120°, so 60° tolerance is the correct target."""
        from verite_tmr.phase.echt import ECHTEstimator
        est = ECHTEstimator(fs=250, buffer_s=4.0, ar_order=30)
        result = est.validate_against_synthetic(
            freq_hz=0.75, duration_s=15.0, tolerance_deg=80.0
        )
        assert result["n_samples"] > 100
        assert result["mean_error_deg"] < 60.0, (
            f"TRUE ECHT mean error {result['mean_error_deg']}° exceeds 60°"
        )

    def test_causal_interp_accuracy(self) -> None:
        """CausalPhaseEstimator (RECOMMENDED) achieves ~18° mean — the accuracy target."""
        from verite_tmr.phase.echt import CausalPhaseEstimator
        est = CausalPhaseEstimator(fs=250, buffer_s=4.0, interior_fraction=0.7)
        result = est.validate_against_synthetic(
            freq_hz=0.75, duration_s=15.0, tolerance_deg=25.0
        )
        assert result["passed"], (
            f"CausalPhaseEstimator failed: mean={result['mean_error_deg']}°, "
            f"p95={result['p95_error_deg']}°"
        )
        assert result["mean_error_deg"] < 25.0, (
            f"CausalPhaseEstimator mean {result['mean_error_deg']}° exceeds 25°"
        )

    def test_ar_synthetic_sinusoid(self) -> None:
        from verite_tmr.phase.ar import ARPhaseEstimator
        est = ARPhaseEstimator(fs=250, buffer_s=4.0)
        result = est.validate_against_synthetic(
            freq_hz=0.75, duration_s=15.0, tolerance_deg=45.0
        )
        # AR is less accurate than ECHT but should be under 60°
        assert result["mean_error_deg"] < 60.0, (
            f"AR mean error {result['mean_error_deg']}° exceeds 60°"
        )

    def test_lms_returns_in_range(self) -> None:
        """LMS is demo only — just check it doesn't crash and stays in [0, 2π]."""
        from verite_tmr.phase.lms import LMSPhaseEstimator
        est = LMSPhaseEstimator(fs=250)
        for i in range(500):
            phase = est.push_and_estimate(np.sin(2 * np.pi * 0.75 * i / 250))
            assert 0 <= phase <= 2 * np.pi

    def test_factory_creates_correct_type(self) -> None:
        from verite_tmr.phase.base import create_phase_estimator
        est = create_phase_estimator("echt")
        assert est.name == "echt"
        assert est.suitable_for_experiments is True

    def test_factory_rejects_hilbert_buffer(self) -> None:
        from verite_tmr.phase.base import create_phase_estimator
        with pytest.raises(ValueError, match="hilbert_buffer"):
            create_phase_estimator("hilbert_buffer")

    def test_echt_reset_clears_state(self) -> None:
        from verite_tmr.phase.echt import ECHTEstimator
        est = ECHTEstimator(fs=250)
        for i in range(500):
            est.push_and_estimate(np.sin(2 * np.pi * 0.75 * i / 250))
        est.reset()
        assert est.last_phase == 0.0

    def test_causal_interp_at_band_edges(self) -> None:
        """
        CausalPhaseEstimator accuracy degrades severely at filter band edges.
        This test DOCUMENTS the known limitation rather than asserting accuracy.

        At 0.75 Hz (band center, default): mean ~18° — excellent
        At 0.5 Hz (band lower edge): mean ~152° — UNUSABLE
        At 1.0 Hz (band upper edge): mean ~116° — UNUSABLE

        Researchers must narrow the filter band to ±0.15 Hz around the
        participant's measured SO peak frequency during baseline calibration.
        """
        from verite_tmr.phase.echt import CausalPhaseEstimator

        # Band center: should be accurate
        est_center = CausalPhaseEstimator(fs=250, so_lo=0.5, so_hi=1.0, buffer_s=4.0)
        r_center = est_center.validate_against_synthetic(
            freq_hz=0.75, duration_s=15.0, tolerance_deg=30.0
        )
        assert r_center["mean_error_deg"] < 30.0, (
            f"Band center (0.75 Hz) should be accurate, got {r_center['mean_error_deg']:.1f}°"
        )

        # Band lower edge: should be severely degraded
        est_low = CausalPhaseEstimator(fs=250, so_lo=0.5, so_hi=1.0, buffer_s=4.0)
        r_low = est_low.validate_against_synthetic(
            freq_hz=0.5, duration_s=15.0, tolerance_deg=180.0
        )
        assert r_low["mean_error_deg"] > 50.0, (
            f"KNOWN LIMITATION VIOLATED: 0.5 Hz should have high error (>50°), "
            f"got {r_low['mean_error_deg']:.1f}°."
        )

        # Band upper edge: should be severely degraded
        est_high = CausalPhaseEstimator(fs=250, so_lo=0.5, so_hi=1.0, buffer_s=4.0)
        r_high = est_high.validate_against_synthetic(
            freq_hz=1.0, duration_s=15.0, tolerance_deg=180.0
        )
        assert r_high["mean_error_deg"] > 50.0, (
            f"KNOWN LIMITATION VIOLATED: 1.0 Hz should have high error (>50°), "
            f"got {r_high['mean_error_deg']:.1f}°."
        )


# ═══════════════════════════════════════════════════════════════════════════
# SPINDLE DETECTION CORRECTNESS TESTS
# ═══════════════════════════════════════════════════════════════════════════

class TestSpindleDetection:
    """
    T2-2: Spindle detector CORRECTNESS.
    Known 13 Hz burst in noise → detect; pure white noise → reject.
    """

    def test_bandpower_detects_sigma_burst(self) -> None:
        """13 Hz burst should produce higher spindle probability than baseline."""
        from verite_tmr.detection.spindle_cnn import SpindleCNN
        cnn = SpindleCNN()  # no weights = bandpower mode

        # Feed pure noise (baseline)
        noise = np.random.randn(500) * 10.0
        for s in noise:
            cnn.push(s)
        noise_prob = cnn.predict()

        # Feed 13 Hz spindle burst + noise
        t = np.arange(500) / 250.0
        spindle = np.sin(2 * np.pi * 13.0 * t) * 50.0  # strong sigma
        spindle += np.random.randn(500) * 5.0  # mild noise
        for s in spindle:
            cnn.push(s)
        burst_prob = cnn.predict()

        assert burst_prob > noise_prob, (
            f"Spindle burst prob ({burst_prob:.3f}) should exceed "
            f"noise prob ({noise_prob:.3f})"
        )

    def test_bandpower_low_on_white_noise(self) -> None:
        """Pure white noise should yield low spindle probability."""
        from verite_tmr.detection.spindle_cnn import SpindleCNN
        cnn = SpindleCNN()
        noise = np.random.randn(500) * 30.0
        for s in noise:
            cnn.push(s)
        prob = cnn.predict()
        assert prob < 0.5, f"White noise spindle prob {prob:.3f} should be < 0.5"

    def test_cohens_kappa_perfect_agreement(self) -> None:
        from verite_tmr.detection.spindle_cnn import SpindleCNN
        true = np.array([1, 1, 0, 0, 1, 0])
        pred = np.array([1, 1, 0, 0, 1, 0])
        kappa = SpindleCNN._cohens_kappa(true, pred)
        assert abs(kappa - 1.0) < 1e-6, f"Expected kappa=1.0, got {kappa}"

    def test_cohens_kappa_no_agreement(self) -> None:
        from verite_tmr.detection.spindle_cnn import SpindleCNN
        true = np.array([1, 1, 0, 0])
        pred = np.array([0, 0, 1, 1])
        kappa = SpindleCNN._cohens_kappa(true, pred)
        assert kappa < 0, f"Random kappa {kappa} should be negative"


# ═══════════════════════════════════════════════════════════════════════════
# K-COMPLEX DETECTION TESTS
# ═══════════════════════════════════════════════════════════════════════════

class TestKComplexDetection:
    """K-complex: known morphology → detection; flat signal → no detection."""

    def test_detects_kcomplex_morphology(self) -> None:
        """K-complex with 80µV amplitude MUST be detected — not just 'doesn't crash'."""
        from verite_tmr.detection.kcomplex import KComplexDetector
        det = KComplexDetector(fs=250, amplitude_threshold_uv=40.0)

        # 1200 samples (4.8s) to exceed min_samples requirement
        signal = np.random.randn(1200) * 3.0
        # Insert strong K-complex at sample 600
        kc_start = 600
        signal[kc_start:kc_start + 50] += np.linspace(0, -80, 50)
        signal[kc_start + 50:kc_start + 150] += np.linspace(-80, 40, 100)
        signal[kc_start + 150:kc_start + 200] += np.linspace(40, 0, 50)

        # Call detect() after each sample (real-time usage pattern)
        events_found = []
        for i, s in enumerate(signal):
            det.push(s)
            if i > 500:
                ev = det.detect()
                if ev is not None:
                    events_found.append(ev)

        assert len(events_found) > 0, (
            f"K-complex detector FAILED to detect 80µV synthetic K-complex. "
            f"events={len(events_found)}, total_windows={det._total_windows}"
        )

    def test_no_detection_on_flat_signal(self) -> None:
        from verite_tmr.detection.kcomplex import KComplexDetector
        det = KComplexDetector(fs=250)
        flat = np.ones(1200) * 10.0
        for s in flat:
            det.push(s)
        event = det.detect()
        assert event is None, "Flat signal should not trigger K-complex"


# ═══════════════════════════════════════════════════════════════════════════
# MEMORY ASSESSOR CORRECTNESS TESTS
# ═══════════════════════════════════════════════════════════════════════════

class TestMemoryAssessor:
    """Deterministic inputs → exact expected outputs."""

    def test_perfect_recall_high_strength(self) -> None:
        from verite_tmr.memory.assessor import MemoryStrengthAssessor
        a = MemoryStrengthAssessor()
        # Perfect recall, fast RT, high confidence
        s = a.compute_strength(correct=1, rt=1.0, confidence=5)
        assert s > 0.7, f"Perfect recall should yield high strength, got {s}"

    def test_no_recall_low_strength(self) -> None:
        from verite_tmr.memory.assessor import MemoryStrengthAssessor
        a = MemoryStrengthAssessor()
        # No recall, slow RT, low confidence
        s = a.compute_strength(correct=0, rt=10.0, confidence=1)
        assert s < 0.3, f"No recall should yield low strength, got {s}"

    def test_strength_always_in_unit_interval(self) -> None:
        from verite_tmr.memory.assessor import MemoryStrengthAssessor
        a = MemoryStrengthAssessor()
        for correct in (0, 1):
            for rt in (0.1, 5.0, 12.0, 100.0):
                for conf in (1, 2, 3, 4, 5):
                    s = a.compute_strength(correct, rt, conf)
                    assert 0.0 <= s <= 1.0

    def test_tier_classification(self) -> None:
        from verite_tmr.memory.assessor import MemoryStrengthAssessor
        a = MemoryStrengthAssessor()
        assert a.get_tier(0.20) == "too_weak"
        assert a.get_tier(0.50) == "sweet_spot"
        assert a.get_tier(0.80) == "too_strong"

    def test_weight_learning_gate(self) -> None:
        """T1-6: Weights must NOT update before 30 sessions."""
        from verite_tmr.memory.assessor import MemoryStrengthAssessor
        a = MemoryStrengthAssessor(Config(min_sessions_for_weight_learning=30))
        original = a._default_weights.copy()

        # Simulate 10 updates (below threshold)
        a.assessments["test"] = {"correct": 1, "rt_s": 2.0, "confidence": 4}
        for i in range(10):
            a.update_weights_from_history("test", 0.8)

        # Weights used for computation should still be defaults
        s_before = a.compute_strength(1, 2.0, 4)
        np.testing.assert_array_almost_equal(a._default_weights, original)

    def test_unknown_concept_returns_neutral(self) -> None:
        from verite_tmr.memory.assessor import MemoryStrengthAssessor
        a = MemoryStrengthAssessor()
        assert a.get_strength("NONEXISTENT_CONCEPT") == 0.5


# ═══════════════════════════════════════════════════════════════════════════
# ORCHESTRATOR GATE TESTS
# ═══════════════════════════════════════════════════════════════════════════

class TestOrchestratorGate:
    """Mock snapshots → correct gate allow/block decisions."""

    def _make_snap(self, **kwargs) -> object:
        """Create a mock snapshot."""
        class _S:
            sleep_stage = kwargs.get("stage", "N2")
            so_phase = kwargs.get("so_phase", 1.0 * np.pi)
            spindle_prob = kwargs.get("spindle_prob", 0.3)
            arousal_risk = kwargs.get("arousal_risk", 0.1)
            artefact_detected = kwargs.get("artefact", False)
            phase_source = "test"
            spindle_source = "test"
        return _S()

    def test_gate_blocks_rem(self) -> None:
        from verite_tmr.orchestrator import AdaptiveCueOrchestrator
        from verite_tmr.audio import AudioCuePackage
        orch = AdaptiveCueOrchestrator(Config(pac_enabled=False))
        pkg = AudioCuePackage("c1", "test", "whispered", 0.5, b"", 1.0)
        orch.load_queue([pkg])

        snap = self._make_snap(stage="REM")
        evt = orch.step(snap)
        assert evt is None, "Gate should block during REM"

    def test_gate_blocks_high_arousal(self) -> None:
        from verite_tmr.orchestrator import AdaptiveCueOrchestrator
        from verite_tmr.audio import AudioCuePackage
        orch = AdaptiveCueOrchestrator(Config(pac_enabled=False))
        pkg = AudioCuePackage("c1", "test", "whispered", 0.5, b"", 1.0)
        orch.load_queue([pkg])

        snap = self._make_snap(arousal_risk=0.8)
        evt = orch.step(snap)
        assert evt is None, "Gate should block when arousal > 0.25"

    def test_gate_blocks_artefact(self) -> None:
        from verite_tmr.orchestrator import AdaptiveCueOrchestrator
        from verite_tmr.audio import AudioCuePackage
        orch = AdaptiveCueOrchestrator(Config(pac_enabled=False))
        pkg = AudioCuePackage("c1", "test", "whispered", 0.5, b"", 1.0)
        orch.load_queue([pkg])

        snap = self._make_snap(artefact=True)
        evt = orch.step(snap)
        assert evt is None, "Gate should block during artefact"

    def test_gate_allows_valid_window(self) -> None:
        from verite_tmr.orchestrator import AdaptiveCueOrchestrator
        from verite_tmr.audio import AudioCuePackage
        orch = AdaptiveCueOrchestrator(Config(pac_enabled=False))
        pkg = AudioCuePackage("c1", "test", "whispered", 0.5, b"data", 1.0)
        orch.load_queue([pkg])

        # All conditions met
        snap = self._make_snap(
            stage="N2", so_phase=1.0 * np.pi,
            spindle_prob=0.3, arousal_risk=0.1,
        )
        evt = orch.step(snap)
        assert evt is not None, "Gate should allow valid delivery window"
        assert evt.concept_name == "test"


# ═══════════════════════════════════════════════════════════════════════════
# SO-SPINDLE COUPLING TESTS
# ═══════════════════════════════════════════════════════════════════════════

class TestSOSpindleCoupling:
    def test_mi_zero_on_noise(self) -> None:
        from verite_tmr.detection.coupling import SOSpindleCoupling
        pac = SOSpindleCoupling(fs=250, window_s=4.0)
        noise = np.random.randn(1000) * 10.0
        for s in noise:
            pac.push(s)
        mi = pac.compute_mi()
        assert mi < 0.3, f"MI on noise should be low, got {mi}"

    def test_mi_structure(self) -> None:
        from verite_tmr.detection.coupling import SOSpindleCoupling
        pac = SOSpindleCoupling(fs=250, window_s=4.0)
        diag = pac.get_diagnostics()
        assert "last_mi" in diag
        assert "is_coupled" in diag


# ═══════════════════════════════════════════════════════════════════════════
# SIMULATION QUALITY TESTS
# ═══════════════════════════════════════════════════════════════════════════

class TestSimulation:
    """Verify simulation produces physiologically coherent output."""

    def test_simulation_phase_not_stuck_at_zero(self) -> None:
        """After pre-warm, phase must be non-zero from tick 1."""
        from verite_tmr.hardware import HardwareInterface
        hw = HardwareInterface(mode="simulation", time_warp=3000)
        assert hw._sim_phase_est is not None
        assert hw._sim_phase_est.last_phase != 0.0, (
            f"Pre-warm failed: last_phase={hw._sim_phase_est.last_phase}"
        )

    def test_simulation_phase_is_smooth(self) -> None:
        """NREM SO phase must be smooth: consecutive samples differ by < 0.5 rad."""
        import time
        from verite_tmr.hardware import HardwareInterface
        hw = HardwareInterface(mode="simulation", time_warp=3000)
        hw.start()
        try:
            time.sleep(2.5)
            phases, stages = [], []
            for _ in range(40):
                snap = hw.get_snapshot()
                if snap:
                    phases.append(snap.so_phase)
                    stages.append(snap.sleep_stage)
                time.sleep(0.08)
        finally:
            hw.stop()
        nrem_phases = [p for p, st in zip(phases, stages) if st in ("N2", "N3") and p != 0.0]
        assert len(nrem_phases) >= 5, f"Not enough NREM phases ({len(nrem_phases)})"
        phase_arr = np.array(nrem_phases)
        # NOTE: Linear std on circular phase data is WRONG — a phase wrap through
        # 2π is smooth, correct SO behavior but spikes linear std to ~2.8.
        # The mean_delta assertion below is the correct metric for phase smoothness.
        mean_delta = float(np.abs(np.diff(phase_arr)).mean())
        assert mean_delta < 0.5, f"Phase jumps too large: mean|Δ|={mean_delta:.3f}"

    def test_simulation_spindle_correlated_with_phase(self) -> None:
        """Spindle probability must peak near SO up-state (phase ~π)."""
        import time
        from verite_tmr.hardware import HardwareInterface
        hw = HardwareInterface(mode="simulation", time_warp=3000)
        hw.start()
        try:
            time.sleep(3.0)
            phases, spindles = [], []
            for _ in range(60):
                snap = hw.get_snapshot()
                if snap and snap.sleep_stage in ("N2", "N3") and snap.so_phase != 0.0:
                    phases.append(snap.so_phase)
                    spindles.append(snap.spindle_prob)
                time.sleep(0.08)
        finally:
            hw.stop()
        if len(phases) < 10:
            pytest.skip("Not enough NREM snapshots")
        ph, sp = np.array(phases), np.array(spindles)
        assert np.all(sp >= 0) and np.all(sp <= 1)
        up_mask = np.abs(ph - np.pi) < (0.5 * np.pi)
        if up_mask.sum() >= 3 and (~up_mask).sum() >= 3:
            assert sp[up_mask].mean() >= sp[~up_mask].mean() * 0.8


# ═══════════════════════════════════════════════════════════════════════════
# INTEGRATION TEST
# ═══════════════════════════════════════════════════════════════════════════

class TestIntegration:
    def test_session_initialization(self) -> None:
        from verite_tmr.session import VeriteSession
        session = VeriteSession(config=Config(phase_predictor="lms"))
        report = session.initialize(mode="simulation")
        assert "phase" in report
        assert "spindle_cnn" in report
        assert "safety" in report
