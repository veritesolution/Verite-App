"""
Safety router — crisis checking endpoint and content moderation.
"""

from fastapi import APIRouter, Depends, Request
from ..schemas import SafetyCheckRequest, SafetyCheckResponse
from ..dependencies import get_current_user, log_action
from ..database import get_db
from ..models import User
from ...safety.crisis_gate import CrisisGate

router = APIRouter()
_gate = CrisisGate()


@router.post("/check", response_model=SafetyCheckResponse)
async def safety_check(
    req: SafetyCheckRequest,
    request: Request,
    user: User = Depends(get_current_user),
    db=Depends(get_db),
):
    result = _gate.check(req.text, user_id=str(user.id))
    if result.blocked:
        await log_action(
            db, "safety.crisis_check_blocked",
            user_id=user.id, request=request, outcome="blocked",
            detail={"level": result.level},
        )
    return SafetyCheckResponse(
        is_safe=not result.blocked,
        crisis_level=result.level,
        resources=result.resources,
        blocked=result.blocked,
    )
