"""Post-generation plan safety validator."""

import json
from typing import Optional
import structlog

log = structlog.get_logger()

BLOCKED_CONTENT = [
    "medication", "drug", "pills", "dosage", "overdose",
    "sedative", "benzodiazepine", "opioid", "alcohol as treatment",
    "cut yourself", "harm yourself",
]

REQUIRED_DISCLAIMER_WORDS = ["professional", "therapist", "clinical", "substitute"]


class PlanSafetyGuard:
    """Validates LLM-generated plans before saving to the database."""

    def validate(self, plan: dict) -> tuple[bool, Optional[str]]:
        """
        Returns (is_safe, rejection_reason).
        Rejection reason is None when the plan is safe.
        """
        plan_str = json.dumps(plan).lower()

        # Check for blocked content
        for phrase in BLOCKED_CONTENT:
            if phrase in plan_str:
                return False, f"Blocked phrase detected: '{phrase}'"

        # Ensure safety note exists and contains appropriate language
        safety_note = plan.get("safety_note", "").lower()
        if not safety_note:
            return False, "Missing safety_note field"

        if not any(word in safety_note for word in REQUIRED_DISCLAIMER_WORDS):
            return False, "Safety note missing required disclaimer language"

        # Ensure all 21 days are present
        weeks = plan.get("weeks", [])
        if len(weeks) != 3:
            return False, f"Expected 3 weeks, got {len(weeks)}"

        for week in weeks:
            days = week.get("days", [])
            if len(days) != 7:
                return False, f"Week {week.get('week')} has {len(days)} days, expected 7"

        return True, None
