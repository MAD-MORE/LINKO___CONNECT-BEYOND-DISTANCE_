# Runbook 03 — Database Migration

## Overview

Database schema changes are applied via sequential SQL migration files in `backend/migrations/`.
Migrations are applied in order (001, 002, ...) and each must be idempotent (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`).

---

## Apply migrations (production)

### Via Fly.io SSH

```bash
# Connect to the database via the backend instance
fly ssh console -a linko-backend

# Inside the container
cd /app
# Run each migration in order
for f in $(ls /app/migrations/*.sql | sort); do
  echo "Applying $f..."
  node -e "
    import pg from 'pg';
    import { readFileSync } from 'fs';
    const pool = new pg.Pool({ connectionString: process.env.LINKO_DATABASE_URL });
    const sql = readFileSync('$f', 'utf8');
    pool.query(sql).then(() => { console.log('OK'); pool.end(); }).catch(err => { console.error(err); process.exit(1); });
  "
done
```

### Via Fly Postgres proxy

```bash
# Open a proxy to the Fly Postgres database
fly proxy 5432:5432 -a linko-pg &

# Apply migrations using psql
export DATABASE_URL="postgres://linko:<password>@localhost:5432/linko"
for f in backend/migrations/*.sql; do
  echo "Applying $f..."
  psql "$DATABASE_URL" -f "$f"
done
```

---

## Check migration status

There is no automatic migration tracking table in the MVP. To check which migrations have been applied, inspect the schema:

```sql
-- Check if the latest tables exist
\dt
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;
```

---

## Creating a new migration

1. Create `backend/migrations/NNN_description.sql` where NNN is the next sequential number
2. Write idempotent SQL (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`)
3. Test locally: `docker compose up postgres -d && psql postgres://linko:linko_dev_password@localhost:5432/linko -f backend/migrations/NNN_description.sql`
4. Commit and deploy

---

## Rollback a migration

SQL migrations in Linko are NOT automatically reversible. For rollback:

1. Write a reverse migration SQL script manually
2. Apply it to the database
3. Do NOT delete the original migration file (it's part of the audit trail)

---

## Emergency: restore from backup

```bash
# List Fly Postgres snapshots
fly pg list-snapshots -a linko-pg

# Create a new DB from snapshot
fly pg create --name linko-pg-restored --snapshot-id <snapshot-id>

# Point backend to restored DB
fly secrets set LINKO_DATABASE_URL=postgres://<restored-url> -a linko-backend
fly deploy -c infra/fly.backend.toml
```
