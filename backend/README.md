# LinkShare control-plane MVP

This service coordinates an explicitly approved, direct Host/Client tunnel. It never accepts or forwards tunnel payloads. The Android application currently contains a mock repository, so this is the concrete contract for replacing it.

## Run

Use Node 22+:

```powershell
cd backend
npm test
npm start
```

For local coturn testing, start the relay first, then use the shared TURN environment file:

```powershell
cd relay
docker compose up -d
cd ..\backend
npm run start:turn
```

`start:turn` loads `../relay/.env`, so the backend and coturn always use the same local `TURN_SHARED_SECRET` and `TURN_URLS`. For physical devices, replace the `127.0.0.1` URLs in `relay/.env` with this machine's reachable LAN or public IP before starting both services.

The sample process seeds `client-demo` and Host `nora`. Set `LINKSHARE_ENROLLMENT_TOKEN` to enable `POST /v1/devices/register`; it requires that secret in `X-Enrollment-Token`, a stable device ID, and a device public key, then returns a 15-minute token. Keep this token in a secrets manager and issue it only after the account service verifies the signed device-attestation/enrollment challenge. Tokens are deliberately not printed or shipped.

## SQLite persistence

`src/db.js` opens `linkshare.db` in this directory and creates the `devices`, `friends`, `sessions`, and `tokens` tables on startup. All application reads and writes use reusable prepared statements. The database file is local development state and is excluded from Git.

`device_id`, `host_device_id`, and `client_device_id` intentionally preserve the Android/frontend terminology. The numeric SQLite `sessions.id` is returned as the API's string `sessionId`. A request is stored as a `sessions` row with status `requesting`; Host approval moves that same row to `handshaking`, preserving the pending request ID used by the frontend.

## HTTPS and deployment

`src/server.js` is an HTTP application process intended to sit behind a TLS-terminating reverse proxy. Production ingress must terminate TLS and forward only trusted traffic; do not expose this process to the public Internet directly. WebSocket signaling uses `wss://` externally. It does not implement WireGuard, STUN, TURN, or a traffic relay—use audited WireGuard and TURN implementations for those data-plane concerns.

## Android contract

All protected REST calls use `Authorization: Bearer <short-lived-device-token>` and JSON bodies are capped at 5 KB.

| Operation | Endpoint / event | Result |
| --- | --- | --- |
| Friends | `GET /v1/friends` | `List<Friend>` exactly matching `LinkShareApi` |
| Request | `POST /v1/connection-requests` | `202 { requestId, state: "requesting" }`; Host receives `connection.requested` |
| Approve | `POST /v1/connection-requests/{id}/approve` | `HostSession`; Client receives `session.approved` containing `SignalingSession` |
| Deny | `POST /v1/connection-requests/{id}/deny` | `204`; Client receives `session.denied` |
| Path state | `POST /v1/sessions/{id}/state` | `handshaking`, `connected`, or `failed` only |
| Candidate exchange | `wss://…/v1/signaling` | send/receive `session.candidate` events, session-bound and size-limited |

WebSocket messages are `{ "version": 1, "event": "…", "data": { … } }`. `session.approved` contains `sessionId`, `hostPublicKey`, `relayUrl`, `expiresAtEpochSeconds`, and optional `turnCredentials`, matching `SignalingSession`; the approval HTTP response contains the corresponding Host fields plus optional `turnCredentials`.

## NAT traversal and relay

Set `TURN_SHARED_SECRET` and `TURN_URLS` (a comma-separated list of public `turn:`/`turns:` URLs) before starting the backend. On approval, the backend adds optional `turnCredentials` to each participant's signaling payload. The credential is a 15-minute coturn REST credential, scoped to that authenticated device and the approved session; it is never a static TURN password. See `../relay/README.md` for coturn Docker setup and production network/TLS notes.

## Security and operational boundaries

- Approval is mandatory per request; a friend relationship never starts a session.
- Leases are Host/Client-pair scoped and expire after 15 minutes; revocation is server-side.
- Input is validated, sensitive routes are rate-limited, and the audit log records metadata only.
- Keep device keys in Android Keystore. Authenticate possession with an Ed25519 challenge/response in the enrollment service; never treat a device ID alone as authentication.
- Terminate active sessions immediately when a device/token is revoked. SQLite is appropriate for the single-process MVP; migrate to PostgreSQL before running multiple backend instances or relying on distributed session coordination.
- Incident response: revoke affected device tokens, terminate their sessions, rotate service credentials/TLS keys, preserve metadata-only audit logs, then require device re-authentication.
