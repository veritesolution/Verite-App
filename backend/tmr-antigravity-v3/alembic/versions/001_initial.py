"""Initial schema — TMR-ANTIGRAVITY v2.0

Revision ID: 001_initial
Create Date: 2026-03-23
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "001_initial"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # ── Users ──────────────────────────────────────────────────────────────
    op.create_table(
        "users",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("email", sa.String(320), nullable=False),
        sa.Column("email_verified", sa.Boolean, server_default="false"),
        sa.Column("hashed_password", sa.String(128), nullable=True),
        sa.Column("google_sub", sa.String(128), nullable=True),
        sa.Column("apple_sub", sa.String(128), nullable=True),
        sa.Column("full_name", sa.String(256), nullable=True),
        sa.Column("locale", sa.String(10), server_default="en"),
        sa.Column("is_active", sa.Boolean, server_default="true"),
        sa.Column("is_researcher", sa.Boolean, server_default="false"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_users_email", "users", ["email"], unique=True)
    op.create_index("ix_users_google_sub", "users", ["google_sub"], unique=True)
    op.create_index("ix_users_apple_sub", "users", ["apple_sub"], unique=True)

    # ── Consent records ───────────────────────────────────────────────────
    op.create_table(
        "consent_records",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("status", sa.String(16), server_default="pending"),
        sa.Column("consent_version", sa.String(20), server_default="1.0"),
        sa.Column("ip_address", sa.String(45), nullable=True),
        sa.Column("user_agent", sa.String(512), nullable=True),
        sa.Column("given_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("withdrawn_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("irb_protocol", sa.String(64), nullable=True),
    )
    op.create_index("ix_consent_user_id", "consent_records", ["user_id"], unique=True)

    # ── User profiles ─────────────────────────────────────────────────────
    op.create_table(
        "user_profiles",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("session_version", sa.Integer, server_default="1"),
        sa.Column("ailment_enc", sa.LargeBinary, nullable=True),
        sa.Column("emotion_enc", sa.LargeBinary, nullable=True),
        sa.Column("origin_story_enc", sa.LargeBinary, nullable=True),
        sa.Column("voice_emotion_enc", sa.LargeBinary, nullable=True),
        sa.Column("age", sa.Integer, nullable=False),
        sa.Column("profession", sa.String(256), nullable=False),
        sa.Column("duration_years", sa.String(64), nullable=True),
        sa.Column("frequency", sa.String(128), nullable=True),
        sa.Column("intensity", sa.Integer, nullable=True),
        sa.Column("locale", sa.String(10), server_default="en"),
        sa.Column("input_mode", sa.String(16), server_default="text"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_user_profiles_user_id", "user_profiles", ["user_id"])

    # ── Habit plans ───────────────────────────────────────────────────────
    op.create_table(
        "habit_plans",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("profile_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("user_profiles.id", ondelete="SET NULL"), nullable=True),
        sa.Column("celery_task_id", sa.String(128), nullable=True),
        sa.Column("status", sa.String(16), server_default="queued"),
        sa.Column("llm_provider", sa.String(32), nullable=True),
        sa.Column("plan_enc", sa.LargeBinary, nullable=True),
        sa.Column("cue_audio_s3_key", sa.String(512), nullable=True),
        sa.Column("cue_audio_cdn_url", sa.String(512), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("error_message", sa.Text, nullable=True),
    )
    op.create_index("ix_habit_plans_user_id", "habit_plans", ["user_id"])

    # ── TMR sessions ──────────────────────────────────────────────────────
    op.create_table(
        "tmr_sessions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("plan_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("habit_plans.id", ondelete="SET NULL"), nullable=True),
        sa.Column("celery_task_id", sa.String(128), nullable=True),
        sa.Column("status", sa.String(16), server_default="scheduled"),
        sa.Column("hardware", sa.String(32), server_default="synthetic"),
        sa.Column("cues_delivered", sa.Integer, server_default="0"),
        sa.Column("cues_planned", sa.Integer, server_default="0"),
        sa.Column("sleep_efficiency", sa.Float, nullable=True),
        sa.Column("n2_minutes", sa.Float, nullable=True),
        sa.Column("n3_minutes", sa.Float, nullable=True),
        sa.Column("spindles_detected", sa.Integer, server_default="0"),
        sa.Column("eeg_s3_key", sa.String(512), nullable=True),
        sa.Column("session_metadata", postgresql.JSONB, nullable=True),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("ended_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_tmr_sessions_user_id", "tmr_sessions", ["user_id"])

    # ── TMR events ────────────────────────────────────────────────────────
    op.create_table(
        "tmr_events",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("session_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("tmr_sessions.id", ondelete="CASCADE"), nullable=False),
        sa.Column("event_type", sa.String(32), nullable=False),
        sa.Column("timestamp_unix", sa.Float, nullable=False),
        sa.Column("sleep_stage", sa.String(8), nullable=True),
        sa.Column("spindle_prob", sa.Float, nullable=True),
        sa.Column("phase_rad", sa.Float, nullable=True),
        sa.Column("arousal_risk", sa.Float, nullable=True),
        sa.Column("extra", postgresql.JSONB, nullable=True),
    )
    op.create_index("ix_tmr_events_session_id", "tmr_events", ["session_id"])
    op.create_index("ix_tmr_events_timestamp", "tmr_events", ["timestamp_unix"])

    # ── Audit logs ────────────────────────────────────────────────────────
    op.create_table(
        "audit_logs",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("users.id", ondelete="SET NULL"), nullable=True),
        sa.Column("actor_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("action", sa.String(128), nullable=False),
        sa.Column("resource_type", sa.String(64), nullable=True),
        sa.Column("resource_id", sa.String(128), nullable=True),
        sa.Column("ip_address", sa.String(45), nullable=True),
        sa.Column("user_agent", sa.String(512), nullable=True),
        sa.Column("outcome", sa.String(16), server_default="success"),
        sa.Column("detail", postgresql.JSONB, nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True),
                  server_default=sa.func.now(), index=True),
    )
    op.create_index("ix_audit_logs_user_id", "audit_logs", ["user_id"])
    op.create_index("ix_audit_logs_action", "audit_logs", ["action"])
    op.create_index("ix_audit_logs_created_at", "audit_logs", ["created_at"])


def downgrade() -> None:
    op.drop_table("audit_logs")
    op.drop_table("tmr_events")
    op.drop_table("tmr_sessions")
    op.drop_table("habit_plans")
    op.drop_table("user_profiles")
    op.drop_table("consent_records")
    op.drop_table("users")
