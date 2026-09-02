-- LINKO DIRECT-P2P-ONLY contract.
-- Supabase remains control/signaling only. No relay endpoint is selected.
-- Android peers exchange ICE candidates through linko_signaling_events and
-- establish the encrypted data path directly between their UDP sockets.

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
BEGIN
  IF v_user_id IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;

  SELECT s.*,
         (dp.user_id = v_user_id) AS is_provider,
         (dr.user_id = v_user_id) AS is_receiver
    INTO v_session
    FROM public.sessions s
    JOIN public.devices dr ON dr.id = s.receiver_device_id
    JOIN public.devices dp ON dp.id = s.provider_device_id
   WHERE s.id = p_session_id;

  IF v_session.id IS NULL THEN RAISE EXCEPTION 'session_not_found'; END IF;
  IF v_session.is_provider THEN v_role := 'provider';
  ELSIF v_session.is_receiver THEN v_role := 'receiver';
  ELSE RAISE EXCEPTION 'unauthorized_participant'; END IF;

  IF v_session.state NOT IN ('approved','signaling','connected') THEN
    RAISE EXCEPTION 'session_not_ready';
  END IF;
  IF v_session.expires_at <= NOW() THEN RAISE EXCEPTION 'session_expired'; END IF;

  RETURN jsonb_build_object(
    'sessionId', p_session_id,
    'endpoint', NULL,
    'host', NULL,
    'port', NULL,
    'key', encode(v_session.tunnel_key, 'base64'),
    'role', v_role,
    'transport', 'direct_udp',
    'relay', false,
    'expiresAt', EXTRACT(EPOCH FROM v_session.expires_at) * 1000
  );
END;
$$;

REVOKE ALL ON FUNCTION public.linko_tunnel_config(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.linko_tunnel_config(UUID) TO authenticated;
