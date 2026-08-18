# Phase 1.5 — User Journeys

## Status

**REVIEW — READY FOR PROJECT-OWNER APPROVAL**

## Purpose

Define the end-to-end experience Linko users should have from the moment a connectivity need or sharing opportunity occurs through connection, monitoring, failure recovery, and termination.

These journeys describe product behavior at the discovery level. They do not authorize implementation decisions that belong to later SDLC phases.

---

## Journey 1 — Receiver requests connectivity

**Persona:** Kwame — Primary Receiver  
**Priority:** P0

### Trigger
Kwame has no or insufficient Internet access and needs to perform an important online activity.

### Journey

1. Kwame opens Linko.
2. Linko establishes his authenticated session.
3. Kwame views eligible trusted contacts.
4. Kwame selects a trusted Provider.
5. Kwame sends a connectivity request.
6. Linko confirms that the request was sent.
7. The Provider receives the request.
8. The Provider reviews the request and decides whether to approve it.
9. If approved, Linko establishes the authorized connection session.
10. Kwame sees the connection state.
11. Kwame uses Internet access through the Linko session.
12. Linko displays relevant session state and usage information.
13. Kwame disconnects when finished, or the session ends because a limit or Provider decision is reached.

### Success outcome
Kwame obtains temporary connectivity through an explicitly authorized session and understands whether the session is active.

### Failure outcomes
- No Provider available.
- Provider rejects request.
- Provider does not respond.
- Connection negotiation fails.
- Network conditions prevent usable connectivity.
- Session is terminated by the Provider or system.

### Recovery
The Receiver should receive a clear reason where safely available and be able to retry an appropriate action without creating duplicate or unauthorized sessions.

---

## Journey 2 — Provider approves and shares connectivity

**Persona:** Ama — Primary Provider  
**Priority:** P0

### Trigger
Ama receives a connectivity request from a trusted contact.

### Journey

1. Ama receives a request notification.
2. Ama opens the request.
3. Linko clearly identifies the requesting user.
4. Ama reviews the request and available session controls.
5. Ama approves or rejects the request.
6. If approved, Linko establishes the authorized session.
7. Ama sees that the session is active.
8. Ama can view appropriate usage/session information.
9. Ama can stop the session at any time.
10. The session ends when Ama stops it, the Receiver disconnects, a configured limit is reached, or the system must terminate it.

### Success outcome
Ama helps the Receiver while retaining clear control over the session.

### Provider control requirements
- Explicit approval
- Clear active-session state
- Session termination control
- Understandable usage information
- No silent or automatic permanent sharing

### Failure outcomes
- Request expires.
- Provider rejects it.
- Network conditions prevent connection.
- Provider device loses connectivity.
- System security controls terminate the session.

---

## Journey 3 — Trusted long-distance family assistance

**Persona:** Kojo — Secondary Provider/Receiver  
**Priority:** P1

### Scenario
A family member in another location needs temporary Internet access.

### Journey

1. Family member opens Linko and identifies Kojo as a trusted contact.
2. They request connectivity.
3. Kojo receives and verifies the request.
4. Kojo approves it if willing and able to help.
5. Linko attempts to establish the authorized session.
6. Both users see the session state.
7. The Receiver uses the connection.
8. Kojo monitors appropriate session information.
9. Either user can end the session according to the product rules.

### Success outcome
A trusted relationship enables temporary connectivity assistance despite physical distance, provided the underlying networks and devices support the connection.

### Important limitation
Distance is not treated as a magic bypass. Connectivity remains subject to Internet routing, latency, bandwidth, carrier policies, NAT/firewall behavior, device restrictions, and Linko infrastructure availability.

---

## Journey 4 — Traveler requests temporary connectivity

**Persona:** Yaw — Secondary Receiver  
**Priority:** P1

### Trigger
Yaw is traveling and temporarily lacks reliable connectivity.

### Journey

1. Yaw opens Linko.
2. He selects a trusted Provider.
3. He requests temporary connectivity.
4. The Provider verifies the request.
5. The Provider approves or rejects it.
6. Linko attempts to establish the session.
7. If successful, Yaw uses the connection.
8. Linko communicates session state and relevant limitations.
9. Yaw disconnects when connectivity is no longer required.

### Success outcome
Yaw gets useful temporary connectivity through an authorized trusted relationship when the network path supports it.

---

## Journey 5 — Provider rejects a request

**Actor:** Provider

### Trigger
A Provider receives a request but does not want to share connectivity.

### Journey

1. Provider receives request.
2. Provider reviews requester identity.
3. Provider selects **Reject**.
4. Linko records the decision as required by the privacy/data-retention policy.
5. Receiver is informed that the request was not approved, without exposing unnecessary private information.

### Success outcome
No connection is established and no Provider resources are exposed.

---

## Journey 6 — Provider does not respond

### Trigger
A request remains unanswered.

### Journey

1. Receiver sends request.
2. Provider receives or is notified of the request.
3. Provider does not respond within the defined request lifetime.
4. Linko expires the request.
5. Receiver sees that the request expired.
6. Receiver may choose another eligible trusted contact.

### Security requirement
An expired request must not automatically become an approved connection.

---

## Journey 7 — Connection establishment fails

### Trigger
The Provider approves a request but the networking session cannot be established.

### Possible causes
- NAT/firewall incompatibility
- Carrier restrictions
- Device/OS limitations
- Provider network loss
- Receiver network loss
- Signaling failure
- Relay unavailable
- Temporary Internet instability

