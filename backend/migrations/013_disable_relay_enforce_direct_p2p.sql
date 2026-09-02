-- LINKO direct-P2P control-plane cutover.
-- Relay/TURN selection is no longer part of the product architecture.

CREATE OR REPLACE FUNCTION public.linko_create_session(p_receiver_device_id uuid, p_provider_device_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
    v_user_id UUID;
    v_session RECORD;
    v_provider RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.devices
        WHERE id = p_receiver_device_id AND user_id = v_user_id AND revoked_at IS NULL
    ) THEN RAISE EXCEPTION 'unauthorized_receiver_device'; END IF;
    IF p_receiver_device_id = p_provider_device_id THEN RAISE EXCEPTION 'cannot_connect_to_self'; END IF;

    SELECT id, public_key INTO v_provider
    FROM public.devices
    WHERE id = p_provider_device_id AND revoked_at IS NULL;
    IF v_provider.id IS NULL THEN RAISE EXCEPTION 'provider_not_found'; END IF;

    INSERT INTO public.sessions (receiver_device_id, provider_device_id, state, expires_at)
    VALUES (p_receiver_device_id, p_provider_device_id, 'requested', NOW() + INTERVAL '5 minutes')
    RETURNING * INTO v_session;

    RETURN jsonb_build_object(
        'id', v_session.id,
        'providerPublicKey', v_provider.public_key,
        'transport', 'direct_udp',
        'relay', false,
        'state', v_session.state,
        'expiresAt', EXTRACT(EPOCH FROM v_session.expires_at) * 1000
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.linko_tunnel_config(p_session_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
    v_user_id UUID;
    v_session RECORD;
    v_role TEXT;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;

    SELECT s.*,
           (dp.user_id = v_user_id) AS is_provider,
           (dr.user_id = v_user_id) AS is_receiver
    INTO v_session
    FROM public.sessions s
    JOIN public.devices dr ON dr.id = s.receiver_device_id
    JOIN public.devices dp ON dp.id = s.provider_device_id
    WHERE s.id = p_session_id AND dr.revoked_at IS NULL AND dp.revoked_at IS NULL;

    IF v_session.id IS NULL THEN RAISE EXCEPTION 'session_not_found'; END IF;
    IF NOT (v_session.is_provider OR v_session.is_receiver) THEN RAISE EXCEPTION 'unauthorized_participant'; END IF;
    IF v_session.state NOT IN ('approved', 'signaling', 'connected') THEN RAISE EXCEPTION 'session_not_ready'; END IF;
    IF v_session.expires_at <= NOW() THEN RAISE EXCEPTION 'session_expired'; END IF;
    IF octet_length(v_session.tunnel_key) <> 32 THEN RAISE EXCEPTION 'invalid_tunnel_key'; END IF;

    IF v_session.is_provider THEN v_role := 'provider'; ELSE v_role := 'receiver'; END IF;

    RETURN jsonb_build_object(
        'sessionId', p_session_id,
        'endpoint', NULL,
        'host', NULL,
        'port', NULL,
        'key', encode(v_session.tunnel_key, 'base64'),
        'role', v_role,
        'expiresAt', EXTRACT(EPOCH FROM v_session.expires_at) * 1000,
        'transport', 'direct_udp',
        'relay', false
    );
END;
$$;

REVOKE ALL ON FUNCTION public.linko_select_healthy_relay() FROM PUBLIC, anon, authenticated;
DROP FUNCTION IF EXISTS public.linko_select_healthy_relay();
