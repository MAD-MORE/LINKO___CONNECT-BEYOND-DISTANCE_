# Capacity & Cost Model — Linko MVP

## Assumptions (MVP / Early Beta)

| Parameter | Value | Basis |
|---|---|---|
| Concurrent active sessions | 50 | Conservative MVP target |
| Avg session duration | 30 minutes | Estimated typical use case |
| Avg relay data per session | 200 MB | Typical mobile browsing |
| % of sessions needing relay | 60% | Pessimistic NAT traversal estimate |
| Monthly active users | 500 | Early beta |
| Sessions per user per month | 10 | Estimated engagement |

---

## Monthly Session Volume

```
500 MAU × 10 sessions/user = 5,000 sessions/month
5,000 sessions × 60% relay = 3,000 relay sessions/month
3,000 relay sessions × 200 MB = 600 GB relay bandwidth/month
```

---

## Infrastructure Cost Estimate (MVP)

### Fly.io Backend
| Resource | Size | Cost/month |
|---|---|---|
| `linko-backend` app | 1× shared-cpu-1x 256MB | ~$5 |
| Outbound bandwidth | ~10 GB (API only) | ~$1 |
| **Backend subtotal** | | **~$6/month** |

### Fly.io Relay
| Resource | Size | Cost/month |
|---|---|---|
| Relay nodes (3 regions) | 3× shared-cpu-1x 512MB | ~$15 |
| Relay bandwidth | 600 GB/month | ~$102 (@ $0.17/GB) |
| **Relay subtotal** | | **~$117/month** |

### Fly.io PostgreSQL
| Resource | Size | Cost/month |
|---|---|---|
| Postgres primary | shared-cpu-1x 1GB | ~$7 |
| Storage (10 GB) | 10 GB | ~$1.50 |
| **DB subtotal** | | **~$8.50/month** |

### Supabase
| Resource | Tier | Cost/month |
|---|---|---|
| Auth + Realtime + DB | Free tier (up to 500MB DB) | $0 |
| **Supabase subtotal** | | **$0/month** |

### Total MVP Estimate
| Category | Cost/month |
|---|---|
| Backend | $6 |
| Relay | $117 |
| PostgreSQL | $8.50 |
| Supabase | $0 |
| **Total** | **~$131.50/month** |

---

## Cost Per User (MVP)

```
$131.50 / 500 MAU = $0.26 per MAU
```

---

## Relay Bandwidth — The Key Cost Driver

Relay bandwidth dominates costs. The critical lever is **reducing relay usage**:

| Strategy | Expected impact |
|---|---|
| Improve NAT traversal (STUN/TURN tuning) | Reduce relay % from 60% to 40% |
| Compress tunnel packets | Reduce bytes by 20–30% |
| Bandwidth caps on free tier | Prevent heavy users from being loss-making |

If relay % drops to 40%:
```
3,000 → 2,000 relay sessions
2,000 × 200 MB = 400 GB → ~$68 relay bandwidth
Total: ~$82/month (vs $131/month)
```

---

## Scale Projections

| MAU | Relay bandwidth | Est. monthly infra cost |
|---|---|---|
| 500 | 600 GB | $132 |
| 2,000 | 2.4 TB | $420 |
| 10,000 | 12 TB | $2,050 |
| 50,000 | 60 TB | ~$10,200 |

At 50k MAU, Fly.io bandwidth pricing drops significantly with volume agreements. At this scale, dedicated relay servers or CDN peering should be evaluated.

---

## Database Capacity

| Entity | Rows at 500 MAU | Row size | Total |
|---|---|---|---|
| users | 500 | ~200 B | ~100 KB |
| devices | ~750 | ~300 B | ~225 KB |
| sessions | ~5,000/month | ~500 B | ~2.5 MB/month |
| usage_records | ~5,000/month | ~200 B | ~1 MB/month |
| security_events | ~10,000/month | ~300 B | ~3 MB/month |

At 500 MAU, total DB growth is ~6–7 MB/month. Supabase free tier (500 MB) handles ~70 months of MVP growth. Fly Postgres (10 GB) handles several years.

---

## Scaling Triggers

| Trigger | Action |
|---|---|
| Backend CPU > 70% sustained | Add second backend instance |
| DB connections > 80% pool | Add PgBouncer or read replica |
| Relay bandwidth > 1 TB/month | Negotiate Fly.io volume pricing |
| Relay CPU > 60% | Add relay instances in busy regions |
| MAU > 5,000 | Review free tier limits, introduce paid plans |
