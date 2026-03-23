# CHANGELOG — v3.0.0 (Production Release)

## Summary

All 9 original peer review findings remain fixed. All 3 new issues introduced
in v2.0.1 are now resolved. The phantom arousal risk feature is fully
implemented. Additional production hardening applied.

---

## Issues Fixed in v3.0.0

### 🔴 Critical: Arousal Risk Computation Implemented

**Was:** The README said "Stop if arousal risk > 25%." The TMREvent model had
an arousal_risk column. The API serialised it. But no code computed it — it was
always `None`. The safety constraint was never enforced.

**Now:** `ArousalRiskEstimator` in `signal_processing.py` computes real-time
arousal probability from three EEG-derived features:
1. Beta/alpha power ratio (cortical activation indicator)
2. EMG proxy via high-gamma RMS (muscle artifact surrogate)
3. Delta suppression (lightening sleep detector)

The TMR worker enforces `AROUSAL_RISK_MAX = 0.25` — cues are suppressed when
arousal risk exceeds 25%. Every cue delivery and suppression event now records
the arousal_risk value in the database.

### 🟡 Fix: Blocking queue.Queue.get() in async _drain()

**Was:** `_AsyncDBWriter._drain()` called `queue.Queue.get(timeout=1.0)` — a
synchronous blocking call inside an `async def`. This blocked the asyncio event
loop for up to 1 second per iteration, neutralising the async benefit.

**Now:** `_AsyncDBWriter` runs its own `asyncio.new_event_loop()` with an
`asyncio.Queue`. The hot EEG loop feeds events via `loop.call_soon_threadsafe`.
The drain coroutine uses `await asyncio.wait_for(queue.get(), ...)` — fully
non-blocking to the event loop.

### 🟡 Fix: AR Lock Read-Side Race

**Was:** `_refit_ar()` held `self._ar_lock` when writing `self._ar_coeffs`,
but `_extend_with_ar()` read it without the lock. Under CPython's GIL this
couldn't crash, but the lock provided false reassurance.

**Now:** `_extend_with_ar()` acquires `_ar_lock` for a brief snapshot-copy of
the coefficients, then releases it before the extrapolation loop. Both read
and write sides are now consistently locked.

### 🟡 Fix: New DB Connection Per Event

**Was:** Each `_write()` call did `async with AsyncSessionLocal()` → add one
row → commit → close, creating and destroying a connection per event.

**Now:** Events are batched: `_drain()` collects up to 50 events per cycle
and writes them in a single transaction. Under typical conditions (1-3
events/min), this means one connection per batch, not per event.

---

## Additional Enhancements in v3.0.0

### API Hardening
- **Deep health check** (`/health`): Probes database connectivity and returns
  503 if unreachable. Kubernetes readiness probe can target this endpoint.
- **Liveness probe** (`/health/live`): Lightweight check with no external deps.
- **Request ID middleware**: Every response includes `X-Request-ID` for
  distributed tracing.
- **Rate limiting middleware**: Sliding-window per-IP limiter with configurable
  RPM and burst. Returns 429 with `Retry-After` header.
- **Graceful shutdown**: Engine is disposed on lifespan exit.

### Signal Processing
- `ArtifactRejector.artifact_rate` property for real-time quality monitoring.
- Session metadata now records artifact_rate, cues_suppressed_arousal count.

### Voice Router
- Fixed broken import: `EmotionAnalyzer` was imported from a non-existent
  `emotion_analyzer` module; it lives in `stt_engine.py`.

### Configuration
- `TMR_AROUSAL_RISK_MAX` exposed in settings (default 0.25).
- `RATE_LIMIT_REQUESTS_PER_MINUTE` and `RATE_LIMIT_BURST` configurable.
- Version bumped to 3.0.0.

### Test Coverage
- New test class: `TestArousalRiskEstimator` (4 tests)
- New test class: `TestAsyncDBWriter` (2 tests)
- New test: AR lock read-side concurrent stress test
- New test class: `TestPlanSafetyGuard` (4 tests)
- New test class: `TestEncryption` (3 tests)
- Artifact rate tracking test

---

## Verification Checklist

| Finding | Status |
|---|---|
| YASA per-sample | ✅ Fixed (v2.0.1) |
| AR lstsq per-sample | ✅ Fixed (v2.0.1) |
| N2 default safety bug | ✅ Fixed (v2.0.1) |
| Phase convention inverted | ✅ Fixed (v2.0.1) |
| Wrong Muse electrode | ✅ Fixed (v2.0.1) |
| asyncio.run() in hot loop | ✅ Fixed (v2.0.1) |
| Nested event loop crash | ✅ Fixed (v2.0.1) |
| Synchronous escalation | ✅ Fixed (v2.0.1) |
| Escalation missing user_id | ✅ Fixed (v2.0.1) |
| Blocking queue.get in async | ✅ Fixed (v3.0.0) |
| AR lock read-side race | ✅ Fixed (v3.0.0) |
| New DB connection per event | ✅ Fixed (v3.0.0) |
| Arousal risk unimplemented | ✅ Implemented (v3.0.0) |
