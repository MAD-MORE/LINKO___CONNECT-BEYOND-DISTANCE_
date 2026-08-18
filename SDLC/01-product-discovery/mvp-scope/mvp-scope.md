# Phase 1.8 — MVP Scope

## Status

**REVIEW — READY FOR PROJECT-OWNER APPROVAL**

## Purpose

Define the smallest credible version of Linko that can prove the core product hypothesis in real-world conditions without prematurely building secondary features.

---

# 1. MVP Objective

The Linko MVP must answer one fundamental question:

> **Can a trusted Provider voluntarily share usable Internet connectivity with an authorized Receiver over distance through Linko, reliably and securely enough to create real user value?**

The MVP is successful only if the end-to-end connectivity loop works on supported Android devices and networks under controlled real-world testing.

---

# 2. MVP Core Loop

```text
Create account
      ↓
Establish trusted relationship
      ↓
Provider becomes available
      ↓
Receiver requests connectivity
      ↓
Provider reviews request
      ↓
Provider approves
      ↓
Linko establishes authorized connection
      ↓
Receiver uses connectivity
      ↓
Session is monitored
      ↓
Provider or Receiver disconnects
      ↓
Session closes safely
```

This is the MVP's highest-priority product flow.

---

# 3. MVP Users

The MVP has two primary roles:

## Receiver

A user who needs temporary Internet connectivity.

## Provider

A user who voluntarily shares connectivity with an authorized Receiver.

One account may perform either role at different times.

---

# 4. MVP Must-Have Features

## 4.1 Account and Authentication

Users must be able to:

- Register.
- Sign in.
- Sign out.
- Maintain an authenticated session.
- Recover access through the supported account-recovery process.

### MVP requirement

Unauthenticated users must not be able to create or control connectivity sessions.

---

## 4.2 User Identity

Each account requires a stable Linko identity that can be used to establish trusted relationships.

The MVP should expose only the identity information necessary for the user experience.

---

## 4.3 Trusted Contacts

Users must be able to establish an authorized relationship with another Linko user.

Required capabilities:

- Find/select a contact through the supported identity mechanism.
- Send an invitation/request where required.
- Accept a relationship.
- Reject a relationship.
- Remove/revoke a relationship.

---

## 4.4 Provider Availability

A Provider must be able to indicate that they are willing to receive connectivity requests.

Availability must not itself authorize connectivity.

```text
Available
   ≠
Authorized
```

---

## 4.5 Connectivity Request

A Receiver must be able to select an eligible Provider and request temporary connectivity.

The request must have a clear state such as:

```text
PENDING
APPROVED
REJECTED
EXPIRED
CANCELLED
```

---

## 4.6 Provider Consent

The Provider must explicitly approve the request before Linko attempts to establish the authorized connectivity session.

The MVP must never silently convert a request into an active connection.

---

## 4.7 Connection Establishment

After approval, Linko must coordinate the technical process required to establish the connectivity path.

This includes the MVP's critical networking components:

- Signaling.
- Connection negotiation.
- Supported peer-to-peer path establishment.
- Appropriate fallback/relay behavior where required.
- Secure traffic transport.
- Session state synchronization.

The exact implementation belongs to later technical architecture and tunnel-engine phases.

---

## 4.8 Active Session

When connectivity is successfully established, both users must see an accurate active-session state.

Minimum information:

- Connection state.
- Session duration.
- Basic permitted usage information.
- Provider/Receiver identity as appropriate.
- Disconnect control.

---

## 4.9 Disconnect

Either authorized participant must be able to end their participation according to the session policy.

Provider must have a reliable stop-sharing control.

After termination:

- Traffic must stop.
- Session state must update.
- Temporary resources must be released.
- The session must not remain usable accidentally.

---

## 4.10 Failure Handling

The MVP must distinguish at least:

- Request failure.
- Connection establishment failure.
- Timeout.
- Provider offline.
- Receiver offline.
- Network interruption.
- Unexpected device termination.
- Relay failure where applicable.

