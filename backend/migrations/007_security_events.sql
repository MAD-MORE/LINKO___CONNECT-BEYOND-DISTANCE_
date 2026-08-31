-- Migration 007: Security events audit log
-- Records security-relevant events for abuse detection and auditing.
-- IMPORTANT: This table must NEVER store user traffic content.

CREATE TABLE IF NOT EXISTS security_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     TEXT,           -- Supabase user ID (nullable if device-only event)
    device_id   UUID,           -- Linko device ID (nullable if user-only event)
    event_type  TEXT NOT NULL,  -- See AbuseEventType in abuse.ts
    metadata    JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for per-user security event queries
CREATE INDEX IF NOT EXISTS idx_security_events_user
    ON security_events (user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

-- Index for per-device security event queries
CREATE INDEX IF NOT EXISTS idx_security_events_device
    ON security_events (device_id, created_at DESC)
    WHERE device_id IS NOT NULL;

-- Index for event type queries (e.g. find all auth failures in last hour)
CREATE INDEX IF NOT EXISTS idx_security_events_type_created
    ON security_events (event_type, created_at DESC);

-- Retention: security events older than 90 days can be purged.
-- Run periodically: DELETE FROM security_events WHERE created_at < NOW() - INTERVAL '90 days';
