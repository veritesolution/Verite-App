# Vérité TMR — Production Docker Image
FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    poppler-utils \
    ffmpeg \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY verite_tmr_v10_5_2.tar.gz /tmp/
RUN pip install --no-cache-dir /tmp/verite_tmr_v10_5_2.tar.gz[all] || \
    pip install --no-cache-dir verite-tmr[all]

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

RUN useradd -m -u 1001 verite
RUN mkdir -p /tmp/verite_uploads /tmp/verite_models && \
    chown verite:verite /tmp/verite_uploads /tmp/verite_models /app
USER verite

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/health')"

CMD ["uvicorn", "main:app", \
     "--host", "0.0.0.0", \
     "--port", "8080", \
     "--workers", "1", \
     "--log-level", "info"]
