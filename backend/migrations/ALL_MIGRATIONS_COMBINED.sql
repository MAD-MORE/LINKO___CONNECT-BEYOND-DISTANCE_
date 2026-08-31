-- ============================================================
-- LINKO — ALL MIGRATIONS (001 → 009)
-- Paste this entire script into:
-- Supabase → SQL Editor → New query → Run
-- ============================================================

-- ── 001: Control plane core tables ──────────────────────────
create extension if not exists pgcrypto;

create table if not exists devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  public_key text not null unique,
  name text not null,
  roles text[] not null check (cardinality(roles) > 0),
  last_seen_at timestamptz not null default now(),
  revoked_at timestamptz
);

create table if not exists sessions (
  id uuid primary key default gen_random_uuid(),
  receiver_device_id uuid not null references devices(id) on delete restrict,
  provider_device_id uuid not null references devices(id) on delete restrict,
  state text not null check (state in ('requested','approved','signaling','connected','revoked','expired','denied')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  approved_at timestamptz,
  revoked_at timestamptz,
  check (receiver_device_id <> provider_device_id)
);

create index if not exists devices_user_idx on devices(user_id);
create index if not exists devices_last_seen_idx on devices(last_seen_at);
create index if not exists sessions_receiver_idx on sessions(receiver_device_id);
create index if not exists sessions_provider_idx on sessions(provider_device_id);
create index if not exists sessions_expiry_idx on sessions(expires_at);
create index if not exists sessions_state_idx on sessions(state);

alter table devices enable row level security;
alter table sessions enable row level security;

-- ── 002: Production schema (profiles, extended sessions) ────
create table if not exists public.profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique,
  linko_id text not null unique,
  display_name text not null default 'LINKO User',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.friend_requests (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null,
  receiver_id uuid not null,
  status text not null default 'pending' check (status in ('pending','accepted','declined')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint friend_requests_no_self check (sender_id <> receiver_id),
  constraint friend_requests_unique unique (sender_id, receiver_id)
);

create index if not exists profiles_user_idx on public.profiles(user_id);
create index if not exists profiles_linko_id_idx on public.profiles(linko_id);
create index if not exists friend_requests_sender_idx on public.friend_requests(sender_id);
create index if not exists friend_requests_receiver_idx on public.friend_requests(receiver_id);
create index if not exists friend_requests_status_idx on public.friend_requests(status);

revoke all on table public.devices from anon;
revoke all on table public.sessions from anon;
revoke all on table public.devices from authenticated;
revoke all on table public.sessions from authenticated;

grant select, insert, update, delete on table public.devices to service_role;
grant select, insert, update, delete on table public.sessions to service_role;

-- ── 003: Friend workflow — functions, triggers, RLS ─────────
create or replace function public.ensure_linko_profile(p_user_id uuid, p_display_name text default 'LINKO User')
returns void language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (user_id, linko_id, display_name)
  values (p_user_id, 'LNK-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)), coalesce(nullif(trim(p_display_name), ''), 'LINKO User'))
  on conflict (user_id) do update
    set display_name = coalesce(nullif(public.profiles.display_name, ''), excluded.display_name),
        updated_at = now();
end;
$$;

create or replace function public.search_linko_users(p_query text, p_exclude_user_id uuid default null)
returns table(user_id uuid, display_name text, linko_id text)
language sql security definer set search_path = public as $$
  select p.user_id, p.display_name, p.linko_id
  from public.profiles p
  where (p.linko_id ilike '%' || p_query || '%' or p.display_name ilike '%' || p_query || '%')
    and (p_exclude_user_id is null or p.user_id <> p_exclude_user_id)
  limit 20;
$$;

create or replace function public.handle_new_linko_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  candidate text;
  display_name_value text;
begin
  display_name_value := coalesce(nullif(trim(new.raw_user_meta_data->>'display_name'), ''), 'LINKO User');
  loop
    candidate := 'LNK-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
    begin
      insert into public.profiles (user_id, linko_id, display_name)
      values (new.id, candidate, display_name_value)
      on conflict (user_id) do update
        set display_name = coalesce(nullif(public.profiles.display_name, ''), excluded.display_name),
            updated_at = now();
      exit;
    exception when unique_violation then
      continue;
    end;
  end loop;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_linko_profile on auth.users;
create trigger on_auth_user_created_linko_profile
after insert on auth.users
for each row execute function public.handle_new_linko_user();

alter table public.profiles enable row level security;
alter table public.friend_requests enable row level security;

drop policy if exists profiles_select_authenticated on public.profiles;
create policy profiles_select_authenticated on public.profiles for select to authenticated using (true);

drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self on public.profiles for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

drop policy if exists friend_requests_select_participant on public.friend_requests;
create policy friend_requests_select_participant on public.friend_requests for select to authenticated
  using ((select auth.uid()) = sender_id or (select auth.uid()) = receiver_id);

drop policy if exists friend_requests_insert_sender on public.friend_requests;
create policy friend_requests_insert_sender on public.friend_requests for insert to authenticated
  with check ((select auth.uid()) = sender_id and sender_id <> receiver_id);

drop policy if exists friend_requests_update_receiver on public.friend_requests;
create policy friend_requests_update_receiver on public.friend_requests for update to authenticated
  using ((select auth.uid()) = receiver_id and status = 'pending')
  with check ((select auth.uid()) = receiver_id and status in ('accepted', 'declined', 'pending'));

-- ── 004: Realtime publication ────────────────────────────────
alter table public.profiles replica identity full;
alter table public.friend_requests replica identity full;
alter table public.sessions replica identity full;

drop policy if exists devices_select_self_realtime on public.devices;
create policy devices_select_self_realtime on public.devices for select to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists sessions_select_participant_realtime on public.sessions;
create policy sessions_select_participant_realtime on public.sessions for select to authenticated
  using (
    exists (select 1 from public.devices d where d.id = sessions.receiver_device_id and d.user_id = (select auth.uid()))
    or exists (select 1 from public.devices d where d.id = sessions.provider_device_id and d.user_id = (select auth.uid()))
  );

drop policy if exists friend_requests_delete_participant on public.friend_requests;
create policy friend_requests_delete_participant on public.friend_requests for delete to authenticated
  using ((select auth.uid()) = sender_id or (select auth.uid()) = receiver_id);

do $$
begin
  begin alter publication supabase_realtime add table public.profiles; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.friend_requests; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.devices; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.sessions; exception when duplicate_object then null; end;
end $$;

-- ── 005: Usage accounting ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS usage_records (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    device_id   UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    role        TEXT NOT NULL CHECK (role IN ('provider', 'receiver')),
    bytes_up    BIGINT NOT NULL DEFAULT 0 CHECK (bytes_up >= 0),
    bytes_down  BIGINT NOT NULL DEFAULT 0 CHECK (bytes_down >= 0),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_usage_device_recorded ON usage_records (device_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_usage_session ON usage_records (session_id, recorded_at DESC);
CREATE OR REPLACE VIEW device_monthly_usage AS
  SELECT device_id, DATE_TRUNC('month', recorded_at) AS billing_month,
    SUM(bytes_up) AS total_bytes_up, SUM(bytes_down) AS total_bytes_down,
    SUM(bytes_up + bytes_down) AS total_bytes
  FROM usage_records GROUP BY device_id, DATE_TRUNC('month', recorded_at);

-- ── 006: Relay nodes registry ─────────────────────────────────
CREATE TABLE IF NOT EXISTS relay_nodes (
    id                  TEXT PRIMARY KEY,
    host                TEXT NOT NULL,
    port                INTEGER NOT NULL CHECK (port > 0 AND port < 65536),
    region              TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'healthy' CHECK (status IN ('healthy', 'degraded', 'offline')),
    last_health_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    current_sessions    INTEGER NOT NULL DEFAULT 0 CHECK (current_sessions >= 0),
    max_sessions        INTEGER NOT NULL DEFAULT 1000 CHECK (max_sessions > 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_relay_nodes_host_port ON relay_nodes (host, port);
CREATE INDEX IF NOT EXISTS idx_relay_nodes_region_status ON relay_nodes (region, status) WHERE status = 'healthy';
CREATE OR REPLACE FUNCTION update_relay_node_timestamp() RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS relay_nodes_updated_at ON relay_nodes;
CREATE TRIGGER relay_nodes_updated_at BEFORE UPDATE ON relay_nodes FOR EACH ROW EXECUTE FUNCTION update_relay_node_timestamp();

-- ── 007: Security events audit log ───────────────────────────
CREATE TABLE IF NOT EXISTS security_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    TEXT,
    device_id  UUID,
    event_type TEXT NOT NULL,
    metadata   JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_security_events_user ON security_events (user_id, created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_security_events_device ON security_events (device_id, created_at DESC) WHERE device_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_security_events_type_created ON security_events (event_type, created_at DESC);

-- ── 008: Subscriptions & billing ──────────────────────────────
CREATE TABLE IF NOT EXISTS plans (
    id                  TEXT PRIMARY KEY,
    display_name        TEXT NOT NULL,
    monthly_quota_bytes BIGINT NOT NULL,
    price_usd_cents     INTEGER NOT NULL DEFAULT 0,
    features            JSONB NOT NULL DEFAULT '{}',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO plans (id, display_name, monthly_quota_bytes, price_usd_cents)
VALUES ('free', 'Free', 1073741824, 0) ON CONFLICT (id) DO NOTHING;
INSERT INTO plans (id, display_name, monthly_quota_bytes, price_usd_cents, features)
VALUES ('pro', 'Pro', 10737418240, 499, '{"unlimited_relay": false, "priority_routing": true}') ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             TEXT NOT NULL,
    plan_id             TEXT NOT NULL REFERENCES plans(id),
    status              TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'cancelled', 'expired', 'trial')),
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,
    play_purchase_token TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_subscriptions_user_active ON subscriptions (user_id) WHERE status = 'active';

CREATE TABLE IF NOT EXISTS payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          TEXT NOT NULL,
    subscription_id  UUID REFERENCES subscriptions(id),
    amount_usd_cents INTEGER NOT NULL,
    currency         TEXT NOT NULL DEFAULT 'USD',
    status           TEXT NOT NULL CHECK (status IN ('pending', 'completed', 'failed', 'refunded')),
    play_order_id    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_payments_user ON payments (user_id, created_at DESC);

-- ── 009: Blocked users ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS blocked_users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    TEXT,
    device_id  UUID,
    reason     TEXT NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT blocked_users_has_actor CHECK (user_id IS NOT NULL OR device_id IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_blocked_users_unique ON blocked_users (COALESCE(user_id, ''), COALESCE(device_id::text, ''));
CREATE INDEX IF NOT EXISTS idx_blocked_users_user ON blocked_users (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_blocked_users_device ON blocked_users (device_id) WHERE device_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_blocked_users_expires ON blocked_users (expires_at) WHERE expires_at IS NOT NULL;

-- ── 010: Permanent Supabase Control Plane RPC Functions ────────
-- 1. Device Registration RPC
CREATE OR REPLACE FUNCTION public.linko_register_device(
    p_public_key TEXT,
    p_name TEXT,
    p_roles TEXT[]
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_device RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    PERFORM public.ensure_linko_profile(v_user_id, p_name);

    INSERT INTO public.devices (user_id, public_key, name, roles, last_seen_at, revoked_at)
    VALUES (v_user_id, p_public_key, p_name, p_roles, NOW(), NULL)
    ON CONFLICT (public_key) DO UPDATE
    SET user_id = v_user_id,
        name = EXCLUDED.name,
        roles = EXCLUDED.roles,
        last_seen_at = NOW(),
        revoked_at = NULL
    RETURNING * INTO v_device;

    RETURN jsonb_build_object(
        'device', jsonb_build_object(
            'id', v_device.id,
            'publicKey', v_device.public_key,
            'name', v_device.name,
            'roles', v_device.roles,
            'lastSeenAt', EXTRACT(EPOCH FROM v_device.last_seen_at) * 1000
        ),
        'user', jsonb_build_object(
            'id', v_user_id
        ),
        'accessToken', current_setting('request.jwt.claim.sub', true)
    );
END;
$$;

-- 2. Touch / Mark Presence RPC
CREATE OR REPLACE FUNCTION public.linko_mark_presence(
    p_device_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_target_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    IF p_device_id IS NOT NULL THEN
        UPDATE public.devices
        SET last_seen_at = NOW()
        WHERE id = p_device_id AND user_id = v_user_id
        RETURNING id INTO v_target_id;
    ELSE
        UPDATE public.devices
        SET last_seen_at = NOW()
        WHERE user_id = v_user_id AND revoked_at IS NULL
        RETURNING id INTO v_target_id;
    END IF;

    RETURN jsonb_build_object(
        'deviceId', COALESCE(v_target_id, p_device_id),
        'lastSeenAt', EXTRACT(EPOCH FROM NOW()) * 1000
    );
END;
$$;

-- 3. Find Active Provider Device For User
CREATE OR REPLACE FUNCTION public.linko_provider_for_user(
    p_friend_user_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_target_user UUID;
    v_device RECORD;
    v_online BOOLEAN;
BEGIN
    v_target_user := p_friend_user_id::UUID;

    SELECT id, public_key, last_seen_at
    INTO v_device
    FROM public.devices
    WHERE user_id = v_target_user
      AND revoked_at IS NULL
      AND 'provider' = ANY(roles)
    ORDER BY last_seen_at DESC
    LIMIT 1;

    IF v_device.id IS NULL THEN
        RETURN jsonb_build_object(
            'device', NULL,
            'online', false,
            'lastSeenAt', 0
        );
    END IF;

    v_online := (v_device.last_seen_at >= NOW() - INTERVAL '3 minutes');

    RETURN jsonb_build_object(
        'device', jsonb_build_object(
            'id', v_device.id,
            'publicKey', v_device.public_key
        ),
        'online', v_online,
        'lastSeenAt', EXTRACT(EPOCH FROM v_device.last_seen_at) * 1000
    );
END;
$$;

-- 4. Create Session Request RPC
CREATE OR REPLACE FUNCTION public.linko_create_session(
    p_receiver_device_id UUID,
    p_provider_device_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_session RECORD;
    v_provider RECORD;
    v_relay_host TEXT := 'linkoconnect-beyond-distance.fly.dev';
    v_relay_port INT := 7000;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.devices WHERE id = p_receiver_device_id AND user_id = v_user_id) THEN
        RAISE EXCEPTION 'unauthorized_receiver_device';
    END IF;

    IF p_receiver_device_id = p_provider_device_id THEN
        RAISE EXCEPTION 'cannot_connect_to_self';
    END IF;

    SELECT id, public_key INTO v_provider
    FROM public.devices
    WHERE id = p_provider_device_id AND revoked_at IS NULL;

    IF v_provider.id IS NULL THEN
        RAISE EXCEPTION 'provider_not_found';
    END IF;

    SELECT host, port INTO v_relay_host, v_relay_port
    FROM public.relay_nodes
    WHERE status = 'healthy'
    ORDER BY current_sessions ASC
    LIMIT 1;

    IF v_relay_host IS NULL THEN
        v_relay_host := 'linkoconnect-beyond-distance.fly.dev';
        v_relay_port := 7000;
    END IF;

    INSERT INTO public.sessions (
        receiver_device_id,
        provider_device_id,
        state,
        expires_at
    )
    VALUES (
        p_receiver_device_id,
        p_provider_device_id,
        'requested',
        NOW() + INTERVAL '5 minutes'
    )
    RETURNING * INTO v_session;

    RETURN jsonb_build_object(
        'id', v_session.id,
        'providerPublicKey', v_provider.public_key,
        'relayUrl', v_relay_host || ':' || v_relay_port::TEXT,
        'state', v_session.state,
        'expiresAt', EXTRACT(EPOCH FROM v_session.expires_at) * 1000
    );
END;
$$;

-- 5. Transition Session State RPC
CREATE OR REPLACE FUNCTION public.linko_transition_session(
    p_session_id UUID,
    p_state TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_session RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    SELECT s.*, 
           (dp.user_id = v_user_id) AS is_provider,
           (dr.user_id = v_user_id) AS is_receiver,
           dr.public_key AS receiver_public_key,
           dp.public_key AS provider_public_key
    INTO v_session
    FROM public.sessions s
    JOIN public.devices dr ON dr.id = s.receiver_device_id
    JOIN public.devices dp ON dp.id = s.provider_device_id
    WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'session_not_found';
    END IF;

    IF NOT (v_session.is_provider OR v_session.is_receiver) THEN
        RAISE EXCEPTION 'unauthorized_session_participant';
    END IF;

    IF p_state = 'approved' THEN
        IF NOT v_session.is_provider THEN
            RAISE EXCEPTION 'only_provider_can_approve';
        END IF;
        UPDATE public.sessions
        SET state = 'approved',
            approved_at = NOW(),
            expires_at = NOW() + INTERVAL '1 hour'
        WHERE id = p_session_id;
    ELSIF p_state = 'denied' THEN
        IF NOT v_session.is_provider THEN
            RAISE EXCEPTION 'only_provider_can_deny';
        END IF;
        UPDATE public.sessions SET state = 'denied' WHERE id = p_session_id;
    ELSIF p_state = 'revoked' THEN
        UPDATE public.sessions SET state = 'revoked', revoked_at = NOW() WHERE id = p_session_id;
    ELSIF p_state IN ('signaling', 'connected') THEN
        UPDATE public.sessions SET state = p_state WHERE id = p_session_id;
    ELSE
        RAISE EXCEPTION 'invalid_state_transition: %', p_state;
    END IF;

    RETURN jsonb_build_object(
        'id', p_session_id,
        'state', p_state,
        'receiverPublicKey', v_session.receiver_public_key,
        'providerPublicKey', v_session.provider_public_key,
        'expiresAt', EXTRACT(EPOCH FROM (NOW() + INTERVAL '1 hour')) * 1000
    );
END;
$$;

-- 6. Get Session State RPC
CREATE OR REPLACE FUNCTION public.linko_get_session(
    p_session_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_session RECORD;
BEGIN
    SELECT s.id, s.state, s.expires_at
    INTO v_session
    FROM public.sessions s
    WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'session_not_found';
    END IF;

    RETURN jsonb_build_object(
        'id', v_session.id,
        'state', v_session.state,
        'expiresAt', EXTRACT(EPOCH FROM v_session.expires_at) * 1000
    );
END;
$$;

-- 7. Pending Provider Requests List RPC
CREATE OR REPLACE FUNCTION public.linko_pending_provider_requests()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_requests JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', s.id,
                'receiverDeviceId', s.receiver_device_id,
                'state', s.state,
                'expiresAt', EXTRACT(EPOCH FROM s.expires_at) * 1000
            )
            ORDER BY s.created_at DESC
        ),
        '[]'::jsonb
    )
    INTO v_requests
    FROM public.sessions s
    JOIN public.devices d ON d.id = s.provider_device_id
    WHERE d.user_id = v_user_id
      AND s.state = 'requested'
      AND s.expires_at > NOW();

    RETURN jsonb_build_object('requests', v_requests);
END;
$$;

-- 8. Tunnel Config RPC
CREATE OR REPLACE FUNCTION public.linko_tunnel_config(
    p_session_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_session RECORD;
    v_role TEXT;
    v_key_bytes BYTEA;
    v_key_b64 TEXT;
    v_relay_host TEXT := 'linkoconnect-beyond-distance.fly.dev';
    v_relay_port INT := 7000;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    SELECT s.*, 
           (dp.user_id = v_user_id) AS is_provider,
           (dr.user_id = v_user_id) AS is_receiver
    INTO v_session
    FROM public.sessions s
    JOIN public.devices dr ON dr.id = s.receiver_device_id
    JOIN public.devices dp ON dp.id = s.provider_device_id
    WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'session_not_found';
    END IF;

    IF v_session.is_provider THEN
        v_role := 'provider';
    ELSIF v_session.is_receiver THEN
        v_role := 'receiver';
    ELSE
        RAISE EXCEPTION 'unauthorized_participant';
    END IF;

    SELECT host, port INTO v_relay_host, v_relay_port
    FROM public.relay_nodes
    WHERE status = 'healthy'
    ORDER BY current_sessions ASC
    LIMIT 1;

    IF v_relay_host IS NULL THEN
        v_relay_host := 'linkoconnect-beyond-distance.fly.dev';
        v_relay_port := 7000;
    END IF;

    v_key_bytes := digest(p_session_id::TEXT || '_LINKO_SECURE_TUNNEL_KEY_V1', 'sha256');
    v_key_b64 := encode(v_key_bytes, 'base64');

    RETURN jsonb_build_object(
        'sessionId', p_session_id,
        'endpoint', jsonb_build_object(
            'host', v_relay_host,
            'port', v_relay_port
        ),
        'host', v_relay_host,
        'port', v_relay_port,
        'key', v_key_b64,
        'role', v_role,
        'expiresAt', EXTRACT(EPOCH FROM v_session.expires_at) * 1000
    );
END;
$$;

-- 9. Control Health Check RPC
CREATE OR REPLACE FUNCTION public.linko_control_health()
RETURNS JSONB
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT jsonb_build_object(
        'ok', true,
        'backend', 'supabase_control_plane',
        'timestamp', NOW()
    );
$$;

-- 10. Signaling Infrastructure & RPCs
CREATE TABLE IF NOT EXISTS public.signals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES public.sessions(id) ON DELETE CASCADE,
    sender_device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    recipient_device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('offer', 'answer', 'ice')),
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_signals_session_created ON public.signals(session_id, created_at ASC);
ALTER TABLE public.signals ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  BEGIN ALTER PUBLICATION supabase_realtime ADD TABLE public.signals; EXCEPTION WHEN duplicate_object THEN NULL; END;
END $$;

CREATE OR REPLACE FUNCTION public.linko_signaling_ticket(
    p_session_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_device_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;

    SELECT id INTO v_device_id
    FROM public.devices
    WHERE user_id = v_user_id AND revoked_at IS NULL
    ORDER BY last_seen_at DESC
    LIMIT 1;

    RETURN jsonb_build_object(
        'sessionId', p_session_id,
        'deviceId', COALESCE(v_device_id::TEXT, ''),
        'expiresAt', EXTRACT(EPOCH FROM (NOW() + INTERVAL '5 minutes')) * 1000
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.linko_send_signal(
    p_session_id UUID,
    p_kind TEXT,
    p_payload JSONB
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_session RECORD;
    v_sender_id UUID;
    v_recipient_id UUID;
    v_signal RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;

    SELECT s.*, 
           dr.user_id AS receiver_user_id,
           dp.user_id AS provider_user_id
    INTO v_session
    FROM public.sessions s
    JOIN public.devices dr ON dr.id = s.receiver_device_id
    JOIN public.devices dp ON dp.id = s.provider_device_id
    WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN RAISE EXCEPTION 'session_not_found'; END IF;

    IF v_session.provider_user_id = v_user_id THEN
        v_sender_id := v_session.provider_device_id;
        v_recipient_id := v_session.receiver_device_id;
    ELSIF v_session.receiver_user_id = v_user_id THEN
        v_sender_id := v_session.receiver_device_id;
        v_recipient_id := v_session.provider_device_id;
    ELSE
        RAISE EXCEPTION 'unauthorized_session_participant';
    END IF;

    INSERT INTO public.signals (
        session_id,
        sender_device_id,
        recipient_device_id,
        kind,
        payload
    )
    VALUES (
        p_session_id,
        v_sender_id,
        v_recipient_id,
        p_kind,
        p_payload
    )
    RETURNING * INTO v_signal;

    RETURN jsonb_build_object(
        'id', v_signal.id,
        'sessionId', p_session_id,
        'senderDeviceId', v_signal.sender_device_id,
        'recipientDeviceId', v_signal.recipient_device_id,
        'kind', v_signal.kind,
        'payload', v_signal.payload,
        'createdAt', EXTRACT(EPOCH FROM v_signal.created_at) * 1000
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.linko_receive_signals(
    p_session_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_signals JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', sig.id,
                'sessionId', sig.session_id,
                'senderDeviceId', sig.sender_device_id,
                'recipientDeviceId', sig.recipient_device_id,
                'kind', sig.kind,
                'payload', sig.payload,
                'createdAt', EXTRACT(EPOCH FROM sig.created_at) * 1000
            )
            ORDER BY sig.created_at ASC
        ),
        '[]'::jsonb
    )
    INTO v_signals
    FROM public.signals sig
    JOIN public.devices d ON d.id = sig.recipient_device_id
    WHERE sig.session_id = p_session_id
      AND d.user_id = v_user_id;

    RETURN jsonb_build_object('signals', v_signals);
END;
$$;

-- 11. Grant Execute Permissions to authenticated users
GRANT EXECUTE ON FUNCTION public.linko_register_device(TEXT, TEXT, TEXT[]) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_mark_presence(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_provider_for_user(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_create_session(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_transition_session(UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_get_session(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_pending_provider_requests() TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_tunnel_config(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_control_health() TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.linko_signaling_ticket(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_send_signal(UUID, TEXT, JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_receive_signals(UUID) TO authenticated;

-- ── Done ──────────────────────────────────────────────────────
select 'LINKO migrations applied successfully' as result;


