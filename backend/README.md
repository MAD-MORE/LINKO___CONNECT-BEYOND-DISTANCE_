# LINKO Control Plane

Phase 5 backend foundation for authentication/device identity, authorization, session coordination and signaling.

## Current implementation

- `src/server.ts` — HTTP API service
- `src/store.ts` — authoritative in-memory session/device state machine for development/tests
- `src/types.ts` — API/domain contracts
- `src/store.test.ts` — lifecycle and revocation tests
- `migrations/001_control_plane.sql` — PostgreSQL persistence baseline with RLS enabled

## API

- `GET /health`
- `POST /v1/devices`
- `POST /v1/sessions`
- `GET /v1/sessions/:id`
- `POST /v1/sessions/:id/transition`

## Invariants

1. Authorization is a backend decision; clients cannot directly choose a connected state.
2. Session transitions follow an explicit state machine.
3. Device revocation propagates to active sessions.
4. Session commands are safe to retry when the requested transition is already represented by the current state or rejected as invalid.
5. The control plane stores metadata only; it does not store traffic payloads.
6. Secrets/configuration are supplied through environment variables and are never committed.

## Development

```bash
npm install
npm test
npm run build
npm start
```

The current store is intentionally in-memory. Production completion of Phase 5 requires wiring the migration to the selected PostgreSQL staging environment, real authentication/device credentials, quotas, background events, signaling transport, security tests and staging deployment evidence.