Users must receive understandable status instead of a false "connected" state.

---

# 5. MVP Security Requirements

Security is part of the MVP, not a post-MVP feature.

Minimum requirements:

- Authenticated users.
- Authorized trusted relationships.
- Explicit Provider consent.
- Session authorization.
- Secure signaling.
- Secure transport where applicable.
- Protected credentials/tokens.
- Rate limiting.
- Basic abuse reporting/blocking.
- Safe session termination.
- No unauthorized access to Provider connectivity.

---

# 6. MVP Privacy Requirements

The MVP must define and implement minimum privacy protections for:

- Account information.
- Contact relationships.
- Connectivity requests.
- Session records.
- Usage information.
- Security events.

Only necessary data should be collected.

The MVP must not expose private Provider information to a Receiver merely because a connection exists.

---

# 7. MVP Networking Scope

The MVP networking target is **supported Android-to-Android connectivity sharing over the Internet**.

The first implementation should prioritize a controlled set of known-good environments rather than claiming universal compatibility.

### Initial testing matrix

```text
Provider Android
       │
       ├── Mobile network A
       │
       ├── Mobile network B
       │
       └── Wi-Fi

Receiver Android
       │
       ├── Mobile network A
       │
       ├── Mobile network B
       │
       └── Wi-Fi
```

Cross-network testing is mandatory before claiming the MVP works remotely.

---

# 8. MVP Relay Strategy

The MVP should support a fallback path where direct connectivity is not possible, subject to cost and technical feasibility.

Priority:

```text
1. Attempt authorized direct path
2. If unavailable, use supported relay path
3. If relay unavailable, fail safely
```

Relay infrastructure must be authenticated, encrypted, observable, and cost-controlled.

---

# 9. MVP User Interface

Minimum screens:

```text
Welcome
│
├── Register / Login
│
└── Home
     ├── My Connectivity
     ├── Trusted Contacts
     ├── Requests
     ├── Active Session
     ├── History
     └── Settings
```

### Receiver screen

Primary action:

**Request Internet**

### Provider request screen

Display:

**[Person] wants connectivity**

Actions:

**Approve** | **Reject**

### Active session

Display:

**CONNECTED**

**Duration**

**Usage**

**STOP SHARING**

The UI should remain simple enough for first-time users to understand without networking knowledge.

---

# 10. MVP Notifications

Minimum notifications:

- New connectivity request.
- Request approved.
- Request rejected.
- Connection established.
- Connection failed.
- Session ended.

Notifications must reflect actual backend/session state.

---

# 11. MVP History

A basic session history should record permitted information such as:

- Date/time.
- Session state.
- Duration.
- Participants as permitted.
- Basic usage information where technically available and privacy-approved.

Detailed analytics are not required for the first MVP.

---

# 12. MVP Admin/Operations Requirements

The team must have basic operational visibility into:

- Service health.
- Connection success/failure.
- Authentication failures.
- Session failures.
- Relay usage.
- Abuse reports.
- Crash/error events.

Admin tooling must not expose unnecessary user content or private traffic.

---

# 13. Explicitly OUT OF MVP

The following are intentionally deferred unless later evidence makes them necessary:

- Complex social networking.
- Public connectivity marketplace.
- Anonymous bandwidth trading.
- Cryptocurrency/token economy.
- Large-scale reward system.
- AI assistant features.
- Advanced recommendation engine.
- Enterprise management suite.
- Multi-platform desktop application.
- iOS implementation.
- Global multi-region optimization.
- Complex referral system.
- Advanced analytics dashboard for users.
- Large advertising system.
- Full institutional platform.
- Sophisticated premium tiers.

These can be reconsidered after MVP validation.

---

# 14. MVP Non-Goals

The MVP does not promise:

