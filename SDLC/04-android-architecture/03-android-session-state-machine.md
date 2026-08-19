# Phase 4.3 — Android Session State Machine

## Status
COMPLETE — APPROVED / UNLOCKED

## Canonical states
`DISCOVERED → REQUESTED → AUTHORIZED → NEGOTIATING → CONNECTING → ACTIVE`

Exceptional states: `REJECTED`, `EXPIRED`, `REVOKED`, `DEGRADED`, `FAILED`, `TERMINATING`, `TERMINATED`.

## Rules
1. Only the backend-authorized session may enter `CONNECTING`.
2. `ACTIVE` requires a valid session credential and transport path.
3. Revocation has priority over reconnect attempts.
4. Every transition has a timeout or terminal condition.
5. Duplicate commands are safe.
6. Client state is reconciled with backend state after reconnect.
7. UI state is derived from the session state machine.
8. Transport failure may move a session to `DEGRADED`; it must not grant new authority.
9. Termination clears ephemeral tunnel material.
10. A stale client cannot resurrect a terminated session.

## Acceptance
Unit tests shall cover every legal transition, illegal transition, duplicate command, timeout, retry, revocation, and recovery path.
