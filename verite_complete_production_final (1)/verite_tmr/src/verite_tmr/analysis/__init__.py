"""
Analytics — Clinical dashboard, Bayesian A/B, longitudinal tracking.
Restored from v9 Cells 11, 20, 20a.
"""
from __future__ import annotations
import sqlite3, json, time, math, warnings
from pathlib import Path
from typing import Any
import numpy as np

class LongitudinalTracker:
    """SQLite-backed longitudinal memory tracking with SM-2 and Ebbinghaus."""
    def __init__(self, db_path: str = "", encrypted: bool = False):
        self._db_path = db_path or "/tmp/verite_tmr.db"
        self._conn = sqlite3.connect(self._db_path, check_same_thread=False)
        self._init_schema()

    def _init_schema(self):
        c = self._conn
        c.execute("""CREATE TABLE IF NOT EXISTS sessions (
            id TEXT PRIMARY KEY, timestamp TEXT, doc_path TEXT,
            n_concepts INT, n_cues INT, n_spindle_coupled INT, notes TEXT)""")
        c.execute("""CREATE TABLE IF NOT EXISTS concepts (
            id TEXT PRIMARY KEY, name TEXT, definition TEXT,
            easiness REAL DEFAULT 2.5, interval_days REAL DEFAULT 1,
            repetitions INT DEFAULT 0, next_review TEXT)""")
        c.execute("""CREATE TABLE IF NOT EXISTS assessments (
            id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT,
            concept_id TEXT, phase TEXT, correct INT, rt_s REAL,
            confidence INT, strength REAL, timestamp TEXT)""")
        c.execute("""CREATE TABLE IF NOT EXISTS cue_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT,
            concept_id TEXT, cue_type TEXT, so_phase REAL,
            spindle_prob REAL, arousal_risk REAL, spindle_coupled INT,
            timestamp TEXT)""")
        c.execute("""CREATE TABLE IF NOT EXISTS ab_trials (
            id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT,
            participant_id TEXT, group_label TEXT, outcome REAL, timestamp TEXT)""")
        c.commit()

    def log_session(self, session_id, doc_path="", n_concepts=0, n_cues=0,
                    n_coupled=0, notes=""):
        self._conn.execute("INSERT OR REPLACE INTO sessions VALUES (?,?,?,?,?,?,?)",
            (session_id, time.strftime("%Y-%m-%dT%H:%M:%S"), doc_path,
             n_concepts, n_cues, n_coupled, notes))
        self._conn.commit()

    def log_cue_event(self, session_id, evt):
        self._conn.execute(
            "INSERT INTO cue_events (session_id,concept_id,cue_type,so_phase,"
            "spindle_prob,arousal_risk,spindle_coupled,timestamp) VALUES (?,?,?,?,?,?,?,?)",
            (session_id, evt.concept_id, evt.cue_type, evt.so_phase_at_delivery,
             evt.spindle_prob_at_delivery, evt.arousal_at_delivery,
             int(evt.spindle_coupled), time.strftime("%Y-%m-%dT%H:%M:%S")))

    def log_assessment(self, session_id, concept_id, phase, correct, rt, conf, strength):
        self._conn.execute(
            "INSERT INTO assessments (session_id,concept_id,phase,correct,rt_s,"
            "confidence,strength,timestamp) VALUES (?,?,?,?,?,?,?,?)",
            (session_id, concept_id, phase, correct, rt, conf, strength,
             time.strftime("%Y-%m-%dT%H:%M:%S")))
        self._conn.commit()

    def sm2_update(self, concept_id, quality):
        """SM-2 spaced repetition update. quality: 0-5."""
        row = self._conn.execute(
            "SELECT easiness, interval_days, repetitions FROM concepts WHERE id=?",
            (concept_id,)).fetchone()
        if not row: return
        e, i, r = row
        if quality >= 3:
            if r == 0: i = 1
            elif r == 1: i = 6
            else: i = i * e
            r += 1
        else:
            r = 0; i = 1
        e = max(1.3, e + 0.1 - (5-quality)*(0.08+(5-quality)*0.02))
        import datetime
        next_rev = (datetime.datetime.now() + datetime.timedelta(days=i)).isoformat()
        self._conn.execute("UPDATE concepts SET easiness=?, interval_days=?, repetitions=?, next_review=? WHERE id=?",
                          (e, i, r, next_rev, concept_id))
        self._conn.commit()

    def ebbinghaus_retention(self, strength, hours_elapsed):
        """R(t) = exp(-t/S) forgetting curve."""
        s = max(strength * 24, 1.0)
        return math.exp(-hours_elapsed / s)

    def get_session_history(self, limit=20):
        return self._conn.execute(
            "SELECT * FROM sessions ORDER BY timestamp DESC LIMIT ?", (limit,)).fetchall()

    def get_cue_stats(self, session_id):
        rows = self._conn.execute(
            "SELECT cue_type, COUNT(*), AVG(spindle_coupled) FROM cue_events WHERE session_id=? GROUP BY cue_type",
            (session_id,)).fetchall()
        return [{"type":r[0],"count":r[1],"coupling_rate":round(r[2],3)} for r in rows]

    def close(self):
        self._conn.close()


class BayesianAB:
    """Bayesian A/B testing with Beta-Bernoulli model."""
    def __init__(self):
        self._groups: dict[str, dict] = {}

    def add_observation(self, group: str, success: bool):
        if group not in self._groups:
            self._groups[group] = {"alpha": 1.0, "beta": 1.0}
        if success: self._groups[group]["alpha"] += 1
        else: self._groups[group]["beta"] += 1

    def prob_b_greater_a(self, group_a="control", group_b="active", n_samples=10000):
        if group_a not in self._groups or group_b not in self._groups:
            return 0.5
        a = self._groups[group_a]; b = self._groups[group_b]
        sa = np.random.beta(a["alpha"], a["beta"], n_samples)
        sb = np.random.beta(b["alpha"], b["beta"], n_samples)
        return float(np.mean(sb > sa))

    def summary(self):
        return {g: {"mean": round(d["alpha"]/(d["alpha"]+d["beta"]),3),
                     "n": int(d["alpha"]+d["beta"]-2)} for g, d in self._groups.items()}


class AnalyticsDashboard:
    """Generate session analytics report."""
    @staticmethod
    def generate_report(session_stats: dict, gate_summary: dict,
                       assessor_profile: dict) -> dict:
        return {
            "session": session_stats,
            "gate": gate_summary,
            "memory": assessor_profile,
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S"),
        }

    @staticmethod
    def generate_html(report: dict) -> str:
        s = report.get("session", {})
        return f"""<html><body><h1>Verite TMR Session Report</h1>
<p>Cues delivered: {s.get('total_cues_delivered',0)}</p>
<p>Spindle coupled: {s.get('spindle_coupled_pct',0)}%</p>
<p>Policy: {s.get('policy_mode','heuristic')}</p>
</body></html>"""

__all__ = ["LongitudinalTracker", "BayesianAB", "AnalyticsDashboard"]
