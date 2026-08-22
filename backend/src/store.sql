-- LINKO Phase 6 persistent control-plane state.
-- Apply through the project's managed Postgres migration system.

create table if not exists linko_connections (
  id uuid primary key,
  receiver_id text not null,
  provider_id text not null,
  status text not null check (status in ('pending','approved','denied','connecting','connected','expired','closed')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);

create index if not exists linko_connections_provider_status_idx
  on linko_connections(provider_id, status);

create index if not exists linko_connections_receiver_status_idx
  on linko_connections(receiver_id, status);

create table if not exists linko_sessions (
  id uuid primary key,
  connection_id uuid not null references linko_connections(id) on delete cascade,
  receiver_id text not null,
  provider_id text not null,
  transport text not null default 'pending' check (transport in ('pending','direct','relay')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  closed_at timestamptz
);

create index if not exists linko_sessions_expiry_idx on linko_sessions(expires_at);

create table if not exists linko_trust (
  owner_id text not null,
  friend_id text not null,
  created_at timestamptz not null default now(),
  primary key(owner_id, friend_id)
);

-- Never store tunnel private keys or application traffic in these tables.