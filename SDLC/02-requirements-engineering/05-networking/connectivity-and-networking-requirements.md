# Phase 2.5 — Connectivity & Networking Requirements

## Status

**CURRENT — READY FOR PROJECT-OWNER REVIEW**

## Purpose

Define the networking behavior Linko must support when a Provider voluntarily shares authorized connectivity with a Receiver. These requirements establish the network problem and constraints without prematurely locking the implementation architecture.

---

# 1. Core Networking Principle

Linko shall provide a controlled mechanism for an authorized Receiver to use connectivity made available by an authorized Provider, subject to device, operating-system, carrier, NAT, firewall, infrastructure, and policy limitations.

**Distance is not itself the network transport.** A remote session requires an Internet path between Linko participants and/or Linko infrastructure.

Linko must not represent global connectivity as guaranteed merely because two users are geographically separated.

---

# 2. Connectivity Model

### NET-001 — Provider Uplink
**Priority:** P0

A Provider must have an active network path capable of reaching Linko's required control/infrastructure services before a shared session can be established.

### NET-002 — Receiver Uplink
**Priority:** P0

A Receiver must have sufficient connectivity to reach Linko's control/infrastructure services needed to negotiate and establish the session.

### NET-003 — End-to-End Path
**Priority:** P0

The system shall establish a valid end-to-end data path between the Receiver and the authorized Provider connectivity path, either directly or through supported infrastructure.

### NET-004 — No Physical-Distance Assumption
**Priority:** P0

The system shall not use geographic proximity as a requirement for establishing a remote connectivity session.

---

# 3. Direct Connectivity

### NET-005 — Direct Path Capability
**Priority:** P1

Where network conditions permit, Linko should support a direct peer path that avoids unnecessary relay traffic.

### NET-006 — NAT Detection
**Priority:** P1

The networking system shall detect or infer relevant NAT characteristics required for connection establishment.

### NET-007 — Firewall Constraints
**Priority:** P0

Connection establishment shall account for firewall restrictions and shall fail safely when required traffic cannot be authorized or delivered.

### NET-008 — Carrier Restrictions
**Priority:** P0

The system shall account for mobile-carrier restrictions such as carrier-grade NAT, blocked inbound traffic, port restrictions, or tethering-related limitations.

### NET-009 — Direct-Path Failure
**Priority:** P1

Failure to establish a direct path shall transition to an approved fallback decision rather than leaving an ambiguous active session.

---

# 4. Relay Connectivity

### NET-010 — Relay Fallback
**Priority:** P1

Where direct connectivity cannot be established and relay infrastructure is available, Linko shall support an authorized relay path.

### NET-011 — Relay Authorization
**Priority:** P0

A relay shall forward traffic only for an authorized active session.

### NET-012 — Relay Session Isolation
**Priority:** P0

Traffic belonging to separate sessions shall remain logically isolated.

### NET-013 — Relay Termination
**Priority:** P0

Relay forwarding shall stop when the authoritative session authorization expires or is revoked.

### NET-014 — Relay Capacity Protection
**Priority:** P1

Relay infrastructure shall enforce capacity, rate, and resource controls to prevent a small number of sessions from exhausting shared resources.

---

# 5. Signaling

### NET-015 — Secure Signaling
**Priority:** P0

Signaling traffic shall use authenticated and appropriately protected transport.

### NET-016 — Capability Exchange
**Priority:** P0

Before selecting a transport path, participants shall exchange only the technical capabilities required for negotiation.

### NET-017 — Endpoint Authentication
**Priority:** P0

The networking system shall authenticate or cryptographically validate relevant session endpoints before establishing protected data forwarding.

### NET-018 — Session Identifier
**Priority:** P0

Each connectivity session shall have a unique identifier suitable for safe correlation across signaling and operational components.

### NET-019 — Replay Protection
**Priority:** P0

Security-sensitive signaling messages shall include appropriate mechanisms to prevent replay or reuse of expired authorization.

---

# 6. Transport

### NET-020 — Supported Transport Set
**Priority:** P0

Linko shall define an explicit supported transport set for control, signaling, and data-plane traffic before implementation is baselined.

### NET-021 — Transport Negotiation
**Priority:** P1

Where multiple supported transports exist, the system shall select a compatible transport based on capability, policy, network conditions, and security requirements.

### NET-022 — Encryption
**Priority:** P0

Sensitive data-plane traffic shall use authenticated encryption or an equivalent secure protocol appropriate to the selected transport.

### NET-023 — Integrity
**Priority:** P0

The receiver shall be able to detect unauthorized modification of protected traffic.

### NET-024 — Confidentiality
**Priority:** P0

Linko infrastructure shall not unnecessarily expose the contents of protected user traffic while providing relay functionality.

---

# 7. Network Changes

### NET-025 — Network Transition Detection
**Priority:** P0

The Android client shall detect relevant changes in network connectivity that can affect an active session.

### NET-026 — Network Handover
**Priority:** P1

Where technically supported, the system should attempt controlled recovery when a device transitions between supported networks.

### NET-027 — Temporary Interruption
**Priority:** P0

Short network interruptions shall move the session into a recoverable state where safe rather than immediately creating duplicate sessions.

### NET-028 — Permanent Failure
**Priority:** P0

A session shall terminate when network failure prevents safe continuation beyond configured recovery limits.

---

# 8. Bandwidth & Resource Controls

### NET-029 — Bandwidth Visibility
**Priority:** P1

Where platform and network information permit, Linko shall provide appropriate session bandwidth/usage information to authorized participants.

### NET-030 — Provider Usage Protection
**Priority:** P0

The Provider shall have mechanisms to limit or stop shared connectivity to protect against unexpected resource consumption.

