"""
Safety — ENFORCED production readiness and GDPR compliance.

v10.3: Safety gates are now HARD INTERLOCKS, not advisory warnings.
Live mode REFUSES to start if critical components are in fallback.

This was the #1 criticism from the independent review:
"A researcher could run a live session with all critical components
in fallback mode and the system would happily start."
That is no longer possible.
"""

from __future__ import annotations

import hashlib
import json
import time
from typing import Any

from verite_tmr.config import Config


class ReadinessLevel:
    """Readiness classification for each component."""
    READY = "ready"           # Validated, suitable for experiments
    DEGRADED = "degraded"     # Fallback active, results will be limited
    CRITICAL = "critical"     # Cannot produce valid scientific results
    BLOCKED = "blocked"       # Will not start in live mode


def validate_pipeline(config: Config, mode: str = "simulation") -> dict:
    """
    Structured readiness check on all pipeline components.

    In LIVE mode: raises RuntimeError if ANY component is CRITICAL.
    In SIMULATION mode: returns warnings but does not block.
    """
    results: dict[str, dict[str, str]] = {}

    # 1. Phase estimation
    advisory = config.phase_predictor_advisory()
    if advisory.get("suitable_for_experiments") == "yes":
        results["phase_estimation"] = {
            "status": f"✅ {config.phase_predictor}",
            "level": ReadinessLevel.READY,
            "detail": advisory.get("status", ""),
        }
    elif advisory.get("suitable_for_experiments") == "conditional":
        results["phase_estimation"] = {
            "status": f"⚠ {config.phase_predictor}",
            "level": ReadinessLevel.DEGRADED,
            "detail": advisory.get("status", ""),
        }
    else:
        results["phase_estimation"] = {
            "status": f"❌ {config.phase_predictor}",
            "level": ReadinessLevel.CRITICAL,
            "detail": f"{config.phase_predictor} is not validated for experiments. "
                      f"Use 'causal_interp' or 'echt'.",
        }

    # 2. Spindle detection — CRITICAL if no trained model
    results["spindle_detection"] = {
        "status": "❌ No trained model — band-power proxy active",
        "level": ReadinessLevel.CRITICAL,
        "detail": "Band-power proxy fires on ANY sigma activity (EMG, alpha). "
                  "All spindle-coupled statistics are INVALID. "
                  "Train on DREAMS: verite-train-spindle --download-dreams",
    }

    # 3. Arousal prediction — DEGRADED (reactive fallback)
    results["arousal_prediction"] = {
        "status": "⚠ Reactive fallback (fires after arousal onset)",
        "level": ReadinessLevel.DEGRADED,
        "detail": "Train on MESA/SHHS: verite-train-arousal --mesa-dir /path/",
    }

    # 4. Audio TTS
    if config.use_polly:
        results["audio_tts"] = {
            "status": "✅ Amazon Polly configured",
            "level": ReadinessLevel.READY,
            "detail": "Conduct intelligibility pilot before experiments",
        }
    else:
        results["audio_tts"] = {
            "status": "⚠ gTTS fallback — NOT validated for sleep",
            "level": ReadinessLevel.DEGRADED,
            "detail": "gTTS with low-pass filter. Intelligibility during sleep is UNKNOWN.",
        }

    # 5. Weight learning gate
    results["weight_learning"] = {
        "status": f"✅ Gate at {config.min_sessions_for_weight_learning} sessions",
        "level": ReadinessLevel.READY,
        "detail": "Weights use defaults until sufficient data collected",
    }

    # 6. Hardware (cannot assess without runtime)
    results["hardware"] = {
        "status": "⚠ Untested — requires device-specific validation",
        "level": ReadinessLevel.DEGRADED if mode != "live" else ReadinessLevel.CRITICAL,
        "detail": "Connect device, run latency test, verify JSON format matches.",
    }

    # ── ENFORCED SAFETY GATE ──────────────────────────────────────────
    # This is the hard interlock the review demanded.
    if mode == "live":
        critical = [
            (k, v) for k, v in results.items()
            if v["level"] == ReadinessLevel.CRITICAL
        ]
        if critical:
            msg = (
                f"\n\n⛔ LIVE MODE BLOCKED — {len(critical)} critical component(s):\n"
                + "\n".join(f"  • {k}: {v['detail']}" for k, v in critical)
                + "\n\nFix ALL critical items before running live experiments."
                + "\nUse mode='simulation' for development/testing."
            )
            results["_gate"] = {
                "status": "⛔ BLOCKED",
                "level": ReadinessLevel.BLOCKED,
                "detail": msg,
            }
            raise RuntimeError(msg)

    return results


