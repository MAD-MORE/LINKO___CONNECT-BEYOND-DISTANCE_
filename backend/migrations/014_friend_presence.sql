-- LINKO real friend presence contract.
-- A friend is online when at least one non-revoked device has a fresh heartbeat.
-- Presence is control-plane state; it does not imply Internet sharing is active.

CREATE OR REPLACE FUNCTION public.linko_get_friends()
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_user UUID := auth.uid(); v_friends JSONB;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  SELECT COALESCE(jsonb_agg(jsonb_build_object(
    'user_id',p.user_id,
    'linko_id',p.linko_id,
    'display_name',p.display_name,
    'username',COALESCE(p.username,p.display_name),
    'relationship_status','friend',
    'is_online',EXISTS(SELECT 1 FROM public.devices d WHERE d.user_id=p.user_id AND d.revoked_at IS NULL AND d.last_seen_at>=NOW()-INTERVAL '45 seconds'),
    'is_sharing',EXISTS(SELECT 1 FROM public.devices d WHERE d.user_id=p.user_id AND 'provider'=ANY(d.roles) AND d.revoked_at IS NULL AND d.last_seen_at>=NOW()-INTERVAL '45 seconds')
  ) ORDER BY p.display_name),'[]'::jsonb) INTO v_friends
  FROM public.friend_requests fr
  JOIN public.profiles p ON p.user_id=CASE WHEN fr.sender_id=v_user THEN fr.receiver_id ELSE fr.sender_id END
  WHERE fr.status='accepted' AND (fr.sender_id=v_user OR fr.receiver_id=v_user);
  RETURN jsonb_build_object('friends',v_friends);
END; $$;

CREATE OR REPLACE FUNCTION public.linko_search_friends(p_query TEXT)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_user UUID := auth.uid(); v_q TEXT := TRIM(p_query); v_results JSONB;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  IF v_q='' THEN RETURN '[]'::jsonb; END IF;
  SELECT COALESCE(jsonb_agg(jsonb_build_object(
    'user_id',p.user_id,'linko_id',p.linko_id,'display_name',p.display_name,'username',COALESCE(p.username,p.display_name),
    'relationship_status',CASE WHEN fr.status='accepted' THEN 'friend' WHEN fr.status='pending' AND fr.sender_id=v_user THEN 'outgoing_pending' WHEN fr.status='pending' AND fr.receiver_id=v_user THEN 'incoming_pending' ELSE 'none' END,
    'request_id',fr.id,
    'is_online',EXISTS(SELECT 1 FROM public.devices d WHERE d.user_id=p.user_id AND d.revoked_at IS NULL AND d.last_seen_at>=NOW()-INTERVAL '45 seconds'),
    'is_sharing',EXISTS(SELECT 1 FROM public.devices d WHERE d.user_id=p.user_id AND 'provider'=ANY(d.roles) AND d.revoked_at IS NULL AND d.last_seen_at>=NOW()-INTERVAL '45 seconds')
  )),'[]'::jsonb) INTO v_results
  FROM public.profiles p LEFT JOIN public.friend_requests fr ON ((fr.sender_id=v_user AND fr.receiver_id=p.user_id) OR (fr.sender_id=p.user_id AND fr.receiver_id=v_user))
  WHERE p.user_id<>v_user AND (p.linko_id ILIKE '%'||v_q||'%' OR p.display_name ILIKE '%'||v_q||'%' OR COALESCE(p.username,'') ILIKE '%'||v_q||'%') LIMIT 25;
  RETURN v_results;
END; $$;

GRANT EXECUTE ON FUNCTION public.linko_get_friends() TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_search_friends(TEXT) TO authenticated;
