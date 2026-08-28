# LINKO Data-Plane Relay Node (`linko-relay`)

Lightweight, high-performance UDP packet forwarder on Fly.io that relays encrypted tunnel traffic between LINKO Provider and Client devices.

---

## 🔒 Zero-Knowledge Security Principles

1. **Blind Forwarding**: The relay NEVER decrypts, inspects, or modifies user traffic. All traffic is end-to-end encrypted with **AES-256-GCM** using keys negotiated directly between client devices.
2. **No Private Keys**: The relay NEVER receives, stores, or processes private session keys or user credentials.
3. **No Plaintext Logging**: Packet contents and browsing payloads are NEVER logged.
4. **Session Ownership Validation**: Validates session ownership via a 32-byte SHA-256 key hash header.

---

## 📡 Ports & Services

- **UDP 7000**: Encrypted data-plane datagram relay
- **TCP 7001 / HTTP**: Health checks (`GET /health`), Prometheus metrics (`GET /metrics`), and session control (`POST /sessions`, `DELETE /sessions/:id`)

---

## 📦 Wire Framing Protocol (V2 Header: 95 Bytes)

```text
┌──────────┬─────────┬──────────────┬──────────┬──────┬──────┬───────────┬─────────┬───────────────────────┐
│ Magic    │ Version │ Session ID   │ Key Hash │ Role │ Type │ Sequence  │ Nonce   │ Ciphertext + Auth Tag │
│ (4B)     │ (1B)    │ (36B UUID)   │ (32B)    │ (1B) │ (1B) │ (8B)      │ (12B)   │ (Variable + 16B tag)  │
└──────────┴─────────┴──────────────┴──────────┴──────┴──────┴───────────┴─────────┴───────────────────────┘
```

* **Magic**: `0x4C, 0x4B, 0x4F, 0x32` (`"LKO2"`)
* **Role**: `1 = Provider`, `2 = Client`
* **Type**: `1 = Data`, `2 = Handshake`, `3 = Keepalive`, `4 = Close`

---

## 🚀 Running Locally

```bash
cd relay
npm install
npm test
npm run dev
```

---

## 🐳 Docker

```bash
docker build -t linko-relay .
docker run -p 7000:7000/udp -p 7001:7001 \
  -e RELAY_NODE_ID=local-1 \
  -e RELAY_REGION=local \
  linko-relay
```

---

## ☁️ Fly.io Deployment

The canonical Fly.io application name is **`linko-relay`**.

```bash
# Deploy to Fly.io using the canonical configuration
fly deploy -c infra/fly.relay.toml --dockerfile relay/Dockerfile --app linko-relay

# Verify dedicated IPv4 allocation for UDP on Fly.io
fly ips allocate-v4 -a linko-relay

# Check health
curl https://linko-relay.fly.dev/health
```
