import pg8000.native
import ssl

SQL = """
-- 1. Grant table access to authenticated and anon roles
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO authenticated;
GRANT SELECT ON public.profiles TO anon, authenticated;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO service_role;

-- 2. Create linko_get_or_create_profile RPC (SECURITY DEFINER)
CREATE OR REPLACE FUNCTION public.linko_get_or_create_profile(
    p_display_name TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_profile RECORD;
    v_linko_id TEXT;
    v_name TEXT;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    SELECT * INTO v_profile FROM public.profiles WHERE user_id = v_user_id;

    IF v_profile.id IS NULL THEN
        v_name := COALESCE(NULLIF(TRIM(p_display_name), ''), 'LINKO User');
        LOOP
            v_linko_id := 'LNK-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
            BEGIN
                INSERT INTO public.profiles (user_id, linko_id, display_name)
                VALUES (v_user_id, v_linko_id, v_name)
                RETURNING * INTO v_profile;
                EXIT;
            EXCEPTION WHEN unique_violation THEN
                CONTINUE;
            END;
        END LOOP;
    END IF;

    RETURN jsonb_build_object(
        'user_id', v_profile.user_id,
        'linko_id', v_profile.linko_id,
        'display_name', v_profile.display_name,
        'username', v_profile.display_name
    );
END;
$$;

-- 3. Create linko_update_profile RPC (SECURITY DEFINER)
CREATE OR REPLACE FUNCTION public.linko_update_profile(
    p_display_name TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_clean_name TEXT;
    v_profile RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'auth_required';
    END IF;

    v_clean_name := TRIM(p_display_name);
    IF LENGTH(v_clean_name) < 2 OR LENGTH(v_clean_name) > 40 THEN
        RAISE EXCEPTION 'invalid_display_name_length';
    END IF;

    PERFORM public.ensure_linko_profile(v_user_id, v_clean_name);

    UPDATE public.profiles
    SET display_name = v_clean_name,
        updated_at = NOW()
    WHERE user_id = v_user_id
    RETURNING * INTO v_profile;

    RETURN jsonb_build_object(
        'user_id', v_profile.user_id,
        'linko_id', v_profile.linko_id,
        'display_name', v_profile.display_name,
        'username', v_profile.display_name
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.linko_get_or_create_profile(TEXT) TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.linko_update_profile(TEXT) TO authenticated;
"""

def main():
    ssl_context = ssl.create_default_context()
    ssl_context.check_hostname = False
    ssl_context.verify_mode = ssl.CERT_NONE

    print("Connecting to Supabase PostgreSQL...")
    try:
        con = pg8000.native.Connection(
            user="postgres.pbnvssbtshvesqwhckfa",
            password="q19C3tZxXHpXGXlQ",
            host="aws-0-eu-central-1.pooler.supabase.com",
            port=6543,
            database="postgres",
            ssl_context=ssl_context
        )
    except Exception as e:
        print(f"Pooler connection failed: {e}. Trying direct...")
        con = pg8000.native.Connection(
            user="postgres",
            password="q19C3tZxXHpXGXlQ",
            host="db.pbnvssbtshvesqwhckfa.supabase.co",
            port=5432,
            database="postgres",
            ssl_context=ssl_context
        )

    print("Connected! Applying SQL fix...")
    con.run(SQL)
    print("✅ SUCCESS! Table grants and SECURITY DEFINER profile RPCs applied!")
    con.close()

if __name__ == "__main__":
    main()
