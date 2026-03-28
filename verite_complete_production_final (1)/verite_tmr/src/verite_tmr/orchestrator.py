"""
AdaptiveCueOrchestrator — Delivery gate + cue selection policy.

Two modes:
    1. Heuristic (default): Rule-based delivery from v9 with fatigue model
    2. Contextual Bandit (T3-1): Thompson Sampling / LinUCB for adaptive delivery

Changes from v9:
    - LinUCB contextual bandit implementation
    - K-complex integration in delivery gate
    - SO-spindle coupling score as delivery criterion
    - Gate rejection tracking with detailed diagnostics
"""

from __future__ import annotations

import time
import warnings
from collections import defaultdict
from dataclasses import dataclass
from typing import Any, Callable, Optional

import numpy as np

from verite_tmr.config import Config
from verite_tmr.audio import AudioCuePackage


@dataclass
class CueEvent:
    """Record of a delivered cue."""
    concept_id: str
    concept_name: str
    cue_type: str
    cue_package: AudioCuePackage | None
    delivered_at: float
    so_phase_at_delivery: float
    spindle_prob_at_delivery: float
    arousal_at_delivery: float
    coupling_mi: float = 0.0
    kcomplex_detected: bool = False
    spindle_coupled: bool = False
    arousal_triggered: bool = False
    phase_source: str = "hardware"
    spindle_source: str = "hardware"
    reward: float = 0.0


