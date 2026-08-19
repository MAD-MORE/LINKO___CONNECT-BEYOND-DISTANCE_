# Phase 4.8 — Android Testing Strategy

## Status
COMPLETE — APPROVED / UNLOCKED

## Test layers

1. Unit tests — domain/session/security rules.
2. Repository/service tests — persistence and API behavior.
3. Transport tests — direct/relay abstractions.
4. Android integration tests — VPNService, lifecycle, permissions, notifications.
5. End-to-end tests — Provider → signaling → path establishment → Receiver.
6. Failure tests — network loss, process death, revocation, relay failure, resource exhaustion.
7. Security tests — authorization, replay resistance, secret handling, session isolation.

## Release gates

A release cannot be considered production-ready when critical tests fail, security invariants are broken, or tunnel lifecycle behavior is undefined on a supported Android environment.

## Required evidence

Every critical networking feature shall have deterministic automated tests plus device-level validation before release.