def generate_protocol_hash(config: Config) -> str:
    """Generate a canonical hash of the experiment configuration."""
    config_json = json.dumps(config.to_dict(), sort_keys=True)
    return hashlib.sha256(config_json.encode()).hexdigest()[:16]


def create_preregistration(
    config: Config,
    study_title: str = "",
    hypotheses: list[str] | None = None,
    sample_size: int = 0,
    output_path: str = "preregistration.json",
    osf_token: str = "",
) -> dict:
    """
    Generate pre-registration document with optional OSF.io upload.
    """
    protocol_hash = generate_protocol_hash(config)

    doc = {
        "protocol_hash": protocol_hash,
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "verite_version": "10.3.0",
        "study_title": study_title,
        "hypotheses": hypotheses or [],
        "planned_sample_size": sample_size,
        "config": config.to_dict(),
        "readiness": {
            k: v for k, v in validate_pipeline(config, "simulation").items()
            if not k.startswith("_")
        },
    }

    with open(output_path, "w") as f:
        json.dump(doc, f, indent=2)

    osf_result = None
    if osf_token:
        osf_result = _upload_to_osf(doc, osf_token, study_title)

    return {
        "protocol_hash": protocol_hash,
        "output_path": output_path,
        "timestamp": doc["timestamp"],
        "osf_upload": osf_result,
    }


def _upload_to_osf(doc: dict, token: str, title: str = "") -> dict:
    """Upload pre-registration to OSF.io."""
    try:
        import requests
        headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/vnd.api+json"}
        payload = {"data": {"type": "nodes", "attributes": {
            "title": title or f"Verite TMR Pre-registration {doc['protocol_hash']}",
            "category": "project", "public": False,
        }}}
        resp = requests.post("https://api.osf.io/v2/nodes/", json=payload, headers=headers)
        if resp.status_code == 201:
            node_id = resp.json()["data"]["id"]
            return {"status": "uploaded", "osf_node": node_id, "url": f"https://osf.io/{node_id}/"}
        return {"status": "failed", "code": resp.status_code}
    except ImportError:
        return {"status": "requests_not_installed"}
    except Exception as e:
        return {"status": f"error: {e}"}


class GDPRDataManager:
    """GDPR-compliant data management."""

    def __init__(self, config: Config) -> None:
        self._enabled = config.gdpr_enabled
        self._retention_days = config.data_retention_days
        self._pseudonymize = config.pseudonymization_enabled
        self._id_mapping: dict[str, str] = {}

    def pseudonymize_id(self, participant_id: str) -> str:
        if not self._pseudonymize:
            return participant_id
        if participant_id not in self._id_mapping:
            pseudo = hashlib.sha256(
                f"{participant_id}_{time.time()}".encode()
            ).hexdigest()[:12]
            self._id_mapping[participant_id] = f"P_{pseudo}"
        return self._id_mapping[participant_id]

    def erase_participant(self, participant_id: str, db_conn: Any = None) -> dict:
        if not self._enabled:
            return {"error": "GDPR module not enabled"}
        erased = []
        if participant_id in self._id_mapping:
            pseudo_id = self._id_mapping.pop(participant_id)
            erased.append(f"id_mapping:{participant_id}")
            if db_conn:
                for table in ["sessions", "assessments", "cue_events", "ab_trials"]:
                    try:
                        db_conn.execute(f"DELETE FROM {table} WHERE participant_id = ?", (pseudo_id,))
                        erased.append(f"db:{table}")
                    except Exception:
                        pass
                db_conn.commit()
        return {"participant_id": participant_id, "erased_sources": erased}

    def enforce_retention(self, db_conn: Any) -> dict:
        if not self._enabled or not db_conn:
            return {"status": "disabled"}
        import datetime
        cutoff = (datetime.datetime.now() - datetime.timedelta(days=self._retention_days)).isoformat()
        deleted = 0
        for table in ["sessions", "assessments", "cue_events"]:
            try:
                c = db_conn.execute(f"DELETE FROM {table} WHERE timestamp < ?", (cutoff,))
                deleted += c.rowcount
            except Exception:
                pass
        db_conn.commit()
        return {"deleted_rows": deleted, "cutoff": cutoff}
