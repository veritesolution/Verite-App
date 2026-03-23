"""Voice interface router — TTS synthesis and STT transcription endpoints."""

from fastapi import APIRouter, Depends, UploadFile, File, HTTPException
from fastapi.responses import Response
from ..schemas import TTSRequest, STTResponse
from ..dependencies import get_current_user
from ..models import User
from ...voice.tts_engine import TTSEngine
from ...voice.stt_engine import STTEngine, EmotionAnalyzer

router = APIRouter()
_tts = TTSEngine()
_stt = STTEngine()
_emotion = EmotionAnalyzer()


@router.post("/synthesize", response_class=Response)
async def synthesize_speech(
    req: TTSRequest,
    user: User = Depends(get_current_user),
):
    """
    Convert text to speech. Returns raw LINEAR16 audio bytes.
    Clients should stream or play this directly.
    """
    audio_bytes = await _tts.synthesize(req.text, locale=req.locale, voice_name=req.voice_name)
    return Response(content=audio_bytes, media_type="audio/wav")


@router.post("/transcribe", response_model=STTResponse)
async def transcribe_audio(
    audio: UploadFile = File(..., description="WAV or FLAC audio file, 16kHz mono"),
    analyze_emotion: bool = True,
    user: User = Depends(get_current_user),
):
    """
    Transcribe speech to text. Optionally analyzes emotion from voice prosody.
    Use for voice-driven intake questionnaires.
    """
    content = await audio.read()
    if len(content) > 10 * 1024 * 1024:  # 10 MB cap
        raise HTTPException(status_code=413, detail="Audio file too large (max 10 MB)")

    transcript, confidence, locale_detected = await _stt.transcribe_bytes(content)

    emotion_scores = None
    if analyze_emotion and transcript:
        emotion_scores = await _emotion.analyze_audio_bytes(content)

    return STTResponse(
        transcript=transcript,
        confidence=confidence,
        locale_detected=locale_detected,
        emotion_scores=emotion_scores,
    )
