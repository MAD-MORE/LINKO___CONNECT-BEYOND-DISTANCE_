import pg8000.native
import ssl
import json

def main():
    print("Auditing Supabase Friendship Database Tables, RPCs, and Publications...")
    ssl_context = ssl.create_default_context()
    ssl_context.check_hostname = False
    ssl_context.verify_mode = ssl.CERT_NONE

    conn = pg8000.native.Connection(
        user="postgres.pbnvssbtshvesqwhckfa",
        password="q19C3tZxXHpXGXlQ",
        host="aws-0-eu-central-1.pooler.supabase.com",
        port=6543,
        database="postgres",
        ssl_context=ssl_context
    )

    # 1. Ensure supabase_realtime publication includes friend_requests and sessions
    print("1. Checking Realtime publication...")
    conn.run("""
        DO $$
        BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM pg_publication_tables 
                WHERE pubname = 'supabase_realtime' AND tablename = 'friend_requests'
            ) THEN
                ALTER PUBLICATION supabase_realtime ADD TABLE public.friend_requests;
            END IF;

            IF NOT EXISTS (
                SELECT 1 FROM pg_publication_tables 
                WHERE pubname = 'supabase_realtime' AND tablename = 'sessions'
            ) THEN
                ALTER PUBLICATION supabase_realtime ADD TABLE public.sessions;
            END IF;

            IF NOT EXISTS (
                SELECT 1 FROM pg_publication_tables 
                WHERE pubname = 'supabase_realtime' AND tablename = 'profiles'
            ) THEN
                ALTER PUBLICATION supabase_realtime ADD TABLE public.profiles;
            END IF;
        END $$;
    """)
    print("[OK] Realtime publication enabled for friend_requests, sessions, and profiles!")

    # 2. Optimize linko_search_friends to handle @ stripping, lowercase, partial LINKO IDs
    print("2. Optimizing linko_search_friends RPC...")
    conn.run("""
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
        v_clean := LTRIM(TRIM(p_query), '@');
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
                      AND d.last_seen_at >= NOW() - INTERVAL '5 minutes'
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
    """)

    # 3. Optimize linko_send_friend_request to auto-accept if reverse request is pending
    print("3. Optimizing linko_send_friend_request RPC with auto-match...")
    conn.run("""
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
        v_existing RECORD;
        v_req RECORD;
    BEGIN
        v_user_id := auth.uid();
        IF v_user_id IS NULL THEN
            RAISE EXCEPTION 'auth_required';
        END IF;

        IF v_user_id = p_receiver_user_id THEN
            RAISE EXCEPTION 'cannot_friend_self';
        END IF;

        -- Check if the reverse request already exists from the receiver
        SELECT * INTO v_existing
        FROM public.friend_requests
        WHERE sender_id = p_receiver_user_id AND receiver_id = v_user_id;

        IF v_existing.id IS NOT NULL THEN
            -- Automatically accept mutually!
            UPDATE public.friend_requests
            SET status = 'accepted', updated_at = NOW()
            WHERE id = v_existing.id
            RETURNING * INTO v_req;

            RETURN jsonb_build_object('id', v_req.id, 'status', 'accepted', 'state', 'friend');
        END IF;

        -- Otherwise upsert our outgoing request
        INSERT INTO public.friend_requests (sender_id, receiver_id, status, created_at, updated_at)
        VALUES (v_user_id, p_receiver_user_id, 'pending', NOW(), NOW())
        ON CONFLICT (sender_id, receiver_id) DO UPDATE
        SET status = 'pending', updated_at = NOW()
        RETURNING * INTO v_req;

        RETURN jsonb_build_object('id', v_req.id, 'status', v_req.status, 'state', 'outgoing_pending');
    END;
    $$;
    """)

    conn.close()
    print("[OK] All friendship audits and resilience optimizations applied successfully!")

if __name__ == "__main__":
    main()
