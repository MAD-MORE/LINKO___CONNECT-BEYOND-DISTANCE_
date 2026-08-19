# Phase 4.2 — Android Service & VPN Lifecycle

## Status
COMPLETE — APPROVED / UNLOCKED

## Objective
Define safe lifecycle behavior for Linko's Android VPN/data-plane service.

### Lifecycle states
`IDLE → AUTHORIZING → PREPARING → STARTING → ACTIVE → DEGRADED → STOPPING → STOPPED`

Failure states may transition to `FAILED` and then recover or stop.

### Requirements
- VPN authorization must precede tunnel activation.
- Active forwarding must remain user-visible through Android-supported mechanisms.
- Starting, stopping, revoking, and failure transitions must be idempotent.
- Process death must not silently preserve unauthorized forwarding.
- Network changes must trigger controlled route/path reassessment.
- All packet queues must be bounded.
- Service shutdown must close sockets, release VPN resources, clear ephemeral session state, and stop foreground execution.
- Session credentials must expire independently of UI lifecycle.
- Reconnection must require valid authorization.
- Android permission denial must produce a deterministic stopped state.
- Battery and background limits must be treated as platform constraints, not bypassed.

## Acceptance
A lifecycle test matrix shall cover clean start/stop, permission denial, process death, network switching, provider revocation, receiver cancellation, relay loss, device restart, and resource pressure.
