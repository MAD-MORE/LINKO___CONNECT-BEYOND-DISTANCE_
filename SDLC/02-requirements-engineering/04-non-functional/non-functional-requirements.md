# Phase 2.4 — Non-Functional Requirements

## Status

**CURRENT — READY FOR PROJECT-OWNER REVIEW**

## Purpose

Define measurable quality attributes and operational constraints for Linko. These requirements describe how well the system must operate, not only what functions it provides.

Values below are engineering targets for the MVP and must be validated against real devices and networks in later testing phases.

---

# 1. Performance

### NFR-001 — API Response Time
**Priority:** P1

For normal authenticated API requests, the service should target a server response time of **≤500 ms at p95**, excluding external provider latency and connectivity establishment.

### NFR-002 — Signaling Latency
**Priority:** P1

For a healthy network, signaling messages should target **≤1 second p95** end-to-end delivery between Linko-controlled signaling endpoints.

### NFR-003 — Session Establishment
**Priority:** P1

For supported networks where the selected connection method is technically available, Linko should target connectivity establishment within **10 seconds at p95**, excluding user decision time.

### NFR-004 — UI Responsiveness
**Priority:** P0

Primary user interactions must remain responsive and must not block the UI thread with networking, cryptography, or long-running operations.

---

# 2. Availability & Reliability

### NFR-005 — Backend Availability
**Priority:** P1

Production backend services should target **99.9% monthly availability** for critical control-plane services, excluding documented maintenance windows.

### NFR-006 — Session State Integrity
**Priority:** P0

The system shall avoid contradictory session states across authoritative components.

### NFR-007 — Safe Failure
**Priority:** P0

When a critical component fails, the system shall fail closed for authorization-sensitive operations and shall not leave an unauthorized connectivity session active.

### NFR-008 — Recovery
**Priority:** P1

Recoverable service failures should automatically recover where safe, without duplicating requests or sessions.

### NFR-009 — Durable Critical State
**Priority:** P0

Critical authorization, session, and security events shall be stored with durability appropriate to their operational importance.

---

# 3. Scalability

### NFR-010 — Horizontal Scalability
**Priority:** P1

Stateless control-plane services should support horizontal scaling without requiring user affinity unless explicitly justified.

### NFR-011 — Capacity Planning
**Priority:** P1

The system shall define measurable capacity limits for API, signaling, relay, database, and notification workloads before production scale.

### NFR-012 — Graceful Degradation
**Priority:** P1

Non-critical features should degrade without taking down core authorization and session-control functions.

---

# 4. Security

### NFR-013 — Encryption in Transit
**Priority:** P0

Sensitive communications shall use modern authenticated encryption and secure transport appropriate to the protocol.

### NFR-014 — Credential Protection
**Priority:** P0

Passwords, tokens, private keys, and other secrets shall not be stored or logged in plaintext where secure alternatives exist.

### NFR-015 — Least Privilege
**Priority:** P0

Services, users, and application components shall receive only the permissions required for their responsibilities.

### NFR-016 — Authorization Freshness
**Priority:** P0

Authorization decisions for sensitive session operations shall use current, verifiable authorization state rather than stale client UI state.

### NFR-017 — Secure Defaults
**Priority:** P0

New accounts, devices, sessions, and services shall default to the safest practical configuration.

### NFR-018 — Security Logging
**Priority:** P0

Security-relevant events shall be auditable without logging unnecessary sensitive content.

### NFR-019 — Dependency Security
**Priority:** P1

Production dependencies shall be monitored for known vulnerabilities and updated according to an established security process.

### NFR-020 — Secret Management
**Priority:** P0

Production secrets shall be stored using approved secret-management mechanisms and shall not be committed to source control.

---

# 5. Privacy

### NFR-021 — Data Minimization
**Priority:** P0

Linko shall collect and retain only data necessary for defined product, security, operational, legal, or accounting purposes.

### NFR-022 — Access Control
**Priority:** P0

Personal and session data shall be accessible only to authorized identities and services.

### NFR-023 — Retention Controls
**Priority:** P1

Data categories shall have documented retention periods and deletion behavior.

### NFR-024 — Privacy by Default
**Priority:** P0

Privacy-protective settings shall be the default unless a user explicitly chooses otherwise and the choice is lawful and appropriate.

### NFR-025 — Traffic Privacy Boundary
**Priority:** P0

Linko shall not inspect, store, or expose user Internet content beyond what is strictly required for an explicitly approved technical or security function.

---

# 6. Android Quality

### NFR-026 — Supported Android Versions
**Priority:** P0

The supported Android version range shall be explicitly defined before MVP release and tested across that range.

### NFR-027 — Lifecycle Safety
**Priority:** P0

Connectivity sessions shall handle Android process, activity, background, and service lifecycle changes safely.

### NFR-028 — Permission Transparency
**Priority:** P0

Runtime permissions shall be requested only when necessary and explained in a user-understandable manner.

### NFR-029 — Battery Efficiency
**Priority:** P1

The application shall minimize unnecessary background CPU, radio, wake-lock, and signaling activity.

### NFR-030 — Resource Limits
**Priority:** P1

The client shall operate within defined memory, CPU, storage, and battery targets suitable for supported Android devices.

---

# 7. Networking Quality

### NFR-031 — Network Adaptability
**Priority:** P0

The system shall tolerate expected changes in network conditions and transition between supported states safely.

### NFR-032 — NAT/Firewall Variability
**Priority:** P1

The connectivity design shall account for common NAT, firewall, carrier, and mobile-network restrictions.

### NFR-033 — Relay Security
**Priority:** P0

Relay infrastructure shall preserve session authorization and confidentiality requirements while forwarding traffic.

