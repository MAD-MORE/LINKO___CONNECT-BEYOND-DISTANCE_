# LINKO — AI HANDOVER & PROJECT CONSTITUTION

**Project:** Linko — Connect Beyond Distance  
**Repository:** `MAD-MORE/LINKO___CONNECT-BEYOND-DISTANCE_`  
**Document purpose:** Persistent handover, context, and change-control contract for every AI, developer, agent, or contributor working on Linko.

---

## 1. READ THIS FIRST

Any AI or developer taking over Linko MUST read this document before modifying code, architecture, requirements, security controls, product scope, or the SDLC.

The repository is the source of truth. Conversation history is NOT the source of truth.

The objective of this document is continuity: a new AI should be able to understand what Linko is, why it exists, how it is supposed to work, and what must not be changed without an explicit decision.

---

## 2. PROJECT IDENTITY

### Name
**Linko**

### Tagline
**Connect Beyond Distance**

### Core idea
Linko is an Android connectivity-sharing platform that allows a person with an available Internet connection (**Provider**) to voluntarily and explicitly share that connection with an authorized person (**Receiver**) over an Internet-based secure tunnel, even when the two devices are physically far apart.

Linko does NOT create Internet from nothing and does NOT remove carrier, bandwidth, latency, battery, NAT, firewall, operating-system, or ISP limitations.

---

## 3. CORE MODEL

```text
Provider Android device
        |
        | Provider's existing Internet
        v
  Secure Linko tunnel
        |
        +--> Direct path when feasible
        |
        +--> Linko relay when required
        |
        v
Receiver Android device
        |
        v
Receiver's authorized Internet traffic
```

The Provider must explicitly authorize each connection session. Friendship alone is NOT sufficient permission.

---

## 4. NON-NEGOTIABLE PRODUCT PRINCIPLES

These principles MUST NOT be silently removed or weakened:

1. **Explicit consent** — Provider approval is required before a session begins.
2. **Provider control** — Provider can stop sharing immediately.
3. **Security by design** — Traffic and session credentials must be protected.
4. **Privacy by design** — Collect and retain the minimum data required.
5. **No fabricated Internet** — Linko uses an existing Provider connection.
6. **Transparent usage** — Users should understand session/data consumption.
7. **Reliable networking** — Direct connectivity is preferred; relay is fallback.
8. **Abuse resistance** — Rate limits, blocking, monitoring, and emergency termination are required.
9. **Sustainable economics** — Infrastructure costs must be considered before scaling.
10. **Android-first MVP** — The initial product is designed around Android networking capabilities.

---

## 5. USER ROLES

### Provider
A user who voluntarily shares an available mobile-data or Wi-Fi connection.

Provider capabilities include:

- Approve/reject connection requests
- Set sharing limits
- See active session
- See usage
- Disconnect Receiver
- Revoke device/session access

### Receiver
A user who requests access to a Provider's connection.

Receiver capabilities include:

- Select trusted Provider
- Request connection
- Connect after approval
- Monitor session state and usage
- Disconnect

---

## 6. MVP DEFINITION

The first technical milestone is NOT the global marketplace or payment system.

The MVP must prove:

> Two authorized Android devices can establish a secure Internet connection over distance, with the Receiver's Internet traffic routed through the Provider's available connection, while the Provider can approve, limit, monitor, and immediately terminate the session.

### MVP features

- Account authentication
- Device registration
- Friend/trust relationship
- Connection request
- Provider approval
- Android VPN integration
- Secure tunnel
- Provider-side traffic forwarding
- Receiver-side traffic routing
- Basic relay fallback
- Connection status
- Data usage counters
- Disconnect/revoke

### Explicitly deferred

Do not add these to the MVP unless the SDLC is formally updated:

- Large social network features
- AI features
- Complex rewards economy
- Global relay fleet
- Enterprise features
- Large-scale marketplace
- Unnecessary analytics

---

## 7. SYSTEM COMPONENTS

```text
android/     -> Android application
backend/     -> authentication, users, friends, sessions, signaling, usage, billing, admin
relay/       -> secure relay/tunneling infrastructure
shared/      -> common models/protocol definitions
docs/        -> technical and product documentation
infra/       -> deployment/infrastructure configuration
tests/       -> automated/integration testing
SDLC/        -> complete product-to-production lifecycle
```

---

## 8. SDLC IS THE DEVELOPMENT CONTRACT

The canonical development sequence is:

1. Product Discovery
2. Requirements Engineering
3. Technical Architecture
4. Android Architecture
5. Linko Tunnel Engine
6. Signaling
7. Relay Infrastructure
8. Backend
9. Database Design
10. Security SDLC
11. Abuse Prevention
12. Privacy
13. UI/UX Development
14. MVP Development
15. Testing
16. Real-World Testing
17. Performance Engineering
18. Business & Monetization
19. Linko Economy
20. Legal & Compliance
21. Google Play Launch
22. Monetization Implementation
23. Observability
24. Beta Program
25. Scale & Global Expansion

