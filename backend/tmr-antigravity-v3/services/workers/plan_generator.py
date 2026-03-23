"""
Habit plan generation Celery task.
Implements: LLM routing → safety guard → structured output → fallback templates → audio cue → S3 upload.
"""

import json
import time
import uuid
from datetime import datetime, timezone
from typing import Optional

import structlog

from .celery_app import celery_app
from ..ai.router import ModelRouter
from ..ai.knowledge_base import KnowledgeBase
from ..ai.safety_guard import PlanSafetyGuard
from ..safety.crisis_gate import ContentModerator

log = structlog.get_logger()
_router = ModelRouter()
_kb = KnowledgeBase()
_guard = PlanSafetyGuard()
_moderator = ContentModerator()


# ── Plan JSON schema ──────────────────────────────────────────────────────────

PLAN_SCHEMA = {
    "type": "object",
    "required": ["habit_target", "mechanism", "weeks", "tmr_cue_phrase", "safety_note"],
    "properties": {
        "habit_target": {"type": "string"},
        "mechanism": {"type": "string"},
        "tmr_cue_phrase": {
            "type": "string",
            "description": "Short 3–5 word phrase to be whispered during sleep as TMR cue",
        },
        "safety_note": {"type": "string"},
        "weeks": {
            "type": "array",
            "minItems": 3,
            "maxItems": 3,
            "items": {
                "type": "object",
                "required": ["week", "theme", "days"],
                "properties": {
                    "week": {"type": "integer"},
                    "theme": {"type": "string"},
                    "days": {
                        "type": "array",
                        "minItems": 7,
                        "maxItems": 7,
                        "items": {
                            "type": "object",
                            "required": ["day", "technique", "intervention", "micro_action", "reflection"],
                            "properties": {
                                "day": {"type": "integer"},
                                "technique": {"type": "string"},
                                "intervention": {"type": "string"},
                                "micro_action": {"type": "string"},
                                "reflection": {"type": "string"},
                            },
                        },
                    },
                },
            },
        },
    },
}

SYSTEM_PROMPT = """You are a clinical psychologist specialising in evidence-based behavioural interventions.
Generate a 21-day habit change plan using CBT, ACT, motivational interviewing, and habit-loop disruption techniques.
Your output must be valid JSON that conforms exactly to the schema provided. Do NOT include markdown, backticks, or any text outside the JSON object.

CRITICAL SAFETY RULES:
- Never recommend dangerous substances, medications, or self-harm of any kind.
- Always include a safety_note acknowledging the tool does not replace professional care.
- If the habit involves substance use, include a note recommending professional assessment.
- Keep language warm, non-judgmental, and empowering.

SCHEMA:
{schema}

AVAILABLE EVIDENCE-BASED TECHNIQUES (choose the most appropriate):
{techniques}
"""


def _build_user_prompt(profile: dict) -> str:
    voice_emotion = ""
    if profile.get("voice_emotion_data"):
        top = sorted(profile["voice_emotion_data"].items(), key=lambda x: -x[1])[:3]
        voice_emotion = f"\nVoice emotion analysis (from intake): {', '.join(f'{k}: {v:.0%}' for k, v in top)}"

    return f"""
Create a 21-day plan for the following person:

Age: {profile.get('age')}
Profession: {profile.get('profession')}
Habit/behaviour: {profile.get('ailment')}
Duration: {profile.get('duration_years')}
Frequency: {profile.get('frequency')}
Severity (1–10): {profile.get('intensity')}
Origin story: {profile.get('origin_story')}
Primary emotion: {profile.get('primary_emotion')}
What it gives them: {profile.get('what_it_gives', 'not specified')}
Trigger emotions: {profile.get('trigger_emotions', 'not specified')}
Past attempts: {profile.get('past_attempts', 'none mentioned')}
Preferred language: {profile.get('locale', 'en')}{voice_emotion}

Generate the complete 21-day plan as JSON only.
""".strip()


# ── Celery task ───────────────────────────────────────────────────────────────

