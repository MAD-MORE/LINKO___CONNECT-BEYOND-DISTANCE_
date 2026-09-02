-- LINKO migration 011: restore the session lookup RPC used by Android clients.
-- The client polls this function while a provider approves and establishes a session.

CREATE OR REPLACE FUNCTION public.linko_get_session(
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
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    SELECT s.id, s.state, s.expires_at
    INTO v_session
    FROM public.sessions s
    JOIN public.devices dr ON dr.id = s.receiver_device_id
    JOIN public.devices dp ON dp.id = s.provider_device_id
    WHERE s.id = p_session_id
      AND (dr.user_id = v_user_id OR dp.user_id = v_user_id);

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

GRANT EXECUTE ON FUNCTION public.linko_get_session(UUID) TO authenticated;
