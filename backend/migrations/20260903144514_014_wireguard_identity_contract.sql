-- LINKO WireGuard identity contract.
-- Each Android installation owns its WireGuard private key locally.
-- Supabase stores only the public key and exposes it to the authenticated session peers.
-- The current production data plane remains LINKO's authenticated direct UDP transport.
-- WireGuard identity/config metadata is registered and exchanged, but is not selected as the
-- active transport until the provider-side packet forwarding engine is ready.

ALTER TABLE public.devices
    ADD COLUMN IF NOT EXISTS wireguard_public_key TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS devices_wireguard_public_key_uq
    ON public.devices(wireguard_public_key)
    WHERE wireguard_public_key IS NOT NULL;

CREATE OR REPLACE FUNCTION public.linko_set_wireguard_public_key(
    p_device_id UUID,
    p_wireguard_public_key TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_device RECORD;
    v_key TEXT := trim(p_wireguard_public_key);
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
    IF v_key = '' OR length(v_key) <> 44 THEN RAISE EXCEPTION 'invalid_wireguard_public_key'; END IF;

    UPDATE public.devices
       SET wireguard_public_key = v_key,
           last_seen_at = NOW()
     WHERE id = p_device_id
       AND user_id = v_user_id
       AND revoked_at IS NULL
     RETURNING id, wireguard_public_key INTO v_device;

    IF v_device.id IS NULL THEN RAISE EXCEPTION 'device_not_found'; END IF;

    RETURN jsonb_build_object(
        'deviceId', v_device.id,
        'wireguardPublicKey', v_device.wireguard_public_key
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.linko_tunnel_config(p_session_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id UUID := auth.uid();
  v_session RECORD;
  v_role TEXT;
  v_local_wg_key TEXT;
  v_peer_wg_key TEXT;
BEGIN
  IF v_user_id IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;

  SELECT s.*,
         (dp.user_id = v_user_id) AS is_provider,
         (dr.user_id = v_user_id) AS is_receiver,
         dp.wireguard_public_key AS provider_wg_key,
         dr.wireguard_public_key AS receiver_wg_key
    INTO v_session
    FROM public.sessions s
    JOIN public.devices dr ON dr.id = s.receiver_device_id
    JOIN public.devices dp ON dp.id = s.provider_device_id
   WHERE s.id = p_session_id;

  IF v_session.id IS NULL THEN RAISE EXCEPTION 'session_not_found'; END IF;
  IF v_session.is_provider THEN
    v_role := 'provider';
    v_local_wg_key := v_session.provider_wg_key;
    v_peer_wg_key := v_session.receiver_wg_key;
  ELSIF v_session.is_receiver THEN
    v_role := 'receiver';
    v_local_wg_key := v_session.receiver_wg_key;
    v_peer_wg_key := v_session.provider_wg_key;
  ELSE
    RAISE EXCEPTION 'unauthorized_participant';
  END IF;

  IF v_session.state NOT IN ('approved','signaling','connected') THEN RAISE EXCEPTION 'session_not_ready'; END IF;
  IF v_session.expires_at <= NOW() THEN RAISE EXCEPTION 'session_expired'; END IF;
  IF v_local_wg_key IS NULL OR v_peer_wg_key IS NULL THEN RAISE EXCEPTION 'wireguard_identity_not_registered'; END IF;

  RETURN jsonb_build_object(
    'sessionId', p_session_id,
    'endpoint', NULL,
    'host', NULL,
    'port', NULL,
    'key', encode(v_session.tunnel_key, 'base64'),
    'role', v_role,
    'transport', 'direct_udp',
    'relay', false,
    'wireguardPublicKey', v_local_wg_key,
    'peerWireguardPublicKey', v_peer_wg_key,
    'wireguardAddress', CASE WHEN v_role = 'receiver' THEN '10.77.0.2/32' ELSE '10.77.0.1/32' END,
    'peerWireguardAddress', CASE WHEN v_role = 'receiver' THEN '10.77.0.1/32' ELSE '10.77.0.2/32' END,
    'expiresAt', EXTRACT(EPOCH FROM v_session.expires_at) * 1000
  );
END;
$$;

REVOKE ALL ON FUNCTION public.linko_set_wireguard_public_key(UUID, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.linko_set_wireguard_public_key(UUID, TEXT) TO authenticated;
REVOKE ALL ON FUNCTION public.linko_tunnel_config(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.linko_tunnel_config(UUID) TO authenticated;

NOTIFY pgrst, 'reload schema';
