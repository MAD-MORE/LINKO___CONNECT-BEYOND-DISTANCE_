# Phase 7 — Security

## Status
SECURITY DESIGN BASELINE COMPLETE; verification and implementation evidence remain required.

## Scope
Threat modeling, identity, authentication, authorization, cryptographic key lifecycle, secure transport, device trust, secrets, abuse prevention, logging, incident response, dependency security, and secure release.

## Security invariants
- Default deny for unauthorized sessions.
- Revocation overrides reconnect.
- Secrets are never logged or committed.
- Session credentials are scoped and time-bounded.
- Control plane does not need traffic payloads.
- Relay resources are isolated per authorized session.

## Gates
Threat model review, dependency audit, static analysis, dynamic testing, penetration testing, secret scanning, and incident-response exercise before production launch.
