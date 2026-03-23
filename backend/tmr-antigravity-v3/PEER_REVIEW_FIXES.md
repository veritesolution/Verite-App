# Peer Review Response — TMR-ANTIGRAVITY v2.0.1

Date: 2026-03-23  
Review source: independent neuroscience + engineering peer review  
Status: all nine technical findings addressed; scientific premise note acknowledged

---

## Finding 1 — YASA called on isolated 30-second windows (accuracy bug)

**Reviewer finding:** `YASAStager` and `SpindleDetector` called YASA algorithms
on every sample on a sliding 4–30 second window, creating a fresh `MNE RawArray`
each time. YASA's `SleepStaging` is designed for overnight recordings where
temporal context across epochs drives accuracy. Running it on a single epoch
destroys that context dependency and will not achieve the published accuracy figures.

**Fix — `services/eeg/sleep_stager.py`:**

- `YASAStager` is now epoch-scheduled, not per-sample.
- `get_stage()` re-runs the classifier at most once every `RESCORE_SECONDS` (5 s).
  Between rescores it returns a cached result in O(1).
- When YASA runs, it is fed all buffered epochs (up to `MIN_EPOCHS_FOR_YASA = 5`
  complete 30-second epochs) so it has the temporal context it needs.
- A single `MNE RawArray` is constructed per rescore, not per sample.

**Fix — `services/eeg/signal_processing.py` (`SpindleDetector`):**

- YASA's `detect_spindles` now runs at most once every `YASA_SPINDLE_RUN_SECONDS`
  (4 s) rather than on every sample.
- Between YASA runs, the cached probability decays at `SPINDLE_DECAY_PER_SECOND`
  (0.4 per second) and is blended with the live RMS-ratio signal.

**Accuracy disclaimer added:** README now states that published YASA accuracy
figures require overnight recordings with full temporal context. Single-epoch
accuracy will be lower; the 85% staging target applies only when adequate
buffering and temporal context are available.

---

## Finding 2 — AR extrapolation runs `lstsq` per sample (performance bug)

**Reviewer finding:** `PhaseEstimator.get_phase()` called `_ar_extrapolate()`
which ran a full OLS regression (`lstsq` on a 512×16 matrix) on every EEG sample.
At 256 Hz this is 256 matrix solves per second. This will consume significant CPU
and almost certainly violates the <100 ms cue delivery target.

**Fix — `services/eeg/signal_processing.py` (`PhaseEstimator`):**

- `_refit_ar()` refits AR coefficients via `lstsq` at most once per
  `AR_REFIT_SECONDS` (0.5 s). The fitted `np.ndarray` is cached on `self._ar_coeffs`.
- `_extend_with_ar()` uses the cached coefficients with a simple `np.dot` loop —
  no matrix solve on the hot path.
- A `threading.Lock` guards the cached coefficients for thread safety.
- The per-call work is now: `sosfilt` → cached AR extend (O(N)) → `hilbert` → `angle`,
  comfortably < 2 ms at 256 Hz.

---

## Finding 3 — Fallback sleep stager defaults to N2 (safety bug)

**Reviewer finding:** `_bandpower_heuristic()` ended with
`return "N2"  # default to N2 in ambiguous cases`. N2 is the stage that allows
TMR cue delivery. Defaulting ambiguous signals to the permissive stage means
cues may be delivered during wakefulness or ambiguous signal epochs.

**Fix — `services/eeg/sleep_stager.py`:**

- `_bandpower_heuristic()` now returns `"unknown"` for all ambiguous cases.
- The branch order is revised so each stage is only returned for unambiguous
  spectral patterns. Every branch that previously would have fallen through to
  `"N2"` now returns `"unknown"`.
- `YASAStager._yasa_score()` also returns `"unknown"` on any error, so the
  fallback path to `_bandpower_heuristic()` then also returns `"unknown"`.
