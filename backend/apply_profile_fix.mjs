import pg from 'pg';
const { Client } = pg;

// Use pooler or direct DB connection
const DB_URL = 'postgresql://postgres:q19C3tZxXHpXGXlQ@db.pbnvssbtshvesqwhckfa.supabase.co:5432/postgres';
const POOLER_URL = 'postgresql://postgres.pbnvssbtshvesqwhckfa:q19C3tZxXHpXGXlQ@aws-0-eu-central-1.pooler.supabase.com:6543/postgres';

const SQL = `
-- 1. Grant table access
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
`;

async function applyFix() {
  console.log('Connecting to PostgreSQL database...');
  let client;
  try {
    client = new Client({ connectionString: DB_URL, ssl: { rejectUnauthorized: false } });
    await client.connect();
  } catch (e) {
    console.log('Direct connection failed, trying pooler:', e.message);
    client = new Client({ connectionString: POOLER_URL, ssl: { rejectUnauthorized: false } });
    await client.connect();
  }

  console.log('Connected! Applying Grants and Profile RPC functions...');
  await client.query(SQL);
  console.log('✅ Successfully applied profile grants and RPC functions!');
  await client.end();
}

applyFix().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
