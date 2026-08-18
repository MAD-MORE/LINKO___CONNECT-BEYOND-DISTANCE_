# Phase 3 — Technical Architecture

## Objective
Design a production-grade architecture for Android clients, backend services, signaling, secure tunneling, relay infrastructure, data, observability, and deployment.

## High-level model

```text
Provider Android
      │
      │ secure tunnel
      ▼
Direct path OR Linko Relay
      │
      ▼
Receiver Android
```

Cloud services provide identity, authorization, friend management, signaling, session coordination, usage accounting, billing, abuse controls, and observability. They should not unnecessarily inspect user traffic.

## Architecture principles

- Prefer direct peer paths when safely feasible.
- Use relay fallback when direct connectivity is unavailable.
- Separate control-plane traffic from data-plane traffic.
- Never build custom cryptography when a proven protocol is suitable.
- Make every session explicitly authorized.
- Design for horizontal scaling.
- Keep relay infrastructure replaceable.

## Main components

- Android client
- Authentication service
- User/device service
- Friendship service
- Signaling service
- Session service
- Relay coordinator
- Relay nodes
- Usage service
- Billing service
- Notification service
- Abuse/risk service
- Admin/operations dashboard
- Database
- Metrics/logging/tracing

## Data plane vs control plane

**Control plane:** identity, friendship, authorization, signaling, session state, usage metadata.

**Data plane:** encrypted network traffic transported between the Receiver, Provider, and relay where required.

## Architecture deliverables

- System architecture diagram
- API contracts
- Threat model
- Deployment topology
- Failure-mode analysis
- Capacity model
- Cost model

## Exit criteria

Architecture decisions are documented, security assumptions are explicit, major failure modes are addressed, and the MVP can be implemented without unresolved architectural blockers.
