# Phase 15 — Testing

## Objective
Prove correctness, security, reliability, and maintainability before real-world beta use.

## Test layers

### Unit
Authentication, authorization, state machines, usage accounting, serialization, protocol helpers.

### Integration
Android ↔ backend ↔ signaling ↔ tunnel ↔ relay.

### Network
Wi-Fi, mobile data, IPv4, IPv6, NAT, packet loss, high latency, DNS, network switching.

### Security
Authentication bypass, session replay, revoked device, unauthorized Receiver, malformed input, rate-limit bypass, secret exposure.

### UI
Onboarding, approval, active connection, disconnect, errors, accessibility.

### Regression
Every release runs the complete critical-path suite.

## Exit criteria

Critical-path tests pass consistently and all critical/high findings are resolved or formally accepted.
