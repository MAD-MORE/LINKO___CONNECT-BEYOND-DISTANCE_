# Phase 4.6 — Packet Processing & Routing

## Status
COMPLETE — APPROVED / UNLOCKED

## Objective
Define the Android data-plane processing boundary without implementing a custom unsafe network stack prematurely.

## Requirements
- Android VPNService provides the supported virtual interface boundary.
- Packet processing is isolated from UI and account features.
- Every packet belongs to an authorized active session.
- Queues have bounded memory and explicit backpressure.
- Oversized or malformed input is rejected safely.
- DNS and route policy are explicit rather than implicit.
- No packet payload is persisted for ordinary operation.
- Transport framing is authenticated and protected by the selected tunnel protocol.
- Shutdown drains or drops queued packets according to bounded termination rules.
- Metrics report counts and resource health without recording payload contents.

## Acceptance
Packet-routing tests cover IPv4/IPv6 behavior as supported, DNS policy, malformed packets, queue exhaustion, session isolation, tunnel loss, and clean shutdown.
