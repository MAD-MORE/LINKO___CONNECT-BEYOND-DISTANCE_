# Phase 3.4 — Trust Boundaries & Threat Model

## Status
COMPLETE

## Trust zones
1. Provider device — user-controlled but potentially compromised.
2. Receiver device — user-controlled but potentially compromised.
3. Public Internet — untrusted transport.
4. Linko control plane — protected infrastructure but treated as zero-trust between services.
5. Relay plane — infrastructure that must not require plaintext application payloads.
6. Data plane — protected session traffic.
7. Administrative plane — highly privileged and separately controlled.

## Principal threats
- Credential theft
- Session-token theft
- Unauthorized Provider/Receiver pairing
- Replay/injection of signaling messages
- Malicious or compromised client
- Relay resource exhaustion
- API abuse and enumeration
- Session hijacking
- Database compromise
- Insider misuse
- Privacy leakage through logs/metadata
- Denial of service
- Malicious application traffic abusing Provider connectivity

## Required mitigations
- Short-lived credentials and rotation
- Explicit authorization checks
- Session-bound cryptographic material
- Replay protection and nonces
- Rate limits and quotas
- Strict relay isolation
- Secret management
- Audit logging
- Privacy-safe telemetry
- Device/session revocation
- Abuse detection and enforcement

## Security assumption
Linko cannot make a compromised endpoint trustworthy. It must minimize what a compromised component can access and prevent infrastructure from becoming an unnecessary traffic-content observer.

## Acceptance
Each threat has a trust boundary, mitigation owner, detection path and recovery strategy before implementation begins.
