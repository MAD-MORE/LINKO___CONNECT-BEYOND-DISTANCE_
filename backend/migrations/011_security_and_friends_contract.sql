-- LINKO MIGRATION 011
-- Security and control-plane contract repair.
--
-- Design principles:
-- 1. A session UUID is an identifier, never secret key material.
-- 2. Tunnel keys are generated with pgcrypto CSPRNG bytes.
-- 3. Tunnel configuration is available only to authenticated participants
--    of an approved/signaling/connected session.
-- 4. Relay selection is fail-closed: no healthy relay means no endpoint.
-- 5. Friend RPCs are authenticated-only and persist their state in Postgres.

ALTER TABLE public.sessions
  ADD COLUMN IF NOT EXISTS tunnel_key BYTEA;

UPDATE public.sessions
   SET tunnel_key = gen_random_bytes(32)
 WHERE tunnel_key IS NULL;

ALTER TABLE public.sessions
  ALTER COLUMN tunnel_key SET DEFAULT gen_random_bytes(32);
ALTER TABLE public.sessions
  ALTER COLUMN tunnel_key SET NOT NULL;

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
  v_relay_host TEXT;
  v_relay_port INT;
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

  SELECT rn.host, rn.port
    INTO v_relay_host, v_relay_port
    FROM public.relay_nodes rn
   WHERE rn.status = 'healthy'
     AND rn.last_health_at >= NOW() - INTERVAL '90 seconds'
     AND rn.current_sessions < rn.max_sessions
   ORDER BY rn.current_sessions ASC, rn.last_health_at DESC
   LIMIT 1;

  IF v_relay_host IS NULL THEN RAISE EXCEPTION 'no_healthy_relay'; END IF;

  RETURN jsonb_build_object(
    'sessionId', p_session_id,
    'endpoint', jsonb_build_object('host', v_relay_host, 'port', v_relay_port),
    'host', v_relay_host,
    'port', v_relay_port,
    'key', encode(v_session.tunnel_key, 'base64'),
    'role', v_role,
    'expiresAt', EXTRACT(EPOCH FROM v_session.expires_at) * 1000
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.linko_get_or_create_profile(p_display_name TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_user UUID := auth.uid(); v_profile RECORD;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  PERFORM public.ensure_linko_profile(v_user, COALESCE(NULLIF(TRIM(p_display_name),''), 'LINKO User'));
  SELECT * INTO v_profile FROM public.profiles WHERE user_id = v_user;
  RETURN jsonb_build_object('user_id',v_profile.user_id,'linko_id',v_profile.linko_id,'display_name',v_profile.display_name,'username',COALESCE(v_profile.username,v_profile.display_name));
END; $$;

CREATE OR REPLACE FUNCTION public.linko_search_friends(p_query TEXT)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_user UUID := auth.uid(); v_q TEXT := TRIM(p_query); v_results JSONB;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  IF v_q='' THEN RETURN '[]'::jsonb; END IF;
  SELECT COALESCE(jsonb_agg(jsonb_build_object('user_id',p.user_id,'linko_id',p.linko_id,'display_name',p.display_name,'username',COALESCE(p.username,p.display_name),'relationship_status',CASE WHEN fr.status='accepted' THEN 'friend' WHEN fr.status='pending' AND fr.sender_id=v_user THEN 'outgoing_pending' WHEN fr.status='pending' AND fr.receiver_id=v_user THEN 'incoming_pending' ELSE 'none' END,'request_id',fr.id,'is_sharing',EXISTS(SELECT 1 FROM public.devices d WHERE d.user_id=p.user_id AND 'provider'=ANY(d.roles) AND d.revoked_at IS NULL AND d.last_seen_at>=NOW()-INTERVAL '3 minutes'))),'[]'::jsonb) INTO v_results
  FROM public.profiles p LEFT JOIN public.friend_requests fr ON ((fr.sender_id=v_user AND fr.receiver_id=p.user_id) OR (fr.sender_id=p.user_id AND fr.receiver_id=v_user))
  WHERE p.user_id<>v_user AND (p.linko_id ILIKE '%'||v_q||'%' OR p.display_name ILIKE '%'||v_q||'%' OR COALESCE(p.username,'') ILIKE '%'||v_q||'%') LIMIT 25;
  RETURN v_results;
END; $$;

CREATE OR REPLACE FUNCTION public.linko_get_friends()
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_user UUID := auth.uid(); v_friends JSONB;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  SELECT COALESCE(jsonb_agg(jsonb_build_object('user_id',p.user_id,'linko_id',p.linko_id,'display_name',p.display_name,'username',COALESCE(p.username,p.display_name),'relationship_status','friend','is_sharing',EXISTS(SELECT 1 FROM public.devices d WHERE d.user_id=p.user_id AND 'provider'=ANY(d.roles) AND d.revoked_at IS NULL AND d.last_seen_at>=NOW()-INTERVAL '3 minutes'))),'[]'::jsonb) INTO v_friends
  FROM public.friend_requests fr JOIN public.profiles p ON p.user_id=CASE WHEN fr.sender_id=v_user THEN fr.receiver_id ELSE fr.sender_id END
  WHERE fr.status='accepted' AND (fr.sender_id=v_user OR fr.receiver_id=v_user);
  RETURN jsonb_build_object('friends',v_friends);
END; $$;

CREATE OR REPLACE FUNCTION public.linko_get_friend_requests()
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_user UUID := auth.uid(); v_requests JSONB;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  SELECT COALESCE(jsonb_agg(jsonb_build_object('id',fr.id,'incoming',fr.receiver_id=v_user,'status',fr.status,'created_at',EXTRACT(EPOCH FROM fr.created_at)*1000,'profile',jsonb_build_object('user_id',p.user_id,'linko_id',p.linko_id,'display_name',p.display_name,'username',COALESCE(p.username,p.display_name)))),'[]'::jsonb) INTO v_requests
  FROM public.friend_requests fr JOIN public.profiles p ON p.user_id=CASE WHEN fr.receiver_id=v_user THEN fr.sender_id ELSE fr.receiver_id END
  WHERE fr.sender_id=v_user OR fr.receiver_id=v_user;
  RETURN jsonb_build_object('requests',v_requests);
END; $$;

CREATE OR REPLACE FUNCTION public.linko_send_friend_request(p_receiver_user_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_user UUID := auth.uid(); v_req RECORD;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  IF v_user=p_receiver_user_id THEN RAISE EXCEPTION 'cannot_friend_self'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE user_id=p_receiver_user_id) THEN RAISE EXCEPTION 'profile_not_found'; END IF;
  IF EXISTS(SELECT 1 FROM public.friend_requests WHERE sender_id=p_receiver_user_id AND receiver_id=v_user AND status='accepted') THEN RAISE EXCEPTION 'already_friends'; END IF;
  INSERT INTO public.friend_requests(sender_id,receiver_id,status,created_at)
  VALUES(v_user,p_receiver_user_id,'pending',NOW())
  ON CONFLICT (sender_id,receiver_id) DO UPDATE SET status='pending', updated_at=NOW()
  RETURNING * INTO v_req;
  RETURN jsonb_build_object('id',v_req.id,'status',v_req.status,'state','outgoing_pending');
END; $$;

CREATE OR REPLACE FUNCTION public.linko_respond_friend_request(p_request_id UUID,p_status TEXT)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_user UUID := auth.uid(); v_req RECORD;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  IF p_status NOT IN ('accepted','declined') THEN RAISE EXCEPTION 'invalid_friend_request_status'; END IF;
  UPDATE public.friend_requests SET status=p_status, responded_at=NOW()
   WHERE id=p_request_id AND receiver_id=v_user AND status='pending' RETURNING * INTO v_req;
  IF v_req.id IS NULL THEN RAISE EXCEPTION 'request_not_found_or_unauthorized'; END IF;
  RETURN jsonb_build_object('id',v_req.id,'status',v_req.status);
END; $$;

REVOKE ALL ON FUNCTION public.linko_get_or_create_profile(TEXT) FROM anon;
REVOKE ALL ON FUNCTION public.linko_search_friends(TEXT) FROM anon;
REVOKE ALL ON FUNCTION public.linko_get_friends() FROM anon;
REVOKE ALL ON FUNCTION public.linko_get_friend_requests() FROM anon;
REVOKE ALL ON FUNCTION public.linko_send_friend_request(UUID) FROM anon;
REVOKE ALL ON FUNCTION public.linko_respond_friend_request(UUID,TEXT) FROM anon;
GRANT EXECUTE ON FUNCTION public.linko_get_or_create_profile(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_search_friends(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_get_friends() TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_get_friend_requests() TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_send_friend_request(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_respond_friend_request(UUID,TEXT) TO authenticated;
