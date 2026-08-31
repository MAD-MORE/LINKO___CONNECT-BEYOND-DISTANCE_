-- Migration 006: Relay nodes registry
-- Tracks active relay nodes, their health, and session capacity.

CREATE TABLE IF NOT EXISTS relay_nodes (
    id                  TEXT PRIMARY KEY,       -- e.g. "iad-1", "lhr-2"
    host                TEXT NOT NULL,
    port                INTEGER NOT NULL CHECK (port > 0 AND port < 65536),
    region              TEXT NOT NULL,          -- e.g. "iad", "lhr", "sin"
    status              TEXT NOT NULL DEFAULT 'healthy'
                            CHECK (status IN ('healthy', 'degraded', 'offline')),
    last_health_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    current_sessions    INTEGER NOT NULL DEFAULT 0 CHECK (current_sessions >= 0),
    max_sessions        INTEGER NOT NULL DEFAULT 1000 CHECK (max_sessions > 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Only one node per host:port combination
CREATE UNIQUE INDEX IF NOT EXISTS idx_relay_nodes_host_port
    ON relay_nodes (host, port);

-- Index for finding healthy nodes by region
CREATE INDEX IF NOT EXISTS idx_relay_nodes_region_status
    ON relay_nodes (region, status)
    WHERE status = 'healthy';

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_relay_node_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER relay_nodes_updated_at
    BEFORE UPDATE ON relay_nodes
    FOR EACH ROW EXECUTE FUNCTION update_relay_node_timestamp();
