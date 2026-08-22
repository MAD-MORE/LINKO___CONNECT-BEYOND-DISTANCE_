-- LINKO control-plane persistence.
-- No traffic payloads are stored in the database.

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

-- Defense-in-depth: Android clients never connect directly to these tables.
-- The LINKO control plane is the authorization boundary and connects using its
-- private DATABASE_URL credentials.
alter table devices enable row level security;
alter table sessions enable row level security;

comment on table devices is 'LINKO device identity and authorization metadata; no traffic payloads';
comment on table sessions is 'LINKO connection/session state; no traffic payloads';