### NFR-034 — Connection Integrity
**Priority:** P0

Unexpected endpoint, authorization, or transport changes shall not silently convert an active session into an unauthorized connection.

---

# 8. Usability & Accessibility

### NFR-035 — Understandable State
**Priority:** P0

Users shall be able to understand whether a request is pending, accepted, connecting, active, degraded, or ended.

### NFR-036 — Consent Clarity
**Priority:** P0

Provider consent screens shall clearly communicate the action being authorized before confirmation.

### NFR-037 — Error Clarity
**Priority:** P1

User-facing errors shall explain what happened and what safe action, if any, the user can take.

### NFR-038 — Accessibility
**Priority:** P1

The Android interface shall target Android accessibility best practices, including readable text, meaningful labels, adequate touch targets, and compatibility with assistive technologies where applicable.

---

# 9. Maintainability

### NFR-039 — Modular Architecture
**Priority:** P1

The system shall maintain clear boundaries between identity, signaling, session control, networking, relay, data, and presentation responsibilities.

### NFR-040 — Code Quality
**Priority:** P1

Production code shall follow documented language/framework standards and automated quality checks appropriate to each component.

### NFR-041 — Documentation
**Priority:** P1

Public interfaces, operational procedures, security-sensitive flows, and non-obvious design decisions shall be documented.

### NFR-042 — Dependency Isolation
**Priority:** P1

Critical infrastructure dependencies shall have documented upgrade and replacement considerations.

---

# 10. Observability

### NFR-043 — Structured Logging
**Priority:** P0

Services shall produce structured logs sufficient for diagnosis while avoiding unnecessary personal or secret data.

### NFR-044 — Metrics
**Priority:** P0

Critical services shall expose metrics for availability, latency, errors, resource usage, and relevant session outcomes.

### NFR-045 — Health Checks
**Priority:** P0

Production services shall expose appropriate health/readiness signals.

### NFR-046 — Alerting
**Priority:** P1

Critical failures and security-relevant anomalies shall have defined alert thresholds and response ownership.

### NFR-047 — Correlation
**Priority:** P1

Distributed operations should support safe correlation across services using non-sensitive request/session identifiers.

---

# 11. Disaster Recovery

### NFR-048 — Backup
**Priority:** P0

Critical persistent data shall be backed up according to documented recovery requirements.

### NFR-049 — Restore Testing
**Priority:** P1

Backups shall be periodically tested for successful restoration rather than assumed to be usable.

### NFR-050 — Recovery Objectives
**Priority:** P1

Before production launch, Linko shall define and validate recovery time objectives (RTO) and recovery point objectives (RPO) for critical services.

---

# 12. Compatibility

### NFR-051 — Device Compatibility
**Priority:** P0

The supported device matrix shall be documented and tested, including relevant Android versions, hardware capabilities, and networking behavior.

### NFR-052 — Backend API Compatibility
**Priority:** P1

Versioned APIs shall preserve compatibility according to documented versioning policy.

### NFR-053 — Protocol Compatibility
**Priority:** P0

Connectivity and signaling protocols shall use explicit versioning/capability negotiation where incompatible changes are possible.

---

# 13. Operational & Cost Constraints

### NFR-054 — Resource Accounting
**Priority:** P1

Relay, storage, database, signaling, and notification resource consumption shall be measurable.

### NFR-055 — Cost Guardrails
**Priority:** P1

Infrastructure shall support configurable safeguards against unexpected usage spikes and runaway costs.

### NFR-056 — Rate Controls
**Priority:** P0

Critical APIs and resource-intensive operations shall have appropriate rate and quota controls.

---

# 14. Compliance & Governance

### NFR-057 — Auditability
**Priority:** P0

Material security, authorization, and administrative actions shall be auditable subject to privacy requirements.

### NFR-058 — Policy Configuration
**Priority:** P1

Policy-sensitive behavior shall be configurable where practical rather than hard-coded into unrelated components.

### NFR-059 — Release Governance
**Priority:** P0

Production releases shall use controlled review, testing, versioning, and rollback procedures.

---

# 15. Measurement Rule

Every quantitative target in this document must eventually have:

- Measurement method
- Measurement environment
- Sample size or observation window
- Acceptance threshold
- Owner
- Evidence location

A target that cannot be measured shall be rewritten or explicitly classified as qualitative.

---

# 16. Important Feasibility Boundary

The values in this document are **engineering targets**, not evidence that the targets are already achieved.

Later testing must validate:

- Connection establishment time
- Battery impact
- CPU/memory use
- Mobile-data overhead
- Network compatibility
- Direct connection success rate
- Relay performance
- Backend availability
- Security controls
- Recovery behavior

If real-world evidence proves a target infeasible, the requirement must go through the established change-control process rather than being silently ignored.

---

# 17. Definition of Done — Phase 2.4

- [x] Performance requirements defined
- [x] Availability/reliability requirements defined
- [x] Scalability requirements defined
- [x] Security requirements defined
- [x] Privacy requirements defined
- [x] Android quality requirements defined
- [x] Networking quality requirements defined
- [x] Usability/accessibility requirements defined
- [x] Maintainability requirements defined
- [x] Observability requirements defined
- [x] Disaster recovery requirements defined
- [x] Compatibility requirements defined
- [x] Operational/cost constraints defined
- [x] Compliance/governance requirements defined
- [x] Measurement rule defined
- [x] Feasibility boundary documented

# Review Gate

**Status: READY FOR PROJECT-OWNER REVIEW AND APPROVAL**

This document does not mark Phase 2.4 complete until the project owner explicitly approves it.

## Next step

**2.5 — Connectivity & Networking Requirements**
