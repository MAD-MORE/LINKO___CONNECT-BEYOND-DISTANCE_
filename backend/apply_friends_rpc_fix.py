import pg8000.native
import ssl

SQL = """
-- 1. Create linko_search_friends RPC
CREATE OR REPLACE FUNCTION public.linko_search_friends(
    p_query TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_clean TEXT;
    v_results JSONB;
BEGIN
    v_user_id := auth.uid();
    v_clean := TRIM(p_query);
    IF v_clean = '' THEN
        RETURN '[]'::JSONB;
    END IF;

    SELECT jsonb_agg(
        jsonb_build_object(
            'user_id', p.user_id,
            'linko_id', p.linko_id,
            'display_name', p.display_name,
            'username', p.display_name,
            'relationship_status', CASE
                WHEN fr.status = 'accepted' THEN 'friend'
                WHEN fr.status = 'pending' AND fr.sender_id = v_user_id THEN 'outgoing_pending'
                WHEN fr.status = 'pending' AND fr.receiver_id = v_user_id THEN 'incoming_pending'
                ELSE 'none'
            END,
            'request_id', fr.id,
            'is_sharing', EXISTS(
                SELECT 1 FROM public.devices d 
                WHERE d.user_id = p.user_id 
                  AND 'provider' = ANY(d.roles) 
                  AND d.last_seen_at >= NOW() - INTERVAL '3 minutes'
            )
        )
    ) INTO v_results
    FROM public.profiles p
    LEFT JOIN public.friend_requests fr ON (
        (fr.sender_id = v_user_id AND fr.receiver_id = p.user_id) OR
        (fr.sender_id = p.user_id AND fr.receiver_id = v_user_id)
    )
    WHERE (v_user_id IS NULL OR p.user_id <> v_user_id)
      AND (
          p.linko_id ILIKE '%' || v_clean || '%' OR
          p.display_name ILIKE '%' || v_clean || '%'
      )
    LIMIT 25;

    RETURN COALESCE(v_results, '[]'::JSONB);
END;
$$;

-- 2. Create linko_get_friends RPC
CREATE OR REPLACE FUNCTION public.linko_get_friends()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_friends JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    SELECT jsonb_agg(
        jsonb_build_object(
            'user_id', p.user_id,
            'linko_id', p.linko_id,
            'display_name', p.display_name,
            'username', p.display_name,
            'relationship_status', 'friend',
            'is_sharing', EXISTS(
                SELECT 1 FROM public.devices d 
                WHERE d.user_id = p.user_id 
                  AND 'provider' = ANY(d.roles) 
                  AND d.last_seen_at >= NOW() - INTERVAL '3 minutes'
            )
        )
    ) INTO v_friends
    FROM public.friend_requests fr
    JOIN public.profiles p ON (
        CASE WHEN fr.sender_id = v_user_id THEN fr.receiver_id ELSE fr.sender_id END = p.user_id
    )
    WHERE fr.status = 'accepted'
      AND (fr.sender_id = v_user_id OR fr.receiver_id = v_user_id);

    RETURN jsonb_build_object('friends', COALESCE(v_friends, '[]'::JSONB));
END;
$$;

-- 3. Create linko_get_friend_requests RPC
CREATE OR REPLACE FUNCTION public.linko_get_friend_requests()
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

    SELECT jsonb_agg(
        jsonb_build_object(
            'id', fr.id,
            'incoming', (fr.receiver_id = v_user_id),
            'status', fr.status,
            'created_at', EXTRACT(EPOCH FROM fr.created_at) * 1000,
            'profile', jsonb_build_object(
                'user_id', p.user_id,
                'linko_id', p.linko_id,
                'display_name', p.display_name,
                'username', p.display_name
            )
        )
    ) INTO v_requests
    FROM public.friend_requests fr
    JOIN public.profiles p ON (
        CASE WHEN fr.receiver_id = v_user_id THEN fr.sender_id ELSE fr.receiver_id END = p.user_id
    )
    WHERE fr.sender_id = v_user_id OR fr.receiver_id = v_user_id;

    RETURN jsonb_build_object('requests', COALESCE(v_requests, '[]'::JSONB));
END;
$$;

-- 4. Create linko_send_friend_request RPC
CREATE OR REPLACE FUNCTION public.linko_send_friend_request(
    p_receiver_user_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_req RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    IF v_user_id = p_receiver_user_id THEN
        RAISE EXCEPTION 'cannot_friend_self';
    END IF;

    INSERT INTO public.friend_requests (sender_id, receiver_id, status, created_at, updated_at)
    VALUES (v_user_id, p_receiver_user_id, 'pending', NOW(), NOW())
    ON CONFLICT (sender_id, receiver_id) DO UPDATE
    SET status = 'pending', updated_at = NOW()
    RETURNING * INTO v_req;

    RETURN jsonb_build_object('id', v_req.id, 'status', v_req.status, 'state', 'outgoing_pending');
END;
$$;

-- 5. Create linko_respond_friend_request RPC
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
    v_user_id UUID;
    v_req RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    UPDATE public.friend_requests
    SET status = p_status, updated_at = NOW()
    WHERE id = p_request_id AND receiver_id = v_user_id
    RETURNING * INTO v_req;

    IF v_req.id IS NULL THEN
        RAISE EXCEPTION 'request_not_found_or_unauthorized';
    END IF;

    RETURN jsonb_build_object('id', v_req.id, 'status', v_req.status);
END;
$$;

-- Grant execute permissions to anon and authenticated
GRANT EXECUTE ON FUNCTION public.linko_search_friends TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.linko_get_friends TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.linko_get_friend_requests TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.linko_send_friend_request TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.linko_respond_friend_request TO anon, authenticated, service_role;
"""

def main():
    print("Applying direct PostgreSQL friend RPCs to Supabase...")
    ssl_context = ssl.create_default_context()
    ssl_context.check_hostname = False
    ssl_context.verify_mode = ssl.CERT_NONE

    try:
        conn = pg8000.native.Connection(
            user="postgres.pbnvssbtshvesqwhckfa",
            password="q19C3tZxXHpXGXlQ",
            host="aws-0-eu-central-1.pooler.supabase.com",
            port=6543,
            database="postgres",
            ssl_context=ssl_context
        )
    except Exception as e:
        print(f"Pooler failed: {e}. Trying direct...")
        conn = pg8000.native.Connection(
            user="postgres",
            password="q19C3tZxXHpXGXlQ",
            host="db.pbnvssbtshvesqwhckfa.supabase.co",
            port=5432,
            database="postgres",
            ssl_context=ssl_context
        )

    print("Connected! Executing friend RPC SQL...")
    conn.run(SQL)
    conn.close()
    print("[OK] Successfully applied all direct friend RPCs!")

if __name__ == "__main__":
    main()
