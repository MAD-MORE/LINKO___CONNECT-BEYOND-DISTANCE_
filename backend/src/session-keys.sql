alter table public.linko_sessions
  add column if not exists receiver_public_key text,
  add column if not exists provider_public_key text;

comment on column public.linko_sessions.receiver_public_key is 'Ephemeral receiver public key; private key never leaves device.';
comment on column public.linko_sessions.provider_public_key is 'Ephemeral provider public key; private key never leaves device.';
