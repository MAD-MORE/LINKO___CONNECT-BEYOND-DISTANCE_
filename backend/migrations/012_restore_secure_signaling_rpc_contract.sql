-- LINKO signaling contract repair.
-- Android calls these RPCs directly through Supabase PostgREST.
-- Realtime remains the low-latency notification layer; Postgres is the source of truth.

CREATE TABLE IF NOT EXISTS public.linko_signaling_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES public.sessions(id) ON DELETE CASCADE,
  sender_device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('offer','answer','ice')),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.linko_signaling_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.linko_signaling_events REPLICA IDENTITY FULL;
CREATE INDEX IF NOT EXISTS idx_linko_signaling_session_created
  ON public.linko_signaling_events(session_id, created_at ASC);

DROP POLICY IF EXISTS linko_signaling_insert_participant ON public.linko_signaling_events;
CREATE POLICY linko_signaling_insert_participant
ON public.linko_signaling_events FOR INSERT TO authenticated
WITH CHECK (
  EXISTS (
    SELECT 1
    FROM public.sessions s
    JOIN public.devices d ON d.id = linko_signaling_events.sender_device_id
    WHERE s.id = linko_signaling_events.session_id
      AND (s.receiver_device_id = d.id OR s.provider_device_id = d.id)
      AND d.user_id = (SELECT auth.uid())
      AND d.revoked_at IS NULL
  )
);

DROP POLICY IF EXISTS linko_signaling_select_participant ON public.linko_signaling_events;
CREATE POLICY linko_signaling_select_participant
ON public.linko_signaling_events FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1
    FROM public.sessions s
    JOIN public.devices d ON d.id IN (s.receiver_device_id, s.provider_device_id)
    WHERE s.id = linko_signaling_events.session_id
      AND d.user_id = (SELECT auth.uid())
      AND d.revoked_at IS NULL
  )
);

DO $$ BEGIN
  BEGIN ALTER PUBLICATION supabase_realtime ADD TABLE public.linko_signaling_events;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
END $$;

CREATE OR REPLACE FUNCTION public.linko_signaling_ticket(p_session_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE uid UUID := (SELECT auth.uid()); device UUID;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  SELECT d.id INTO device
  FROM public.devices d
  JOIN public.sessions s ON (s.receiver_device_id=d.id OR s.provider_device_id=d.id)
  WHERE s.id=p_session_id AND d.user_id=uid AND d.revoked_at IS NULL
  ORDER BY d.last_seen_at DESC LIMIT 1;
  IF device IS NULL THEN RAISE EXCEPTION 'session_participant_required'; END IF;
  RETURN jsonb_build_object('sessionId',p_session_id,'deviceId',device,'expiresAt',EXTRACT(EPOCH FROM NOW()+INTERVAL '5 minutes')*1000);
END; $$;

CREATE OR REPLACE FUNCTION public.linko_send_signal(p_session_id UUID,p_kind TEXT,p_payload JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE uid UUID := (SELECT auth.uid()); sender UUID; recipient UUID; e RECORD;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  IF p_kind NOT IN ('offer','answer','ice') THEN RAISE EXCEPTION 'invalid_signal_kind'; END IF;

  SELECT CASE WHEN dr.user_id=uid THEN s.receiver_device_id WHEN dp.user_id=uid THEN s.provider_device_id END,
         CASE WHEN dr.user_id=uid THEN s.provider_device_id WHEN dp.user_id=uid THEN s.receiver_device_id END
    INTO sender, recipient
    FROM public.sessions s
    JOIN public.devices dr ON dr.id=s.receiver_device_id
    JOIN public.devices dp ON dp.id=s.provider_device_id
   WHERE s.id=p_session_id AND (dr.user_id=uid OR dp.user_id=uid)
     AND dr.revoked_at IS NULL AND dp.revoked_at IS NULL;

  IF sender IS NULL THEN RAISE EXCEPTION 'session_participant_required'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.sessions WHERE id=p_session_id AND state IN ('approved','signaling','connected')) THEN
    RAISE EXCEPTION 'session_not_ready';
  END IF;

  INSERT INTO public.linko_signaling_events(session_id,sender_device_id,kind,payload)
  VALUES(p_session_id,sender,p_kind,COALESCE(p_payload,'{}'::jsonb)) RETURNING * INTO e;

  RETURN jsonb_build_object('id',e.id,'sessionId',e.session_id,'senderDeviceId',e.sender_device_id,'recipientDeviceId',recipient,'kind',e.kind,'payload',e.payload,'createdAt',EXTRACT(EPOCH FROM e.created_at)*1000);
END; $$;

CREATE OR REPLACE FUNCTION public.linko_receive_signals(p_session_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE uid UUID := (SELECT auth.uid()); device UUID; result JSONB;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'auth_required'; END IF;
  SELECT d.id INTO device
  FROM public.devices d
  JOIN public.sessions s ON (s.receiver_device_id=d.id OR s.provider_device_id=d.id)
  WHERE s.id=p_session_id AND d.user_id=uid AND d.revoked_at IS NULL
  LIMIT 1;
  IF device IS NULL THEN RAISE EXCEPTION 'session_participant_required'; END IF;

  SELECT COALESCE(jsonb_agg(jsonb_build_object('id',e.id,'sessionId',e.session_id,'senderDeviceId',e.sender_device_id,'recipientDeviceId',CASE WHEN e.sender_device_id=s.receiver_device_id THEN s.provider_device_id ELSE s.receiver_device_id END,'kind',e.kind,'payload',e.payload,'createdAt',EXTRACT(EPOCH FROM e.created_at)*1000) ORDER BY e.created_at ASC),'[]'::jsonb)
  INTO result
  FROM public.linko_signaling_events e
  JOIN public.sessions s ON s.id=e.session_id
  WHERE e.session_id=p_session_id AND e.sender_device_id<>device;

  RETURN jsonb_build_object('signals',result);
END; $$;

REVOKE ALL ON FUNCTION public.linko_signaling_ticket(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.linko_send_signal(UUID,TEXT,JSONB) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.linko_receive_signals(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.linko_signaling_ticket(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_send_signal(UUID,TEXT,JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_receive_signals(UUID) TO authenticated;
