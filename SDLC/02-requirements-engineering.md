# Phase 2 — Requirements Engineering

## Objective
Translate the product vision into testable functional, non-functional, security, operational, and business requirements.

## Functional requirements

### Accounts
- Registration and login
- Email/phone verification as appropriate
- Profile management
- Device registration
- Account deletion
- Session history

### Friends
- Search users
- Send friend request
- Accept/reject request
- Remove friend
- Block user
- Trust relationship status

### Connection requests
- Receiver selects Provider
- Receiver requests connection
- Provider sees requester identity
- Provider approves/rejects
- Session credentials are negotiated only after authorization

### Provider controls
- Start/stop sharing
- Data limit
- Optional duration limit
- Usage visibility
- Current Receiver visibility
- Immediate session termination

### Receiver controls
- Connect/disconnect
- Connection state
- Data usage
- Session duration
- Basic network quality indicators

### Networking
- Secure tunnel establishment
- Direct path when feasible
- Relay fallback when necessary
- Reconnection handling
- Network switching handling
- Session expiration

## Non-functional requirements

- Secure by default
- Low tunnel overhead
- Reasonable battery consumption
- Responsive UI
- Graceful network failure handling
- Horizontal scalability
- Observable services
- Auditable security events

## Security requirements

- Strong device identity
- Per-session authorization
- Encrypted tunnel traffic
- Short-lived session credentials
- Key rotation/revocation strategy
- Server-side authorization checks
- Provider kill switch
- Abuse detection and rate limits

## Privacy requirements

- Minimize personal data
- Do not store browsing contents unnecessarily
- Clear privacy disclosures
- Data retention limits
- Account/data deletion workflows

## Business requirements

- Free tier for adoption
- Paid plans must cover infrastructure costs
- Usage economics must be measurable
- Relay-heavy usage must be controlled
- Subscription and/or premium connectivity features must be transparent

## Acceptance-test examples

**FR-CONNECT-001:** A Receiver cannot establish a session without Provider approval.

**FR-CONNECT-002:** Provider termination ends the Receiver's Linko session.

**FR-CONNECT-003:** Every active session has a unique session identifier and usage counters.

**SEC-CONNECT-001:** Tunnel traffic is encrypted according to the selected production protocol.

**PRIVACY-001:** Linko does not retain browsing contents as a default product requirement.

## Exit criteria

All MVP requirements have unique identifiers, acceptance criteria, priorities, and owners.
