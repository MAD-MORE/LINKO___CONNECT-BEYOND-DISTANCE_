# Phase 3.2 — System Context & High-Level Architecture

## Status
COMPLETE — OWNER UNLOCKED SEQUENTIAL EXECUTION

## Purpose
Define the major Linko components and how they interact.

## System Context

```text
Provider Android
   │ encrypted control/signaling + protected traffic
   ▼
Linko Control Plane ─── Identity / API / Session / Policy / Signaling
   │                         │
   │                         └── Database / Cache / Queue
   │
   └────────────── Relay Coordination ──────────────┐
                                                    ▼
                                             Linko Relay
                                                    │
                                                    ▼
                                             Receiver Android

Direct path: Provider Android ⇄ Receiver Android
Fallback:    Provider Android ⇄ Relay ⇄ Receiver Android
```

## Components

1. **Android Client** — user interface, identity, consent, VPN/tunnel control, networking state.
2. **API/Control Plane** — accounts, devices, relationships, authorization, session lifecycle and policy.
3. **Signaling Service** — endpoint coordination and connection negotiation.
4. **Relay Service** — protected packet forwarding when direct connectivity fails.
5. **Data Layer** — durable account, device, relationship, policy and required session records.
6. **Cache** — short-lived state and performance acceleration; never the sole authority for critical authorization.
7. **Queue/Workers** — asynchronous notifications, cleanup, analytics and operational jobs.
8. **Observability** — logs, metrics, traces and health signals with privacy limits.

## Core rule
The backend controls **who may connect**; the data plane carries **authorized traffic**. The control plane must not become an unnecessary payload-processing path.

## Failure model
- API unavailable → new sessions cannot be authorized; existing sessions follow defined lease/expiry rules.
- Signaling unavailable → direct/reconnect attempts may fail, but durable authorization is not corrupted.
- Relay unavailable → direct paths can continue; new relay-dependent sessions fail or use another relay.
- Database unavailable → critical state changes stop safely.
- Android process/network interruption → session transitions to reconnecting/terminated according to policy.

## Architecture acceptance
The architecture must support explicit trust boundaries, direct-path preference, relay fallback, authoritative authorization, session isolation, and measurable failure behavior.

## Next
3.3 — Component Architecture & Responsibilities.
