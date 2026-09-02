-- LINKO migration 015: direct-session failure state and PostgREST cache refresh.
-- Supabase remains the control plane; data-plane traffic stays direct UDP/P2P.

CREATE OR REPLACE FUNCTION public.linko_transition_session(p_session_id UUID, p_state TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  uid UUID := auth.uid();
  s public.sessions;
  r public.devices;
  p public.devices;
  current_state TEXT;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'authentication_required'; END IF;
  SELECT * INTO s FROM public.sessions WHERE id = p_session_id;
  IF s.id IS NULL THEN RAISE EXCEPTION 'session_not_found'; END IF;
  current_state := s.state;
  SELECT * INTO r FROM public.devices WHERE id = s.receiver_device_id;
  SELECT * INTO p FROM public.devices WHERE id = s.provider_device_id;
  IF r.id IS NULL OR p.id IS NULL THEN RAISE EXCEPTION 'session_devices_invalid'; END IF;
  IF r.user_id <> uid AND p.user_id <> uid THEN RAISE EXCEPTION 'session_party_required'; END IF;

  IF s.expires_at <= NOW() AND current_state NOT IN ('revoked','expired','denied','failed') THEN
    UPDATE public.sessions SET state='expired' WHERE id=p_session_id RETURNING * INTO s;
    RAISE EXCEPTION 'session_expired';
  END IF;

  CASE p_state
    WHEN 'approved' THEN
      IF p.user_id <> uid THEN RAISE EXCEPTION 'provider_approval_required'; END IF;
      IF current_state <> 'requested' THEN
        IF current_state = 'approved' THEN RETURN jsonb_build_object('id',s.id,'state',s.state,'expiresAt',EXTRACT(EPOCH FROM s.expires_at)*1000); END IF;
        RAISE EXCEPTION 'invalid_transition';
      END IF;
      UPDATE public.sessions SET state='approved', approved_at=NOW(), expires_at=NOW()+INTERVAL '1 hour' WHERE id=p_session_id RETURNING * INTO s;
    WHEN 'denied' THEN
      IF p.user_id <> uid THEN RAISE EXCEPTION 'provider_approval_required'; END IF;
      IF current_state <> 'requested' THEN
        IF current_state = 'denied' THEN RETURN jsonb_build_object('id',s.id,'state',s.state,'expiresAt',EXTRACT(EPOCH FROM s.expires_at)*1000); END IF;
        RAISE EXCEPTION 'invalid_transition';
      END IF;
      UPDATE public.sessions SET state='denied' WHERE id=p_session_id RETURNING * INTO s;
    WHEN 'signaling' THEN
      IF current_state NOT IN ('approved','signaling') THEN RAISE EXCEPTION 'invalid_transition'; END IF;
      UPDATE public.sessions SET state='signaling' WHERE id=p_session_id RETURNING * INTO s;
    WHEN 'connected' THEN
      IF current_state NOT IN ('signaling','connected') THEN RAISE EXCEPTION 'invalid_transition'; END IF;
      UPDATE public.sessions SET state='connected' WHERE id=p_session_id RETURNING * INTO s;
    WHEN 'failed' THEN
      IF current_state IN ('denied','revoked','expired') THEN
        RETURN jsonb_build_object('id',s.id,'state',s.state,'expiresAt',EXTRACT(EPOCH FROM s.expires_at)*1000);
      END IF;
      UPDATE public.sessions SET state='failed' WHERE id=p_session_id RETURNING * INTO s;
    WHEN 'revoked' THEN
      IF current_state NOT IN ('approved','signaling','connected','failed') THEN RAISE EXCEPTION 'invalid_transition'; END IF;
      UPDATE public.sessions SET state='revoked', revoked_at=NOW() WHERE id=p_session_id RETURNING * INTO s;
    ELSE
      RAISE EXCEPTION 'invalid_session_state_transition';
  END CASE;

  RETURN jsonb_build_object('id',s.id,'receiverDeviceId',s.receiver_device_id,'providerDeviceId',s.provider_device_id,'state',s.state,'createdAt',EXTRACT(EPOCH FROM s.created_at)*1000,'expiresAt',EXTRACT(EPOCH FROM s.expires_at)*1000,'approvedAt',CASE WHEN s.approved_at IS NULL THEN NULL ELSE EXTRACT(EPOCH FROM s.approved_at)*1000 END);
END;
$$;

GRANT EXECUTE ON FUNCTION public.linko_get_session(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_transition_session(UUID, TEXT) TO authenticated;
NOTIFY pgrst, 'reload schema';
