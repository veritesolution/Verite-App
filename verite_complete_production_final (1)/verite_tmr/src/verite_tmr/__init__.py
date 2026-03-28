"""
Vérité TMR v10.0 — Closed-Loop Targeted Memory Reactivation System

A scientifically-grounded, production-validated system for delivering
targeted memory reactivation cues during sleep.

Modules:
    phase       — Real-time EEG phase estimation (ECHT, AR, LMS)
    detection   — Sleep event detection (spindles, K-complexes, arousal)
    audio       — TTS cue generation and calibration
    memory      — Memory strength assessment and spaced repetition
    document    — Multi-format document ingestion and concept extraction
    analysis    — SO-spindle coupling, analytics, A/B testing
    hardware    — Wearable device adapters
    safety      — Production checklist and GDPR compliance

Quick Start:
    from verite_tmr import VeriteSession
    session = VeriteSession(config_path="my_config.json")
    session.run()
"""

__version__ = "10.5.2"
__author__ = "Vérité TMR Research Team"

from verite_tmr.config import Config, load_config, validate_config
from verite_tmr.session import VeriteSession

__all__ = [
    "Config",
    "load_config",
    "validate_config",
    "VeriteSession",
    "__version__",
]
