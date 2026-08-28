# Runbook 02 — Deploy Linko Relay Nodes

## Prerequisites
- `flyctl` installed and authenticated
- Docker installed (for local image testing)

---

## Deploy relay (single region)

```bash
fly deploy -c infra/fly.relay.toml
```

## Add relay to additional regions

```bash
# Europe
fly scale count 1 --region lhr -a linko-relay

# Asia-Pacific
fly scale count 1 --region sin -a linko-relay

# List all machines and regions
fly machines list -a linko-relay
```

---

## Verify relay health

```bash
# Get the relay's health endpoint (port 7001 is internal, use SSH)
fly ssh console -a linko-relay --command "wget -q -O- http://localhost:7001/health"

# Or via Fly proxy
fly proxy 7001:7001 -a linko-relay &
curl http://localhost:7001/health
curl http://localhost:7001/metrics
```

---

## Graceful drain (before maintenance)

Relay nodes handle SIGTERM gracefully. Fly.io sends SIGTERM before terminating a machine:

```bash
# Scale down to zero (drains and stops)
fly scale count 0 --region lhr -a linko-relay

# Restore
fly scale count 1 --region lhr -a linko-relay
```

Existing sessions will reconnect automatically via the control plane within 30 seconds.

---

## Emergency relay reset

If a relay node is in a bad state:

```bash
# Restart a specific machine
fly machine restart <machine-id> -a linko-relay

# Or destroy and recreate
fly machine destroy <machine-id> -a linko-relay --force
fly deploy -c infra/fly.relay.toml
```

---

## Bandwidth monitoring

```bash
fly ssh console -a linko-relay --command "wget -q -O- http://localhost:7001/metrics"
# Look for: linko_relay_bytes_forwarded_total
```

If relay bandwidth exceeds expected levels, check for runaway sessions:
```bash
fly ssh console -a linko-relay --command "wget -q -O- http://localhost:7001/health | grep activeSessions"
```