### NET-031 — Session Quotas
**Priority:** P1

The system shall support configurable session quotas where required for safety, fairness, infrastructure protection, or business rules.

### NET-032 — Congestion Handling
**Priority:** P1

The networking layer shall respond safely to congestion and degraded throughput without bypassing security or authorization controls.

---

# 9. Connection Quality

### NET-033 — Connectivity State
**Priority:** P0

The system shall maintain a meaningful connection state reflecting whether the data path is preparing, connected, degraded, recovering, or terminated.

### NET-034 — Latency Awareness
**Priority:** P1

Relevant components shall be able to measure or estimate network latency for operational and connection-quality decisions where practical.

### NET-035 — Packet Loss Awareness
**Priority:** P1

Where supported by the selected transport, Linko shall detect meaningful packet loss or transport degradation.

### NET-036 — Throughput Awareness
**Priority:** P1

Where technically available, Linko shall measure or estimate useful throughput without generating unnecessary traffic solely for measurement.

---

# 10. Failure & Recovery

### NET-037 — Connection Timeout
**Priority:** P0

Connection establishment shall have explicit timeout limits.

### NET-038 — Keepalive
**Priority:** P1

Active sessions shall use appropriate liveness mechanisms where required by the selected transport.

### NET-039 — Stale Session Cleanup
**Priority:** P0

Stale networking state shall be cleaned up after defined inactivity or authorization-expiration conditions.

### NET-040 — Safe Reconnect
**Priority:** P0

Reconnect operations shall revalidate session authorization and shall not assume that previous authorization remains valid indefinitely.

### NET-041 — Duplicate Prevention
**Priority:** P0

Recovery procedures shall prevent accidental creation of multiple active data paths for one logical session unless explicitly supported.

---

# 11. DNS & Internet Access Considerations

### NET-042 — DNS Dependency
**Priority:** P1

Required Linko control services shall define their DNS dependencies and failure behavior.

### NET-043 — Restricted Networks
**Priority:** P0

The system shall handle environments where required ports, domains, protocols, or destinations are blocked.

### NET-044 — Captive Portals
**Priority:** P1

The system shall detect or safely handle networks requiring captive-portal authentication where technically possible.

### NET-045 — No False Connectivity
**Priority:** P0

The application shall not display a session as fully connected merely because control-plane signaling is available when the authorized data path is unavailable.

---

# 12. Mobile Network & Tethering Boundary

### NET-046 — Android Platform Constraints
**Priority:** P0

The design shall comply with Android networking APIs, background-execution rules, VPN/tunnel APIs, permissions, and platform security boundaries applicable to the supported Android versions.

### NET-047 — Carrier/Tethering Policy
**Priority:** P0

The system shall not assume that every carrier or plan permits third-party sharing/tethering-like behavior.

### NET-048 — Provider Disclosure
**Priority:** P0

Before enabling sharing, the Provider shall be informed that carrier, plan, device, or OS restrictions may prevent or limit the session.

### NET-049 — Unsupported Environment
**Priority:** P0

When the required networking capability is unavailable on a device or network, Linko shall report the limitation rather than attempting an unauthorized workaround.

---

# 13. Security Boundary

### NET-050 — Authorization Before Data Forwarding
**Priority:** P0

No data-plane forwarding shall begin until required session authorization is valid.

### NET-051 — Revocation Enforcement
**Priority:** P0

Data forwarding shall stop when valid session authorization is revoked or expires, subject only to bounded cleanup time.

### NET-052 — Endpoint Binding
**Priority:** P0

The established data path shall be bound to the authorized session and relevant endpoints so that another user or session cannot silently take over the path.

### NET-053 — Key Material Protection
**Priority:** P0

Cryptographic key material used by networking components shall be protected according to the security requirements established in Phase 2.7.

---

# 14. Observability

### NET-054 — Connection Metrics
**Priority:** P1

The system shall record appropriate non-sensitive networking metrics such as connection attempts, success/failure, establishment time, and termination reason.

### NET-055 — Relay Metrics
**Priority:** P1

Relay infrastructure shall expose operational metrics sufficient to monitor capacity, errors, throughput, and resource consumption.

### NET-056 — Diagnostic Correlation
**Priority:** P1

Networking events shall support correlation using the session identifier without exposing unnecessary user content.

---

# 15. Geographic & Internet Boundaries

### NET-057 — Global Internet Dependency
**Priority:** P0

A remote Linko session depends on Internet reachability between participants and/or Linko infrastructure. Linko shall not claim that physical distance can be eliminated as a network dependency.

### NET-058 — Region Selection
**Priority:** P1

Where relay infrastructure is deployed across regions, the system should select an appropriate region according to availability, latency, policy, and capacity.

### NET-059 — Regional Failure
**Priority:** P1

Where multiple relay/control regions exist, the system should support controlled failover subject to authorization and session constraints.

---

# 16. Definition of Done — Phase 2.5

- [x] Core connectivity model defined
- [x] Direct connectivity requirements defined
- [x] NAT/firewall/carrier constraints defined
- [x] Relay requirements defined
- [x] Signaling requirements defined
- [x] Transport requirements defined
- [x] Network transition requirements defined
- [x] Bandwidth/resource controls defined
- [x] Connection-quality requirements defined
- [x] Failure/recovery requirements defined
- [x] DNS/restricted-network requirements defined
- [x] Android/mobile-network boundary defined
- [x] Security boundary defined
- [x] Networking observability defined
- [x] Geographic/Internet limitations explicitly documented

# Review Gate

**Status: READY FOR PROJECT-OWNER REVIEW AND APPROVAL**

This document does not mark Phase 2.5 complete until the project owner explicitly approves it.

## Next step

**2.6 — Android Platform Requirements**
