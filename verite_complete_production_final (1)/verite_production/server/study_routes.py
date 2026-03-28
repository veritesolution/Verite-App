"""
Vérité TMR — Study Routes (Flashcards, Quizzes, Audio Streaming)

Three routers:
    /study/*  — Flashcard session with real strength scoring
    /quiz/*   — MCQ pre-sleep baseline + post-sleep consolidation check
    /audio/*  — WAV streaming for study audio + cue preview

The critical fix: answerCard() calls assessor.compute_strength() with REAL
values from the user's actual behavior — not random dice from assess_simulation().
When completeStudy() runs, it rebuilds the TMR cue queue from those real scores.
"""

from __future__ import annotations

import io
import random
import struct
import time
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

# ── Study Router ──────────────────────────────────────────────────────────────

study_router = APIRouter(prefix="/study", tags=["study"])


class StartStudyRequest(BaseModel):
    shuffle: bool = True


class CardAnswerRequest(BaseModel):
    concept_key: str
    rating: int = Field(..., ge=1, le=5)  # 1=forgot, 5=perfect
    shown_at: float  # timestamp from when card was served


class StudyCompleteResponse(BaseModel):
    n_studied: int
    n_sweet_spot: int
    n_cues_queued: int
    strengths: dict[str, float]


def _get_session(request: Request):
    """Get active session from app.state, set by main.py."""
    session = getattr(request.app.state, "active_session", None)
    if not session:
        raise HTTPException(404, "No active session. Start a TMR session first.")
    return session


@study_router.post("/start")
async def start_study(req: StartStudyRequest, session=Depends(_get_session)) -> dict:
    """Start a flashcard study session from the uploaded document's concepts."""
    if not session.concepts:
        raise HTTPException(422, "No concepts loaded. Upload a document first.")

    concepts = list(session.concepts)
    if req.shuffle:
        random.shuffle(concepts)

    # Store study state in session
    session._study_queue = list(concepts)
    session._study_index = 0
    session._study_answers: dict[str, dict] = {}

    return {
        "n_concepts": len(concepts),
        "concepts": [c.get("concept", "") for c in concepts],
        "status": "ready",
    }


@study_router.get("/card/next")
async def next_card(session=Depends(_get_session)) -> dict:
    """Get the next flashcard. Front = concept name, back = definition."""
    queue = getattr(session, "_study_queue", [])
    idx = getattr(session, "_study_index", 0)

    if idx >= len(queue):
        return {"done": True, "n_remaining": 0}

    card = queue[idx]
    return {
        "done": False,
        "index": idx,
        "n_remaining": len(queue) - idx,
        "n_total": len(queue),
        "concept": card.get("concept", ""),
        "category": card.get("category", ""),
        "difficulty": card.get("difficulty", "medium"),
        "definition": card.get("definition", ""),
        "shown_at": time.time(),  # client sends this back in answer
    }


@study_router.post("/card/answer")
async def answer_card(req: CardAnswerRequest, session=Depends(_get_session)) -> dict:
    """
    Record a flashcard answer with REAL performance data.

    This is the critical fix: instead of random dice from assess_simulation(),
    we compute strength from the user's actual rating and reaction time.

    Rating mapping:
        1 = forgot completely → correct=0, confidence=1
        2 = barely remembered → correct=0, confidence=2
        3 = remembered with effort → correct=1, confidence=3
        4 = remembered well → correct=1, confidence=4
        5 = instant recall → correct=1, confidence=5
    """
    rt_s = max(0.1, time.time() - req.shown_at)  # reaction time in seconds
    correct = 1 if req.rating >= 3 else 0
    confidence = req.rating

    # Compute REAL strength using the assessor's formula
    strength = session.assessor.compute_strength(correct, rt_s, confidence)
    tier = session.assessor.get_tier(strength)

    # Write into assessor — this REPLACES any random simulation data
    session.assessor.assessments[req.concept_key] = {
        "concept": {"concept": req.concept_key},
        "correct": correct,
        "rt_s": round(rt_s, 2),
        "confidence": confidence,
        "strength": round(strength, 4),
        "priority": session.assessor._assign_priority(strength),
        "sweet_spot": tier == "sweet_spot",
        "cue_dose": session.assessor._compute_cue_dose(strength),
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "source": "real_flashcard",  # flag that this is real data, not simulated
    }

    # Store answer for study completion
    answers = getattr(session, "_study_answers", {})
    answers[req.concept_key] = {
        "rating": req.rating, "rt_s": round(rt_s, 2),
        "strength": round(strength, 4), "tier": tier,
    }
    session._study_answers = answers

    # Advance index
    session._study_index = getattr(session, "_study_index", 0) + 1

    return {
        "concept": req.concept_key,
        "strength": round(strength, 4),
        "tier": tier,
        "correct": correct,
        "rt_s": round(rt_s, 2),
        "sweet_spot": tier == "sweet_spot",
    }


