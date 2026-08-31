-- Migration 005: Usage accounting
-- Records per-session bandwidth usage reported by devices.

CREATE TABLE IF NOT EXISTS usage_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    device_id       UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    role            TEXT NOT NULL CHECK (role IN ('provider', 'receiver')),
    bytes_up        BIGINT NOT NULL DEFAULT 0 CHECK (bytes_up >= 0),
    bytes_down      BIGINT NOT NULL DEFAULT 0 CHECK (bytes_down >= 0),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for per-device usage queries (billing, quota checks)
CREATE INDEX IF NOT EXISTS idx_usage_device_recorded
    ON usage_records (device_id, recorded_at DESC);

-- Index for per-session usage aggregation
CREATE INDEX IF NOT EXISTS idx_usage_session
    ON usage_records (session_id, recorded_at DESC);

-- Monthly usage aggregation helper view
CREATE OR REPLACE VIEW device_monthly_usage AS
SELECT
    device_id,
    DATE_TRUNC('month', recorded_at) AS billing_month,
    SUM(bytes_up)   AS total_bytes_up,
    SUM(bytes_down) AS total_bytes_down,
    SUM(bytes_up + bytes_down) AS total_bytes
FROM usage_records
GROUP BY device_id, DATE_TRUNC('month', recorded_at);
