# Phase 1.6 — Core Use Cases

## Status

**REVIEW — READY FOR PROJECT-OWNER APPROVAL**

## Purpose

Define the core capabilities Linko must support from the perspective of its users and trusted connectivity-sharing system. These are product-level use cases; implementation details belong to later SDLC phases.

## Actors

- **Receiver** — requests temporary connectivity.
- **Provider** — voluntarily provides connectivity and controls the session.
- **Trusted Contact** — a user who may act as either Receiver or Provider.
- **Linko System** — authentication, authorization, session coordination, notifications, policy enforcement, and state management.
- **Security/Abuse System** — detects and responds to unauthorized or abusive behavior according to later approved security policies.

---

## UC-01 — Create an account

**Primary actor:** User  
**Goal:** Establish a Linko identity.

**Preconditions:** User has a supported device and Internet access sufficient for registration.

**Main flow:**
1. User opens Linko.
2. User chooses registration.
3. User provides required registration information.
4. Linko validates the information.
5. Linko creates the account.
6. Linko establishes an authenticated session.

**Alternative/exception flows:** Invalid information, duplicate account, service unavailable, verification failure.

**Postcondition:** A valid account exists or registration fails safely.

**Security:** Credentials and verification data must be protected; exact mechanisms are defined later.

**Acceptance:** A legitimate user can register without creating duplicate or unauthenticated accounts.

---

## UC-02 — Authenticate and manage a session

**Primary actor:** User

**Goal:** Securely access the user's Linko account.

**Main flow:** Authenticate → validate account state → establish session → show authorized features.

**Exceptions:** Invalid credentials, expired session, suspended account, service unavailable.

**Acceptance:** Users cannot access another user's account or protected session controls.

---

## UC-03 — Manage trusted contacts

**Primary actor:** User

**Goal:** Control which people can request or provide connectivity.

**Main flow:**
1. User opens trusted contacts.
2. User searches/selects a person using the supported identity mechanism.
3. User sends an invitation/request where required.
4. Contact accepts or declines.
5. Linko updates the relationship state.

**Alternative flows:** Contact declines, request expires, user removes contact, account becomes unavailable.

**Acceptance:** Trust relationships require the required authorization and can be revoked.

---

## UC-04 — Set Provider availability

**Primary actor:** Provider

**Goal:** Indicate whether the Provider is willing and able to receive connectivity requests.

**Main flow:** Provider opens sharing controls → chooses availability → Linko updates Provider status.

**Acceptance:** Availability does not itself grant access. A separate authorized request and approval remain required.

---

## UC-05 — Request connectivity

**Primary actor:** Receiver

**Goal:** Ask an eligible trusted Provider for temporary connectivity.

**Main flow:**
1. Receiver selects an eligible trusted Provider.
2. Receiver selects/enters the required request information.
3. Receiver confirms the request.
4. Linko verifies authorization and sends the request.
5. Receiver sees pending status.

**Exceptions:** Provider unavailable, relationship invalid, request rate limit reached, account restricted, service unavailable.

**Acceptance:** A Receiver can send an authorized request and cannot automatically activate a connection.

---

## UC-06 — Receive, review, and decide on a request

**Primary actor:** Provider

**Goal:** Decide whether to share connectivity.

**Main flow:** Receive request → identify Receiver → review information/controls → approve or reject.

**If rejected:** No connection is established.

**If approved:** Linko creates an authorized connection-establishment task.

**Acceptance:** Provider consent is explicit and recorded as required by policy.

---

## UC-07 — Establish an authorized connection

**Primary actor:** Linko System  
**Supporting actors:** Provider, Receiver

**Goal:** Establish the Linko connectivity session after authorization.

**Preconditions:** Valid accounts, trusted relationship, active request, Provider approval.

**Main flow:**
1. Linko validates authorization.
2. Linko coordinates connection establishment.
3. Linko reports progress.
4. Connection succeeds or fails.
5. On success, session becomes ACTIVE.

**Exceptions:** NAT/firewall restrictions, carrier restrictions, device/OS limitations, network loss, relay/signaling failure, timeout.

**Acceptance:** No session becomes ACTIVE without the required authorization, and failed establishment never appears as an active session.

---

## UC-08 — Monitor an active session

**Primary actors:** Provider, Receiver

**Goal:** Understand whether connectivity is active and view permitted session information.

**Main flow:** Open active session → display state → display permitted usage/session information → update state as conditions change.

**Acceptance:** Both sides receive truthful session state; private information not required for the experience is not exposed.

---

## UC-09 — Control session limits

**Primary actor:** Provider

**Goal:** Control sharing according to supported product limits.

**Possible controls:** Session duration, permitted usage, availability, or other approved limits.

**Main flow:** Provider selects control → Linko validates it → control applies to the session.

**Acceptance:** Limits cannot silently exceed Provider authorization and are enforced consistently with later technical/security specifications.

---

## UC-10 — Disconnect a session

**Primary actors:** Provider, Receiver

**Goal:** End active connectivity.

**Main flow:** User selects disconnect/stop → Linko terminates session → both sides receive updated state → resources are released.

**Acceptance:** Provider can stop sharing immediately; Receiver can end their own session; terminated sessions do not remain usable.

---

## UC-11 — Handle connection failure

**Primary actor:** Linko System

**Goal:** Safely handle a failed connection attempt.

**Main flow:** Detect failure/timeout → mark establishment failed → release temporary state → communicate understandable status → allow retry where appropriate.

**Acceptance:** Failed connections do not create false active sessions or leave unintended authorization active.

---

## UC-12 — Handle unexpected network/device failure

**Primary actor:** Linko System

**Goal:** Fail safely when an active session loses required connectivity or a participating device becomes unavailable.