Detailed phase documents are stored in `SDLC/01-*` through `SDLC/25-*`.

Do not skip phases casually.

---

## 9. CHANGE CONTROL — PREVENTING UNAUTHORIZED MODIFICATIONS

No AI or contributor may silently redefine Linko.

Before changing any of the following, the contributor MUST create or update a documented proposal:

- Product vision
- Core user roles
- MVP definition
- Network architecture
- Security model
- Privacy model
- Monetization model
- SDLC phase order
- Core repository structure
- Provider/Receiver model

### Required process for major changes

```text
Proposed change
      |
      v
Explain reason
      |
      v
Assess affected requirements
      |
      v
Assess security/privacy impact
      |
      v
Assess cost/performance impact
      |
      v
Update documentation
      |
      v
Obtain explicit project-owner approval
      |
      v
Implement
      |
      v
Test
      |
      v
Record decision
```

An AI MUST NOT interpret an ambiguous request as permission to redesign the product.

When uncertain, preserve the existing architecture and ask for a decision rather than replacing it.

---

## 10. AI HANDOVER RULES

Every new AI should follow this order:

1. Read `SDLC/AI-HANDOVER.md`.
2. Read `SDLC/README.md`.
3. Read `SDLC/PROGRESS.md`.
4. Identify the single step marked CURRENT.
5. Read the current step's deliverable.
6. Inspect the repository before proposing changes.
7. Preserve established decisions.
8. Make the smallest change necessary to satisfy the current phase.
9. Run appropriate tests.
10. Update documentation when a decision changes.
11. Update `SDLC/PROGRESS.md` after meaningful progress.
12. Leave a clear handover.

### Never do this

- Do not restart Linko from scratch because the project looks unfamiliar.
- Do not replace the architecture merely because another framework is preferred.
- Do not remove security controls to make development easier.
- Do not remove Provider consent.
- Do not silently change the business model.
- Do not claim a networking feature works without real testing.
- Do not introduce secrets into the repository.
- Do not treat a temporary prototype shortcut as production architecture.
- Do not start a locked SDLC step.

---

## 11. DECISION LOG

Important architecture/product decisions should be recorded here or in a dedicated decision record under `SDLC/decisions/`.

### Current decisions

| ID | Decision | Status |
|---|---|---|
| LINKO-001 | Product name is Linko | Accepted |
| LINKO-002 | Tagline is Connect Beyond Distance | Accepted |
| LINKO-003 | Android-first development | Accepted |
| LINKO-004 | Provider/Receiver model | Accepted |
| LINKO-005 | Provider must explicitly approve a session | Accepted |
| LINKO-006 | Secure Internet tunnel is the core technical mechanism | Accepted |
| LINKO-007 | Direct path preferred; relay is fallback | Accepted |
| LINKO-008 | SDLC phases are the master development roadmap | Accepted |
| LINKO-009 | Security and privacy are first-class requirements | Accepted |
| LINKO-010 | Monetization must not compromise the core product or user trust | Accepted |
| LINKO-011 | Supabase is the official and permanent LINKO backend control plane | Accepted |

---

## 12. CURRENT PROJECT STATUS

**Lifecycle state:** SDLC Phase 1 — Product Discovery

**Current step:** **1.10 — Technical Feasibility — REVIEW / READY FOR PROJECT-OWNER APPROVAL**

**Completed through:** 1.9 — Market & Competitor Research — APPROVED

**Next step after approval:** 1.11 — Business Model Hypotheses

**Do not jump directly to production implementation without completing the relevant requirements and architecture decisions.**

---

## 13. DEFINITION OF DONE

A Linko feature is not complete merely because code compiles.

A feature is done when applicable items below are satisfied:

- Requirements documented
- Architecture consistent with the SDLC
- Security implications assessed
- Privacy implications assessed
- Implementation complete
- Unit tests added
- Integration tests added where applicable
- Real-device/network testing performed where applicable
- Failure cases handled
- Observability added where needed
- Documentation updated
- No secrets committed
- Product behavior matches the agreed specification

---

## 14. HANDOVER TEMPLATE

Every AI/developer handing work to another should leave:

```text
CURRENT PHASE:

WHAT WAS COMPLETED:

FILES CHANGED:

ARCHITECTURE DECISIONS:

TESTS RUN:

KNOWN ISSUES:

NEXT TASK:

DO NOT CHANGE:

PENDING DECISIONS:
```

---

## 15. FINAL RULE

**Linko's identity is more important than any individual implementation.**

Technology may evolve when justified. Frameworks may change when justified. Infrastructure may scale when justified.

But the product's documented requirements, security principles, consent model, and SDLC must not be silently rewritten by a new AI or contributor.

When a better idea is discovered, document it, evaluate it, obtain approval, and update the source of truth deliberately.