- The TMR hot loop in `tmr_worker.py` already rejects any stage not in
  `("N2", "N3")`, so `"unknown"` is always safe.

**Test added:** `TestSleepStager.test_ambiguous_signal_returns_unknown_not_n2`
fills the buffer with a flat low-amplitude signal that has no dominant frequency
band and asserts the returned stage is not `"N2"`.

---

## Finding 4 — Phase convention inverted (science bug)

**Reviewer finding:** The docstring said `0 = SO trough, ±π = SO peak`. The
standard electrophysiology convention for `scipy.signal.hilbert` is the opposite:
trough = ±π, peak = 0. The up-state window `0.75π–1.25π` was therefore targeting
the down-state/trough region, not the up-state.

**Fix — `services/eeg/signal_processing.py` (`PhaseEstimator`):**

- Docstring corrected to state the standard Hilbert convention:
  `phase = 0 → SO positive peak (up-state)`, `phase = ±π → SO negative trough`.
- `is_in_upstate()` now checks `|phase_rad| < half_window_rad` (default `π/4`),
  which correctly targets ±45° around the positive peak (phase=0).
- The old `0.75π–1.25π` normalised window is removed.
- `get_phase_normalized()` helper removed (was normalised to the wrong convention).

**Test added:** `TestPhaseEstimator.test_phase_convention_positive_peak_near_zero`
fills the buffer with a cosine (which starts at its positive peak) and verifies
the returned phase is within ±π/2 of 0, directly testing the convention fix.

---

## Finding 5 — Wrong electrode used for Muse (science bug)

**Reviewer finding:** `eeg_uv = float(sample.channels[0])` hard-coded channel 0.
On a Muse headband, channel 0 is TP9 — a temporal/occipital electrode. Slow
oscillations and sleep spindles are frontal-dominant phenomena. Muse frontal
channels are AF7 (index 1) and AF8 (index 2).

**Fix — `services/workers/tmr_worker.py`:**

- Added `_FRONTAL_CHANNEL` mapping: `{"muse": 1, "openbci": 0, "synthetic": 0}`.
- `ch_idx = _FRONTAL_CHANNEL.get(hardware, 0)` is resolved once at session
  startup and used for all sample reads.
- The selected channel name is logged at session start so the operator can verify.

**Note:** OpenBCI channel 0 is typically F3 or F4 on a standard 10-20 montage
with the Cyton board. If a different montage is used, `EEG_BEST_CHANNEL_INDEX`
should be set in the environment to the correct frontal channel index.

---

## Finding 6 — `asyncio.run()` on every DB write in the hot loop (performance bug)

**Reviewer finding:** `asyncio.run(_log_event(...))` was called inside the
per-sample EEG loop on every spindle detection event. Each `asyncio.run()` creates
a new event loop, opens a DB connection, commits, and tears down. At 5–50 ms per
call, this directly violates the <100 ms end-to-end cue delivery latency target.

**Fix — `services/workers/tmr_worker.py`:**

- Added `_AsyncDBWriter(threading.Thread)` daemon thread that owns a private
  `asyncio.run()` event loop.
- Events are pushed to `queue.Queue(maxsize=500)` via a non-blocking `put_nowait()`.
  If the queue is full (writer fell behind), the event is dropped with a warning
  rather than blocking the EEG path.
- The hot loop contains zero I/O, zero `asyncio.run()` calls, and zero network
  operations. All DB writes are off-path.
- `asyncio.run()` is retained for session setup/teardown operations
  (`_update_session_status`, `_fetch_cue_audio`) that run once per session, not
  per sample.

---

## Finding 7 — Nested event loop crash in `plan_generator.py` (correctness bug)

**Reviewer finding:** Inside `async def _run()` (called via `asyncio.run(_run())`),
the code called `loop = asyncio.get_event_loop(); loop.run_until_complete(...)`.
Calling `run_until_complete()` from inside a running event loop raises
`RuntimeError: This event loop is already running`. The content moderation step
always failed silently, meaning all LLM outputs were effectively unmoderated.

