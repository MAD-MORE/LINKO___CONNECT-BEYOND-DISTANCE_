# Linko Relay Node

UDP relay server that forwards encrypted tunnel traffic between Linko Provider and Receiver devices.

## What it does

- Listens on UDP port 7000 for relay packets
- Verifies session ownership using SHA-256 key hashes (relay is blind to plaintext — it never decrypts traffic)
- Forwards encrypted payloads between the two session parties
- Enforces per-session bandwidth limits (default: 1 GB)
- Exposes HTTP health and Prometheus metrics on port 7001

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `UDP_PORT` | No | `7000` | UDP relay port |
| `PORT` | No | `7001` | HTTP health/metrics port |
| `RELAY_NODE_ID` | No | `relay-1` | Node identifier |
| `RELAY_REGION` | No | `default` | Geographic region (e.g. `iad`, `lhr`, `sin`) |
| `BANDWIDTH_LIMIT_BYTES_PER_SESSION` | No | `1073741824` (1 GB) | Max bytes per session |

## Running locally

```bash
npm install
npm run dev
```

## Running with Docker

```bash
docker build -t linko-relay .
docker run -p 7000:7000/udp -p 7001:7001 \
  -e RELAY_NODE_ID=local-1 \
  -e RELAY_REGION=local \
  linko-relay
```

## Health check

```bash
curl http://localhost:7001/health
```

## Session registration

The Linko control plane registers sessions via the HTTP API:

```bash
# Register a session
curl -X POST http://localhost:7001/sessions \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<uuid>","key":"<base64url-32-byte-key>"}'

# Remove a session
curl -X DELETE http://localhost:7001/sessions/<uuid>
```

## Deploying to Fly.io

```bash
fly launch --name linko-relay --region iad --no-deploy
fly secrets set RELAY_NODE_ID=iad-1 RELAY_REGION=iad
fly deploy
# Add more regions
fly scale count 1 --region lhr
fly scale count 1 --region sin
```

## Packet format

```
Bytes 0-35:   Session ID (36-byte UUID ASCII string)
Bytes 36-67:  SHA-256 hash of session key (32 bytes binary)
Bytes 68+:    Encrypted payload (AES-256-GCM — relay is blind)
```

The relay strips the 68-byte header and forwards only the encrypted payload to the other party.

## Security notes

- The relay NEVER decrypts payloads. All traffic is AES-256-GCM encrypted end-to-end.
- Session keys are never sent to the relay. Only the SHA-256 hash of the key is used for verification.
- Third-party packet injection is blocked (only 2 parties per session are accepted).
- Sessions expire after 4 hours maximum.
- Bandwidth limits prevent relay abuse.
