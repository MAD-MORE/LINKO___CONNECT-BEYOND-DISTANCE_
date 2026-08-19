# Phase 2.2 — System Actors & Roles

## Status

**CURRENT — READY FOR PROJECT-OWNER REVIEW**

## Purpose

Define every important human, system, service, and external actor that interacts with Linko or influences its operation.

This document establishes role boundaries and authorization responsibilities for the requirements baseline. It does not define implementation architecture.

---

# 1. Actor Model

Linko has two primary human product roles:

1. **Receiver** — requests temporary connectivity assistance.
2. **Provider** — voluntarily makes supported connectivity available.

A user account may perform either role at different times.

Additional actors are required to operate the platform safely and commercially.

---

# 2. Human Actors

## ACT-001 — Receiver

### Definition

An authenticated Linko user who requests connectivity assistance.

### Responsibilities

- Maintain a valid account.
- Establish/use an eligible trusted relationship.
- Request connectivity intentionally.
- Respect session limits and platform policies.
- End the session when assistance is no longer required.
- Report abuse or technical problems when necessary.

### Permissions

The Receiver may:

- View eligible trusted Providers.
- Send a connectivity request.
- Cancel a pending request.
- View their own session status.
- End their own active session.
- View permitted personal session history.
- Block/report another user where supported.

### Restrictions

The Receiver must not:

- Start a Provider session without authorization.
- Bypass Provider consent.
- Control another user's device.
- Access another user's private account information.
- Circumvent Linko limits or abuse controls.

---

## ACT-002 — Provider

### Definition

An authenticated Linko user who voluntarily offers supported connectivity assistance.

### Responsibilities

- Explicitly choose whether to be available.
- Review requests before approval.
- Understand that approved sharing may consume data, battery, CPU, or other resources.
- Stop sharing when desired.
- Report abuse or suspicious activity.

### Permissions

The Provider may:

- Set availability.
- View eligible incoming requests.
- Approve or reject requests.
- Cancel/terminate their active sharing session.
- View permitted personal session history.
- Block/report another user where supported.

### Restrictions

The Provider must not:

- Be forced into sharing.
- Have sharing enabled without explicit consent.
- Allow an unauthorized Receiver to establish a session.
- Be prevented from stopping an active session, subject to safe termination behavior.

---

## ACT-003 — Account Owner

### Definition

The person controlling a Linko account.

### Responsibilities

- Protect account credentials.
- Maintain accurate account/security information where required.
- Control trusted relationships.
- Initiate account security actions.

The Account Owner may act as Receiver, Provider, or both depending on the current session.

---

# 3. Platform Actors

## ACT-004 — Linko Client Application

The Android application executing product logic on the user's device.

### Responsibilities

- Authenticate with authorized services.
- Display accurate session state.
- Obtain required user consent.
- Enforce client-side product controls.
- Coordinate supported connectivity operations.
- Protect locally stored sensitive information.
- Report relevant operational events.

The client must never be treated as the sole security authority for sensitive server-controlled decisions.

---

## ACT-005 — Linko Backend

The trusted service layer coordinating accounts, relationships, requests, authorization, session state, and other platform functions.

### Responsibilities

- Authenticate requests.
- Authorize protected operations.
- Maintain authoritative session/request state.
- Apply policy and abuse controls.
- Issue or coordinate short-lived authorization material where required.
- Record appropriate audit/security events.

---

## ACT-006 — Signaling Service

The service responsible for coordinating connection establishment metadata between participating endpoints.

### Responsibilities

- Coordinate connection setup.
- Exchange only necessary signaling information.
- Authenticate signaling participants.
- Expire stale signaling state.
- Prevent unauthorized signaling actions.

The signaling service must not be assumed to carry all user traffic.

---

## ACT-007 — Relay Service

An infrastructure service that may forward encrypted connectivity traffic when a direct path cannot be established and relay fallback is supported.

### Responsibilities

- Forward authorized traffic according to session policy.
- Enforce session authorization.
- Enforce resource/rate limits.
- Terminate forwarding when authorization expires or the session ends.
- Provide operational metrics.

Relay architecture is a later engineering decision and must not be treated as universally required for every session.

---

## ACT-008 — Notification Service

A platform/service component responsible for delivering relevant request, approval, session, and security notifications.

### Responsibilities

- Deliver permitted notifications.
- Avoid exposing unnecessary sensitive information.
- Respect user notification preferences where applicable.

---

## ACT-009 — Authentication / Identity Provider

An external or Linko-managed identity service responsible for authenticating users according to the selected architecture.

### Responsibilities

- Verify authentication credentials or identity assertions.
- Issue/validate appropriate authentication artifacts.
- Support account recovery/security mechanisms where selected.

The final provider is an implementation decision for a later phase.

---

# 4. Operational & Governance Actors

## ACT-010 — Linko Support Operator

An authorized human responsible for legitimate customer support and operational assistance.

### Permissions

Access must be limited to the minimum information necessary for the support task.

Support operators must not have unrestricted access to user traffic contents.

---

## ACT-011 — Security Operator

An authorized security/operations role responsible for investigating abuse, account compromise, infrastructure attacks, and security incidents.

### Permissions

