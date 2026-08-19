# Phase 3.1 — Architecture Principles & Constraints

## Status

**CURRENT — READY FOR PROJECT-OWNER REVIEW**

## Purpose

Establish the architectural rules that every later Linko design and implementation must follow. This document is the guardrail for the Android client, control plane, signaling system, relay infrastructure, data layer, security model, deployment model, and future scaling work.

This is an architecture decision document, not an implementation guide. It deliberately defines **what the architecture must achieve** before individual technologies are finalized.

---

# 1. Core Architecture Principles

### ARC-001 — User-Controlled Connectivity
**Priority:** P0

A Linko connectivity session shall exist only within an explicitly authorized Provider/Receiver relationship and shall be controllable by the participating users.

### ARC-002 — Separation of Control Plane and Data Plane
**Priority:** P0

Linko shall separate control operations—identity, authorization, session management, signaling, policy, and coordination—from the user traffic data plane.

**Reason:** Control failures should not require exposing user traffic to application servers, and data forwarding should not require the backend to inspect application payloads.

### ARC-003 — End-to-End Data-Plane Protection
**Priority:** P0

Where technically supported by the selected tunnel design, user traffic shall remain protected between the authorized endpoints and shall not require routine payload inspection by Linko infrastructure.

### ARC-004 — Zero Trust Between Components
**Priority:** P0

No service, client, relay, or network location shall be trusted solely because it is inside Linko infrastructure.

### ARC-005 — Least Privilege
**Priority:** P0

Every component shall receive the minimum network, data, API, and administrative permissions required for its role.

### ARC-006 — Explicit Trust Boundaries
**Priority:** P0

The architecture shall document trust boundaries between Android devices, users, signaling, APIs, relays, databases, operators, and third-party infrastructure.

### ARC-007 — Secure by Default
**Priority:** P0

The architecture shall prefer secure defaults and shall not require users or operators to understand low-level security configuration for normal operation.

### ARC-008 — Fail Closed for Authorization
**Priority:** P0

If authorization state cannot be verified, sensitive operations shall stop or remain unavailable rather than silently continuing with assumed permission.

---

# 2. Android Architecture Constraints

### ARC-009 — Android Platform Compliance
**Priority:** P0

The Android client shall use supported Android networking and VPN mechanisms and shall respect platform lifecycle, permission, battery, and background-execution constraints.

### ARC-010 — VPN-Based Architecture Boundary
**Priority:** P0

The MVP architecture shall treat Android's supported VPN framework as the primary application-level mechanism for capturing and forwarding device traffic where the product requirements permit it.

### ARC-011 — No Unauthorized System Modification
**Priority:** P0

The architecture shall not depend on root access, modified firmware, carrier bypasses, or unauthorized system changes for normal operation.

### ARC-012 — User-Visible Networking State
**Priority:** P0

Active networking functionality shall remain understandable and controllable through Android's supported user-visible mechanisms.

### ARC-013 — Lifecycle Resilience
**Priority:** P0

The Android architecture shall tolerate process recreation, connectivity changes, screen state changes, and system resource pressure without creating unauthorized persistent sessions.

---

# 3. Control Plane Architecture

### ARC-014 — Authoritative Backend
**Priority:** P0

The backend shall be authoritative for identity, authorization, policy, and critical session state that cannot safely be controlled solely by clients.

### ARC-015 — Stateless API Preference
**Priority:** P1

API services should remain stateless where practical, with durable state delegated to appropriate managed data services.

### ARC-016 — Session State Machine
**Priority:** P0

Connectivity sessions shall use an explicit state machine with valid transitions and server-side authorization checks.

### ARC-017 — Idempotent Control Operations
**Priority:** P0

Retryable control-plane operations shall be idempotent or protected against duplicate effects.

### ARC-018 — Versioned APIs
**Priority:** P0

Public and client-facing APIs shall have explicit compatibility and versioning rules.

---

# 4. Signaling Architecture

### ARC-019 — Signaling as Coordination
**Priority:** P0

Signaling shall coordinate endpoints and connectivity establishment rather than becoming the default path for user traffic payloads.

### ARC-020 — Authenticated Signaling
**Priority:** P0

Signaling messages shall be associated with authenticated identities and authorized sessions.

### ARC-021 — Replay Resistance
**Priority:** P0

Signaling operations shall resist replay, stale-session reuse, and unauthorized message injection.

### ARC-022 — Signaling Failure Isolation
**Priority:** P1

