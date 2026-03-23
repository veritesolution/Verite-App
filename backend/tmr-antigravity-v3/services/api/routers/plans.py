"""
Habit plan router.
Creates 21-day AI-generated plans and manages their lifecycle.
"""

import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
import structlog

from ..database import get_db
from ..models import User, UserProfile, HabitPlan, PlanStatus
from ..schemas import PlanCreateRequest, PlanStatusResponse, PlanContentResponse
from ..dependencies import get_current_user, require_consent, log_action
from ...safety.crisis_gate import CrisisGate
from ...workers.celery_app import celery_app

log = structlog.get_logger()
router = APIRouter()
crisis_gate = CrisisGate()


@router.post("/", response_model=PlanStatusResponse, status_code=status.HTTP_202_ACCEPTED)
async def create_plan(
    req: PlanCreateRequest,
    request: Request,
    user: User = Depends(require_consent),
    db: AsyncSession = Depends(get_db),
):
    """
    Submit a profile for 21-day habit plan generation.
    Returns a task ID immediately — plan is generated asynchronously.
    """
    # Verify profile ownership
    result = await db.execute(
        select(UserProfile).where(
            UserProfile.id == req.profile_id,
            UserProfile.user_id == user.id,
        )
    )
    profile = result.scalar_one_or_none()
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found")

    # Safety check on ailment text — pass user_id for escalation webhook (Bug 9 fix)
    ailment_text = profile.ailment or ""
    crisis_result = crisis_gate.check(ailment_text, user_id=str(user.id))
    if crisis_result.blocked:
        await log_action(
            db, "safety.crisis_blocked", user_id=user.id,
            resource_type="plan", request=request,
            outcome="blocked",
            detail={"crisis_level": crisis_result.level, "resources": crisis_result.resources},
        )
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail={
                "type": "crisis_detected",
                "message": "We detected something that needs immediate attention.",
                "crisis_level": crisis_result.level,
                "resources": crisis_result.resources,
            },
        )

    # Create pending plan record
    plan = HabitPlan(
        user_id=user.id,
        profile_id=profile.id,
        status=PlanStatus.QUEUED,
    )
    db.add(plan)
    await db.flush()

    # Enqueue Celery task
    from ...workers.plan_generator import generate_plan
    task = generate_plan.apply_async(
        args=[str(plan.id), str(profile.id)],
        queue="plans",
    )
    plan.celery_task_id = task.id
    await db.flush()

    await log_action(
        db, "plan.create", user_id=user.id,
        resource_type="plan", resource_id=plan.id,
        request=request, detail={"task_id": task.id},
    )

    return plan


@router.get("/{plan_id}", response_model=PlanContentResponse)
async def get_plan(
    plan_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        select(HabitPlan).where(HabitPlan.id == plan_id, HabitPlan.user_id == user.id)
    )
    plan = result.scalar_one_or_none()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")

    return PlanContentResponse(
        id=plan.id,
        status=plan.status.value,
        llm_provider=plan.llm_provider,
        cue_audio_cdn_url=plan.cue_audio_cdn_url,
        created_at=plan.created_at,
        completed_at=plan.completed_at,
        error_message=plan.error_message,
        plan_content=plan.plan_content,  # decrypts on access
    )


@router.get("/", response_model=list[PlanStatusResponse])
async def list_plans(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
    limit: int = 10,
    offset: int = 0,
):
    result = await db.execute(
        select(HabitPlan)
        .where(HabitPlan.user_id == user.id)
        .order_by(HabitPlan.created_at.desc())
        .limit(limit).offset(offset)
    )
    return result.scalars().all()


@router.delete("/{plan_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_plan(
    plan_id: uuid.UUID,
    request: Request,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """GDPR right-to-erasure: zeroise encrypted content but keep audit trail."""
    result = await db.execute(
        select(HabitPlan).where(HabitPlan.id == plan_id, HabitPlan.user_id == user.id)
    )
    plan = result.scalar_one_or_none()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")

    plan._plan_enc = None  # erase encrypted content
    plan.cue_audio_cdn_url = None
    await log_action(db, "plan.delete_gdpr", user_id=user.id,
                     resource_type="plan", resource_id=plan_id, request=request)
