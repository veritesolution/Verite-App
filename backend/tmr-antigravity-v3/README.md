# TMR-ANTIGRAVITY v3.0.0

**Production-ready AI habit change platform with optional Targeted Memory Reactivation (TMR) sleep protocol.**

> ⚠ **Disclaimer:** This system is a research and wellness tool. It is not a medical device, does not diagnose or treat any condition, and is not a substitute for professional clinical care. TMR efficacy for habit change has not been established in controlled clinical trials. All TMR features are for research purposes only.

---

## What's new in v3.0.0

- **Arousal risk gate now enforced** — Cue delivery is suppressed when real-time arousal probability exceeds 25%, preventing sleeper wake-ups. This was previously documented but unimplemented.
- **Async DB writer fully non-blocking** — Uses `asyncio.Queue` with batched transactions instead of blocking `queue.Queue.get()`.
- **AR coefficient lock consistency** — Both read and write sides of the phase estimator's AR coefficients are now properly locked.
- **API hardening** — Deep health checks, request ID tracing, rate limiting middleware.
- **All 9 original + 3 follow-up peer review findings resolved.**

---

## What this system does

1. **AI Habit Plan Generator** — Collects a structured intake (text or voice), calls an LLM (Groq, Gemini, or OpenAI with automatic fallback), and produces a personalised 21-day evidence-based intervention plan using CBT, ACT, habit loop disruption, and motivational interviewing techniques.

2. **Voice Interface** — Google Cloud STT/TTS (with offline Whisper/pyttsx3 fallback) guides users through intake questions and reads the plan aloud. Hume AI optionally extracts emotional cues from voice prosody.

3. **TMR Protocol** — During a sleep session, real EEG from a Muse or OpenBCI device is processed in real time. When slow-oscillation up-states and sleep spindles co-occur (N2/N3 sleep), a soft audio cue — the habit's associated phrase — is played at <100 ms latency.

4. **Safety gate** — Every user input and every LLM output is checked by a tiered keyword safety system before any action proceeds. Crisis events trigger immediate human escalation.

---

## Quick start (development)

