# Threat Model — Linko

## Assets

| Asset | Sensitivity | Impact if compromised |
|---|---|---|
| User credentials (password) | Critical | Account takeover |
| Supabase access token | Critical | Full API access as that user |
| Device JWT | High | Session hijack, impersonation |
| Session tunnel key | High | Decrypt user traffic, inject packets |
| Provider's Internet traffic | High | Traffic interception |
| Receiver's Internet traffic | High | Traffic interception |
| Friend relationships | Medium | Privacy exposure |
| Usage records | Medium | Billing fraud, privacy exposure |
| Device IDs | Low-Medium | Linkability, correlation |

---

## Trust Boundaries

```
[Public Internet]
      |
      | HTTPS (TLS 1.3)
      v
[Linko Control Plane API] — Trust: authenticated device JWT
      |
      | PostgreSQL TLS
      v
[PostgreSQL Database] — Trust: DB credentials, private network only
      |
[Linko Relay Node] — Trust: session key issued by control plane
      |
      | UDP (AES-GCM encrypted)
      v
[Provider Device] / [Receiver Device] — Trust: device JWT + session key
```

---

## Threat Catalogue

### T1 — Account Takeover via Credential Theft
- **Vector:** Attacker guesses or steals email/password
- **Mitigation:** Supabase handles auth; enforce minimum password length (6+ chars enforced in backend); rate-limit `/v1/auth/signup` and login endpoints; MFA available via Supabase (future)
- **Status:** Partially mitigated (rate limiting in backend)

### T2 — Device Token Forgery
- **Vector:** Attacker forges a device JWT to impersonate another device
- **Mitigation:** JWTs are HMAC-SHA256 signed with `LINKO_JWT_SECRET` (min 32 bytes); tokens include `deviceId` and `sub` (userId) claims; backend verifies both match the database record on every request
- **Status:** Mitigated

### T3 — Session Hijacking via Stolen Tunnel Key
- **Vector:** Attacker intercepts the tunnel key and decrypts/injects traffic
- **Mitigation:** Tunnel keys are 32-byte random values, transmitted only over HTTPS; keys are per-session, revoked immediately on session termination; relay nodes verify session key on first packet
- **Status:** Mitigated

### T4 — Unauthorized Connection (No Provider Consent)
- **Vector:** Attacker creates a session and forces it to `approved` without Provider action
- **Mitigation:** Server-side enforcement: only a device with `providerDeviceId` matching the authenticated device can transition a session to `approved`; Provider must explicitly call the transition endpoint
- **Status:** Mitigated (core invariant)

### T5 — Replay Attack on Signaling
- **Vector:** Attacker replays a captured signaling message to re-establish a terminated session
- **Mitigation:** Signaling tickets include session ID and expiry; expired sessions reject all signaling; nonce tracking prevents reuse within TTL window
- **Status:** Mitigated

### T6 — Traffic Interception on Relay
- **Vector:** Relay node operator reads user traffic
- **Mitigation:** Traffic is AES-GCM encrypted end-to-end between Receiver and Provider; relay nodes only see ciphertext and cannot read plaintext; relay operator cannot derive keys (keys only issued by control plane to session parties)
- **Status:** Mitigated (relay is blind to plaintext)

### T7 — Denial of Service on Backend
- **Vector:** Attacker floods API with requests, exhausting backend resources
- **Mitigation:** Per-device rate limiting (token bucket); per-IP rate limiting via Fly.io proxy; body size limits on all POST requests; connection timeouts
- **Status:** Mitigated

### T8 — Friendship Bypass (Connect to Non-Friend)
- **Vector:** Attacker creates a session with a Provider they are not friends with
- **Mitigation:** Backend verifies friendship via Supabase `friend_requests` table before creating any session; check is server-side and cannot be bypassed from the client
- **Status:** Mitigated

### T9 — SQL Injection
- **Vector:** Attacker injects SQL via API request body fields
- **Mitigation:** All PostgreSQL queries use parameterized statements (`pg` library); input validation on all fields before DB access; no raw string interpolation in queries
- **Status:** Mitigated

### T10 — Secret Leakage via Logs
- **Vector:** `LINKO_JWT_SECRET`, `DATABASE_URL`, or Supabase secret key appear in logs
- **Mitigation:** `observability.ts` never logs Authorization headers or environment variables; error messages are normalized before logging; startup validates secrets are set without printing them
- **Status:** Mitigated (by design rule — security-middleware.ts enforces)

### T11 — Malicious Provider (Traffic Inspection)
- **Vector:** A Provider deliberately inspects the Receiver's traffic
- **Mitigation:** By design, the Provider's device does forward the Receiver's traffic — this is the product's core mechanism. The trust model requires the Receiver to trust the Provider. Linko's ToS prohibits traffic inspection. Technical mitigations (traffic sandboxing on Provider) are a post-MVP enhancement.
- **Status:** Accepted risk / design constraint; disclosed in ToS

### T12 — Relay Resource Exhaustion (Bandwidth Abuse)
- **Vector:** Attacker uses relay to transfer massive amounts of data, exhausting bandwidth budget
- **Mitigation:** Per-session bandwidth limits enforced at relay; relay reports usage to control plane; control plane enforces plan quotas; sessions exceeding quota are terminated
- **Status:** Mitigated

---

## Security Controls Summary

| Control | Where enforced |
|---|---|
| HTTPS / TLS 1.3 | Fly.io proxy (automatic) |
| JWT authentication | `auth.ts` + `server.ts` |
| Input validation | `server.ts` (every endpoint) |
| Parameterized SQL | `postgres-store.ts` |
| Rate limiting | `rate-limiter.ts` |
| Session key per-session | `tunnel-key-store.ts` |
| Provider consent enforcement | `server.ts` (transition endpoint) |
| Friendship verification | `server.ts` (server-side Supabase query) |
| AES-GCM tunnel encryption | `EncryptedDatagramTunnel.kt` |
| No plaintext logging of secrets | `observability.ts` design rule |
| Relay blind to plaintext | `relay-server.ts` (forward only) |

---

## Out of Scope (MVP)

- Certificate pinning (Android)
- MFA enforcement
- Hardware-backed attestation (Android SafetyNet/Play Integrity)
- Traffic sandboxing on Provider device
- Formal penetration test (post-beta)
