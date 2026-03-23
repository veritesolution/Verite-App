"""
TMR session router.
Schedules and monitors Targeted Memory Reactivation sessions.
"""

import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
import structlog

from ..database import get_db
from ..models import User, HabitPlan, TMRSession, TMRSessionStatus, TMREvent
from ..schemas import TMRSessionCreateRequest, TMRSessionResponse
from ..dependencies import get_current_user, require_consent, log_action

log = structlog.get_logger()
router = APIRouter()


@router.post("/", response_model=TMRSessionResponse, status_code=status.HTTP_202_ACCEPTED)
async def start_session(
    req: TMRSessionCreateRequest,
    request: Request,
    user: User = Depends(require_consent),
    db: AsyncSession = Depends(get_db),
):
    """
    Schedule a TMR session. Session runs in a dedicated Celery worker
    that communicates directly with EEG hardware (or synthetic driver in dev).

    ⚠ DISCLAIMER: TMR efficacy for habit change has not been established in
    controlled clinical trials. This feature is for research purposes only.
    """
    # Verify plan if provided
    if req.plan_id:
        result = await db.execute(
            select(HabitPlan).where(HabitPlan.id == req.plan_id, HabitPlan.user_id == user.id)
        )
        if not result.scalar_one_or_none():
            raise HTTPException(status_code=404, detail="Plan not found")

    session = TMRSession(
        user_id=user.id,
        plan_id=req.plan_id,
        hardware=req.hardware,
        cues_planned=req.max_cues,
        status=TMRSessionStatus.SCHEDULED,
    )
    db.add(session)
    await db.flush()

    # Dispatch to dedicated EEG worker queue
    from ...workers.tmr_worker import run_tmr_session
    task = run_tmr_session.apply_async(
        args=[str(session.id), req.hardware],
        kwargs={
            "max_duration_s": req.max_duration_minutes * 60,
            "max_cues": req.max_cues,
        },
        queue="eeg",
    )
    session.celery_task_id = task.id
    await db.flush()

    await log_action(
        db, "tmr.start", user_id=user.id,
        resource_type="tmr_session", resource_id=session.id,
        request=request, detail={"hardware": req.hardware, "task_id": task.id},
    )

    return session


@router.get("/{session_id}", response_model=TMRSessionResponse)
async def get_session(
    session_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        select(TMRSession).where(TMRSession.id == session_id, TMRSession.user_id == user.id)
    )
    session = result.scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    return session


@router.post("/{session_id}/abort", status_code=status.HTTP_200_OK)
async def abort_session(
    session_id: uuid.UUID,
    request: Request,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        select(TMRSession).where(
            TMRSession.id == session_id,
            TMRSession.user_id == user.id,
            TMRSession.status == TMRSessionStatus.RUNNING,
        )
    )
    session = result.scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404, detail="No active session found")

    from ...workers.celery_app import celery_app
    celery_app.control.revoke(session.celery_task_id, terminate=True)
    session.status = TMRSessionStatus.ABORTED
    session.ended_at = datetime.now(timezone.utc)

    await log_action(db, "tmr.abort", user_id=user.id,
                     resource_type="tmr_session", resource_id=session_id, request=request)

    return {"message": "Session aborted"}


@router.get("/{session_id}/events", response_model=list[dict])
async def get_events(
    session_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
    limit: int = 200,
):
    """Return all logged events for a TMR session (for research analysis)."""
    # Verify ownership first
    result = await db.execute(
        select(TMRSession).where(TMRSession.id == session_id, TMRSession.user_id == user.id)
    )
    if not result.scalar_one_or_none():
        raise HTTPException(status_code=404, detail="Session not found")

    events_result = await db.execute(
        select(TMREvent)
        .where(TMREvent.session_id == session_id)
        .order_by(TMREvent.timestamp_unix)
        .limit(limit)
    )
    events = events_result.scalars().all()
    return [
        {
            "event_type": e.event_type,
            "timestamp_unix": e.timestamp_unix,
            "sleep_stage": e.sleep_stage,
            "spindle_prob": e.spindle_prob,
            "phase_rad": e.phase_rad,
            "arousal_risk": e.arousal_risk,
            "extra": e.extra,
        }
        for e in events
    ]
