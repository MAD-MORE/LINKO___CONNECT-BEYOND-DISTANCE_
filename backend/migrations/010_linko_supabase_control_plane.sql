-- ============================================================
-- LINKO MIGRATION 010: Complete Supabase Control Plane RPC Suite
-- Makes Supabase the permanent, direct control-plane for LINKO.
-- All client operations execute directly via PostgreSQL RPCs.
-- ============================================================

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
    v_device_id UUID;
    v_device RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    -- Ensure profile exists
    PERFORM public.ensure_linko_profile(v_user_id, p_name);

    -- Insert or update device
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
    v_relay_host TEXT := 'linko-relay.fly.dev';
    v_relay_port INT := 7000;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    -- Verify receiver device belongs to caller
    IF NOT EXISTS (SELECT 1 FROM public.devices WHERE id = p_receiver_device_id AND user_id = v_user_id) THEN
        RAISE EXCEPTION 'unauthorized_receiver_device';
    END IF;

    IF p_receiver_device_id = p_provider_device_id THEN
        RAISE EXCEPTION 'cannot_connect_to_self';
    END IF;

    -- Look up provider device
    SELECT id, public_key INTO v_provider
    FROM public.devices
    WHERE id = p_provider_device_id AND revoked_at IS NULL;

    IF v_provider.id IS NULL THEN
        RAISE EXCEPTION 'provider_not_found';
    END IF;

    -- Check if healthy relay node is registered
    SELECT host, port INTO v_relay_host, v_relay_port
    FROM public.relay_nodes
    WHERE status = 'healthy'
    ORDER BY current_sessions ASC
    LIMIT 1;

    IF v_relay_host IS NULL THEN
        v_relay_host := 'linko-relay.fly.dev';
        v_relay_port := 7000;
    END IF;

    -- Create session
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
    v_is_provider BOOLEAN;
    v_is_receiver BOOLEAN;
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
    v_relay_host TEXT := 'linko-relay.fly.dev';
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

    -- Lookup relay host
    SELECT host, port INTO v_relay_host, v_relay_port
    FROM public.relay_nodes
    WHERE status = 'healthy'
    ORDER BY current_sessions ASC
    LIMIT 1;

    IF v_relay_host IS NULL THEN
        v_relay_host := 'linko-relay.fly.dev';
        v_relay_port := 7000;
    END IF;

    -- Generate a deterministic 32-byte AES-GCM session key derived from session UUID and internal secret
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

-- 11. Grant Execute Permissions
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

