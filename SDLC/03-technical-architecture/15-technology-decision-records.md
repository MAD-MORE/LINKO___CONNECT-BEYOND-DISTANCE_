# Phase 3.15 — Technology Decision Records

## Status
COMPLETE — INITIAL ARCHITECTURE BASELINE

## Decision 1 — Android networking
**Decision:** Use Android's supported VPN framework as the application-level traffic interception boundary for the MVP.
**Reason:** Supported platform mechanism; avoids root/system modification.
**Trade-off:** Android lifecycle, permission and platform constraints must be handled.

## Decision 2 — Direct path plus relay fallback
**Decision:** Attempt direct endpoint connectivity first; use Linko relays when direct connectivity is unavailable or unsuitable.
**Reason:** Reduces latency and infrastructure cost while preserving reachability.

## Decision 3 — Separate control/data planes
**Decision:** API/signaling infrastructure coordinates authorization and connectivity while the data plane forwards protected traffic.
**Reason:** Limits payload exposure and allows independent scaling.

## Decision 4 — Standards-first cryptography/networking
**Decision:** Prefer established protocols and libraries over custom cryptography or invented transport protocols.
**Reason:** Reduces security and interoperability risk.

## Decision 5 — Authoritative backend state
**Decision:** Backend/data services remain authoritative for identity, authorization, policy and critical session state.
**Reason:** Prevents client-side authorization bypass.

## Decision 6 — Relay isolation
**Decision:** Relays are specialized forwarding infrastructure rather than general application servers.
**Reason:** Allows independent scaling, security hardening and cost management.

## Deferred technology choices
Specific database engine, cloud provider, signaling implementation, relay implementation, tunnel protocol/library, orchestration platform and observability vendor remain technology-selection tasks after quantitative evaluation.

## Architecture gate
No implementation technology is considered final merely because it is listed here. Each major technology must have a later decision record covering requirements fit, security, performance, operational complexity, cost, lock-in and failure behavior.

## Phase 3 architecture acceptance
The architecture is considered structurally complete when context, components, trust boundaries, tunnel, signaling, relay, data, API, security, reliability, scaling, deployment and observability designs are consistent with the requirements baseline.
