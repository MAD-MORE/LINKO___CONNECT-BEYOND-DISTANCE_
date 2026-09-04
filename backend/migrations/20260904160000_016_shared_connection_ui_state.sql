-- Shared live UI state for a connected LINKO session.
-- This stores metadata/counters only; packet payloads never enter the database.

CREATE TABLE IF NOT EXISTS public.session_ui_state (
    session_id UUID NOT NULL REFERENCES public.sessions(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('provider', 'receiver')),
    tx_bytes BIGINT NOT NULL DEFAULT 0 CHECK (tx_bytes >= 0),
    rx_bytes BIGINT NOT NULL DEFAULT 0 CHECK (rx_bytes >= 0),
    latency_ms INTEGER NOT NULL DEFAULT 0 CHECK (latency_ms >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, device_id)
);

CREATE INDEX IF NOT EXISTS session_ui_state_updated_idx
    ON public.session_ui_state(session_id, updated_at DESC);

ALTER TABLE public.session_ui_state ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.session_ui_state FROM anon;
REVOKE ALL ON TABLE public.session_ui_state FROM authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.session_ui_state TO service_role;

CREATE OR REPLACE FUNCTION public.linko_publish_session_ui_state(
    p_session_id UUID,
    p_device_id UUID,
    p_role TEXT,
    p_tx_bytes BIGINT,
    p_rx_bytes BIGINT,
    p_latency_ms INTEGER DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    session_row public.sessions%ROWTYPE;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    SELECT s.* INTO session_row
    FROM public.sessions s
    JOIN public.devices d
      ON d.id = CASE
          WHEN p_role = 'receiver' THEN s.receiver_device_id
          WHEN p_role = 'provider' THEN s.provider_device_id
          ELSE NULL
      END
    WHERE s.id = p_session_id
      AND d.id = p_device_id
      AND d.user_id = auth.uid();

    IF NOT FOUND THEN
        RAISE EXCEPTION 'session_device_not_authorized';
    END IF;

    IF session_row.state NOT IN ('signaling', 'connected') THEN
        RAISE EXCEPTION 'session_not_active';
    END IF;

    INSERT INTO public.session_ui_state (
        session_id, device_id, role, tx_bytes, rx_bytes, latency_ms, updated_at
    ) VALUES (
        p_session_id,
        p_device_id,
        p_role,
        GREATEST(0, p_tx_bytes),
        GREATEST(0, p_rx_bytes),
        GREATEST(0, p_latency_ms),
        now()
    )
    ON CONFLICT (session_id, device_id) DO UPDATE SET
        role = EXCLUDED.role,
        tx_bytes = GREATEST(public.session_ui_state.tx_bytes, EXCLUDED.tx_bytes),
        rx_bytes = GREATEST(public.session_ui_state.rx_bytes, EXCLUDED.rx_bytes),
        latency_ms = EXCLUDED.latency_ms,
        updated_at = EXCLUDED.updated_at;

    RETURN jsonb_build_object('ok', true, 'sessionId', p_session_id::text);
END;
$$;

CREATE OR REPLACE FUNCTION public.linko_get_session_ui_state(p_session_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    session_row public.sessions%ROWTYPE;
    receiver_name TEXT;
    provider_name TEXT;
    receiver_linko_id TEXT;
    provider_linko_id TEXT;
    receiver_tx BIGINT := 0;
    receiver_rx BIGINT := 0;
    receiver_latency INTEGER := 0;
    provider_tx BIGINT := 0;
    provider_rx BIGINT := 0;
    provider_latency INTEGER := 0;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    SELECT s.* INTO session_row
    FROM public.sessions s
    WHERE s.id = p_session_id
      AND EXISTS (
          SELECT 1 FROM public.devices d
          WHERE d.id IN (s.receiver_device_id, s.provider_device_id)
            AND d.user_id = auth.uid()
      );

    IF NOT FOUND THEN
        RAISE EXCEPTION 'session_not_authorized';
    END IF;

    SELECT d.name, d.id::text INTO receiver_name, receiver_linko_id
    FROM public.devices d WHERE d.id = session_row.receiver_device_id;
    SELECT d.name, d.id::text INTO provider_name, provider_linko_id
    FROM public.devices d WHERE d.id = session_row.provider_device_id;

    SELECT
        COALESCE(MAX(CASE WHEN role = 'receiver' THEN tx_bytes END), 0),
        COALESCE(MAX(CASE WHEN role = 'receiver' THEN rx_bytes END), 0),
        COALESCE(MAX(CASE WHEN role = 'receiver' THEN latency_ms END), 0),
        COALESCE(MAX(CASE WHEN role = 'provider' THEN tx_bytes END), 0),
        COALESCE(MAX(CASE WHEN role = 'provider' THEN rx_bytes END), 0),
        COALESCE(MAX(CASE WHEN role = 'provider' THEN latency_ms END), 0)
    INTO receiver_tx, receiver_rx, receiver_latency, provider_tx, provider_rx, provider_latency
    FROM public.session_ui_state
    WHERE session_id = p_session_id;

    RETURN jsonb_build_object(
        'sessionId', session_row.id::text,
        'state', session_row.state,
        'receiverDeviceId', session_row.receiver_device_id::text,
        'receiverName', receiver_name,
        'receiverLinkoId', receiver_linko_id,
        'providerDeviceId', session_row.provider_device_id::text,
        'providerName', provider_name,
        'providerLinkoId', provider_linko_id,
        'receiverTxBytes', receiver_tx,
        'receiverRxBytes', receiver_rx,
        'providerTxBytes', provider_tx,
        'providerRxBytes', provider_rx,
        'sharedLatencyMs', GREATEST(receiver_latency, provider_latency),
        'updatedAt', now()
    );
END;
$$;

REVOKE ALL ON FUNCTION public.linko_publish_session_ui_state(UUID, UUID, TEXT, BIGINT, BIGINT, INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.linko_get_session_ui_state(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.linko_publish_session_ui_state(UUID, UUID, TEXT, BIGINT, BIGINT, INTEGER) TO authenticated;
GRANT EXECUTE ON FUNCTION public.linko_get_session_ui_state(UUID) TO authenticated;