Security operators may access security telemetry and relevant account/session metadata according to documented policy and least privilege.

---

## ACT-012 — System Administrator

A privileged infrastructure role responsible for maintaining production systems.

Administrative access must use strong authentication, least privilege, logging, and controlled operational procedures.

---

## ACT-013 — Compliance / Legal Reviewer

An authorized role that reviews applicable legal, privacy, telecom, consumer, payment, and distribution requirements.

This role does not directly control user sessions.

---

# 5. External Ecosystem Actors

## ACT-014 — Android / Google Play Platform

The platform and distribution ecosystem imposing technical, security, privacy, and distribution requirements on the application.

## ACT-015 — Mobile Network Operator / ISP

The network provider supplying Internet connectivity to the Provider or Receiver.

The operator is outside Linko's direct control. Compatibility and policy constraints must therefore be treated as external dependencies.

## ACT-016 — Device Operating System

The Android operating system and its networking, permission, lifecycle, battery, and security controls.

## ACT-017 — Payment Provider

A payment service used if Linko implements paid plans, purchases, rewards, or other monetized transactions.

The exact provider is a later business/technical decision.

---

# 6. Authorization Matrix

| Actor | Request | Approve | Reject | Start/Use Session | End Session | Manage Availability | Account Admin |
|---|---:|---:|---:|---:|---:|---:|---:|
| Receiver | Yes | No | No | Yes, after authorization | Yes | No | Limited own account |
| Provider | No | Yes | Yes | Yes, after authorization | Yes | Yes | Limited own account |
| Backend | Controlled | Enforces | Enforces | Coordinates | Enforces | Stores state | Service-level |
| Signaling | Coordinates | No | No | Coordinates | Terminates stale state | No | No |
| Relay | No | No | No | Forwards authorized traffic | Terminates forwarding | No | No |
| Support Operator | No | No | No | No | No | No | Limited support |
| Security Operator | No | No | No | No | Controlled incident actions | No | Security controls |
| System Administrator | No | No | No | No | Operational | No | Infrastructure |

---

# 7. Consent Rules

1. Provider availability does not equal session consent.
2. A connectivity request does not equal approval.
3. Provider approval is mandatory for a Provider session.
4. Session authorization must be time/state bounded.
5. Either participant must have an appropriate disconnect mechanism.
6. Authorization must be revoked when the session ends.
7. Reconnection after authorization expiry requires a valid authorization process.

---

# 8. Trust Boundaries

The following boundaries require explicit security controls:

```text
Receiver Device
      │
      │ authenticated control
      ▼
Linko Backend
      │
      ├──────── Signaling Service
      │
      └──────── Relay Service (when used)
      │
      ▼
Provider Device
```

External boundaries include:

- User → Android device
- Device → Linko services
- Linko services → third-party infrastructure
- Linko → mobile operators/ISPs
- Linko → payment providers
- Linko → Google Play ecosystem

---

# 9. Least-Privilege Rules

Every actor must receive only the permissions required for its function.

In particular:

- Receiver cannot control Provider availability.
- Provider cannot access Receiver account data beyond what product requirements permit.
- Support cannot access traffic contents merely for support.
- Relay cannot decrypt user traffic solely to forward it where the architecture provides end-to-end/encrypted transport.
- Client-side claims cannot override server authorization.
- Administrative roles require controlled privileged access.

---

# 10. Actor-Related Abuse Cases

Requirements must account for:

- Fake accounts
- Stolen accounts
- Unauthorized session attempts
- Provider coercion
- Receiver abuse
- Request spam
- Resource exhaustion
- Relay abuse
- Automated account creation
- Credential attacks
- Session hijacking
- Malicious clients

These become detailed requirements in later Phase 2 categories, especially security and abuse prevention.

---

# 11. Actor Traceability

| Actor | Primary requirement domains |
|---|---|
| Receiver | Functional, UX, security, privacy, networking |
| Provider | Functional, UX, security, privacy, resource usage |
| Account Owner | Identity, security, privacy |
| Android Client | Android, functional, networking, security |
| Backend | Functional, security, reliability, data |
| Signaling | Networking, security, reliability |
| Relay | Networking, security, performance, business |
| Notification Service | Functional, privacy, reliability |
| Identity Provider | Security, authentication, privacy |
| Support Operator | Operations, privacy, security |
| Security Operator | Security, abuse, operations |
| System Administrator | Infrastructure, security, reliability |
| Compliance Reviewer | Compliance, privacy, legal |
| Google Play/Android | Compliance, platform |
| Mobile Operator/ISP | Networking, feasibility |
| Payment Provider | Business, payments, security |

---

# 12. Definition of Done — 2.2

- [x] Primary human actors defined
- [x] Platform actors defined
- [x] Operational actors defined
- [x] External actors defined
- [x] Actor responsibilities defined
- [x] Actor permissions defined
- [x] Authorization matrix defined
- [x] Consent rules defined
- [x] Trust boundaries identified
- [x] Least-privilege rules defined
- [x] Actor abuse cases identified
- [x] Actor traceability defined

---

# Review Gate

**Status: READY FOR PROJECT-OWNER REVIEW AND APPROVAL**

## Next step

**2.3 — Functional Requirements**