Temporary signaling failures should not corrupt durable session state or cause unrelated active sessions to become unauthorized.

---

# 5. Data Plane & Relay Architecture

### ARC-023 — Direct Path Preference
**Priority:** P0

Where technically and operationally possible, Linko should prefer a direct endpoint-to-endpoint path to reduce latency and relay infrastructure cost.

### ARC-024 — Relay Fallback
**Priority:** P0

The architecture shall support a relay path for environments where direct connectivity cannot be established.

### ARC-025 — Relay as Forwarder
**Priority:** P0

Relay infrastructure shall primarily forward authorized protected traffic and shall not require application-layer payload inspection for normal operation.

### ARC-026 — Session Isolation
**Priority:** P0

Traffic and resources belonging to different sessions shall be isolated.

### ARC-027 — Relay Authorization
**Priority:** P0

A relay shall accept forwarding work only when it can validate an authorized session.

### ARC-028 — Relay Resource Limits
**Priority:** P0

Relays shall enforce connection, bandwidth, memory, CPU, and session limits to prevent a single user or attack from exhausting infrastructure.

---

# 6. Networking Architecture

### ARC-029 — NAT Traversal Strategy
**Priority:** P0

The architecture shall account for NAT and firewall traversal and shall define a standards-based strategy for attempting direct connectivity before relay fallback.

### ARC-030 — Path Negotiation
**Priority:** P0

Endpoints shall be able to negotiate an available connectivity path without exposing unauthorized session information.

### ARC-031 — Connectivity Agility
**Priority:** P1

The data plane should tolerate changes in network type, address, and route when Android and the selected transport mechanisms allow it.

### ARC-032 — IPv4/IPv6 Awareness
**Priority:** P1

The architecture shall consider both IPv4 and IPv6 environments and define fallback behavior.

---

# 7. Security Architecture

### ARC-033 — Identity-Centric Authorization
**Priority:** P0

Security decisions shall be tied to authenticated identities, devices, session state, and policy rather than network location alone.

### ARC-034 — Key Separation
**Priority:** P0

Long-term identity credentials, session credentials, and transport keys shall have distinct roles and lifecycles where appropriate.

### ARC-035 — Secret Isolation
**Priority:** P0

Production secrets shall remain outside source code and ordinary client application assets.

### ARC-036 — Security Boundary Documentation
**Priority:** P0

Every architecture diagram involving sensitive information shall identify trust boundaries and security assumptions.

---

# 8. Data Architecture

### ARC-037 — Single Source of Truth
**Priority:** P0

Each critical piece of state shall have a clearly identified authoritative data source.

### ARC-038 — Durable State Separation
**Priority:** P0

Durable account, authorization, and required session state shall be separated conceptually from ephemeral connection state.

### ARC-039 — Schema Evolution
**Priority:** P0

Data models shall support controlled migration and compatibility across supported application versions.

### ARC-040 — Privacy-Aware Data Model
**Priority:** P0

The architecture shall avoid storing traffic payloads and shall minimize personally identifiable and network metadata to what the requirements justify.

---

# 9. Reliability Architecture

### ARC-041 — No Single Non-Replaceable Failure Point
**Priority:** P0

Critical infrastructure shall be designed so that a single service instance failure does not unnecessarily terminate the entire product.

### ARC-042 — Graceful Degradation
**Priority:** P0

When optional services fail, Linko should degrade gracefully rather than fail unrelated functionality.

### ARC-043 — Recovery-Oriented Design
**Priority:** P0

Services shall define restart, recovery, retry, timeout, and state-reconciliation behavior.

### ARC-044 — Bounded Retries
**Priority:** P0

Automated retries shall use bounded attempts and backoff to avoid retry storms.

---

# 10. Scalability Architecture

### ARC-045 — Horizontal Scaling Preference
**Priority:** P1

Stateless control-plane components should be horizontally scalable where practical.

### ARC-046 — Independent Relay Scaling
**Priority:** P0

Relay capacity shall be scalable independently from account and control-plane services.

### ARC-047 — Regional Expansion
**Priority:** P1

The architecture should allow relay and service deployment across multiple regions without requiring a redesign of core session semantics.

### ARC-048 — Resource-Aware Scheduling
**Priority:** P1

Infrastructure scheduling shall account for bandwidth, connection count, CPU, memory, and geographic/latency characteristics.

---

# 11. Observability Architecture

