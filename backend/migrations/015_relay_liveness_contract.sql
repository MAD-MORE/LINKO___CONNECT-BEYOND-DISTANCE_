-- LINKO migration 015: make relay liveness authoritative at selection time.
-- Session creation does not depend on relay availability; tunnel configuration does.

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
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.devices
        WHERE id = p_receiver_device_id
          AND user_id = v_user_id
          AND revoked_at IS NULL
    ) THEN
        RAISE EXCEPTION 'unauthorized_receiver_device';
    END IF;

    IF p_receiver_device_id = p_provider_device_id THEN
        RAISE EXCEPTION 'cannot_connect_to_self';
    END IF;

    SELECT id, public_key INTO v_provider
    FROM public.devices
    WHERE id = p_provider_device_id
      AND revoked_at IS NULL;

    IF v_provider.id IS NULL THEN
        RAISE EXCEPTION 'provider_not_found';
    END IF;

    INSERT INTO public.sessions (
        receiver_device_id,
        provider_device_id,
        state,
        expires_at,
        tunnel_key
    )
    VALUES (
        p_receiver_device_id,
        p_provider_device_id,
        'requested',
        NOW() + INTERVAL '5 minutes',
        gen_random_bytes(32)
    )
    RETURNING * INTO v_session;

    RETURN jsonb_build_object(
        'id', v_session.id,
        'providerPublicKey', v_provider.public_key,
        'relayUrl', NULL,
        'state', v_session.state,
        'expiresAt', EXTRACT(EPOCH FROM v_session.expires_at) * 1000
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.linko_create_session(UUID, UUID) TO authenticated;
