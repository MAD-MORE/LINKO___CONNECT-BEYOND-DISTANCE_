-- Migration 009: Blocked users / abuse enforcement
-- Tracks automatically and manually blocked users and devices.

CREATE TABLE IF NOT EXISTS blocked_users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     TEXT,
    device_id   UUID,
    reason      TEXT NOT NULL,
    expires_at  TIMESTAMPTZ,           -- NULL = permanent
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- At least one of user_id or device_id must be set
    CONSTRAINT blocked_users_has_actor CHECK (user_id IS NOT NULL OR device_id IS NOT NULL)
);

-- Unique constraint: one block record per (user_id, device_id) pair
CREATE UNIQUE INDEX IF NOT EXISTS idx_blocked_users_unique
    ON blocked_users (COALESCE(user_id, ''), COALESCE(device_id::text, ''));

-- Index for fast block lookup by user_id
CREATE INDEX IF NOT EXISTS idx_blocked_users_user
    ON blocked_users (user_id)
    WHERE user_id IS NOT NULL;

-- Index for fast block lookup by device_id
CREATE INDEX IF NOT EXISTS idx_blocked_users_device
    ON blocked_users (device_id)
    WHERE device_id IS NOT NULL;

-- Index for expiry cleanup
CREATE INDEX IF NOT EXISTS idx_blocked_users_expires
    ON blocked_users (expires_at)
    WHERE expires_at IS NOT NULL;

-- Periodic cleanup: DELETE FROM blocked_users WHERE expires_at < NOW();
