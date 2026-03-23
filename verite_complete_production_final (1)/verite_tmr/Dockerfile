FROM python:3.11-slim

# System dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    poppler-utils tesseract-ocr ffmpeg libsndfile1 \
    libsqlcipher-dev portaudio19-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python package
COPY pyproject.toml README.md ./
COPY src/ ./src/
RUN pip install --no-cache-dir ".[all]"

# Copy scripts and tests
COPY tests/ ./tests/
COPY scripts/ ./scripts/
COPY notebooks/ ./notebooks/

# Create data directories
RUN mkdir -p /data/models /data/sessions /data/exports

# Environment
ENV VERITE_DATA_DIR=/data
ENV VERITE_MODEL_DIR=/data/models
ENV PYTHONUNBUFFERED=1

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "from verite_tmr import __version__; print(__version__)"

# Default: run validation
CMD ["verite", "validate", "--mode", "simulation"]
