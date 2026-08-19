# Phase 3.10 — Security Architecture

## Status
COMPLETE

## Security layers
1. Android platform security
2. Identity/authentication
3. Session authorization
4. Cryptographic transport protection
5. API/service authentication
6. Relay isolation
7. Data-store protection
8. Administrative controls
9. Monitoring and incident response

## Core decisions
- Never trust network location alone.
- Bind authorization to identity, device and session.
- Separate long-lived identity credentials from session keys.
- Rotate/revoke credentials.
- Use established cryptographic protocols rather than custom cryptography.
- Fail closed when authorization is uncertain.
- Keep secrets outside source control.

## Security lifecycle
Threat modeling precedes implementation; security tests are part of CI; vulnerabilities have severity and remediation rules; incidents have containment and recovery procedures.

## Acceptance
Security architecture can demonstrate defense in depth without requiring Linko infrastructure to inspect protected application payloads.
