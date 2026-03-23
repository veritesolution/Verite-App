# ════════════════════════════════════════════════════════════════════════════
# TMR-ANTIGRAVITY — Developer Makefile
# Usage: make <target>
# ════════════════════════════════════════════════════════════════════════════

.PHONY: help install dev test test-unit test-integration test-coverage \
        up down build migrate lint format clean generate-key

PYTHON := python3
PIP    := pip3
DC     := docker-compose -f infra/docker/docker-compose.yml

help:
	@echo ""
	@echo "  TMR-ANTIGRAVITY v2.0 — Developer Commands"
	@echo ""
	@echo "  Setup:"
	@echo "    make install          Install all dependencies"
	@echo "    make generate-key     Generate FERNET_KEY and SECRET_KEY"
	@echo "    make migrate          Run Alembic database migrations"
	@echo ""
	@echo "  Development:"
	@echo "    make dev              Start full local stack (Docker)"
	@echo "    make up               docker-compose up -d"
	@echo "    make down             docker-compose down"
	@echo "    make logs             Follow API logs"
	@echo "    make api              Run API locally (no Docker)"
	@echo ""
	@echo "  Testing:"
	@echo "    make test             Run all unit tests"
	@echo "    make test-unit        Unit tests only (no DB required)"
	@echo "    make test-integration Run integration tests (requires DB)"
	@echo "    make test-coverage    Full coverage report"
	@echo "    make test-safety      Run crisis gate safety regression suite"
	@echo ""
	@echo "  Code quality:"
	@echo "    make lint             Run ruff linter"
	@echo "    make format           Auto-format with ruff"
	@echo ""
	@echo "  Deployment:"
	@echo "    make build            Build Docker images"
	@echo "    make push             Push images to registry"
	@echo "    make k8s-apply        Apply Kubernetes manifests"
	@echo ""

# ── Setup ─────────────────────────────────────────────────────────────────────

install:
	$(PIP) install -r requirements/api.txt
	$(PIP) install pytest pytest-asyncio pytest-cov httpx coverage

generate-key:
	@echo ""
	@echo "  SECRET_KEY (paste into .env):"
	@$(PYTHON) -c "import secrets; print('SECRET_KEY=' + secrets.token_hex(32))"
	@echo ""
	@echo "  FERNET_KEY (paste into .env):"
	@$(PYTHON) -c "from cryptography.fernet import Fernet; print('FERNET_KEY=' + Fernet.generate_key().decode())"
	@echo ""

migrate:
	alembic upgrade head

# ── Development ───────────────────────────────────────────────────────────────

dev: up
	@echo "  API:      http://localhost:8000/docs"
	@echo "  Flower:   http://localhost:5555"
	@echo "  Grafana:  http://localhost:3001"
	@echo "  Prometheus: http://localhost:9090"

up:
	$(DC) up -d

down:
	$(DC) down

logs:
	$(DC) logs -f api

api:
	uvicorn services.api.main:app --reload --port 8000

worker-plans:
	celery -A services.workers.celery_app:celery_app worker \
		--queues plans --concurrency 2 --loglevel info

worker-eeg:
	celery -A services.workers.celery_app:celery_app worker \
		--queues eeg --concurrency 1 --loglevel info

# ── Testing ───────────────────────────────────────────────────────────────────

test: test-unit

test-unit:
	$(PYTHON) -m pytest tests/unit/ -v --tb=short -m "not integration and not hardware"

test-integration:
	$(PYTHON) -m pytest tests/integration/ -v --tb=short -m integration

test-safety:
	$(PYTHON) -m pytest tests/unit/test_all.py::TestCrisisGate -v

test-coverage:
	$(PYTHON) -m pytest tests/unit/ \
		--cov=services \
		--cov-report=term-missing \
		--cov-report=html:htmlcov \
		--cov-fail-under=80
	@echo "\n  Coverage report: htmlcov/index.html"

# ── Code quality ──────────────────────────────────────────────────────────────

lint:
	ruff check services/ tests/

format:
	ruff format services/ tests/

# ── Build & Deploy ────────────────────────────────────────────────────────────

REGISTRY ?= your-registry.io
TAG      ?= 2.0.0

build:
	docker build -f infra/docker/Dockerfile.api -t $(REGISTRY)/tmr-api:$(TAG) .
	docker build -f infra/docker/Dockerfile.eeg -t $(REGISTRY)/tmr-eeg:$(TAG) .

push: build
	docker push $(REGISTRY)/tmr-api:$(TAG)
	docker push $(REGISTRY)/tmr-eeg:$(TAG)

k8s-apply:
	@# Substitute image tags before applying
	sed "s/YOUR_REGISTRY/$(REGISTRY)/g" infra/k8s/manifests.yaml | kubectl apply -f -

clean:
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null; true
	find . -type f -name "*.pyc" -delete 2>/dev/null; true
	rm -rf htmlcov .coverage .pytest_cache
