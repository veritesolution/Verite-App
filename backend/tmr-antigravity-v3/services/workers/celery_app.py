"""Celery application and queue configuration."""

from celery import Celery
from ..api.config import settings

celery_app = Celery("tmr_antigravity")

celery_app.conf.update(
    broker_url=settings.CELERY_BROKER_URL,
    result_backend=settings.CELERY_RESULT_BACKEND,
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="UTC",
    enable_utc=True,
    task_track_started=True,
    task_acks_late=True,
    worker_prefetch_multiplier=1,        # fair dispatch — important for long EEG tasks

    # Queue routing
    task_routes={
        "workers.generate_plan": {"queue": "plans"},
        "workers.run_tmr_session": {"queue": "eeg"},
        "workers.audit_logger.*": {"queue": "audit"},
    },

    # Queues definition
    task_queues={
        "plans": {"exchange": "plans", "routing_key": "plans"},
        "eeg": {"exchange": "eeg", "routing_key": "eeg"},
        "audit": {"exchange": "audit", "routing_key": "audit"},
    },
    task_default_queue="plans",

    # Retry policy
    task_max_retries=3,
    task_retry_backoff=True,
    task_retry_backoff_max=300,

    # Result expiry
    result_expires=86400 * 7,            # 7 days

    # Worker settings
    worker_max_tasks_per_child=100,      # recycle workers periodically
)

# Import tasks so Celery auto-discovers them
celery_app.autodiscover_tasks(["services.workers"])
