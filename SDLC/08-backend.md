# Phase 8 — Backend

## Objective
Build the secure control plane for identity, devices, friendships, sessions, signaling, usage, billing, notifications, administration, and abuse controls.

## Logical services

```text
backend/
├── auth/
├── users/
├── devices/
├── friends/
├── sessions/
├── signaling/
├── relay/
├── usage/
├── billing/
├── notifications/
├── abuse/
└── admin/
```

## API principles

- Version APIs.
- Validate every input.
- Authenticate every protected operation.
- Authorize every resource access.
- Rate-limit sensitive endpoints.
- Use idempotency where retries can create duplicate state.
- Return stable error codes.
- Never log secrets or sensitive payloads.

## Exit criteria

The MVP control plane supports authenticated users, device registration, friendships, connection requests, approvals, session lifecycle, usage metadata, and administrative controls.