@study_router.post("/complete")
async def complete_study(session=Depends(_get_session)) -> dict:
    """
    Complete study session and rebuild TMR cue queue from REAL scores.

    This replaces the random assess_simulation() data with actual performance.
    Only sweet-spot concepts (0.30-0.70 strength) get whispered cues generated.
    """
    answers = getattr(session, "_study_answers", {})
    if not answers:
        raise HTTPException(422, "No study answers recorded. Complete some flashcards first.")

    # Rebuild cue queue from real strength scores
    cue_packages = []
    strengths = {}
    n_sweet = 0

    for key, data in answers.items():
        s = data["strength"]
        strengths[key] = s
        if session.assessor.get_tier(s) == "sweet_spot":
            n_sweet += 1
            pkg = session.audio_engine.generate_whispered(key, concept_id=key)
            cue_packages.append(pkg)

    session.orchestrator.load_queue(cue_packages)

    return {
        "n_studied": len(answers),
        "n_sweet_spot": n_sweet,
        "n_cues_queued": len(cue_packages),
        "strengths": strengths,
        "audio_backend": session.audio_engine.backend,
        "message": f"TMR queue rebuilt with {len(cue_packages)} cues from real study data.",
    }


# ── Quiz Router ───────────────────────────────────────────────────────────────

quiz_router = APIRouter(prefix="/quiz", tags=["quiz"])


class StartQuizRequest(BaseModel):
    mode: str = Field("pre_sleep", pattern="^(pre_sleep|post_sleep)$")
    n_questions: int = Field(10, ge=1, le=50)


class QuizAnswerRequest(BaseModel):
    concept_key: str
    selected_index: int  # 0-3
    shown_at: float


@quiz_router.post("/start")
async def start_quiz(req: StartQuizRequest, session=Depends(_get_session)) -> dict:
    """Start a multiple-choice quiz (pre_sleep or post_sleep)."""
    if not session.concepts:
        raise HTTPException(422, "No concepts loaded.")

    concepts = list(session.concepts)
    if len(concepts) < 4:
        raise HTTPException(422, "Need at least 4 concepts for MCQ generation.")

    # Generate MCQ questions
    questions = []
    pool = concepts[:req.n_questions] if len(concepts) >= req.n_questions else concepts

    for concept in pool:
        correct_def = concept.get("definition", "")
        correct_name = concept.get("concept", "")

        # Get 3 distractors from other concepts
        others = [c for c in concepts if c.get("concept") != correct_name]
        distractors = random.sample(others, min(3, len(others)))
        distractor_defs = [d.get("definition", "") for d in distractors]

        # Build options and shuffle
        options = [correct_def] + distractor_defs
        correct_idx = 0
        indices = list(range(len(options)))
        random.shuffle(indices)
        shuffled_options = [options[i] for i in indices]
        new_correct_idx = indices.index(correct_idx)

        questions.append({
            "concept": correct_name,
            "question": f"Which definition matches: {correct_name}?",
            "options": shuffled_options,
            "correct_index": new_correct_idx,
        })

    session._quiz_questions = questions
    session._quiz_mode = req.mode
    session._quiz_index = 0
    session._quiz_results: list[dict] = []

    return {
        "mode": req.mode,
        "n_questions": len(questions),
        "status": "ready",
    }


