"""
MemoryStrengthAssessor — Sweet-spot targeting for TMR cue selection.

Ref: Antony et al. (2012) Nat Neurosci — intermediate-strength TMR benefit

Formula: S = w_acc * accuracy + w_spd * (1 - rt_norm) + w_conf * conf_norm

⚠ ORIGINAL HEURISTIC — These weights are NOT from Antony et al. (2012).
Antony identified intermediate-strength benefit behaviourally. The specific
composite formula is our implementation of that concept.

Changes from v9:
    T1-3: Added validate_formula() for correlating S with TMR benefit
    T1-6: Hard gate — weight learning disabled until session_count >= 30
"""

from __future__ import annotations

import datetime
import math
import time
import warnings
from dataclasses import dataclass, field
from typing import Any

import numpy as np

from verite_tmr.config import Config


@dataclass
class AssessmentResult:
    """Result of a single concept assessment."""
    concept: dict
    correct: int
    rt_s: float
    confidence: int
    strength: float
    priority: str
    sweet_spot: bool
    cue_dose: int
    timestamp: str


class MemoryStrengthAssessor:
    """
    Pre-sleep memory assessment with sweet-spot targeting.

    The "sweet spot" (0.30 ≤ S ≤ 0.70) targets concepts that are partially
    learned — strong enough to have a memory trace, weak enough to benefit
    from TMR reactivation during sleep.
    """

    def __init__(self, config: Config | None = None) -> None:
        cfg = config or Config()
        self.sweet_low = cfg.sweet_low
        self.sweet_high = cfg.sweet_high
        self.max_rt = cfg.max_rt_s
        self.min_sessions_for_learning = cfg.min_sessions_for_weight_learning
        self.weight_learning_enabled = cfg.weight_learning_enabled

        # Weights
        self._default_weights = np.array([
            cfg.weight_accuracy, cfg.weight_speed, cfg.weight_confidence
        ])
        self._learned_weights = self._default_weights.copy()
        self._weight_n_updates = 0
        self._weight_history: list[dict] = []

        # Assessment storage
        self.assessments: dict[str, dict[str, Any]] = {}
        self.similarity_matrix: np.ndarray | None = None
        self.differentiation_pairs: list[dict] = []

    def compute_strength(self, correct: int, rt: float, confidence: int) -> float:
        """
        Compute memory strength score S ∈ [0, 1].

        Uses learned weights if sufficient sessions have been accumulated,
        otherwise uses default weights.
        """
        rt_norm = min(rt, self.max_rt) / self.max_rt
        conf_norm = (confidence - 1) / 4.0
        speed = 1.0 - rt_norm

        # T1-6 FIX: Use learned weights ONLY after minimum session threshold
        if self._weight_n_updates >= self.min_sessions_for_learning:
            w = self._learned_weights
        else:
            w = self._default_weights

        s = w[0] * correct + w[1] * speed + w[2] * conf_norm
        return float(np.clip(s, 0.0, 1.0))

    def assess_simulation(self, concepts: list[dict]) -> dict[str, dict]:
        """Simulate assessment for development/testing."""
        for c in concepts:
            strength_raw = np.random.beta(2.5, 2.5)
            correct = 1 if strength_raw > 0.45 else 0
            rt = np.random.exponential(3.0) + 0.8
            confidence = max(1, min(5, int(strength_raw * 4.5) + 1))
            strength = self.compute_strength(correct, rt, confidence)
            key = c.get("concept", c.get("term", "?"))

            self.assessments[key] = {
                "concept": c,
                "correct": correct,
                "rt_s": round(rt, 2),
                "confidence": confidence,
                "strength": round(strength, 4),
                "priority": self._assign_priority(strength),
                "sweet_spot": self.sweet_low <= strength <= self.sweet_high,
                "cue_dose": self._compute_cue_dose(strength),
                "timestamp": datetime.datetime.now().isoformat(),
            }
        return self.assessments

    def update_weights_from_history(
        self, concept_key: str, post_sleep_strength: float
    ) -> None:
        """
        Online weight update from post-sleep quiz result.

        T1-6 FIX: This update is GATED — weights are only updated and reported
        after min_sessions_for_learning (default 30) sessions have been collected.
        Before that threshold, the posterior confidence intervals span nearly the
        entire weight simplex, making any update statistically indefensible.
        """
        r = self.assessments.get(concept_key)
        if r is None:
            return

        self._weight_n_updates += 1

        if self._weight_n_updates < self.min_sessions_for_learning:
            remaining = self.min_sessions_for_learning - self._weight_n_updates
            if self._weight_n_updates % 5 == 0:
                warnings.warn(
                    f"Weight learning: {remaining} more sessions needed before "
                    f"learned weights become active. Using default weights."
                )
            return

        # Perform gradient update only after threshold
        rt_norm = min(r.get("rt_s", 5.0), self.max_rt) / self.max_rt
        conf_norm = (r.get("confidence", 3) - 1) / 4.0
        feats = np.array([float(r.get("correct", 0)), 1.0 - rt_norm, conf_norm])
        predicted = float(np.dot(self._learned_weights, feats))
        error = post_sleep_strength - predicted
        lr = 0.05 / (1.0 + self._weight_n_updates * 0.01)
        grad = error * feats
        new_w = np.clip(self._learned_weights + lr * grad, 0.05, 0.90)
        new_w /= new_w.sum()
        self._learned_weights = new_w
        self._weight_history.append({
            "n": self._weight_n_updates,
            "weights": new_w.tolist(),
            "error": error,
        })

    def get_strength(self, concept_name: str) -> float:
        """Return cached strength for a concept, or 0.5 (neutral prior) if unknown."""
        r = self.assessments.get(concept_name)
        if r is None:
            return 0.5
        if isinstance(r, dict):
            return float(r.get("strength", 0.5))
        return float(r)

    def get_tier(self, strength: float) -> str:
        if strength < self.sweet_low:
            return "too_weak"
        if strength > self.sweet_high:
            return "too_strong"
        return "sweet_spot"

    def get_sweet_spot_targets(self) -> list[dict]:
        targets = [v for v in self.assessments.values() if v.get("sweet_spot")]
        return sorted(targets, key=lambda x: x["strength"])

    def validate_formula(
        self,
        pre_scores: list[float],
        post_scores: list[float],
        tmr_applied: list[bool],
    ) -> dict:
        """
        T1-3: Validate that S actually predicts TMR benefit.

        Correlate S scores with memory improvement (post - pre) across
        participants. Requires ≥ 30 participants.

        Args:
            pre_scores: Pre-sleep strength scores per concept
            post_scores: Post-sleep strength scores per concept
            tmr_applied: Whether TMR was applied per concept

        Returns:
            Validation results with Pearson r and significance
        """
        if len(pre_scores) < 30:
            return {
                "error": f"Need ≥ 30 observations (got {len(pre_scores)})",
                "recommendation": "Collect more data before validating formula",
            }

        pre = np.array(pre_scores)
        post = np.array(post_scores)
        tmr = np.array(tmr_applied)

        improvement = post - pre

        # Correlation between S and improvement for TMR items only
        tmr_idx = tmr.astype(bool)
        if tmr_idx.sum() < 10:
            return {"error": "Need ≥ 10 TMR items for validation"}

        from scipy import stats
        r_tmr, p_tmr = stats.pearsonr(pre[tmr_idx], improvement[tmr_idx])
        rho_tmr, p_rho = stats.spearmanr(pre[tmr_idx], improvement[tmr_idx])

        return {
            "pearson_r": round(float(r_tmr), 4),
            "pearson_p": round(float(p_tmr), 6),
            "spearman_rho": round(float(rho_tmr), 4),
            "spearman_p": round(float(p_rho), 6),
            "n_tmr": int(tmr_idx.sum()),
            "n_control": int((~tmr_idx).sum()),
            "significant_p05": p_tmr < 0.05,
            "weights_used": (
                self._learned_weights.tolist()
                if self._weight_n_updates >= self.min_sessions_for_learning
                else self._default_weights.tolist()
            ),
            "weights_source": (
                "learned" if self._weight_n_updates >= self.min_sessions_for_learning
                else "default_heuristic"
            ),
        }

    def _assign_priority(self, strength: float) -> str:
        if strength < self.sweet_low:
            return "low (forgotten — re-study first)"
        if strength > self.sweet_high:
            return "low (consolidated — skip TMR)"
        if strength < 0.50:
            return "HIGH (prime sweet spot)"
        return "MEDIUM (upper sweet spot)"

    def _compute_cue_dose(self, strength: float) -> int:
        if strength < self.sweet_low or strength > self.sweet_high:
            return 0
        dose_raw = math.exp(-((strength - 0.40) ** 2) / (2 * 0.12 ** 2))
        return max(1, round(dose_raw * 5))

    def get_session_profile(self) -> dict:
        return {
            "timestamp": datetime.datetime.now().isoformat(),
            "n_assessed": len(self.assessments),
            "sweet_spot_count": sum(
                1 for v in self.assessments.values() if v.get("sweet_spot")
            ),
            "avg_strength": (
                float(np.mean([v["strength"] for v in self.assessments.values()]))
                if self.assessments else 0.0
            ),
            "weights": (
                self._learned_weights.tolist()
                if self._weight_n_updates >= self.min_sessions_for_learning
                else self._default_weights.tolist()
            ),
            "weights_source": (
                "learned" if self._weight_n_updates >= self.min_sessions_for_learning
                else "default_heuristic"
            ),
            "n_weight_updates": self._weight_n_updates,
        }
