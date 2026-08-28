# Runbook 05 — Rollback Strategy

## Principles

- Every deployment to production is versioned by Fly.io (immutable images)
- Rollback = re-deploying the previous image (< 2 minutes)
- Database migrations are NOT automatically rolled back — only forward
- Backend is stateless; rolling back the app code does NOT require DB rollback unless there's a migration incompatibility

---

## Standard Code Rollback (no DB changes)

```bash
# List the last 5 releases
fly releases list -a linko-backend | head -6

# Deploy previous version (VERSION is the v-number from releases list)
fly deploy --image registry.fly.io/linko-backend:deployment-<VERSION> -a linko-backend

# Verify
curl https://linko-backend.fly.dev/health
```

For relay:
```bash
fly releases list -a linko-relay | head -6
fly deploy --image registry.fly.io/linko-relay:deployment-<VERSION> -a linko-relay
```

---

## Rollback with Incompatible DB Migration

If a migration added a column and the old code doesn't know about it, old code still works (additive migrations are backwards-compatible).

If a migration REMOVED or RENAMED a column that old code depends on:
1. **Do not roll back the database** — this risks data corruption
2. Instead: fix the issue in a new code version and deploy forward
3. Write a compensating migration if needed

---

## Canary Rollout (Future — for large changes)

Fly.io supports canary deployments:

```bash
fly deploy -c infra/fly.backend.toml --strategy=canary
# Validates new version with 1 instance before rolling out
```

---

## First-Time Setup Checklist

Run this once before any production deployment:

```bash
# 1. Create Fly apps
fly launch --name linko-backend --region iad --no-deploy
fly launch --name linko-relay --region iad --no-deploy

# 2. Create Fly Postgres
fly postgres create --name linko-pg --region iad --initial-cluster-size 1 --vm-size shared-cpu-1x --volume-size 10

# 3. Attach DB to backend
fly postgres attach linko-pg -a linko-backend

# 4. Set backend secrets
fly secrets set \
  LINKO_JWT_SECRET="$(openssl rand -hex 32)" \
  SUPABASE_URL="https://<project>.supabase.co" \
  SUPABASE_PUBLISHABLE_KEY="sb_publishable_..." \
  SUPABASE_SECRET_KEY="sb_secret_..." \
  TUNNEL_HOST="linko-relay.fly.dev" \
  TUNNEL_PORT="7000" \
  LINKO_ADMIN_SECRET="$(openssl rand -hex 24)" \
  -a linko-backend

# 5. Set relay secrets
fly secrets set \
  RELAY_NODE_ID="iad-1" \
  RELAY_REGION="iad" \
  -a linko-relay

# 6. Deploy
fly deploy -c infra/fly.backend.toml
fly deploy -c infra/fly.relay.toml

# 7. Run migrations
fly proxy 5432:5432 -a linko-pg &
for f in backend/migrations/*.sql; do psql "$DATABASE_URL" -f "$f"; done

# 8. Verify
curl https://linko-backend.fly.dev/health
```