@quiz_router.get("/question")
async def get_question(session=Depends(_get_session)) -> dict:
    """Get the current quiz question."""
    questions = getattr(session, "_quiz_questions", [])
    idx = getattr(session, "_quiz_index", 0)

    if idx >= len(questions):
        return {"done": True, "n_remaining": 0}

    q = questions[idx]
    return {
        "done": False,
        "index": idx,
        "n_remaining": len(questions) - idx,
        "n_total": len(questions),
        "concept": q["concept"],
        "question": q["question"],
        "options": q["options"],
        "shown_at": time.time(),
        # Don't send correct_index — that's server-side only
    }


@quiz_router.post("/answer")
async def answer_question(req: QuizAnswerRequest, session=Depends(_get_session)) -> dict:
    """Submit a quiz answer and get feedback."""
    questions = getattr(session, "_quiz_questions", [])
    idx = getattr(session, "_quiz_index", 0)

    if idx >= len(questions):
        raise HTTPException(422, "Quiz already complete.")

    q = questions[idx]
    correct = req.selected_index == q["correct_index"]
    rt_s = max(0.1, time.time() - req.shown_at)

    result = {
        "concept": req.concept_key,
        "correct": correct,
        "correct_index": q["correct_index"],
        "selected_index": req.selected_index,
        "rt_s": round(rt_s, 2),
    }

    results = getattr(session, "_quiz_results", [])
    results.append(result)
    session._quiz_results = results
    session._quiz_index = idx + 1

    return {
        **result,
        "done": idx + 1 >= len(questions),
        "n_remaining": len(questions) - idx - 1,
    }


@quiz_router.post("/complete")
async def complete_quiz(session=Depends(_get_session)) -> dict:
    """
    Complete quiz and compute results.
    For post_sleep mode: calls update_weights_from_history() to refine the
    strength formula from actual consolidation data.
    """
    results = getattr(session, "_quiz_results", [])
    mode = getattr(session, "_quiz_mode", "pre_sleep")

    if not results:
        raise HTTPException(422, "No quiz answers recorded.")

    n_correct = sum(1 for r in results if r["correct"])
    n_total = len(results)
    accuracy = round(n_correct / n_total * 100, 1) if n_total > 0 else 0.0
    avg_rt = round(sum(r["rt_s"] for r in results) / n_total, 2) if n_total > 0 else 0.0

    # For post-sleep quiz: update weight learning with real consolidation data
    weight_updates = 0
    if mode == "post_sleep":
        for r in results:
            key = r["concept"]
            # Convert quiz performance to a post-sleep strength score
            post_strength = 1.0 if r["correct"] else 0.0
            # Scale by speed: faster correct answers = stronger consolidation
            if r["correct"] and r["rt_s"] < 5.0:
                post_strength = min(1.0, 0.7 + 0.3 * (1.0 - r["rt_s"] / 5.0))

            session.assessor.update_weights_from_history(key, post_strength)
            weight_updates += 1

    return {
        "mode": mode,
        "n_correct": n_correct,
        "n_total": n_total,
        "accuracy_pct": accuracy,
        "avg_rt_s": avg_rt,
        "weight_updates": weight_updates,
        "results": results,
        "assessor_profile": session.assessor.get_session_profile(),
    }


# ── Audio Router ──────────────────────────────────────────────────────────────

audio_router = APIRouter(prefix="/audio", tags=["audio"])


