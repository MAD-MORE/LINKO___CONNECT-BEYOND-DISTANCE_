# Runbook 04 — Incident Response

## Severity Levels

| Level | Definition | Response time |
|---|---|---|
| **P0 — Critical** | Total service outage; all users affected | Immediate |
| **P1 — High** | Major feature broken; many users affected | < 30 min |
| **P2 — Medium** | Degraded performance; some users affected | < 2 hours |
| **P3 — Low** | Minor issue; few users affected | Next business day |

---

## P0: Backend Completely Down

**Symptoms:** `/health` returns non-200; all API calls fail; app shows "Cannot connect"

```bash
# 1. Check current deployment status
fly status -a linko-backend

# 2. View recent logs for error
fly logs -a linko-backend --region iad | tail -50

# 3. Check database health
fly ssh console -a linko-backend --command "curl -s http://localhost:8080/health"

# 4a. If crash loop: rollback
fly releases list -a linko-backend
fly deploy --image registry.fly.io/linko-backend:<last-good-version>

# 4b. If DB unreachable: check Fly Postgres
fly status -a linko-pg
fly postgres failover -a linko-pg  # If primary failed

# 5. Verify recovery
curl https://linko-backend.fly.dev/health
```

---

## P0: Relay Completely Down

**Symptoms:** Users cannot establish tunnels even through relay; direct connections may still work

```bash
# 1. Check relay status
fly status -a linko-relay

# 2. Restart all relay machines
fly machines list -a linko-relay
fly machine restart <machine-id> -a linko-relay  # For each machine

# 3. Verify
fly ssh console -a linko-relay --command "wget -q -O- http://localhost:7001/health"
```

---

## P1: High Error Rate on Specific Endpoint

```bash
# 1. Check logs for specific errors
fly logs -a linko-backend | grep '"status":5'

# 2. Check metrics
fly ssh console -a linko-backend --command "curl -s http://localhost:8080/metrics"

# 3. If DB query issue: check slow queries
fly proxy 5432:5432 -a linko-pg &
psql "$DATABASE_URL" -c "SELECT pid, now()-query_start, query FROM pg_stat_activity WHERE state='active' ORDER BY query_start ASC LIMIT 10;"
```

---

## Security Incident: Suspected Data Breach

1. **Immediately rotate all secrets:**
   ```bash
   fly secrets set LINKO_JWT_SECRET=$(openssl rand -hex 32) -a linko-backend
   fly secrets set SUPABASE_SECRET_KEY=<new-key> -a linko-backend
   fly deploy -c infra/fly.backend.toml
   ```
   > This invalidates all existing device JWTs — all users will be logged out.

2. **Revoke Supabase keys** from the Supabase dashboard.

3. **Review security_events table** for suspicious activity:
   ```sql
   SELECT * FROM security_events WHERE created_at > NOW() - INTERVAL '24 hours' ORDER BY created_at DESC;
   ```

4. **Notify affected users** if personal data was exposed (GDPR 72-hour notification requirement).

5. **Preserve logs** for forensic analysis before restarting.

---

## Communication Template

```
Status: [Investigating / Identified / Monitoring / Resolved]
Impact: [Description of user impact]
Start time: [HH:MM UTC]
Resolved time: [HH:MM UTC or N/A]
Root cause: [Brief description]
Fix: [What was done]
Next steps: [Prevention measures]
```