- Universal Android compatibility.
- Universal carrier compatibility.
- Guaranteed connection through restrictive networks.
- Unlimited bandwidth.
- Free Internet.
- Bypassing carrier policies.
- Circumventing network restrictions.
- Perfect zero-latency connectivity.

Product messaging must remain technically honest.

---

# 15. MVP Acceptance Criteria

The MVP is considered functionally complete only when all applicable criteria below are satisfied:

### Product flow

- [ ] User can register.
- [ ] User can authenticate.
- [ ] Users can establish trusted relationships.
- [ ] Provider can indicate availability.
- [ ] Receiver can request connectivity.
- [ ] Provider receives request.
- [ ] Provider can reject request.
- [ ] Provider can approve request.
- [ ] Approved request can initiate connection establishment.
- [ ] Successful connection produces an ACTIVE session.
- [ ] Receiver can use the supported connectivity path.
- [ ] Session state is synchronized.
- [ ] Provider can stop sharing.
- [ ] Receiver can disconnect.
- [ ] Session terminates safely.

### Reliability

- [ ] Connection failure is detected.
- [ ] Timeout is handled.
- [ ] Device/network interruption is handled.
- [ ] No false ACTIVE state remains after termination.

### Security

- [ ] Unauthorized users cannot initiate protected sessions.
- [ ] Provider consent is mandatory.
- [ ] Session authorization is enforced.
- [ ] Credentials/tokens are protected.
- [ ] Basic abuse controls operate.

### Privacy

- [ ] Required data handling is documented.
- [ ] Access to session information is authorized.
- [ ] Unnecessary private information is not exposed.

### Real-world feasibility

- [ ] At least one supported remote-network configuration works end-to-end.
- [ ] Multiple supported network combinations are tested.
- [ ] Performance and reliability measurements are recorded.

---

# 16. MVP Release Gates

The MVP cannot be declared ready merely because the Android UI works.

Required sequence:

```text
Product requirements
       ↓
Architecture
       ↓
Networking prototype
       ↓
Security implementation
       ↓
Android implementation
       ↓
Automated tests
       ↓
Device tests
       ↓
Cross-network tests
       ↓
Real-world pilot
       ↓
MVP acceptance review
```

---

# 17. MVP Success Hypothesis

The MVP should demonstrate three things:

### Technical

A supported Provider and Receiver can establish and maintain an authorized remote connectivity session.

### Human

Users understand the request/approval model and perceive meaningful value.

### Economic

The cost of providing the service can plausibly support a sustainable business model.

---

# 18. Phase 1 Dependencies

The MVP scope depends on the following later work:

- Phase 2 — Requirements Engineering
- Phase 3 — Technical Architecture
- Phase 4 — Android Architecture
- Phase 5 — Tunnel Engine
- Phase 6 — Signaling
- Phase 7 — Relay Infrastructure
- Phase 8 — Backend
- Phase 9 — Database
- Phase 10 — Security
- Phase 12 — Privacy
- Phase 15 — Testing
- Phase 16 — Real-World Testing

These dependencies do not change the Phase 1 scope; they define where implementation details will be resolved.

---

# 19. Phase 1.8 Acceptance Checklist

- [x] MVP objective defined
- [x] Core loop defined
- [x] Primary MVP roles defined
- [x] Must-have features defined
- [x] Security baseline defined
- [x] Privacy baseline defined
- [x] Networking scope defined
- [x] Relay strategy defined at product level
- [x] UI scope defined
- [x] Notification scope defined
- [x] History scope defined
- [x] Operational requirements defined
- [x] Explicitly out-of-MVP features defined
- [x] Non-goals defined
- [x] Acceptance criteria defined
- [x] Release gates defined
- [x] Success hypothesis defined
- [x] Dependencies documented

---

# Review Gate

**Status:** READY FOR PROJECT-OWNER REVIEW AND APPROVAL

This deliverable is not marked complete until the project owner explicitly approves it.

## Next step after approval

**Phase 1.9 — Market & Competitor Research**

Phase 1 remains **IN PROGRESS**.