### Journey

1. Provider approves.
2. Linko begins connection establishment.
3. Connection fails or times out.
4. Linko ends incomplete connection state safely.
5. Both users receive an understandable status.
6. Linko must not report the session as active when usable connectivity was not established.
7. Users may retry where appropriate.

### Success outcome
Failure is handled safely and transparently without creating a false active session or unauthorized access.

---

## Journey 8 — Active session is terminated by Provider

### Trigger
Ama decides to stop sharing.

### Journey

1. Ama opens the active session.
2. Ama selects **Disconnect/Stop Sharing**.
3. Linko confirms or immediately executes according to the safety-critical interaction design.
4. The session is terminated.
5. Receiver loses Linko-provided connectivity.
6. Both sides receive updated session state.
7. Temporary session resources are released.

### Success outcome
Provider control is immediate and reliable.

---

## Journey 9 — Receiver disconnects normally

### Trigger
Kwame no longer needs the connection.

### Journey

1. Kwame selects **Disconnect**.
2. Linko terminates the session.
3. Provider is informed of the updated state.
4. Temporary resources are released.
5. Usage/session information is finalized according to the data policy.

### Success outcome
The session ends cleanly without requiring Provider intervention.

---

## Journey 10 — Unexpected session termination

### Trigger
The Provider device, Receiver device, network, relay, or Linko service becomes unavailable.

### Journey

1. Linko detects loss of session health or connectivity.
2. Session transitions to an appropriate disconnected/failed state.
3. Both users receive updated state when communication remains possible.
4. Linko releases session resources where possible.
5. Users may retry after the underlying issue is resolved.

### Requirement
The system must fail closed: unexpected communication loss must not result in unintended persistent authorization.

---

## Journey 11 — Unauthorized access attempt

**Persona:** Unwanted User — Negative Persona

### Trigger
An unauthorized person attempts to access a Provider's connection or session.

### Journey

1. Linko receives an invalid or unauthorized request.
2. Authentication/authorization controls reject it.
3. No connectivity session is established.
4. Appropriate abuse/security controls are triggered where required.
5. The legitimate Provider remains protected.

### Success outcome
Unauthorized access does not reach the Provider's shared connection.

---

## Journey 12 — Suspicious or abusive active session

### Trigger
A connected Receiver violates a Linko policy, security control, or abuse threshold.

### Journey

1. Linko detects a policy/security signal.
2. The system evaluates the event according to the later security and abuse-prevention specifications.
3. If termination is required, the session is stopped.
4. The Provider is informed where appropriate.
5. The event is handled according to the security/privacy policy.
6. Repeated abuse may lead to account restrictions.

### Important boundary
The exact detection algorithms, enforcement mechanisms, and data retention belong to later Security SDLC, Abuse Prevention, and Privacy phases. This journey only defines the required product behavior.

---

## Cross-journey state model

The high-level Linko session should move through controlled states:

```text
NO_SESSION
   ↓
REQUESTED
   ↓
PENDING_PROVIDER_DECISION
   ├── REJECTED
   └── APPROVED
          ↓
      CONNECTING
          ├── FAILED
          └── ACTIVE
                 ├── RECEIVER_DISCONNECTED
                 ├── PROVIDER_STOPPED
                 ├── LIMIT_REACHED
                 ├── NETWORK_LOST
                 └── SECURITY_TERMINATED
                          ↓
                     TERMINATED
```

No path should allow a user to move from **REQUESTED** directly to **ACTIVE** without the required authorization.

## Journey-wide product requirements

1. Provider consent must be explicit.
2. Receiver requests must have a controlled lifetime.
3. Identity and authorization must be clear.
4. Active session state must be visible.
5. Provider must be able to terminate sharing.
6. Receiver must be able to disconnect.
7. Failed connections must not appear active.
8. Unexpected failures must fail closed.
9. Unauthorized users must not gain connectivity.
10. Users must receive understandable status messages.
11. The system must accurately represent real network limitations.
12. Session resources must be released after termination.
13. Privacy and data retention must follow later approved policies.
14. The journey must work for the P0 Provider/Receiver relationship before expanding to secondary use cases.

## Journey validation plan

Validate these journeys using:

- Low-fidelity user-flow review
- Clickable prototype testing
- Provider interviews
- Receiver interviews
- Controlled technical prototype testing
- Failure-mode testing
- Usability testing

Measure:

- Request completion rate
- Approval comprehension
- Time to establish a session
- User understanding of connection state
- Successful disconnection rate
- Failure recovery rate
- Provider confidence/control
- Receiver confidence/clarity

## Acceptance criteria

- [x] Primary Receiver journey defined
- [x] Primary Provider journey defined
- [x] Long-distance trusted-contact journey defined
- [x] Traveler journey defined
- [x] Request rejection journey defined
- [x] Request expiration journey defined
- [x] Connection failure journey defined
- [x] Provider termination journey defined
- [x] Receiver termination journey defined
- [x] Unexpected termination journey defined
- [x] Unauthorized-access journey defined
- [x] Abuse/security termination journey defined
- [x] High-level session state model defined
- [x] Cross-journey requirements defined
- [x] Validation plan defined

## Review gate

**Status:** READY FOR PROJECT-OWNER REVIEW AND APPROVAL

This deliverable does not mark Phase 1.5 complete until the project owner explicitly approves it.

## Next deliverable after approval

**Phase 1.6 — Core Use Cases**

Phase 1 remains **IN PROGRESS**. Phase 2 remains locked.
