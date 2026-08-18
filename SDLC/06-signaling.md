# Phase 6 — Signaling

## Objective
Create the control-plane mechanism that lets authenticated devices discover session endpoints and negotiate an authorized connection without carrying ordinary user traffic.

## Flow

```text
Receiver → request → Linko Signaling → Provider
Provider → approve → Linko Signaling → Receiver
Receiver/Provider → session negotiation → tunnel
```

## Requirements

- Authenticated signaling
- Friend/authorization verification
- One-time session identifiers
- Short-lived connection credentials
- Replay protection
- Session expiration
- Device online/offline state
- Explicit approval
- Audit events without storing traffic contents

## Failure handling

Expired request, revoked friendship, offline Provider, duplicate request, rejected request, and tunnel setup failure must have deterministic states.

## Exit criteria

Two registered devices can securely negotiate an authorized session with no ability to establish data-plane access without explicit Provider approval.