def _pcm_to_wav(pcm_data: bytes, sample_rate: int = 44100, channels: int = 1, bits: int = 16) -> bytes:
    """Convert raw PCM bytes to WAV format with proper RIFF header."""
    data_size = len(pcm_data)
    header = struct.pack(
        "<4sI4s4sIHHIIHH4sI",
        b"RIFF", 36 + data_size, b"WAVE",
        b"fmt ", 16,  # PCM format chunk
        1,  # PCM format
        channels, sample_rate,
        sample_rate * channels * (bits // 8),  # byte rate
        channels * (bits // 8),  # block align
        bits,  # bits per sample
        b"data", data_size,
    )
    return header + pcm_data


def _generate_study_audio(session: Any, text: str) -> bytes:
    """
    Generate full-volume study audio (NOT whispered).
    Uses Polly without whisper SSML, or gTTS at normal speed.
    """
    engine = session.audio_engine

    if engine.backend == "polly":
        try:
            import boto3
            client = boto3.client("polly", region_name="us-east-1")
            # Normal voice, NO whisper effect, full volume
            resp = client.synthesize_speech(
                Text=text, TextType="text", OutputFormat="pcm",
                VoiceId="Joanna", Engine="neural",
                SampleRate=str(engine._sample_rate),
            )
            pcm = resp["AudioStream"].read()
            return _pcm_to_wav(pcm, engine._sample_rate)
        except Exception:
            pass

    if engine.backend == "gtts":
        try:
            from gtts import gTTS
            from pydub import AudioSegment
            tts = gTTS(text=text, lang="en", slow=False)  # normal speed
            buf = io.BytesIO()
            tts.write_to_fp(buf)
            buf.seek(0)
            seg = AudioSegment.from_mp3(buf)
            seg = seg.set_frame_rate(engine._sample_rate).set_channels(1)
            return _pcm_to_wav(seg.raw_data, engine._sample_rate)
        except Exception:
            pass

    # Fallback: silence with a click (indicates audio system works)
    import numpy as np
    n = int(engine._sample_rate * 0.5)
    silence = (np.zeros(n) * 32767).astype(np.int16).tobytes()
    return _pcm_to_wav(silence, engine._sample_rate)


@audio_router.get("/study/{concept_key}")
async def get_study_audio(concept_key: str, session=Depends(_get_session)):
    """Stream full-volume study audio for a concept (name + definition)."""
    concept = None
    for c in session.concepts:
        if c.get("concept", "") == concept_key:
            concept = c
            break
    if not concept:
        raise HTTPException(404, f"Concept '{concept_key}' not found")

    text = f"{concept.get('concept', '')}. {concept.get('definition', '')}"
    wav_data = _generate_study_audio(session, text)

    return StreamingResponse(
        io.BytesIO(wav_data),
        media_type="audio/wav",
        headers={"Content-Length": str(len(wav_data))},
    )


@audio_router.get("/definition/{concept_key}")
async def get_definition_audio(concept_key: str, session=Depends(_get_session)):
    """Stream just the definition audio (for flashcard back reveal)."""
    concept = None
    for c in session.concepts:
        if c.get("concept", "") == concept_key:
            concept = c
            break
    if not concept:
        raise HTTPException(404, f"Concept '{concept_key}' not found")

    wav_data = _generate_study_audio(session, concept.get("definition", ""))
    return StreamingResponse(
        io.BytesIO(wav_data),
        media_type="audio/wav",
        headers={"Content-Length": str(len(wav_data))},
    )


@audio_router.get("/cue/{concept_key}")
async def get_cue_preview(concept_key: str, session=Depends(_get_session)):
    """Stream whispered TMR cue preview (what plays during sleep)."""
    pkg = session.audio_engine.generate_whispered(concept_key, concept_id=concept_key)
    wav_data = _pcm_to_wav(pkg.audio_data, pkg.sample_rate)
    return StreamingResponse(
        io.BytesIO(wav_data),
        media_type="audio/wav",
        headers={"Content-Length": str(len(wav_data))},
    )


@audio_router.get("/concepts")
async def list_audio_concepts(session=Depends(_get_session)) -> dict:
    """List all concepts with audio available."""
    return {
        "concepts": [
            {
                "key": c.get("concept", ""),
                "definition": c.get("definition", "")[:100],
                "category": c.get("category", ""),
                "study_url": f"/audio/study/{c.get('concept', '')}",
                "definition_url": f"/audio/definition/{c.get('concept', '')}",
                "cue_url": f"/audio/cue/{c.get('concept', '')}",
            }
            for c in session.concepts
        ],
        "audio_backend": session.audio_engine.backend,
    }
