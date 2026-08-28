-- Linko PostgreSQL production setup
-- Run once before first deployment.
-- Migrations in backend/migrations/ are applied after this.

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create application role (least-privilege)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'linko_app') THEN
    CREATE ROLE linko_app LOGIN PASSWORD 'REPLACE_WITH_SECURE_PASSWORD';
  END IF;
END
$$;

GRANT CONNECT ON DATABASE linko TO linko_app;
GRANT USAGE ON SCHEMA public TO linko_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO linko_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO linko_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO linko_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO linko_app;
