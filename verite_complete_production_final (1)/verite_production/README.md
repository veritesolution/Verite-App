# Vérité TMR v10.5.2 — Production Integration Guide (Final)

Complete integration of the verite_tmr Python package with a Kotlin Android app
via a FastAPI backend server.

## Production Improvements (v10.5.2 Final)

### Server Security Hardening
- Constant-time API key comparison (secrets.compare_digest) — prevents timing attacks
- Configurable CORS origins — no more allow_origins=["*"]
- In-memory rate limiting — 120 req/min per IP (configurable)
- File upload size limit — 50MB default, extension whitelist
- Config key whitelist — prevents injection of arbitrary attributes
- Startup validation — blocks production starts with default API keys
- Structured JSON error responses with request ID tracking

### Android Production Readiness
- ForegroundService with WakeLock — keeps app alive during 8-hour sleep sessions
- Network security config — HTTPS enforced, cleartext only for localhost/emulator
- POST_NOTIFICATIONS permission — required for Android 13+ foreground service
- Material3 theming with dark mode + Dynamic Color
- Server-synced elapsed timer — polls /session/status every 5s (no client drift)
- Efficient ArrayDeque ring buffer — O(1) tick history instead of O(n) list copy
- Service lifecycle management — proper bind/unbind with ViewModel

### Delivery Gate Logic
A cue is delivered only when ALL pass simultaneously:
1. Sleep stage: N2 or N3
2. SO phase: [135deg, 225deg] (Moelle 2002)
3. Spindle probability >= 0.15 (fatigue-adapted)
4. Arousal risk <= 0.25
5. No artefact
6. Min interval: 30s (Antony 2012)
7. (optional) PAC coupling MI >= 0.01 (Staresina 2015)
8. (optional) K-complex detected (Ngo 2013)

### Production Checklist
- [ ] Train SpindleCNN on DREAMS dataset
- [ ] Train ArousalPredictor on MESA dataset
- [ ] Set strong API_KEY (>=32 chars), ENVIRONMENT=production
- [ ] Configure CORS_ORIGINS, deploy behind HTTPS
- [ ] Test ForegroundService survives 8+ hours screen off
- [ ] Ethics board approval for human sleep study
