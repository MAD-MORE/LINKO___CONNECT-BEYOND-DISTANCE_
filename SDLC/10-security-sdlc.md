# Phase 10 — Security SDLC

## Objective
Treat security as a continuous engineering process rather than a final review.

## Security model

- Zero-trust authorization
- Strong device identity
- Per-session authorization
- Encrypted data plane
- Short-lived credentials
- Key rotation/revocation
- Secure local secret storage
- Server-side authorization
- Security event auditing

## Threat areas

- Account takeover
- Device impersonation
- Session hijacking
- Replay attacks
- Unauthorized traffic routing
- Relay abuse
- Credential leakage
- API abuse
- Malicious clients
- Denial of service

## Security lifecycle

Requirement → threat model → secure design → implementation → static analysis → dependency scanning → tests → penetration testing → monitoring → incident response.

## Exit criteria

Critical/high-risk findings are resolved or formally accepted, secrets are managed safely, dependency vulnerabilities are reviewed, and incident-response procedures exist.
