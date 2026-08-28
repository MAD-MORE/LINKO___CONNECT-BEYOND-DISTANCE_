# Scaling Playbook — Linko

## Overview

This playbook describes how to scale Linko from beta (50 MAU) to global scale (100k+ MAU) while maintaining performance, reliability, and cost efficiency.

---

## Scaling Phases

### Phase A: Beta → 1,000 MAU

**Current state:** 1 backend instance, 1–3 relay nodes, 1 Fly Postgres

**Actions:**
- Monitor error rates and response times via `/metrics`
- Add a second backend instance if P95 latency > 150ms: `fly scale count 2 -a linko-backend`
- Add relay nodes in high-usage regions
- Ensure DB connection pool is not saturated (target: < 80% of max connections)

**Cost estimate:** ~$150–300/month

---

### Phase B: 1k → 10k MAU

**Bottlenecks:** Single Postgres instance, no caching, polling-based signaling

**Actions:**

1. **Add PostgreSQL read replica** for read-heavy queries (session status, presence):
   ```bash
   fly postgres create --name linko-pg-replica --fork-from linko-pg --region lhr
   ```
   Update backend to route read queries to replica.

2. **Add Redis for session caching** (in-memory session state, rate limit counters):
   ```bash
   fly redis create --name linko-redis --region iad
   ```
   Move `ControlPlaneStore` hot paths to Redis.

3. **Add PgBouncer** connection pooler to prevent DB connection exhaustion.

4. **Expand relay fleet:** Add nodes in 3+ regions (iad, lhr, sin, syd).

5. **Upgrade signaling to WebSocket** — reduces polling overhead by 80%.

**Cost estimate:** ~$600–1,500/month

---

### Phase C: 10k → 100k MAU

**Bottlenecks:** Single-region backend, relay bandwidth costs dominate

**Actions:**

1. **Multi-region backend deployment:**
   ```bash
   fly scale count 2 --region iad,lhr,sin -a linko-backend
   ```
   Use Fly.io's anycast routing — requests automatically go to nearest region.

2. **PostgreSQL global replication:**
   - Primary in iad, read replicas in lhr and sin
   - Backend routes writes to primary, reads to regional replica

3. **Relay cost optimization:**
   - Negotiate volume pricing with Fly.io (>10 TB/month discount)
   - Improve NAT traversal to reduce relay dependency from 60% → 30%
   - Implement relay compression (reduce bytes ~20%)

4. **CDN for static assets** (APK update check, help content): Cloudflare free tier.

5. **Horizontal backend auto-scaling:**
   ```toml
   # In fly.backend.toml
   [http_service]
     auto_stop_machines = "stop"
     auto_start_machines = true
     min_machines_running = 2
   ```

**Cost estimate:** ~$3,000–8,000/month

---

### Phase D: 100k+ MAU

At this scale, architecture evolves significantly:

| Component | Change |
|---|---|
| Backend | Microservices split (auth, session, signaling, usage separate services) |
| Database | Distributed Postgres (CockroachDB or Citus) |
| Relay | Custom relay infrastructure on dedicated VMs (exit from Fly.io per-GB pricing) |
| Signaling | WebSocket service with horizontal scaling |
| Cache | Redis Cluster for distributed session state |
| CDN | CloudFront or Cloudflare for global edge caching |
| Observability | Datadog or Grafana Cloud for unified monitoring |

**Cost estimate:** $15,000–50,000/month (but revenue should far exceed this)

---

## Relay Regional Expansion Plan

| Phase | Regions | Covers |
|---|---|---|
| Beta | iad (US-East) | North America |
| 1k MAU | + lhr (London) | Europe |
| 5k MAU | + sin (Singapore) | Southeast Asia |
| 10k MAU | + syd (Sydney) | Australia/NZ |
| 20k MAU | + gru (São Paulo) | Latin America |
| 50k MAU | + nrt (Tokyo) | East Asia |

---

## Key Scaling Metrics to Monitor

| Metric | Action trigger |
|---|---|
| Backend P95 latency > 150ms | Add backend instance |
| DB connections > 80% pool | Add PgBouncer |
| Relay sessions > 800/node | Add relay node |
| Relay bandwidth > 5 TB/month | Negotiate pricing |
| Error rate > 1% | Investigate and fix before scaling |
| MAU > 5k | Implement Redis caching |
| MAU > 20k | Multi-region backend |
