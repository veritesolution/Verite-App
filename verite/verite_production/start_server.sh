#!/bin/bash
echo "========================================"
echo " Verite Production Server v2.1"
echo " Starting on port 8001..."
echo "========================================"
echo

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "ERROR: Python3 is not installed"
    exit 1
fi

# Create venv if needed
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
    source venv/bin/activate
    echo "Installing dependencies..."
    pip install -r requirements.txt
else
    source venv/bin/activate
fi

echo
echo "Server starting at: http://0.0.0.0:8001"
echo "API docs at: http://localhost:8001/docs"
echo "Health check: http://localhost:8001/api/v1/health"
echo
echo "Press Ctrl+C to stop the server"
echo "========================================"

uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
