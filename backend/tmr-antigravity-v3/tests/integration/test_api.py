"""
Integration tests — full HTTP stack with test database.
Requires: PostgreSQL running locally (or use docker-compose.test.yml).
Run with: pytest tests/integration/ -v --integration
"""

import pytest
import asyncio
import uuid
from httpx import AsyncClient, ASGITransport

# Mark all tests in this file as integration tests
pytestmark = pytest.mark.integration


@pytest.fixture(scope="session")
def event_loop():
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()


@pytest.fixture(scope="session")
async def app():
    """Boot the FastAPI app with a test database."""
    import os
    os.environ.setdefault("DATABASE_URL", "postgresql+asyncpg://test:test@localhost/tmr_test")
    os.environ.setdefault("SECRET_KEY", "test-secret-key-minimum-32-characters-long!")
    os.environ.setdefault("ENVIRONMENT", "test")
    os.environ.setdefault("FERNET_KEY", "aBcDeFgHiJkLmNoPqRsTuVwXyZ012345=")

    from services.api.main import app
    from services.api.database import engine, Base
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield app
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest.fixture
async def client(app):
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        yield c


@pytest.fixture
async def auth_headers(client):
    """Register a user and return auth headers."""
    resp = await client.post("/auth/register", json={
        "email": f"test_{uuid.uuid4().hex[:8]}@example.com",
        "password": "TestPassword1!",
        "full_name": "Test User",
    })
    assert resp.status_code == 201, resp.text
    token = resp.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


# ── Health check ──────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_health_endpoint(client):
    resp = await client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


# ── Registration and login ────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_register_new_user(client):
    email = f"new_{uuid.uuid4().hex[:8]}@example.com"
    resp = await client.post("/auth/register", json={
        "email": email,
        "password": "SecurePass1!",
    })
    assert resp.status_code == 201
    data = resp.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"


@pytest.mark.asyncio
async def test_duplicate_registration_fails(client):
    email = f"dup_{uuid.uuid4().hex[:8]}@example.com"
    payload = {"email": email, "password": "SecurePass1!"}
    r1 = await client.post("/auth/register", json=payload)
    r2 = await client.post("/auth/register", json=payload)
    assert r1.status_code == 201
    assert r2.status_code == 409


@pytest.mark.asyncio
async def test_login_valid_credentials(client, auth_headers):
    # Extract email from /users/me
    me_resp = await client.get("/users/me", headers=auth_headers)
    email = me_resp.json()["email"]

    resp = await client.post("/auth/login", json={"email": email, "password": "TestPassword1!"})
    assert resp.status_code == 200
    assert "access_token" in resp.json()


@pytest.mark.asyncio
async def test_login_wrong_password_fails(client, auth_headers):
    me_resp = await client.get("/users/me", headers=auth_headers)
    email = me_resp.json()["email"]
    resp = await client.post("/auth/login", json={"email": email, "password": "WrongPass99!"})
    assert resp.status_code == 401


# ── Safety checks ─────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_safety_check_safe_input(client, auth_headers):
    resp = await client.post("/safety/check",
                             json={"text": "I want to stop scrolling Instagram"},
                             headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["is_safe"] is True
    assert resp.json()["blocked"] is False


@pytest.mark.asyncio
async def test_safety_check_crisis_input(client, auth_headers):
    resp = await client.post("/safety/check",
                             json={"text": "I want to kill myself"},
                             headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["is_safe"] is False
    assert data["blocked"] is True
    assert data["crisis_level"] == "immediate_danger"
    assert "US" in data["resources"]


# ── Consent ───────────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_consent_required_for_plans(client, auth_headers):
    # Without consent, plan creation should be forbidden
    resp = await client.post("/plans/", json={"profile_id": str(uuid.uuid4())},
                             headers=auth_headers)
    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_give_consent(client, auth_headers):
    resp = await client.post("/users/consent",
                             json={"consent_version": "1.0"},
                             headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["status"] == "given"


# ── Profile creation ──────────────────────────────────────────────────────────

@pytest.fixture
async def consented_headers(client, auth_headers):
    await client.post("/users/consent", json={"consent_version": "1.0"}, headers=auth_headers)
    return auth_headers


@pytest.mark.asyncio
async def test_create_profile_valid(client, consented_headers):
    resp = await client.post("/users/profiles", json={
        "age": 29,
        "profession": "software engineer",
        "ailment_description": "compulsive social media scrolling 50 times per day",
        "duration_years": "3 years",
        "frequency": "50 times per day",
        "intensity": 7,
        "origin_story": "Started during COVID lockdowns",
        "primary_emotion": "anxiety and restlessness",
    }, headers=consented_headers)
    assert resp.status_code == 201
    data = resp.json()
    assert "id" in data
    assert data["age"] == 29


@pytest.mark.asyncio
async def test_create_profile_crisis_input_blocked(client, consented_headers):
    resp = await client.post("/users/profiles", json={
        "age": 29,
        "profession": "teacher",
        "ailment_description": "I want to kill myself because of my addiction",
        "duration_years": "1 year",
        "frequency": "daily",
        "intensity": 9,
        "origin_story": "...",
        "primary_emotion": "despair",
    }, headers=consented_headers)
    assert resp.status_code == 422
    assert resp.json()["detail"]["type"] == "crisis_detected"


# ── Plan creation (mocked Celery) ─────────────────────────────────────────────

@pytest.mark.asyncio
async def test_create_plan_queued(client, consented_headers):
    # First create a profile
    profile_resp = await client.post("/users/profiles", json={
        "age": 30, "profession": "designer",
        "ailment_description": "excessive phone checking compulsion",
        "duration_years": "2 years", "frequency": "60x daily",
        "intensity": 6, "origin_story": "Work stress trigger",
        "primary_emotion": "anxiety",
    }, headers=consented_headers)
    profile_id = profile_resp.json()["id"]

    with pytest.MonkeyPatch.context() as mp:
        mock_task = MagicMock()
        mock_task.id = "test-task-uuid"
        mp.setattr(
            "services.workers.plan_generator.generate_plan.apply_async",
            lambda *a, **kw: mock_task,
        )
        resp = await client.post("/plans/", json={"profile_id": profile_id},
                                 headers=consented_headers)

    assert resp.status_code == 202
    data = resp.json()
    assert data["status"] == "queued"
    assert "id" in data


# ── GDPR erasure ──────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_gdpr_account_erasure(client, auth_headers):
    resp = await client.delete("/users/me", headers=auth_headers)
    assert resp.status_code == 204

    # Subsequent access should fail — account deactivated
    me_resp = await client.get("/users/me", headers=auth_headers)
    assert me_resp.status_code == 401
