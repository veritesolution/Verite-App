"""
VeriteSession — Single entry point for running TMR experiments.

v10.2 FIX (Issue 4): Now imports and wires hardware, document, and analysis modules.
The session owns the hardware connection, document ingestion, and analytics logging.

Usage:
    from verite_tmr import VeriteSession
    session = VeriteSession(config_path="config.json")
    session.run(mode="simulation", document_path="notes.pdf")
"""

from __future__ import annotations

import logging
import time
from pathlib import Path
from typing import Any

from verite_tmr.config import Config, load_config, validate_config

logger = logging.getLogger(__name__)


class VeriteSession:
    """
    Complete TMR session runner.

    Orchestrates ALL components end-to-end:
        1. Document ingestion (document/)
        2. Concept extraction + memory assessment
        3. Audio cue generation
        4. Hardware connection (hardware/) with model plugin pipeline
        5. Real-time detection (phase, spindle, K-complex, arousal, coupling)
        6. Cue delivery via orchestrator
        7. Longitudinal tracking and analytics (analysis/)
    """

    def __init__(
        self,
        config: Config | None = None,
        config_path: str | Path | None = None,
        model_registry: Any = None,
        **overrides: Any,
    ) -> None:
        if config is not None:
            self.config = config
        else:
            self.config = load_config(config_path, **overrides)
        validate_config(self.config)
        self._model_registry = model_registry
        self._components_initialized = False
        self._running = False

    def initialize(self, mode: str = "simulation", ws_uri: str = "",
                   psg_path: str = "") -> dict:
        """Initialize all components and return readiness report."""
        from verite_tmr.safety import validate_pipeline

        report = {}

        # Phase estimator
        from verite_tmr.phase import create_phase_estimator
        self.phase_estimator = create_phase_estimator(
            self.config.phase_predictor, fs=250
        )
        report["phase"] = {
            "type": self.config.phase_predictor,
            "advisory": self.config.phase_predictor_advisory(),
        }

        # Detection
        from verite_tmr.detection import (
            SpindleCNN, KComplexDetector, ArousalPredictor, ArtefactDetector,
            SOSpindleCoupling,
        )
        self.spindle_cnn = SpindleCNN()
        self.kcomplex_detector = KComplexDetector(
            amplitude_threshold_uv=self.config.kcomplex_amplitude_uv,
        )
        self.arousal_predictor = ArousalPredictor()
        self.artefact_detector = ArtefactDetector()
        self.coupling = SOSpindleCoupling(
            min_coupling=self.config.pac_min_coupling,
        )
        report["spindle_cnn"] = {"mode": self.spindle_cnn.mode, "ready": self.spindle_cnn.is_ready}
        report["arousal"] = {"mode": self.arousal_predictor.mode, "trained": self.arousal_predictor.is_trained}

        # Memory assessment
        from verite_tmr.memory import MemoryStrengthAssessor
        self.assessor = MemoryStrengthAssessor(config=self.config)

        # Audio
        from verite_tmr.audio import TMRAudioEngine
        self.audio_engine = TMRAudioEngine(config=self.config)
        report["audio"] = {"backend": self.audio_engine.backend}

        # Orchestrator
        from verite_tmr.orchestrator import AdaptiveCueOrchestrator
        self.orchestrator = AdaptiveCueOrchestrator(config=self.config)

        # Document processor (Issue 4 fix: now wired in)
        from verite_tmr.document import DocumentProcessor, ConceptExtractor
        self.doc_processor = DocumentProcessor()
        self.concept_extractor = ConceptExtractor()
        report["document"] = {"available": True}

        # Hardware interface (Issue 4 fix: now wired in)
        from verite_tmr.hardware import HardwareInterface, ModelRegistry
        registry = self._model_registry or ModelRegistry()
        self.hardware = HardwareInterface(
            mode=mode, ws_uri=ws_uri,
            model_registry=registry,
            time_warp=self.config.time_warp,
            psg_path=psg_path,
        )
        report["hardware"] = {"mode": mode, "ws_uri": ws_uri or "(simulation)"}

        # Longitudinal tracker (Issue 4 fix: now wired in)
        from verite_tmr.analysis import LongitudinalTracker
        db_path = self.config.db_path or "/tmp/verite_tmr.db"
        self.tracker = LongitudinalTracker(db_path=db_path)
        report["tracker"] = {"db_path": db_path}

        # GDPR compliance (Gap 3 fix: wired into session, not just a class)
        from verite_tmr.safety import GDPRDataManager
        self.gdpr = GDPRDataManager(self.config)
        report["gdpr"] = {"enabled": self.config.gdpr_enabled,
                          "pseudonymization": self.config.pseudonymization_enabled}

        # Safety
        # Memory formula status (P2-B)
        report["memory_formula"] = {
            "weights": {
                "accuracy": self.config.weight_accuracy,
                "speed": self.config.weight_speed,
                "confidence": self.config.weight_confidence,
            },
            "status": "⚠ Original heuristic — NOT empirically validated",
            "validation_required": (
                "Call assessor.validate_formula(pre_scores, post_scores, tmr_applied) "
                "after ≥30 participants. Pearson r must be significant (p < 0.05)."
            ),
            "sessions_for_weight_learning": self.config.min_sessions_for_weight_learning,
        }

        safety_report = validate_pipeline(self.config, mode)
        report["safety"] = safety_report

        self._components_initialized = True
        logger.info(f"Session initialized: mode={mode}")
        return report

    def run(
        self,
        mode: str = "simulation",
        hours: float | None = None,
        document_path: str | None = None,
        ws_uri: str = "",
        psg_path: str = "",
    ) -> dict:
        """
        Run a complete TMR session end-to-end.

        Full pipeline:
            1. Ingest document → extract concepts
            2. Assess memory strength → identify sweet-spot targets
            3. Generate audio cues
            4. Connect hardware (or start simulation)
            5. Run delivery loop with orchestrator
            6. Log results to longitudinal tracker
        """
        if not self._components_initialized:
            self.initialize(mode, ws_uri=ws_uri, psg_path=psg_path)

        hours = hours or self.config.simulate_hours
        _raw_session_id = time.strftime("%Y%m%d_%H%M%S")
        # GDPR: pseudonymize the session ID before it touches any storage layer.
        if hasattr(self, 'gdpr') and self.config.gdpr_enabled:
            session_id = self.gdpr.pseudonymize_id(_raw_session_id)
            logger.info(f'Session pseudonymized. mode={mode}, hours={hours}')
        else:
            session_id = _raw_session_id
            logger.info(f'Starting session {session_id}: {hours}h, mode={mode}')

        # Step 1: Document ingestion
        concepts = []
        if document_path and Path(document_path).exists():
            try:
                text = self.doc_processor.extract_text(document_path)
                concepts = self.concept_extractor.extract_concepts(
                    text, max_concepts=self.config.max_concepts
                )
                logger.info(f"Extracted {len(concepts)} concepts from {document_path}")
            except Exception as e:
                logger.warning(f"Document processing failed: {e}")

        # Step 2: Memory assessment (simulation mode)
        if concepts:
            self.assessor.assess_simulation(concepts)

        # Step 3: Generate audio cues
        cue_packages = []
        if concepts:
            for c in concepts:
                name = c.get("concept", c.get("term", ""))
                if self.assessor.get_tier(self.assessor.get_strength(name)) == "sweet_spot":
                    pkg = self.audio_engine.generate_whispered(name, concept_id=name)
                    cue_packages.append(pkg)
            self.orchestrator.load_queue(cue_packages)

        # Step 4 & 5: Hardware loop with delivery
        if mode == "simulation":
            result = self._run_with_hardware(session_id, hours)
        elif mode == "live":
            result = self._run_with_hardware(session_id, hours)
        else:
            result = self._run_with_hardware(session_id, hours)

        # Step 6: Log to tracker with pseudonymized IDs
        stats = self.orchestrator.session_stats()
        # GDPR: pseudonymize participant ID for tracker storage
        participant_id = session_id  # default: session = participant
        if hasattr(self, 'gdpr') and self.config.gdpr_enabled:
            participant_id = self.gdpr.pseudonymize_id(f"participant_{session_id}")
        self.tracker.log_session(
            session_id,
            doc_path=document_path or "",
            n_concepts=len(concepts),
            n_cues=stats.get("total_cues_delivered", 0),
            n_coupled=0,
        )

        stats["session_id"] = session_id
        stats["mode"] = mode
        stats["n_concepts"] = len(concepts)
        stats["simulated_hours"] = hours
        return stats

    def _run_with_hardware(self, session_id: str, hours: float) -> dict:
        """Run session using HardwareInterface for all modes."""
        import numpy as np

        self.hardware.start()
        end_t = time.time() + hours * 3600 / self.config.time_warp
        delivered = []
        last_checkpoint = time.time()

        try:
            while time.time() < end_t and self._running is not False:
                snap = self.hardware.get_snapshot()
                if snap:
                    evt = self.orchestrator.step(snap)
                    if evt:
                        self.hardware.record_cue_latency(snap.timestamp)
                        try:
                            self.tracker.log_cue_event(session_id, evt)
                        except Exception:
                            pass
                        delivered.append(evt)

                # Periodic checkpoint
                if time.time() - last_checkpoint >= self.config.checkpoint_interval_s:
                    last_checkpoint = time.time()

                time.sleep(self.config.tick_real_s)
        finally:
            self.hardware.stop()

        return {"delivered": len(delivered)}

    def stop(self) -> None:
        """Stop a running session."""
        self._running = False
        if hasattr(self, "hardware"):
            self.hardware.stop()
        if hasattr(self, "tracker"):
            self.tracker.close()
        logger.info("Session stopped")
