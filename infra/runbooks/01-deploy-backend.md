# Runbook 01 — Deploy Linko Backend

## Prerequisites
- `flyctl` installed and authenticated (`fly auth login`)
- Access to the `linko-backend` Fly.io app
- All secrets already set (see Runbook 05 for first-time setup)

---

## Standard Deploy (Code Change)

```bash
# From repo root
fly deploy -c infra/fly.backend.toml --strategy=rolling
```

Rolling strategy ensures zero downtime — the new instance passes health checks before the old one is terminated.

**Expected output:**
```
==> Deploying ...
==> Monitoring deployment
  Canary machine running successfully ...
  Machine successfully replaced
==> v2 deployed successfully
```

## Verify deployment

```bash
curl https://linko-backend.fly.dev/health
# Expected: {"service":"linko-control-plane","status":"ok","database":"postgres"}
```

---

## Emergency Rollback

```bash
# List recent releases
fly releases list -a linko-backend

# Roll back to previous version
fly deploy --image registry.fly.io/linko-backend:<previous-version> -a linko-backend
```

---

## Scale up (traffic spike)

```bash
# Add a second instance in the same region
fly scale count 2 -a linko-backend --region iad

# Add instance in EU for lower latency
fly scale count 1 -a linko-backend --region lhr
```

---

## View logs

```bash
fly logs -a linko-backend
fly logs -a linko-backend --region iad
```

---

## SSH into backend instance

```bash
fly ssh console -a linko-backend
```

---

## Check database connectivity

```bash
fly ssh console -a linko-backend --command "node -e \"process.env.NODE_ENV='production'; import('./dist/db-verify.js')\""
```
