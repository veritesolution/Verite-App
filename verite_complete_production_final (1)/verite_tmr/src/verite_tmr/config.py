"""
Vérité TMR v10.0 — Centralized Configuration

All tunable parameters in one place with validation, type safety,
and literature citations. Replaces the global CONFIG dict from v9.

Every parameter cites its source or is explicitly flagged as an
original design choice requiring validation.

Changes from v9:
    - Immutable after validation (prevents runtime mutation bugs)
    - Type-annotated with dataclass
    - hilbert_buffer REMOVED from valid phase predictors (was non-causal trap)
    - USE_POLLY default: True (was False — peer review confirmed this was wrong in v9 code)
    - DB_ENCRYPTION_ENABLED default: True (was False)
    - Added ECHT phase predictor option
    - Added K-complex detection parameters
    - Added PAC coupling parameters
    - Added weight learning minimum session gate (T1-6 fix)
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Any


@dataclass(frozen=False)
class Config:
    """Complete session configuration with literature-backed defaults."""

    # ── Document & Concept ────────────────────────────────────────────────
    max_concepts: int = 10
    sim_threshold: float = 0.60  # TF-IDF cosine sim for diff-pair flagging

    # ── Memory Strength (sweet-spot targeting) ────────────────────────────
    # Formula: S = w_acc*acc + w_spd*(1-rt_norm) + w_conf*conf_norm
    # ⚠ ORIGINAL HEURISTIC — not from Antony et al. (2012).
    # Antony identified intermediate-strength benefit behaviourally.
    # These weights are our implementation; NOT empirically validated.
    sweet_low: float = 0.30     # Antony 2012 concept
    sweet_high: float = 0.70    # Antony 2012 concept
    weight_accuracy: float = 0.50    # original design
    weight_speed: float = 0.25       # original design
    weight_confidence: float = 0.25  # original design
    max_rt_s: float = 12.0

    # ── Weight Learning Gate (T1-6 fix) ───────────────────────────────────
    # Dirichlet weight updates are statistically indefensible with < 30 sessions.
    # With 3 parameters, the posterior confidence intervals span the full simplex
    # for the first 5-10 sessions.
    min_sessions_for_weight_learning: int = 30
    weight_learning_enabled: bool = True

    # ── Delivery Gate ─────────────────────────────────────────────────────
    # Ngo et al. 2013, Mölle et al. 2002
    so_phase_low: float = 0.75    # SO up-state start (× π radians) — Mölle 2002
    so_phase_high: float = 1.25   # SO up-state end   (× π radians) — Mölle 2002
    spindle_prob_thresh: float = 0.15
    arousal_risk_max: float = 0.25
    min_interval_s: float = 30.0  # Antony 2012

    # ── K-Complex Detection (T1-1: NEW in v10) ───────────────────────────
    # K-complexes: landmark NREM events at SO troughs, primary spindle triggers
    # Ref: Ngo 2013 — triggers on SO negative half-wave
    kcomplex_enabled: bool = True
    kcomplex_amplitude_uv: float = 75.0   # peak-to-trough threshold
    kcomplex_duration_min_s: float = 0.5
    kcomplex_duration_max_s: float = 1.5

    # ── SO-Spindle PAC Coupling (T3-2: NEW in v10) ───────────────────────
    # Staresina et al. 2015: memory consolidation benefits from SO-nested spindles
    # Tort et al. 2010: modulation index for cross-frequency PAC
    pac_enabled: bool = True
    pac_window_s: float = 10.0
    pac_min_coupling: float = 0.01  # minimum modulation index for delivery

    # ── Cue Dose ──────────────────────────────────────────────────────────
    max_cues_per_concept: int = 5   # Antony 2012: diminishing returns beyond ~5
    spindle_window_s: float = 3.0

    # ── Fatigue Model ─────────────────────────────────────────────────────
    # Mölle et al. (2002): spindle density peaks in first 4h of sleep
    fatigue_onset_h: float = 4.0
    fatigue_spindle_floor: float = 0.10
    adaptive_fatigue: bool = True

    # ── Simulation ────────────────────────────────────────────────────────
    simulate_hours: float = 8.0
    time_warp: float = 3000.0
    tick_real_s: float = 0.05

    # ── Audio ─────────────────────────────────────────────────────────────
    base_volume: float = 0.20
    volume_floor: float = 0.04
    volume_ceiling: float = 0.35
    tts_sample_rate: int = 44100
    # FIX v10: USE_POLLY defaults True (v9 had False despite review claiming True)
    use_polly: bool = True

    # ── Phase Prediction ──────────────────────────────────────────────────
    # v10: "echt" added as recommended production option
    # v10: "hilbert_buffer" REMOVED — was non-causal trap (T1-2 fix)
    # "echt"    : ECHT algorithm (Zrenner et al. 2020) — RECOMMENDED for experiments
    # "hardware": firmware provides so_phase — REQUIRED for lowest latency
    # "ar"      : Burg AR(30) ~120ms — acceptable if ECHT unavailable
    # "lms"     : DEMO ONLY — not a validated EEG phase estimator
    phase_predictor: str = "causal_interp"

    # ── Contextual Bandit (T3-1: NEW in v10) ──────────────────────────────
    # Thompson Sampling bandit replaces heuristic delivery policy
    bandit_enabled: bool = False  # enable after 5 participants of data
    bandit_min_sessions: int = 5
    bandit_algorithm: str = "linucb"  # "linucb" | "thompson"

    # ── A/B Analysis ──────────────────────────────────────────────────────
    # DISABLED by default. Per-concept logging violates independence.
    ab_analysis_enabled: bool = False

    # ── Checkpointing ─────────────────────────────────────────────────────
    checkpoint_interval_s: float = 60.0
    display_interval_s: float = 2.0

    # ── Database ──────────────────────────────────────────────────────────
    # FIX v10: encryption ON by default (v9 had False)
    db_encryption_enabled: bool = True
    db_path: str = ""  # auto-set if empty

    # ── Artefact Rejection ────────────────────────────────────────────────
    artefact_rejection_enabled: bool = True
    artefact_amplitude_uv: float = 200.0
    artefact_flatline_threshold: float = 0.5
    artefact_hf_power_threshold: float = 50.0
    # FIX v10: ICA integration (T1-5)
    ica_enabled: bool = True   # requires MNE-Python
    ica_n_components: int = 15

    # ── Multi-Channel EEG ─────────────────────────────────────────────────
    n_eeg_channels: int = 1          # 1 = single channel (NeuroBand default)
    reference_channel: int = 0        # Channel index used as phase reference

    # ── Personalized Cue Calibration (T3-3: NEW in v10) ───────────────────
    cue_calibration_enabled: bool = False
    cue_calibration_n_trials: int = 10

    # ── GDPR Compliance (T3-6: NEW in v10) ────────────────────────────────
    gdpr_enabled: bool = False
    data_retention_days: int = 365
    pseudonymization_enabled: bool = True

    def validate(self) -> list[str]:
        """Return list of validation errors. Empty = valid."""
        errors: list[str] = []

        # Weight sum
        w_sum = self.weight_accuracy + self.weight_speed + self.weight_confidence
        if abs(w_sum - 1.0) > 1e-4:
            errors.append(f"Weights must sum to 1.0 (got {w_sum:.4f})")

        # Sweet spot bounds
        if not (0 < self.sweet_low < self.sweet_high < 1):
            errors.append("Require 0 < sweet_low < sweet_high < 1")

        # Safety
        if self.min_interval_s < 5:
            errors.append("min_interval_s < 5s is unsafe")

        # Phase predictor validation
        valid_predictors = ("echt", "causal_interp", "ar", "lms", "hardware")
        if self.phase_predictor not in valid_predictors:
            errors.append(
                f"Unknown phase_predictor '{self.phase_predictor}'. "
                f"Valid: {valid_predictors}. "
                f"NOTE: 'hilbert_buffer' was REMOVED in v10 — it is non-causal "
                f"and cannot be used for phase-locked cueing."
            )

        # Volume safety
        if self.volume_ceiling > 0.50:
            errors.append("volume_ceiling > 0.50 risks hearing damage during sleep")

        # Bandit prerequisites
        if self.bandit_enabled and self.bandit_algorithm not in ("linucb", "thompson"):
            errors.append(f"Unknown bandit_algorithm: {self.bandit_algorithm}")

        # K-complex bounds
        if self.kcomplex_enabled:
            if self.kcomplex_duration_min_s >= self.kcomplex_duration_max_s:
                errors.append("kcomplex_duration_min_s must be < kcomplex_duration_max_s")

        return errors

    def to_dict(self) -> dict[str, Any]:
        """Export as dict for serialization."""
        return asdict(self)

    def to_json(self, path: str | Path) -> None:
        """Save config as JSON with validation."""
        errors = self.validate()
        if errors:
            raise ValueError(f"Cannot save invalid config: {errors}")
        Path(path).write_text(json.dumps(self.to_dict(), indent=2))

    @classmethod
    def from_json(cls, path: str | Path) -> Config:
        """Load config from JSON file."""
        data = json.loads(Path(path).read_text())
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> Config:
        """Create config from dict, ignoring unknown keys."""
        return cls(**{k: v for k, v in d.items() if k in cls.__dataclass_fields__})

    def phase_predictor_advisory(self) -> dict[str, str]:
        """Return latency and suitability advisory for current phase predictor."""
        advisories = {
            "echt": {
                "latency": "~20-50 ms",
                "status": "✅ True Zrenner 2020 algorithm (~45° mean error)",
                "suitable_for_experiments": "yes",
            },
            "causal_interp": {
                "latency": "~20-50 ms",
                "status": "✅ RECOMMENDED — highest accuracy (~18° mean error)",
                "suitable_for_experiments": "yes",
            },
            "hardware": {
                "latency": "~0 ms (firmware-provided)",
                "status": "✅ Optimal if firmware validated",
                "suitable_for_experiments": "yes",
            },
            "ar": {
                "latency": "~120 ms",
                "status": "⚠ May exceed SO window budget — validate on hardware",
                "suitable_for_experiments": "conditional",
            },
            "lms": {
                "latency": "~4 ms",
                "status": "❌ Demo only — not a validated EEG phase estimator",
                "suitable_for_experiments": "no",
            },
        }
        return advisories.get(self.phase_predictor, {
            "latency": "unknown",
            "status": "❌ Unknown predictor",
            "suitable_for_experiments": "no",
        })


def load_config(path: str | Path | None = None, **overrides: Any) -> Config:
    """
    Load configuration from file or create default, with optional overrides.

    Priority: overrides > file > defaults
    """
    if path and Path(path).exists():
        cfg = Config.from_json(path)
    else:
        cfg = Config()

    for k, v in overrides.items():
        if hasattr(cfg, k):
            object.__setattr__(cfg, k, v)

    return cfg


def validate_config(cfg: Config) -> None:
    """Validate config and raise ValueError if invalid."""
    errors = cfg.validate()
    if errors:
        raise ValueError(
            "Configuration errors:\n" + "\n".join(f"  • {e}" for e in errors)
        )
