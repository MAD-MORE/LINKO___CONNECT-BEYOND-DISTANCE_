# LINKO Phase 5.1 — Production Signaling Contract

## Purpose

This document defines the backend boundary required to turn the current Android connection flow into a real remote-data connection. The Android client must not treat the existing `MockLinkShareRepository` as production networking.

## Connection sequence

1. Receiver authenticates with LINKO.
2. Receiver sends `connection.request` for a trusted provider.
3. Provider receives the request and explicitly approves or denies it.
4. Signaling service returns a short-lived session ID.
5. Both peers exchange ephemeral tunnel negotiation material through signaling.
6. Peers attempt a direct path.
7. If direct negotiation fails, signaling assigns a relay.
8. The tunnel is established.
9. Android `VpnService` routes receiver traffic into the tunnel.
10. Provider forwards approved traffic to the internet.
11. Session close invalidates the tunnel credentials.

## Required server operations

- `POST /v1/connections/request`
- `POST /v1/connections/{requestId}/approve`
- `POST /v1/connections/{requestId}/deny`
- `GET /v1/connections/{requestId}`
- `POST /v1/sessions/{sessionId}/negotiate`
- `POST /v1/sessions/{sessionId}/relay`
- `DELETE /v1/sessions/{sessionId}`

## Security requirements

- TLS only.
- Short-lived access/session tokens.
- Provider approval is mandatory.
- Do not send raw VPN traffic through the signaling API.
- Never persist private tunnel keys on the signaling server.
- Session IDs must be unguessable and scoped to the authenticated users.
- Rate-limit connection requests and approval attempts.
- Revoke credentials on disconnect, expiry, or explicit denial.

## Current repository boundary

`SignalingRepository.kt` is the Android abstraction for this contract. The current repository still contains a mock implementation because no LINKO signaling service endpoint/protocol has been supplied in the repository yet.

Do not claim the tunnel is production-ready until a deployed signaling service and relay/direct transport implementation are connected to this boundary.
