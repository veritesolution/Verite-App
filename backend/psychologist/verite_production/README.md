# Verite v2.0 — AI Mental Health Support API

Production-ready REST API + WebSocket backend for Kotlin/Android mobile clients.

## What This Is

A FastAPI backend that provides AI-powered mental health support conversations using CBT, DBT, and Motivational Interviewing principles. It is designed to connect to a Kotlin Android mobile app.

**This is NOT a medical device. NOT a substitute for professional mental health care.**

## Architecture

```
┌─────────────────────┐     REST / WebSocket      ┌──────────────────────────────┐
│   Kotlin Android    │ ◄──────────────────────► │     FastAPI Backend         │
│   Mobile App        │    JWT Auth               │                              │
│                     │                            │  ┌─────────┐ ┌───────────┐  │
│  • ChatViewModel    │                            │  │ Safety  │ │ LLM Router│  │
│  • Repository       │                            │  │ Module  │ │ (Groq/    │  │
│  • TokenManager     │                            │  │         │ │  Gemini/  │  │
│  • WebSocket Client │                            │  │ Crisis  │ │  OpenAI/  │  │
└─────────────────────┘                            │  │ Detect  │ │  Claude)  │  │
                                                   │  └─────────┘ └───────────┘  │
                                                   │  ┌─────────┐ ┌───────────┐  │
                                                   │  │   RAG   │ │  Session  │  │
                                                   │  │  FAISS  │ │  Manager  │  │
                                                   │  └─────────┘ └───────────┘  │
                                                   └──────────────────────────────┘
```

## Quick Start

### 1. Clone and configure

```bash
cp .env.example .env
# Edit .env — set SECRET_KEY, JWT_SECRET, and at least one LLM API key
# Generate secrets: python -c "import secrets; print(secrets.token_hex(32))"
```

### 2. Run with Docker

```bash
docker compose up --build
```

### 3. Run without Docker

```bash
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 4. Verify

```bash
curl http://localhost:8000/api/v1/health
# Open http://localhost:8000/docs for Swagger UI
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/v1/auth/register | Create account |
| POST | /api/v1/auth/login | Get JWT tokens |
| POST | /api/v1/auth/refresh | Refresh access token |
| POST | /api/v1/chat/message | Send message, get response |
| POST | /api/v1/chat/session | Create new session |
| GET | /api/v1/chat/sessions | List all sessions |
| GET | /api/v1/chat/session/{id} | Get session details |
| DELETE | /api/v1/chat/session/{id} | Delete session |
| POST | /api/v1/feedback | Submit turn feedback |
| GET | /api/v1/health | Health check |
| WS | /api/v1/ws/chat | WebSocket real-time chat |

## Kotlin Integration

Complete Android/Kotlin code is in `docs/kotlin/`. Copy these files into your project:

| File | Purpose |
|------|---------|
| VeriteModels.kt | Data classes matching API schemas |
| VeriteApiService.kt | Retrofit interface |
| NetworkModule.kt | HTTP client with JWT auth interceptor |
| VeriteRepository.kt | Repository pattern for data access |
| VeriteWebSocket.kt | Real-time WebSocket client |
| ChatViewModel.kt | ViewModel with UI state management |

### Android Gradle dependencies

```groovy
// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// JSON
implementation("com.google.code.gson:gson:2.10.1")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

// Lifecycle
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

// Secure storage
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

### Initialize in Application class

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
    }
}
```

## Testing

```bash
pytest tests/ -v --tb=short
```

## What Changed From v1

| Issue | v1 (Notebook) | v2 (This) |
|-------|---------------|-----------|
| Hardcoded API keys | ✗ Keys in notebook | ✓ Environment variables |
| Architecture | Monolithic notebook | Modular FastAPI |
| Authentication | None | JWT with refresh tokens |
| Session management | Global singleton | Per-user async sessions |
| Crisis detection | 14 regex patterns | 30+ regex + 25 semantic + behavioral |
| Crisis lock | Permanent (no escape) | De-escalation after 3 safe turns |
| Thread safety | Broken lru_cache + lists | Async locks + stateless engine |
| LLM providers | Sync, single provider | Async, fallback chain with retry |
| Prompt injection | 6 naive patterns | 15+ patterns + structural detection |
| JSON parsing | 3 strategies | 5 strategies + regex extraction |
| Tests | 25 smoke tests | 60+ including adversarial |
| Mobile ready | Gradio only | REST + WebSocket APIs |
| Rate limiting | None | Configurable per-minute/hour |
| Monitoring | None | Health endpoint + structured logging |

## Disclaimer

This software is provided for educational and research purposes only. It is NOT a medical device, NOT clinically validated, and NOT a substitute for professional mental health care. If you or someone you know is in crisis, contact a local emergency service or crisis hotline immediately.
