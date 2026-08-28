-- Migration 008: Subscriptions and billing
-- Supports future monetization. MVP: all users are on the free plan.

CREATE TABLE IF NOT EXISTS plans (
    id                  TEXT PRIMARY KEY,       -- e.g. "free", "pro", "business"
    display_name        TEXT NOT NULL,
    monthly_quota_bytes BIGINT NOT NULL,        -- Relay data quota per month
    price_usd_cents     INTEGER NOT NULL DEFAULT 0,
    features            JSONB NOT NULL DEFAULT '{}',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed the free plan
INSERT INTO plans (id, display_name, monthly_quota_bytes, price_usd_cents)
VALUES ('free', 'Free', 1073741824, 0)  -- 1 GB
ON CONFLICT (id) DO NOTHING;

INSERT INTO plans (id, display_name, monthly_quota_bytes, price_usd_cents, features)
VALUES ('pro', 'Pro', 10737418240, 499, '{"unlimited_relay": false, "priority_routing": true}')  -- 10 GB, $4.99
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         TEXT NOT NULL,
    plan_id         TEXT NOT NULL REFERENCES plans(id),
    status          TEXT NOT NULL DEFAULT 'active'
                        CHECK (status IN ('active', 'cancelled', 'expired', 'trial')),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    play_purchase_token TEXT,                   -- Google Play purchase token
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_subscriptions_user_active
    ON subscriptions (user_id)
    WHERE status = 'active';

CREATE TABLE IF NOT EXISTS payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             TEXT NOT NULL,
    subscription_id     UUID REFERENCES subscriptions(id),
    amount_usd_cents    INTEGER NOT NULL,
    currency            TEXT NOT NULL DEFAULT 'USD',
    status              TEXT NOT NULL CHECK (status IN ('pending', 'completed', 'failed', 'refunded')),
    play_order_id       TEXT,                   -- Google Play order ID
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_user ON payments (user_id, created_at DESC);
