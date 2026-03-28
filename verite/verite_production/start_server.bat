@echo off
chcp 65001 >nul
echo ========================================
echo  Verite Production Server v2.1
echo  Starting on port 8001...
echo ========================================
echo.

python --version 2>NUL
if errorlevel 1 (
    echo ERROR: Python not found. Install from https://www.python.org
    pause
    exit /b 1
)

if exist "venv" (
    echo Found existing venv, using it...
) else (
    echo Creating virtual environment...
    python -m venv venv
)

call venv\Scripts\activate.bat

echo Installing dependencies...
pip install -r requirements.txt --quiet

if errorlevel 1 (
    echo.
    echo ERROR: pip install failed. Check output above.
    pause
    exit /b 1
)

echo.
echo Server starting at: http://0.0.0.0:8001
echo API docs at: http://localhost:8001/docs
echo Health check: http://localhost:8001/api/v1/health
echo.
echo Press Ctrl+C to stop the server
echo ========================================
echo.

uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
