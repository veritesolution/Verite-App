"""Users router — profile management, consent, and GDPR erasure."""

import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from ..database import get_db
from ..models import User, UserProfile, ConsentRecord, ConsentStatus
from ..schemas import (
    UserResponse, ProfileCreateRequest, ProfileResponse,
    ConsentRequest, ConsentResponse,
)
from ..dependencies import get_current_user, log_action
from ...safety.crisis_gate import CrisisGate

router = APIRouter()
_gate = CrisisGate()


@router.get("/me", response_model=UserResponse)
async def get_me(user: User = Depends(get_current_user)):
    return user


@router.post("/consent", response_model=ConsentResponse)
async def give_consent(
    req: ConsentRequest,
    request: Request,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(ConsentRecord).where(ConsentRecord.user_id == user.id))
    consent = result.scalar_one_or_none()
    if consent:
        consent.status = ConsentStatus.GIVEN
        consent.given_at = datetime.now(timezone.utc)
        consent.consent_version = req.consent_version
        consent.irb_protocol = req.irb_protocol
    else:
        consent = ConsentRecord(
            user_id=user.id,
            status=ConsentStatus.GIVEN,
            consent_version=req.consent_version,
            irb_protocol=req.irb_protocol,
            given_at=datetime.now(timezone.utc),
            ip_address=request.client.host,
            user_agent=request.headers.get("user-agent"),
        )
        db.add(consent)

    await log_action(db, "user.consent_given", user_id=user.id, request=request,
                     detail={"version": req.consent_version, "irb": req.irb_protocol})
    return consent


@router.delete("/consent", status_code=status.HTTP_204_NO_CONTENT)
async def withdraw_consent(
    request: Request,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(select(ConsentRecord).where(ConsentRecord.user_id == user.id))
    consent = result.scalar_one_or_none()
    if consent:
        consent.status = ConsentStatus.WITHDRAWN
        consent.withdrawn_at = datetime.now(timezone.utc)
    await log_action(db, "user.consent_withdrawn", user_id=user.id, request=request)


@router.post("/profiles", response_model=ProfileResponse, status_code=status.HTTP_201_CREATED)
async def create_profile(
    req: ProfileCreateRequest,
    request: Request,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # Safety check — pass user_id so escalation webhook can identify the user
    crisis = _gate.check(req.ailment_description, user_id=str(user.id))
    if crisis.blocked:
        raise HTTPException(status_code=422, detail={
            "type": "crisis_detected",
            "resources": crisis.resources,
        })

    profile = UserProfile(
        user_id=user.id,
        age=req.age,
        profession=req.profession,
        duration_years=req.duration_years,
        frequency=req.frequency,
        intensity=req.intensity,
        locale=req.locale,
        input_mode=req.input_mode,
    )
    profile.ailment = req.ailment_description
    profile.primary_emotion = req.primary_emotion
    profile.origin_story = req.origin_story
    if req.voice_emotion_data:
        profile.voice_emotion_json = req.voice_emotion_data

    db.add(profile)
    await db.flush()
    await log_action(db, "user.profile_created", user_id=user.id,
                     resource_type="profile", resource_id=profile.id, request=request)
    return profile


@router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_account_gdpr(
    request: Request,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """GDPR right to erasure — zeroise all PII and encrypted content."""
    user.email = f"deleted_{user.id}@erased.invalid"
    user.full_name = None
    user.hashed_password = None
    user.google_sub = None
    user.apple_sub = None
    user.is_active = False

    # Erase all profile encrypted fields
    result = await db.execute(select(UserProfile).where(UserProfile.user_id == user.id))
    for profile in result.scalars().all():
        profile._ailment_enc = None
        profile._emotion_enc = None
        profile._origin_story_enc = None
        profile._voice_emotion_enc = None

    await log_action(db, "user.gdpr_erasure", user_id=user.id, request=request)
