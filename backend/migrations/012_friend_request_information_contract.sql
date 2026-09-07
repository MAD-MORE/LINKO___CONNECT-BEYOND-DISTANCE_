-- LINKO MIGRATION 012
-- Complete friend-request information contract.
-- Every request/response carries enough profile + state information for
-- both devices to render the same relationship state without guessing.

CREATE OR REPLACE FUNCTION public.linko_send_friend_request(p_receiver_user_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user UUID := auth.uid();
  v_req RECORD;
  v_sender RECORD;
  v_receiver RECORD;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  IF v_user = p_receiver_user_id THEN RAISE EXCEPTION 'cannot_friend_self'; END IF;

  SELECT * INTO v_sender FROM public.profiles WHERE user_id = v_user;
  SELECT * INTO v_receiver FROM public.profiles WHERE user_id = p_receiver_user_id;
  IF v_receiver.user_id IS NULL THEN RAISE EXCEPTION 'profile_not_found'; END IF;

  IF EXISTS (
    SELECT 1 FROM public.friend_requests
    WHERE ((sender_id = v_user AND receiver_id = p_receiver_user_id)
       OR (sender_id = p_receiver_user_id AND receiver_id = v_user))
      AND status = 'accepted'
  ) THEN
    RAISE EXCEPTION 'already_friends';
  END IF;

  INSERT INTO public.friend_requests(sender_id, receiver_id, status, created_at)
  VALUES(v_user, p_receiver_user_id, 'pending', NOW())
  ON CONFLICT (sender_id, receiver_id)
  DO UPDATE SET status = 'pending', updated_at = NOW()
  RETURNING * INTO v_req;

  RETURN jsonb_build_object(
    'id', v_req.id,
    'status', v_req.status,
    'state', 'outgoing_pending',
    'created_at', EXTRACT(EPOCH FROM v_req.created_at) * 1000,
    'sender', jsonb_build_object(
      'user_id', v_sender.user_id,
      'linko_id', v_sender.linko_id,
      'display_name', v_sender.display_name,
      'username', COALESCE(v_sender.username, v_sender.display_name)
    ),
    'receiver', jsonb_build_object(
      'user_id', v_receiver.user_id,
      'linko_id', v_receiver.linko_id,
      'display_name', v_receiver.display_name,
      'username', COALESCE(v_receiver.username, v_receiver.display_name)
    )
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.linko_respond_friend_request(
  p_request_id UUID,
  p_status TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user UUID := auth.uid();
  v_req RECORD;
  v_sender RECORD;
  v_receiver RECORD;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  IF p_status NOT IN ('accepted', 'declined') THEN
    RAISE EXCEPTION 'invalid_friend_request_status';
  END IF;

  UPDATE public.friend_requests
     SET status = p_status,
         responded_at = NOW(),
         updated_at = NOW()
   WHERE id = p_request_id
     AND receiver_id = v_user
     AND status = 'pending'
   RETURNING * INTO v_req;

  IF v_req.id IS NULL THEN
    RAISE EXCEPTION 'request_not_found_or_unauthorized';
  END IF;

  SELECT * INTO v_sender FROM public.profiles WHERE user_id = v_req.sender_id;
  SELECT * INTO v_receiver FROM public.profiles WHERE user_id = v_req.receiver_id;

  RETURN jsonb_build_object(
    'id', v_req.id,
    'status', v_req.status,
    'state', CASE WHEN v_req.status = 'accepted' THEN 'friends' ELSE 'declined' END,
    'created_at', EXTRACT(EPOCH FROM v_req.created_at) * 1000,
    'responded_at', EXTRACT(EPOCH FROM v_req.responded_at) * 1000,
    'sender', jsonb_build_object(
      'user_id', v_sender.user_id,
      'linko_id', v_sender.linko_id,
      'display_name', v_sender.display_name,
      'username', COALESCE(v_sender.username, v_sender.display_name)
    ),
    'receiver', jsonb_build_object(
      'user_id', v_receiver.user_id,
      'linko_id', v_receiver.linko_id,
      'display_name', v_receiver.display_name,
      'username', COALESCE(v_receiver.username, v_receiver.display_name)
    )
  );
END;
$$;

REVOKE ALL ON FUNCTION public.linko_send_friend_request(UUID) FROM anon;
REVOKE ALL ON FUNCTION public.linko_respond_friend_request(UUID,TEXT) FROM anon;
GRANT EXECUTE ON FUNCTION public.linko_send_friend_request(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_respond_friend_request(UUID,TEXT) TO authenticated;

-- Make the request row itself visible to Realtime with old values available.
ALTER TABLE public.friend_requests REPLICA IDENTITY FULL;
DO $$
BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE public.friend_requests;
EXCEPTION
  WHEN duplicate_object THEN NULL;
END;
$$;
