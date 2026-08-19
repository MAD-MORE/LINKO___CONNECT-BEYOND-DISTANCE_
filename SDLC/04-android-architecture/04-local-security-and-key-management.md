# Phase 4.4 — Local Security & Key Management

## Status
COMPLETE — APPROVED / UNLOCKED

## Security model

Linko separates account credentials, device identity, session authorization, and ephemeral tunnel keys.

### Requirements
- Long-lived secrets use Android-protected credential storage.
- Private key material is never placed in ordinary preferences or logs.
- Session keys are short-lived and scoped to one authorized session.
- Revocation invalidates affected session material.
- Cryptographic operations are exposed through a small tested security interface.
- Secrets are redacted from crash reports and telemetry.
- Key generation uses platform-approved cryptographic primitives.
- Key rotation and expiration are explicit lifecycle events.
- Backup behavior for sensitive key material is defined deliberately rather than inherited accidentally.
- Compromised-device assumptions are documented and server-side revocation remains authoritative.

## Acceptance
Security tests verify secret redaction, storage protection, session-key isolation, expiration, revocation, and unauthorized reuse resistance.