@celery_app.task(
    bind=True,
    name="workers.generate_plan",
    queue="plans",
    max_retries=2,
    default_retry_delay=30,
    acks_late=True,
    reject_on_worker_lost=True,
)
def generate_plan(self, plan_id: str, profile_id: str):
    """
    Main plan generation task. Runs in a Celery worker.
    Updates the DB plan record throughout.
    """
    from ..api.database import AsyncSessionLocal
    from ..api.models import HabitPlan, UserProfile, PlanStatus
    from sqlalchemy import select
    import asyncio

    async def _run():
        async with AsyncSessionLocal() as db:
            # Fetch plan and profile
            plan_result = await db.execute(select(HabitPlan).where(HabitPlan.id == uuid.UUID(plan_id)))
            plan = plan_result.scalar_one_or_none()
            if not plan:
                log.error("plan_not_found", plan_id=plan_id)
                return

            profile_result = await db.execute(select(UserProfile).where(UserProfile.id == uuid.UUID(profile_id)))
            profile = profile_result.scalar_one_or_none()
            if not profile:
                log.error("profile_not_found", profile_id=profile_id)
                plan.status = PlanStatus.FAILED
                plan.error_message = "Profile not found"
                await db.commit()
                return

            plan.status = PlanStatus.GENERATING
            await db.commit()

            # Build profile dict for prompt
            profile_dict = {
                "age": profile.age,
                "profession": profile.profession,
                "ailment": profile.ailment,
                "duration_years": profile.duration_years,
                "frequency": profile.frequency,
                "intensity": profile.intensity,
                "origin_story": profile.origin_story,
                "primary_emotion": profile.primary_emotion,
                "what_it_gives": None,
                "trigger_emotions": None,
                "past_attempts": None,
                "locale": profile.locale,
                "voice_emotion_data": profile.voice_emotion_json,
            }

            # Retrieve relevant techniques from KB
            techniques = _kb.get_techniques(profile_dict.get("ailment", ""), top_k=8)
            techniques_text = "\n".join(f"- {t['name']}: {t['description']}" for t in techniques)

            system = SYSTEM_PROMPT.format(
                schema=json.dumps(PLAN_SCHEMA, indent=2),
                techniques=techniques_text,
            )
            user_prompt = _build_user_prompt(profile_dict)

            # Try LLM providers in order
            plan_json = None
            provider_used = None
            last_error = None

            for provider in _router.get_providers():
                try:
                    raw = await _router.generate_async(
                        provider=provider,
                        system=system,
                        user=user_prompt,
                        max_tokens=4000,
                    )
                    plan_json = _parse_and_validate(raw)
                    if plan_json:
                        provider_used = provider
                        break
                except Exception as e:
                    log.warning("llm_provider_failed", provider=provider, error=str(e))
                    last_error = str(e)

            # Fallback: rule-based template
            if not plan_json:
                log.warning("all_llm_providers_failed", error=last_error)
                plan_json = _kb.get_fallback_template(profile_dict)
                provider_used = "rule_based_fallback"

            # Post-generation safety check
            # Bug 7 fix: we are already inside `async def _run()` which is
            # called via asyncio.run().  Calling loop.run_until_complete()
            # from inside a running loop raises RuntimeError.  Use `await`.
            plan_text = json.dumps(plan_json)
            is_safe, _ = await _moderator.is_safe(plan_text)
            if not is_safe:
                plan.status = PlanStatus.FAILED
                plan.error_message = "Generated content failed safety review"
                await db.commit()
                return

            # Generate audio cue and upload to S3
            cue_phrase = plan_json.get("tmr_cue_phrase", "rest and release")
            s3_key, cdn_url = await _generate_and_upload_cue(cue_phrase, str(plan.id))

            # Save plan
            plan.plan_content = plan_json   # encrypts on assignment
            plan.llm_provider = provider_used
            plan.status = PlanStatus.READY
            plan.completed_at = datetime.now(timezone.utc)
            plan.cue_audio_s3_key = s3_key
            plan.cue_audio_cdn_url = cdn_url
            await db.commit()

            log.info("plan_generated", plan_id=plan_id, provider=provider_used)

    try:
        import asyncio
        asyncio.run(_run())
    except Exception as exc:
        log.error("plan_generation_task_failed", plan_id=plan_id, error=str(exc))
        self.retry(exc=exc)


def _parse_and_validate(raw: str) -> Optional[dict]:
    """Parse LLM output as JSON, validate required keys."""
    # Strip any markdown fences
    cleaned = raw.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.split("```")[1]
        if cleaned.startswith("json"):
            cleaned = cleaned[4:]

    try:
        data = json.loads(cleaned)
        required = {"habit_target", "weeks", "tmr_cue_phrase", "safety_note"}
        if not required.issubset(data.keys()):
            return None
        if len(data.get("weeks", [])) != 3:
            return None
        return data
    except json.JSONDecodeError:
        return None


async def _generate_and_upload_cue(phrase: str, plan_id: str) -> tuple[str, Optional[str]]:
    """Generate TTS audio cue and upload to S3. Returns (s3_key, cdn_url)."""
    from ..voice.tts_engine import TTSEngine
    import boto3

    tts = TTSEngine()
    audio_bytes = await tts.synthesize(
        text=f"Remember... {phrase}",
        locale="en-US",
        speaking_rate=0.75,
    )

    s3_key = f"cues/{plan_id}/cue.wav"
    try:
        from ..api.config import settings
        s3 = boto3.client(
            "s3",
            aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
            aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
            region_name=settings.AWS_REGION,
        )
        s3.put_object(
            Bucket=settings.S3_BUCKET_AUDIO,
            Key=s3_key,
            Body=audio_bytes,
            ContentType="audio/wav",
        )
        cdn_url = (
            f"https://{settings.CLOUDFRONT_DOMAIN}/{s3_key}"
            if settings.CLOUDFRONT_DOMAIN
            else None
        )
        return s3_key, cdn_url
    except Exception as e:
        log.warning("s3_upload_failed", error=str(e))
        return s3_key, None
