# LINKO Signaling Service

Phase 5.1 backend boundary.

This service is the control plane only. It must never carry raw VPN traffic.

## Responsibilities

- Authenticate peers.
- Create short-lived connection requests.
- Deliver provider approval/denial.
- Create scoped sessions.
- Exchange ephemeral negotiation metadata.
- Select direct or relay transport.
- Expire and revoke sessions.

## Required API

- `POST /v1/connections/request`
- `POST /v1/connections/{requestId}/approve`
- `POST /v1/connections/{requestId}/deny`
- `GET /v1/connections/{requestId}`
- `POST /v1/sessions/{sessionId}/negotiate`
- `POST /v1/sessions/{sessionId}/relay`
- `DELETE /v1/sessions/{sessionId}`

## Security

TLS is required. Tokens and session identifiers are short-lived and scoped to authenticated users. Provider approval is mandatory. Private tunnel keys remain on peers and are never persisted by the signaling service.

The relay carries encrypted tunnel packets only; signaling does not inspect application traffic.

## Implementation boundary

The Android `SignalingRepository` targets this contract. The server implementation should be deployed separately from the Android client and relay transport.