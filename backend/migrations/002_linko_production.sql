-- LINKO production database schema for Supabase/PostgreSQL.
-- The control-plane backend connects directly with DATABASE_URL.
-- Traffic payloads are intentionally not stored here.

create extension if not exists pgcrypto;

create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  public_key text not null unique,
  name text not null,
  roles text[] not null check (cardinality(roles) > 0 and roles <@ array['provider','receiver']::text[]),
  last_seen_at timestamptz not null default now(),
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  constraint devices_name_nonempty check (length(trim(name)) > 0)
);

create index if not exists devices_user_idx on public.devices(user_id);
create index if not exists devices_active_user_idx on public.devices(user_id) where revoked_at is null;

create table if not exists public.sessions (
  id uuid primary key default gen_random_uuid(),
  receiver_device_id uuid not null references public.devices(id) on delete restrict,
  provider_device_id uuid not null references public.devices(id) on delete restrict,
  state text not null check (state in ('requested','approved','signaling','connected','revoked','expired','denied')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  approved_at timestamptz,
  revoked_at timestamptz,
  constraint sessions_distinct_devices check (receiver_device_id <> provider_device_id),
  constraint sessions_approval_consistency check (
    (state in ('approved','signaling','connected') and approved_at is not null)
    or state not in ('approved','signaling','connected')
  ),
  constraint sessions_revocation_consistency check (
    (state = 'revoked' and revoked_at is not null)
    or state <> 'revoked'
  )
);

create index if not exists sessions_receiver_idx on public.sessions(receiver_device_id);
create index if not exists sessions_provider_idx on public.sessions(provider_device_id);
create index if not exists sessions_expiry_idx on public.sessions(expires_at);
create index if not exists sessions_active_receiver_idx on public.sessions(receiver_device_id, created_at desc)
  where state not in ('revoked','expired','denied');
create index if not exists sessions_active_provider_idx on public.sessions(provider_device_id, created_at desc)
  where state not in ('revoked','expired','denied');

-- Backend authorization remains authoritative. RLS provides defense-in-depth
-- for any future Data API access; the trusted backend uses its database role.
alter table public.devices enable row level security;
alter table public.sessions enable row level security;

-- These tables are control-plane data and should not be directly writable by
-- anonymous clients. Do not grant anon access. Backend/server roles can use
-- the database connection directly.
revoke all on table public.devices from anon;
revoke all on table public.sessions from anon;
revoke all on table public.devices from authenticated;
revoke all on table public.sessions from authenticated;

-- Keep the Supabase service_role available for future server-side tooling,
-- while RLS remains enabled as defense in depth.
grant select, insert, update, delete on table public.devices to service_role;
grant select, insert, update, delete on table public.sessions to service_role;