**Main flow:** Detect unhealthy session → transition state → terminate/recover according to approved policy → release resources → update users when possible.

**Acceptance:** Unexpected failure cannot grant persistent unintended access.

---

## UC-13 — Handle request expiration

**Primary actor:** Linko System

**Goal:** Prevent stale requests from remaining valid indefinitely.

**Main flow:** Request reaches lifetime limit → Linko expires request → Receiver is informed → no connection can be established from that expired request.

**Acceptance:** An expired request can never automatically become active.

---

## UC-14 — Block/report a user

**Primary actors:** User, Linko System

**Goal:** Protect users from unwanted or abusive contacts.

**Main flow:** User selects report/block → Linko validates action → relationship/access is restricted → event is handled according to policy.

**Acceptance:** Blocked users cannot continue the prohibited interaction through the blocked relationship.

---

## UC-15 — Detect and respond to unauthorized access

**Primary actor:** Security/Abuse System

**Goal:** Prevent unauthorized connectivity access.

**Main flow:** Unauthorized request/activity detected → reject access → apply appropriate security controls → optionally record security event according to privacy policy.

**Acceptance:** Unauthorized users cannot obtain Provider connectivity.

---

## UC-16 — Handle abusive active sessions

**Primary actor:** Security/Abuse System

**Goal:** Protect users and Linko infrastructure from abusive behavior.

**Main flow:** Policy/security signal detected → evaluate → warn, restrict, or terminate according to approved policy → update relevant user state.

**Boundary:** Exact detection algorithms, thresholds, enforcement architecture, and retention are defined in later Security, Abuse Prevention, and Privacy phases.

**Acceptance:** Abuse controls can terminate or restrict a session without bypassing legitimate Provider control.

---

## UC-17 — Receive notifications

**Primary actors:** Provider, Receiver

**Goal:** Stay informed about requests and important session state.

**Examples:** New request, approval/rejection, connection established, connection failed, session ending, session terminated.

**Acceptance:** Notifications reflect actual system state and do not expose unnecessary sensitive information.

---

## UC-18 — View session history and usage information

**Primary actor:** User

**Goal:** Understand previous connectivity-sharing activity.

**Main flow:** User opens history → Linko displays permitted historical sessions and usage information → user views relevant details.

**Acceptance:** History is accurate, access-controlled, and handled according to the approved privacy/data-retention policy.

---

## UC-19 — Manage account security

**Primary actor:** User

**Goal:** Maintain control of account security.

**Examples:** Sign out, manage authentication factors where supported, review active sessions, recover account, revoke access.

**Acceptance:** Security actions require appropriate authentication and take effect reliably.

---

## UC-20 — Access premium/business capabilities

**Primary actor:** User

**Goal:** Access paid Linko capabilities when monetization is introduced.

**Examples:** Premium session controls, enhanced limits, advanced history, or other capabilities validated by the Business & Monetization phase.

**Important:** No specific pricing or premium entitlement is considered final in Phase 1.

**Acceptance:** Paid capabilities must never bypass core security, consent, privacy, carrier restrictions, or technical limitations.

---

## Core use-case relationship model

```text
USER
 ├── Create Account
 ├── Authenticate
 ├── Manage Trusted Contacts
 ├── Manage Account Security
 └── Receive Notifications

RECEIVER
 ├── Request Connectivity
 ├── Monitor Session
 ├── Disconnect Session
 └── View History/Usage

PROVIDER
 ├── Set Availability
 ├── Review Request
 ├── Approve/Reject Request
 ├── Control Session Limits
 ├── Monitor Session
 └── Stop Session

LINKO SYSTEM
 ├── Establish Authorized Connection
 ├── Handle Failure
 ├── Handle Unexpected Termination
 └── Expire Requests

SECURITY / ABUSE SYSTEM
 ├── Reject Unauthorized Access
 ├── Block/Report User
 └── Handle Abusive Sessions
```

## Core authorization rule

The fundamental authorization chain is:

**Trusted relationship → Connectivity request → Provider decision → Authorized establishment → Active session**

No shortcut may bypass Provider consent.

## Non-functional expectations discovered by these use cases

These are requirements to carry into later phases, not implementation decisions here:

- Security must be designed into every protected action.
- User state must be accurate and understandable.
- Provider control must be reliable.
- Failure must be handled safely.
- Privacy must govern identity, history, and usage information.
- The system must represent network limitations honestly.
- The architecture must support controlled scaling.

## Validation plan

Validate core use cases through:

- User-flow walkthroughs
- Prototype testing
- Provider/Receiver interviews
- Controlled connectivity tests
- Failure-mode testing
- Security scenario testing

## Acceptance criteria

- [x] Account and authentication use cases defined
- [x] Trusted-contact use case defined
- [x] Provider availability defined
- [x] Connectivity request defined
- [x] Provider decision defined
- [x] Authorized connection establishment defined
- [x] Active-session monitoring defined
- [x] Session limits defined at product level
- [x] Session disconnection defined
- [x] Connection failure defined
- [x] Unexpected failure defined
- [x] Request expiration defined
- [x] Block/report defined
- [x] Unauthorized access response defined
- [x] Abuse response defined
- [x] Notifications defined
- [x] History/usage defined
- [x] Account security defined
- [x] Premium/business capability boundary defined
- [x] Authorization chain defined
- [x] Validation plan defined

## Review gate

**Status:** READY FOR PROJECT-OWNER REVIEW AND APPROVAL

This deliverable does not mark Phase 1.6 complete until the project owner explicitly approves it.

## Next deliverable after approval

**Phase 1.7 — Value Proposition**

Phase 1 remains **IN PROGRESS**. Phase 2 remains locked.
