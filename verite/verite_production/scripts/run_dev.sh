#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# Verite API — Development Run Script
# Usage: bash scripts/run_dev.sh
# ═══════════════════════════════════════════════════════════════

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# Generate .env from example if not exists
if [ ! -f .env ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env

    # Auto-generate secrets for dev
    SECRET=$(python3 -c "import secrets; print(secrets.token_hex(32))")
    JWT_SECRET=$(python3 -c "import secrets; print(secrets.token_hex(32))")

    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s/CHANGE_ME_TO_A_RANDOM_64_CHAR_STRING/$SECRET/" .env
        sed -i '' "s/CHANGE_ME_TO_A_DIFFERENT_RANDOM_STRING/$JWT_SECRET/" .env
        sed -i '' "s/APP_ENV=production/APP_ENV=development/" .env
        sed -i '' "s/APP_DEBUG=false/APP_DEBUG=true/" .env
    else
        sed -i "s/CHANGE_ME_TO_A_RANDOM_64_CHAR_STRING/$SECRET/" .env
        sed -i "s/CHANGE_ME_TO_A_DIFFERENT_RANDOM_STRING/$JWT_SECRET/" .env
        sed -i "s/APP_ENV=production/APP_ENV=development/" .env
        sed -i "s/APP_DEBUG=false/APP_DEBUG=true/" .env
    fi

    echo "Generated .env with development settings"
    echo ""
    echo "*** IMPORTANT: Edit .env and add at least one LLM API key ***"
    echo "  GROQ_API_KEY=     (recommended, free: https://console.groq.com/keys)"
    echo "  GEMINI_API_KEY=   (free: https://aistudio.google.com/apikey)"
    echo ""
fi

# Create data directories
mkdir -p data/{sessions,feedback,faiss_cache,logs}

echo "Starting Verite API in development mode..."
echo "Swagger docs: http://localhost:8000/docs"
echo "Health check: http://localhost:8000/api/v1/health"
echo ""

exec uvicorn app.main:app \
    --host 0.0.0.0 \
    --port 8000 \
    --reload \
    --log-level info
