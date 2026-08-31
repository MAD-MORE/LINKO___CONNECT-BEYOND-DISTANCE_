# Performance Benchmarks — Linko

## Target Performance Metrics (MVP)

### Backend Control Plane

| Endpoint | P50 latency | P95 latency | P99 latency | Throughput |
|---|---|---|---|---|
| GET /health | < 5ms | < 20ms | < 50ms | > 1000 rps |
| POST /v1/sessions | < 50ms | < 150ms | < 300ms | > 100 rps |
| GET /v1/sessions/:id | < 20ms | < 80ms | < 200ms | > 200 rps |
| POST /v1/sessions/:id/transition | < 50ms | < 150ms | < 300ms | > 100 rps |
| GET /v1/sessions/:id/tunnel | < 30ms | < 100ms | < 250ms | > 200 rps |
| POST /v1/devices/presence | < 20ms | < 60ms | < 150ms | > 500 rps |

### Relay Node

| Metric | Target |
|---|---|
| UDP packet forwarding latency (relay processing overhead) | < 1ms |
| Max concurrent sessions per relay node | 1,000 |
| Max relay throughput per node | 100 Mbps |
| Session registration latency (HTTP → relay) | < 50ms |

### Android App

| Metric | Target |
|---|---|
| Session establishment time (from tap to connected) | < 10 seconds |
| Relay fallback trigger time (if direct fails) | < 10 seconds |
| Battery drain during active Provider session (1 hour) | < 5% |
| Battery drain during active Receiver session (1 hour) | < 8% |
| App cold start to interactive | < 2 seconds |
| VPN startup time (from approval to routing) | < 3 seconds |

---

## Load Test Script

Uses `autocannon` for HTTP load testing:

```bash
cd tests/performance
npm install autocannon -g

# Health check throughput
autocannon -c 50 -d 30 https://linko-backend.fly.dev/health

# Presence heartbeat load (authenticated — requires valid JWT)
autocannon -c 20 -d 30 \
  -H "Authorization: Bearer $DEVICE_JWT" \
  -m POST \
  -H "Content-Type: application/json" \
  https://linko-backend.fly.dev/v1/devices/presence
```

---

## Profiling the Backend

```bash
# CPU profile (local)
NODE_ENV=development node --prof src/server.ts &
# Run load test...
kill %1
node --prof-process isolate-*.log > profile.txt
```

---

## Android Battery Profiling

1. Open Android Studio → Profiler → Energy Profiler
2. Start a Linko Provider session on the test device
3. Record for 5 minutes with the screen off
4. Look for: `WakeLock`, `Network`, `Location` usage during session

**Targets:**
- No WAKE_LOCK held except during active packet processing
- Network radio should enter low-power state during idle periods between packets
- No GPS usage

---

## Relay Throughput Test

```bash
# Send UDP packets to relay (requires a registered session)
node tests/performance/relay-throughput.js \
  --host linko-relay.fly.dev \
  --port 7000 \
  --session-id <uuid> \
  --key <base64-key> \
  --duration 30 \
  --packet-size 1024 \
  --pps 1000
```

---

## Known Bottlenecks (MVP Baseline)

| Component | Bottleneck | Mitigation |
|---|---|---|
| Backend | Single Node.js process (no clustering) | Add 2nd instance, consider clustering post-MVP |
| DB | Single PostgreSQL instance | Add read replica for query-heavy endpoints |
| Relay | Per-session bandwidth limit in code | Kernel-level traffic shaping (post-MVP) |
| Signaling | 2-second polling interval | Reduce to 1s or switch to WebSocket post-MVP |