### Prerequisites
- Docker Desktop (or Docker + docker-compose)
- Python 3.11+
- At minimum one LLM API key: [Groq](https://console.groq.com) (free) or [Gemini](https://makersuite.google.com)

### 1. Clone and configure

```bash
git clone https://github.com/yourlab/tmr-antigravity.git
cd tmr-antigravity

# Generate required secrets
make generate-key

# Copy template and fill in your values
cp .env.template .env
# → Paste SECRET_KEY and FERNET_KEY from above
# → Add at least one LLM API key (GROQ_API_KEY or GEMINI_API_KEY)
```

### 2. Start the full stack

```bash
make dev
```

This starts:
| Service | URL |
|---|---|
| API (FastAPI + Swagger) | http://localhost:8000/docs |
| Celery Flower (task monitor) | http://localhost:5555 |
| Grafana (metrics) | http://localhost:3001 |
| Prometheus | http://localhost:9090 |

### 3. Run migrations

```bash
make migrate
```

### 4. Run tests

```bash
make test              # unit tests only (fast, no DB)
make test-safety       # crisis gate regression suite
make test-integration  # requires running stack
make test-coverage     # full coverage report
```

---

## API overview

### Authentication

```bash
# Register
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"SecurePass1!"}'

# Login
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"SecurePass1!"}'
# → { "access_token": "eyJ...", "token_type": "bearer" }
```

### Complete workflow

```bash
TOKEN="eyJ..."   # from login response

# 1. Give informed consent
curl -X POST http://localhost:8000/users/consent \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"consent_version":"1.0"}'

# 2. Create intake profile
curl -X POST http://localhost:8000/users/profiles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "age": 29,
    "profession": "software engineer",
    "ailment_description": "compulsive social media scrolling 50+ times per day",
    "duration_years": "3 years",
    "frequency": "50+ times per day",
    "intensity": 7,
    "origin_story": "Started during COVID lockdowns as a way to manage loneliness.",
    "primary_emotion": "anxiety and restlessness"
  }'
# → { "id": "profile-uuid", ... }

# 3. Generate 21-day plan (async)
curl -X POST http://localhost:8000/plans/ \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"profile_id":"profile-uuid"}'
# → { "id": "plan-uuid", "status": "queued" }

# 4. Poll for completion
curl http://localhost:8000/plans/plan-uuid \
  -H "Authorization: Bearer $TOKEN"
# → { "status": "ready", "plan_content": { ... }, "cue_audio_cdn_url": "..." }

# 5. Start a TMR session (uses synthetic EEG in dev)
curl -X POST http://localhost:8000/tmr/ \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"plan_id":"plan-uuid","hardware":"synthetic","max_cues":5}'
```

---

## EEG hardware setup

### Muse headband

1. Pair Muse via Bluetooth to your machine.
2. In a separate terminal: `muselsl stream --name Muse`
3. Set `EEG_HARDWARE=muse` in `.env`.
4. The EEG worker will auto-connect to the LSL stream.

### OpenBCI (Cyton or Cyton+Daisy)

1. Connect the USB dongle.
2. Note the serial port: `/dev/ttyUSB0` (Linux) or `/dev/tty.usbserial-*` (macOS).
3. Set `EEG_HARDWARE=openbci` and `EEG_SERIAL_PORT=/dev/ttyUSB0` in `.env`.
4. For the Docker EEG worker, uncomment the `devices:` section in `docker-compose.yml`.

> Impedance check: the system logs a warning if any channel exceeds 20 kΩ. Check electrode contact and scalp prep if this occurs.

---

## Architecture overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client layer                            │
│  React Native app   Web dashboard   Voice (STT/TTS)   EEG app  │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTPS / JWT
┌─────────────────────────▼───────────────────────────────────────┐
│              API Gateway (nginx + TLS + rate limit)             │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│                FastAPI backend (3+ pods)                        │
│  /auth  /users  /plans  /tmr  /voice  /safety  /metrics        │
└──────┬──────────────────┬──────────────────────────────────────┘
       │                  │
       ▼                  ▼
  PostgreSQL            Redis
  (encrypted)      (session cache,
                    Celery broker)
       │
       ├─── Celery: plans queue (LLM generation, S3 upload)
       └─── Celery: eeg queue (real-time TMR loop, hardware I/O)
                          │
                    EEG hardware
                  (Muse / OpenBCI)
```

---

## Project structure

```
tmr-antigravity/
├── services/
│   ├── api/              FastAPI app, routers, models, schemas
│   ├── workers/          Celery tasks (plan generation, TMR loop)
│   ├── eeg/              Hardware drivers, signal processing, audio
│   ├── ai/               LLM router, knowledge base, safety guard
│   ├── voice/            TTS/STT engines, emotion analysis
│   └── safety/           Crisis gate, content moderation
├── tests/
│   ├── unit/             All unit tests (no external deps)
│   └── integration/      Full HTTP stack tests (requires DB)
├── infra/
│   ├── docker/           Dockerfiles, docker-compose, Prometheus config
│   └── k8s/              Kubernetes manifests (production)
├── alembic/              Database migrations
├── requirements/         Python dependency files
├── .github/workflows/    CI/CD pipeline
├── .env.template         Configuration template
├── Makefile              Developer commands
└── pyproject.toml        Project config, pytest, ruff, coverage
```

---

## Safety and compliance

### Crisis detection
Every text input is checked against a tiered keyword system before any LLM call or database write. Immediate danger triggers human escalation via webhook within the same request cycle. This check is synchronous, always runs, and cannot be bypassed.

### Data protection
- All clinical fields (ailment, emotions, plan content) are encrypted at rest using Fernet (AES-128-CBC + HMAC-SHA256) before PostgreSQL storage.
- Database-level encryption at rest should also be enabled (AWS RDS with KMS, or equivalent).
- All traffic is TLS-only.
- Audit logs capture every meaningful action with timestamp, IP, and user agent for HIPAA compliance.
- GDPR right-to-erasure: `/users/me DELETE` zeroes all PII and encrypted content while preserving the audit trail.

### For clinical use
1. Obtain IRB approval before recruiting participants.
2. Implement HIPAA BAAs with AWS and Google Cloud.
3. Consult with a regulatory attorney about FDA Class II vs. wellness tool classification before any clinical claims.
4. The `/users/consent` endpoint captures informed consent with version tracking and IP logging.

---

## Performance targets

| Metric | Target |
|---|---|
| EEG sample → audio cue | < 100 ms |
| Plan generation p95 | < 8 s |
| API concurrent users | 10,000 |
| Sleep staging accuracy | > 85% per stage |
| Spindle detection AUC | > 0.90 (DREAMS dataset) |
| Crisis gate false-negative rate | Non-zero (keyword filter — see safety docs) |
| System uptime SLO | 99.5% |

---

## Roadmap

| Phase | Duration | Milestone |
|---|---|---|
| 1: Core backend + AI | Months 1–3 | API live, plans generating, voice working |
| 2: EEG integration | Months 4–7 | Real hardware, spindle detection, TMR loop |
| 3: Scalability | Months 8–10 | Kubernetes, load tested at 10k users |
| 4: Clinical pilot | Months 11–16 | IRB approved, n≥30 RCT, outcomes published |
| 5: Compliance | Ongoing | HIPAA, GDPR, FDA pre-submission |

---

## Contributing

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`.
3. Add tests for all new code (coverage gate: 80%).
4. Run `make lint test` before pushing.
5. Open a pull request — CI will run all checks automatically.

**Never commit `.env` files, API keys, or patient data to version control.**

---

## License

MIT License — see `LICENSE` file. Clinical and regulatory use requires additional legal review.

---

## Changelog

### v2.0.1 — peer review fixes (2026-03)

All nine issues raised in the independent peer review have been addressed:

| # | File | Fix |
|---|---|---|
| 1 | `sleep_stager.py` | YASA now runs on all buffered epochs (temporal context), not a single isolated window |
| 2 | `signal_processing.py` | AR coefficients cached; `lstsq` runs ≤ twice per second, not once per sample |
| 3 | `sleep_stager.py` | Ambiguous fallback stage changed from `"N2"` (permissive) to `"unknown"` (safe) |
| 4 | `signal_processing.py` | Phase convention corrected: `phase=0` → SO positive peak; window updated to `±π/4` |
| 5 | `tmr_worker.py` | Electrode selection is hardware-aware; Muse uses AF7 (index 1), not TP9 (index 0) |
| 6 | `tmr_worker.py` | DB writes moved off the hot loop into a daemon thread via `queue.Queue` |
| 7 | `plan_generator.py` | Replaced `loop.run_until_complete()` inside async context with `await` |
| 8 | `crisis_gate.py` | Escalation webhook fires in a daemon thread — no longer blocks the response |
| 9 | `crisis_gate.py` | Webhook payload includes `user_id` so on-call reviewer can locate the user |
| — | `crisis_gate.py`, `README.md` | Removed false "0% false-negative rate" claim; `hopeless` lone-word pattern removed; `ContentModerator` fallback fixed |

---

*Built for research. Designed for production. Grounded in science.*
