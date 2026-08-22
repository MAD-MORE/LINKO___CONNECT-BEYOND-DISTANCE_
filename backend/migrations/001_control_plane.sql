-- LINKO Phase 5 persistence baseline.
-- No traffic payloads are stored in the control plane.

create table if not exists devices (
  id uuid primary key,
  user_id uuid not null,
  public_key text not null unique,
  name text not null,
  roles text[] not null,
  last_seen_at timestamptz not null,
  revoked_at timestamptz
);

create table if not exists sessions (
  id uuid primary key,
  receiver_device_id uuid not null references devices(id),
  provider_device_id uuid not null references devices(id),
  state text not null check (state in ('requested','approved','signaling','connected','revoked','expired','denied')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  approved_at timestamptz,
  revoked_at timestamptz
);

create index if not exists sessions_receiver_idx on sessions(receiver_device_id);
create index if not exists sessions_provider_idx on sessions(provider_device_id);
create index if not exists sessions_expiry_idx on sessions(expires_at);

-- Defense-in-depth: application/service authorization remains authoritative.
alter table devices enable row level security;
alter table sessions enable row level security;
