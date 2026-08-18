# Phase 1.8 — MVP Scope

## Status

**COMPLETE — PROJECT-OWNER APPROVED**

## MVP objective

Prove whether a trusted Provider can voluntarily share usable Internet connectivity with an authorized Receiver over distance through Linko, reliably and securely enough to create real user value.

## Core loop

```text
Create account
→ Establish trusted relationship
→ Provider available
→ Receiver requests
→ Provider approves
→ Authorized connection established
→ Receiver uses connectivity
→ Session monitored
→ Disconnect
→ Session closes safely
```

## MVP roles

### Receiver
Requests temporary Internet connectivity.

### Provider
Voluntarily shares an available connection with an authorized Receiver.

One account may perform either role.

## Must-have capabilities

- Account registration and authentication
- Stable user identity
- Trusted contacts
- Provider availability
- Connectivity requests
- Explicit Provider approval
- Authorized connection establishment
- Active-session state
- Session limits/control
- Disconnect/stop sharing
- Failure and timeout handling
- Basic notifications
- Basic session history
- Basic abuse reporting/blocking
- Security and privacy protections
- Operational monitoring

## Networking scope

The initial target is supported Android-to-Android connectivity sharing over the Internet. Linko must begin with controlled, tested network/device combinations rather than claiming universal compatibility.

Preferred path:

```text
Direct path
   ↓ if unavailable
Supported relay
   ↓ if unavailable
Safe failure
```

## Security baseline

- Authentication
- Authorization
- Explicit Provider consent
- Secure signaling
- Secure transport
- Protected credentials/tokens
- Rate limiting
- Abuse controls
- Safe termination

## Privacy baseline

Collect only data required for operation, security, support, and approved product functions. Access to account, relationship, session, and usage information must be authorization-controlled.

## MVP UI

```text
Welcome
├── Register / Login
└── Home
    ├── My Connectivity
    ├── Trusted Contacts
    ├── Requests
    ├── Active Session
    ├── History
    └── Settings
```

Provider request:

**[Person] wants connectivity**

**Approve | Reject**

Active session:

**CONNECTED | Duration | Usage | STOP SHARING**

## Explicitly out of MVP

- Complex social networking
- Public connectivity marketplace
- Anonymous bandwidth trading
- Cryptocurrency/token economy
- Large reward system
- AI assistant features
- Advanced recommendation engine
- Enterprise suite
- Desktop application
- iOS implementation
- Global relay optimization
- Complex referral system
- Advanced user analytics
- Large advertising system
- Full institutional platform
- Sophisticated premium tiers

## Non-goals

The MVP does not promise:

- Universal Android compatibility
- Universal carrier compatibility
- Guaranteed connection through restrictive networks
- Unlimited bandwidth
- Free Internet
- Carrier-policy bypass
- Network-restriction circumvention
- Zero-latency connectivity

## MVP acceptance gate

The MVP is functionally complete only when implementation and later testing demonstrate the full authorized flow, safe failure handling, security/privacy requirements, and at least one real supported remote-network configuration.

## Release sequence

```text
Requirements
→ Architecture
→ Networking prototype
→ Security
→ Android implementation
→ Automated tests
→ Device tests
→ Cross-network tests
→ Real-world pilot
→ MVP acceptance
```

## Approval record

- [x] MVP objective defined
- [x] Core loop defined
- [x] MVP roles defined
- [x] Must-have scope defined
- [x] Security baseline defined
- [x] Privacy baseline defined
- [x] Networking scope defined
- [x] UI scope defined
- [x] Out-of-MVP scope defined
- [x] Non-goals defined
- [x] Acceptance gate defined
- [x] Project owner approved

**Approved:** Project owner

**Next:** Phase 1.9 — Market & Competitor Research