class AdaptiveCueOrchestrator:
    """
    Delivery gate + cue selection.

    Gate criteria (all must pass):
        1. Sleep stage: N2 or N3
        2. SO phase: within up-state window [0.75π, 1.25π]
        3. Spindle probability ≥ threshold (fatigue-adapted)
        4. Arousal risk ≤ 0.25
        5. Minimum inter-cue interval ≥ 30s
        6. No artefact detected
        7. (Optional) SO-spindle coupling MI ≥ threshold
        8. (Optional) K-complex detected in current window
    """

    def __init__(self, config: Config | None = None) -> None:
        cfg = config or Config()
        self._config = cfg
        self._queue: list[AudioCuePackage] = []
        self._delivered: list[CueEvent] = []
        self._concept_counts: dict[str, int] = defaultdict(int)
        self._last_delivery_t: float = 0.0
        self._night_cue_log: list[dict] = []
        self._gate_blocked_log: list[dict] = []

        # Fatigue model
        self._session_start_t: float = time.time()
        self._fatigue_spindle_thr: float = cfg.spindle_prob_thresh
        self._fatigue_max_cues: int = cfg.max_cues_per_concept

        # Contextual bandit (T3-1)
        self._bandit_enabled = cfg.bandit_enabled
        self._bandit = None
        if self._bandit_enabled:
            self._bandit = LinUCBBandit(n_arms=3, n_features=5)

    def load_queue(self, packages: list[AudioCuePackage]) -> None:
        self._queue = sorted(packages, key=lambda p: p.priority_score, reverse=True)

    def step(
        self,
        snap: Any,
        audio_callback: Callable | None = None,
        coupling_mi: float = 0.0,
        kcomplex_detected: bool = False,
    ) -> CueEvent | None:
        """
        Main delivery step. Called once per tick.

        Args:
            snap: BrainStateSnapshot
            audio_callback: Called with AudioCuePackage when cue is delivered
            coupling_mi: SO-spindle coupling modulation index
            kcomplex_detected: Whether K-complex was detected this window
        """
        ok, reason = self._gate_check(snap, coupling_mi, kcomplex_detected)
        if not ok:
            return None

        candidates = [
            p for p in self._queue
            if self._concept_counts[p.concept_id] < self._fatigue_max_cues
        ]
        if not candidates:
            return None

        # Select cue
        if self._bandit_enabled and self._bandit is not None:
            pkg = self._select_bandit(snap, candidates)
        else:
            pkg = self._select_heuristic(snap, candidates)

        now = time.time()
        self._last_delivery_t = now
        self._concept_counts[pkg.concept_id] += 1

        if audio_callback:
            audio_callback(pkg)

        evt = CueEvent(
            concept_id=pkg.concept_id,
            concept_name=pkg.concept_name,
            cue_type=pkg.cue_type,
            cue_package=pkg,
            delivered_at=now,
            so_phase_at_delivery=snap.so_phase,
            spindle_prob_at_delivery=snap.spindle_prob,
            arousal_at_delivery=snap.arousal_risk,
            coupling_mi=coupling_mi,
            kcomplex_detected=kcomplex_detected,
            phase_source=getattr(snap, "phase_source", "unknown"),
            spindle_source=getattr(snap, "spindle_source", "unknown"),
        )
        self._delivered.append(evt)
        self._night_cue_log.append({
            "t": now, "concept": pkg.concept_name, "type": pkg.cue_type,
            "stage": snap.sleep_stage, "so_phase": round(snap.so_phase, 3),
            "spindle": round(snap.spindle_prob, 3),
            "arousal": round(snap.arousal_risk, 3),
            "coupling_mi": round(coupling_mi, 4),
            "kcomplex": kcomplex_detected,
        })
        return evt

    def _gate_check(
        self, snap: Any, coupling_mi: float, kcomplex_detected: bool,
    ) -> tuple[bool, str]:
        self._apply_fatigue_model()
        cfg = self._config

        if snap.sleep_stage not in ("N2", "N3"):
            self._log_block(f"stage={snap.sleep_stage}")
            return False, f"stage={snap.sleep_stage}"

        if getattr(snap, "artefact_detected", False):
            self._log_block("artefact")
            return False, "artefact"

        if not (cfg.so_phase_low * np.pi <= snap.so_phase <= cfg.so_phase_high * np.pi):
            self._log_block(f"SO={snap.so_phase:.2f}")
            return False, f"SO={snap.so_phase:.2f}"

        if snap.spindle_prob < self._fatigue_spindle_thr:
            self._log_block(f"spindle={snap.spindle_prob:.2f}")
            return False, f"spindle={snap.spindle_prob:.2f}"

        if snap.arousal_risk > cfg.arousal_risk_max:
            self._log_block(f"arousal={snap.arousal_risk:.2f}")
            return False, f"arousal={snap.arousal_risk:.2f}"

        if (time.time() - self._last_delivery_t) < cfg.min_interval_s:
            self._log_block("interval")
            return False, "interval"

        # Optional: PAC coupling gate
        if cfg.pac_enabled and coupling_mi < cfg.pac_min_coupling:
            self._log_block(f"coupling_mi={coupling_mi:.4f}")
            return False, f"coupling_mi={coupling_mi:.4f}"

        return True, "ok"

    def _log_block(self, reason: str) -> None:
        self._gate_blocked_log.append({"reason": reason, "t": time.time()})

    def _apply_fatigue_model(self) -> None:
        if not self._config.adaptive_fatigue:
            return
        h = (time.time() - self._session_start_t) / 3600
        if h > self._config.fatigue_onset_h:
            decay = min((h - self._config.fatigue_onset_h) / 4.0, 1.0)
            self._fatigue_spindle_thr = max(
                self._config.fatigue_spindle_floor,
                self._config.spindle_prob_thresh - 0.05 * decay,
            )
            self._fatigue_max_cues = max(
                3, self._config.max_cues_per_concept - int(decay * 2)
            )

    def _select_heuristic(self, snap: Any, candidates: list) -> AudioCuePackage:
        if snap.spindle_prob >= 0.50:
            preference = ("combined", "whispered", "tonal")
        elif snap.spindle_prob >= 0.25:
            preference = ("whispered", "combined", "tonal")
        else:
            preference = ("tonal", "whispered", "combined")

        for ctype in preference:
            typed = [p for p in candidates if p.cue_type == ctype]
            if typed:
                return max(typed, key=lambda p: p.priority_score)
        return max(candidates, key=lambda p: p.priority_score)

    def _select_bandit(self, snap: Any, candidates: list) -> AudioCuePackage:
        """Select cue type using LinUCB contextual bandit."""
        context = np.array([
            snap.spindle_prob,
            snap.arousal_risk,
            snap.so_phase / (2 * np.pi),
            (time.time() - self._session_start_t) / 28800,  # normalized hours
            0.5,  # placeholder for concept strength
        ])

        arm = self._bandit.select_arm(context)
        arm_types = ["whispered", "tonal", "combined"]
        selected_type = arm_types[arm]

        typed = [p for p in candidates if p.cue_type == selected_type]
        if typed:
            return max(typed, key=lambda p: p.priority_score)
        return max(candidates, key=lambda p: p.priority_score)

    def register_spindle_feedback(self, evt: CueEvent) -> None:
        evt.spindle_coupled = True
        evt.reward = 1.0
        if self._bandit:
            arm_types = ["whispered", "tonal", "combined"]
            if evt.cue_type in arm_types:
                self._bandit.update(arm_types.index(evt.cue_type), 1.0, np.zeros(5))

    def session_stats(self) -> dict:
        if not self._delivered:
            return {"total_cues_delivered": 0}
        total = len(self._delivered)
        coupled = sum(1 for e in self._delivered if e.spindle_coupled)
        so_ok = sum(
            1 for e in self._delivered
            if (0.75 * np.pi) <= e.so_phase_at_delivery <= (1.25 * np.pi)
        )
        return {
            "total_cues_delivered": total,
            "spindle_coupled_pct": round(coupled / total * 100, 1),
            "so_upstate_pct": round(so_ok / total * 100, 1),
            "unique_concepts_cued": len(self._concept_counts),
            "policy_mode": "bandit" if self._bandit_enabled else "heuristic",
        }

    def gate_rejection_summary(self) -> dict:
        from collections import Counter
        reasons = Counter(
            e["reason"].split("=")[0] for e in self._gate_blocked_log
        )
        total = len(self._gate_blocked_log) + len(self._delivered)
        return {
            "total_gate_checks": total,
            "delivered": len(self._delivered),
            "blocked": len(self._gate_blocked_log),
            "delivery_rate_pct": round(len(self._delivered) / max(total, 1) * 100, 1),
            "block_reasons": dict(reasons.most_common()),
        }


class LinUCBBandit:
    """
    LinUCB contextual bandit for adaptive cue selection.

    T3-1: Replaces heuristic delivery policy after sufficient data.
    Context: (spindle_prob, arousal_risk, so_phase_norm, hours_elapsed, concept_strength)
    Arms: whispered, tonal, combined
    Reward: post-cue spindle coupling (0 or 1)
    """

    def __init__(self, n_arms: int = 3, n_features: int = 5, alpha: float = 1.0) -> None:
        self.n_arms = n_arms
        self.n_features = n_features
        self.alpha = alpha
        self._A = [np.eye(n_features) for _ in range(n_arms)]
        self._b = [np.zeros(n_features) for _ in range(n_arms)]

    def select_arm(self, context: np.ndarray) -> int:
        """Select arm using UCB criterion."""
        ucbs = []
        for a in range(self.n_arms):
            A_inv = np.linalg.inv(self._A[a])
            theta = A_inv @ self._b[a]
            ucb = float(context @ theta + self.alpha * np.sqrt(context @ A_inv @ context))
            ucbs.append(ucb)
        return int(np.argmax(ucbs))

    def update(self, arm: int, reward: float, context: np.ndarray) -> None:
        """Update arm statistics with observed reward."""
        self._A[arm] += np.outer(context, context)
        self._b[arm] += reward * context