**Fix — `services/workers/plan_generator.py`:**

```python
# Before (raises RuntimeError — content moderation silently disabled):
loop = asyncio.get_event_loop()
is_safe, _ = loop.run_until_complete(_moderator.is_safe(plan_text))

# After (correct — we are already inside async def _run()):
is_safe, _ = await _moderator.is_safe(plan_text)
```

---

## Finding 8 — Synchronous blocking escalation (safety + UX bug)

**Reviewer finding:** `CrisisGate._escalate()` used `httpx.Client` (synchronous)
with a 3-second timeout. The docstring said "Never blocks the response to the user"
— this was false. Every crisis detection stalled the request for up to 3 seconds.

**Fix — `services/safety/crisis_gate.py`:**

```python
# Before (blocks for up to 3 s):
def _escalate(self, text, level):
    with httpx.Client(timeout=3.0) as client:
        client.post(webhook, ...)

# After (fires in background thread, returns immediately):
threading.Thread(
    target=self._escalate,
    args=(level, user_id),
    daemon=True,
    name="crisis-escalation",
).start()
```

---

## Finding 9 — Escalation webhook contains no identifying information (safety bug)

**Reviewer finding:** The webhook payload included the alert level and timestamp
but explicitly omitted user identity. The human reviewer who receives the webhook
has no way to identify which user is in crisis or contact them. The escalation
system was structurally incapable of actually helping anyone.

**Fix — `services/safety/crisis_gate.py`:**

- `check()` now accepts an optional `user_id: str` parameter.
- The `user_id` is included in the webhook payload.
- Raw input text is still NOT transmitted (privacy preservation).
- All callers (`plans.py`, `users.py`, `safety.py`) pass `user_id=str(user.id)`
  to `gate.check()`.

**Payload before:**
```json
{"event": "crisis_detected", "level": "immediate_danger", "timestamp": 1234567890}
```

**Payload after:**
```json
{
  "event": "crisis_detected",
  "level": "immediate_danger",
  "timestamp": 1234567890,
  "user_id": "3f2a1b4c-...",
  "action_required": "Immediate human review required. Locate user in the admin dashboard and initiate contact."
}
```

---

## Safety accuracy claims — corrected

**Reviewer finding:** README claimed "Crisis gate false-negative rate: 0%". No
keyword-based regex system achieves 0% false negatives. Also: "hopeless" as a
standalone word triggered too many false positives on habit-change context.
ContentModerator used CrisisGate as a fallback for LLM output, which provides
no protection (LLM plans do not contain phrases like "kill myself").

**Fixes:**

1. `README.md`: Removed the "0% false-negative rate" performance target. Replaced
   with an honest description of the gate as a first-pass keyword filter with
   documented limitations and required additional layers for clinical deployment.

2. `crisis_gate.py` (`_HIGH_RISK`): Removed `"hopeless"` as a standalone trigger.
   Replaced with contextual phrases: `"feel completely hopeless about living"`,
   `"no hope left for my life"`.

3. `crisis_gate.py` (`ContentModerator._structural_check`): Fallback now calls
   `PlanSafetyGuard().validate()` (structural plan validation) instead of
   `CrisisGate.check()`. This correctly catches plans with missing disclaimers,
   wrong structure, or dangerous medical advice keywords — not suicidal language.

---

## Scientific premise — noted, not changed

The reviewer correctly noted that TMR evidence is strongest for declarative memory
consolidation (hippocampal replay), and that the literature on habit change via
TMR (which involves procedural/implicit memory in basal ganglia circuits) is
mechanistically distinct and sparse. The system's disclaimer
("TMR efficacy for habit change has not been established in controlled clinical
trials") is preserved and the word "evidence-based" in the TMR component is not
applied. This is a research question the clinical team must address through the
Phase 4 RCT, not a code fix.