### ARC-049 — Operational Visibility
**Priority:** P0

The architecture shall expose enough metrics, logs, traces, and health signals to diagnose failures without collecting unnecessary user traffic content.

### ARC-050 — Correlation IDs
**Priority:** P0

Requests and control-plane operations shall support safe correlation using non-sensitive identifiers.

### ARC-051 — Privacy-Safe Telemetry
**Priority:** P0

Observability data shall not become an unintended source of sensitive user or traffic-content exposure.

### ARC-052 — Health Checks
**Priority:** P0

Critical services shall expose appropriate health/readiness signals for deployment and orchestration.

---

# 12. Deployment Architecture

### ARC-053 — Environment Separation
**Priority:** P0

Development, staging, and production environments shall be separated appropriately.

### ARC-054 — Immutable Release Preference
**Priority:** P1

Production services should be deployed from reproducible, versioned build artifacts.

### ARC-055 — Safe Rollout
**Priority:** P0

The architecture shall support controlled deployment, rollback, and rapid disabling of problematic releases.

### ARC-056 — Configuration Separation
**Priority:** P0

Environment-specific configuration and secrets shall be separated from application source code.

---

# 13. Cost Architecture

### ARC-057 — Cost Visibility
**Priority:** P0

The architecture shall make major infrastructure cost drivers measurable, especially relay bandwidth, compute, storage, and egress.

### ARC-058 — Relay Cost Control
**Priority:** P0

The architecture shall avoid unnecessary relay usage where direct connectivity is safely available.

### ARC-059 — Resource Quotas
**Priority:** P0

Infrastructure and users shall have enforceable resource limits appropriate to the product's business and abuse model.

---

# 14. Technology Selection Constraints

### ARC-060 — Standards Before Novelty
**Priority:** P0

Technology choices shall favor mature, documented, interoperable standards over custom protocols unless a demonstrated requirement justifies otherwise.

### ARC-061 — Replaceability
**Priority:** P1

Critical infrastructure components should be replaceable without rewriting unrelated business logic.

### ARC-062 — Managed Services Where Justified
**Priority:** P1

Managed infrastructure may be preferred where it materially improves reliability/security and does not create unacceptable lock-in or cost.

### ARC-063 — Open Protocol Boundaries
**Priority:** P1

Networking and service boundaries should use documented protocols and interfaces to reduce unnecessary vendor dependence.

---

# 15. Architecture Non-Goals

### ARC-064 — No Magic Distance Elimination
**Priority:** P0

The architecture shall not claim that physical distance, propagation delay, carrier coverage, or the laws of networking can be eliminated. Linko can make authorized connectivity possible across distance through Internet-based routing, but cannot make the connection physically local.

### ARC-065 — No Carrier Data Creation
**Priority:** P0

Linko does not create mobile-data capacity. The Provider's existing Internet/mobile connectivity is used to forward traffic.

### ARC-066 — No Unauthorized Carrier Bypass
**Priority:** P0

The architecture shall not depend on bypassing carrier billing, carrier access controls, Android security, or network-provider restrictions.

### ARC-067 — No Invisible Connectivity
**Priority:** P0

The architecture shall not require hidden networking access after a user has ended or revoked sharing.

---

# 16. Architecture Decision Gates

Before Phase 3 architecture is baselined, the project shall produce and review:

1. System context diagram
2. Container/component architecture
3. Trust-boundary diagram
4. Control-plane/data-plane separation
5. Android networking architecture
6. Direct-connect/relay strategy
7. Signaling architecture
8. Data architecture
9. Security architecture
10. Deployment topology
11. Observability architecture
12. Failure/recovery model
13. Technology decision records
14. Architecture risk register

# 17. Definition of Done — Phase 3.1

- [x] Core architecture principles defined
- [x] Android constraints defined
- [x] Control-plane principles defined
- [x] Signaling constraints defined
- [x] Data-plane/relay principles defined
- [x] Networking constraints defined
- [x] Security architecture principles defined
- [x] Data architecture principles defined
- [x] Reliability principles defined
- [x] Scalability principles defined
- [x] Observability principles defined
- [x] Deployment principles defined
- [x] Cost constraints defined
- [x] Technology-selection constraints defined
- [x] Architecture non-goals defined
- [x] Architecture decision gates defined

# Review Gate

**Status: READY FOR PROJECT-OWNER REVIEW**

## Next step

**3.2 — System Context & High-Level Architecture**
