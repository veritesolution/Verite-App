"""
Evidence-based intervention knowledge base.
Used for technique retrieval during plan generation and as a rule-based
fallback when all LLM providers are unavailable.
"""

import json
from typing import Optional
import structlog

log = structlog.get_logger()

# ── Intervention database ─────────────────────────────────────────────────────
# Each technique has: name, framework, description, best_for (habit categories)

INTERVENTIONS = [
    {
        "name": "Habit loop interruption",
        "framework": "Habit Loop Theory (Duhigg 2012)",
        "description": "Identify cue→routine→reward chain; replace routine with competing behaviour.",
        "best_for": ["scrolling", "phone", "addiction", "compulsion"],
    },
    {
        "name": "Cognitive restructuring",
        "framework": "CBT (Beck 1979)",
        "description": "Identify automatic negative thoughts; challenge and replace with balanced cognitions.",
        "best_for": ["anxiety", "rumination", "perfectionism", "overthinking"],
    },
    {
        "name": "Values clarification",
        "framework": "ACT (Hayes 2004)",
        "description": "Identify core values; use them as intrinsic motivation anchors.",
        "best_for": ["procrastination", "avoidance", "direction", "purpose"],
    },
    {
        "name": "Urge surfing",
        "framework": "DBT / Mindfulness (Linehan 1993)",
        "description": "Observe craving as a wave without acting on it; builds distress tolerance.",
        "best_for": ["craving", "substance", "scrolling", "eating", "gambling"],
    },
    {
        "name": "Implementation intentions",
        "framework": "Goal Setting Theory (Gollwitzer 1999)",
        "description": "Pre-commit to 'If–Then' plans: 'If I feel X, I will do Y instead.'",
        "best_for": ["procrastination", "exercise", "routine", "phone", "scrolling"],
    },
    {
        "name": "Behavioural activation",
        "framework": "BA (Martell 2001)",
        "description": "Schedule positive activities to break avoidance–low mood cycles.",
        "best_for": ["depression", "avoidance", "isolation", "low motivation"],
    },
    {
        "name": "Progressive muscle relaxation",
        "framework": "Relaxation Training (Jacobson 1938)",
        "description": "Systematic tension-release of muscle groups to reduce physiological arousal.",
        "best_for": ["anxiety", "stress", "insomnia", "tension"],
    },
    {
        "name": "Motivational enhancement",
        "framework": "Motivational Interviewing (Miller & Rollnick 2013)",
        "description": "Explore ambivalence; elicit change talk; strengthen commitment.",
        "best_for": ["substance", "alcohol", "addiction", "ambivalence", "resistance"],
    },
    {
        "name": "Stimulus control",
        "framework": "Behaviour Therapy (Bootzin 1972)",
        "description": "Modify environmental cues that trigger the unwanted behaviour.",
        "best_for": ["insomnia", "scrolling", "phone", "eating", "substance"],
    },
    {
        "name": "Self-compassion practice",
        "framework": "Self-Compassion (Neff 2003)",
        "description": "Treat yourself with the kindness you would offer a close friend during difficulty.",
        "best_for": ["perfectionism", "self-criticism", "shame", "eating", "depression"],
    },
    {
        "name": "Mindfulness-based relapse prevention",
        "framework": "MBRP (Bowen 2009)",
        "description": "Mindful awareness of triggers and cravings to reduce automatic responding.",
        "best_for": ["addiction", "substance", "alcohol", "relapse"],
    },
    {
        "name": "Behavioural experiments",
        "framework": "CBT (Salkovskis 1985)",
        "description": "Test dysfunctional beliefs by designing small, controlled real-world experiments.",
        "best_for": ["anxiety", "social", "avoidance", "perfectionism"],
    },
]

# ── Rule-based template days ───────────────────────────────────────────────────

def _make_week(week_num: int, theme: str, techniques: list[dict]) -> dict:
    n = len(techniques)
    days = []
    for day in range(1, 8):
        t = techniques[(day - 1) % n]
        days.append({
            "day": day,
            "technique": t["name"],
            "intervention": t["framework"],
            "micro_action": f"Spend 5 minutes practising {t['name'].lower()} today.",
            "reflection": "What did you notice? What was easy or difficult?",
        })
    return {"week": week_num, "theme": theme, "days": days}


class KnowledgeBase:
    """Retrieves relevant techniques and provides fallback templates."""

    def get_techniques(self, ailment_text: str, top_k: int = 6) -> list[dict]:
        """Simple keyword match — replace with sentence-transformer similarity in production."""
        text_lower = ailment_text.lower()
        scored = []
        for t in INTERVENTIONS:
            score = sum(1 for kw in t["best_for"] if kw in text_lower)
            scored.append((score, t))
        scored.sort(key=lambda x: -x[0])
        top = [t for _, t in scored[:top_k]] or INTERVENTIONS[:top_k]
        return top

    def get_fallback_template(self, profile: dict) -> dict:
        """Return a fully-formed rule-based 21-day plan when LLMs are unavailable."""
        ailment = profile.get("ailment", "unwanted habit")
        techniques = self.get_techniques(ailment)

        log.warning("using_fallback_template", ailment=ailment[:50])

        return {
            "habit_target": ailment,
            "mechanism": (
                "This plan uses evidence-based behavioural science techniques to help you "
                "understand the habit loop, build awareness, and replace the behaviour with "
                "more aligned alternatives."
            ),
            "tmr_cue_phrase": "calm and free",
            "safety_note": (
                "⚠ This plan is not a substitute for professional clinical care. "
                "If you are experiencing significant distress, please contact a qualified therapist."
            ),
            "weeks": [
                _make_week(1, "Awareness and understanding", techniques[:3]),
                _make_week(2, "Practising alternatives", techniques[1:4]),
                _make_week(3, "Consolidation and relapse prevention", techniques[2:5]),
            ],
        }
