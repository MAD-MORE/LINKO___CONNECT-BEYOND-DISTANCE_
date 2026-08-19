# Phase 2.1 — Requirements Engineering Charter

## Status

**CURRENT — READY FOR PROJECT-OWNER REVIEW**

## Purpose

Define the rules, scope, ownership, quality standards, and working method for Phase 2 — Requirements Engineering.

This charter governs how Linko's approved Product Discovery baseline is converted into precise, testable, traceable requirements.

---

# 1. Phase 2 Objective

Phase 2 will transform the approved Phase 1 product baseline into a complete Software Requirements Specification (SRS)-level requirements system that engineering, design, security, testing, operations, business, and future AI collaborators can use without guessing.

The output must answer:

- What must Linko do?
- Who is allowed to do it?
- Under what conditions?
- What data is required?
- What happens when things fail?
- What security and privacy controls are mandatory?
- What performance and reliability levels are required?
- How will every requirement be verified?
- What is MVP versus later scope?

---

# 2. Source of Truth

The authoritative hierarchy is:

1. Project-owner-approved decisions.
2. `SDLC/PROGRESS.md` for sequence and current position.
3. `SDLC/AI-HANDOVER.md` for continuation rules.
4. Approved Phase 1 Product Discovery artifacts.
5. Approved Phase 1 Requirements Summary.
6. Approved Phase 2 requirements artifacts.

No AI or collaborator may silently override an approved higher-level decision.

---

# 3. Phase 2 Scope

Phase 2 covers:

- Actors and roles
- Functional requirements
- Non-functional requirements
- Networking/connectivity requirements
- Android requirements
- Security requirements
- Privacy requirements
- Data requirements
- Backend/infrastructure requirements
- Reliability/availability
- Performance/resource requirements
- Abuse prevention
- Business/monetization requirements
- Compliance/store requirements
- Requirements traceability
- Verification and acceptance criteria
- Requirements baseline
- Formal review
- Final approval

Architecture and implementation remain controlled by later phases unless a requirement requires feasibility clarification.

---

# 4. Requirement Quality Standard

Every substantive requirement should be:

- **Necessary** — supports an approved product objective.
- **Unambiguous** — has one reasonable interpretation.
- **Atomic** — describes one requirement wherever practical.
- **Testable** — can be objectively verified.
- **Traceable** — linked to its source and downstream validation.
- **Feasible** — technically and operationally plausible, or explicitly marked for validation.
- **Prioritized** — MVP, post-MVP, or deferred.
- **Owned** — has an accountable product/technical owner where appropriate.
- **Versioned** — changes are recorded.
- **Secure/privacy-aware** — security and privacy implications are considered.

---

# 5. Requirement Identifier Standard

Requirements will use stable identifiers.

Examples:

- `FR-001` — Functional Requirement
- `NFR-001` — Non-Functional Requirement
- `NET-001` — Networking Requirement
- `AND-001` — Android Requirement
- `SEC-001` — Security Requirement
- `PRIV-001` — Privacy Requirement
- `DATA-001` — Data Requirement
- `INFRA-001` — Backend/Infrastructure Requirement
- `REL-001` — Reliability Requirement
- `PERF-001` — Performance Requirement
- `ABUSE-001` — Abuse Prevention Requirement
- `BUS-001` — Business Requirement
- `COM-001` — Compliance Requirement

Identifiers must not be casually reused after deletion. If a requirement is retired, its history must remain traceable.

---

# 6. Requirement Priority

Each requirement must have a priority:

### P0 — Critical
Required for safe/basic operation. Cannot be omitted from MVP.

### P1 — Core
Required for a credible MVP and primary user value.

### P2 — Important
Valuable but can be deferred without breaking the core product.

### P3 — Future
Post-MVP or experimental capability.

Priority changes require documented justification.

---

# 7. Requirement Lifecycle

```text
Proposed
   ↓
Drafted
   ↓
Reviewed
   ↓
Accepted
   ↓
Baselined
   ↓
Implemented
   ↓
Verified
   ↓
Validated
```

A requirement may also become:

- Rejected
- Deferred
- Superseded
- Retired

Those states must retain traceability.

---

# 8. Acceptance & Verification Rule

No requirement is considered complete merely because someone implemented it.

For every testable requirement, Phase 2 must define appropriate acceptance evidence such as:

- Automated test
- Integration test
- Instrumented Android test
- Manual test
- Security test
- Performance measurement
- Operational verification
- Legal/compliance review
- Product acceptance review

The verification method must be appropriate to the requirement.

---

# 9. Traceability Rule

Requirements must maintain forward and backward traceability:

```text
Business/Product Need
        ↓
Phase 1 Decision
        ↓
Requirement ID
        ↓
Design/Architecture
        ↓
Implementation
        ↓
Test Case
        ↓
Evidence
        ↓
Release
```

If a requirement has no source or no eventual verification path, it must be flagged for review.

---

# 10. Change Control

Requirements may change when evidence demonstrates that the current baseline is incorrect, incomplete, unsafe, infeasible, legally problematic, or commercially invalid.

Every material change must record:

- Requirement ID
- Old requirement
- Proposed requirement
- Reason
- Evidence
- Impact
- Dependencies
- Security/privacy impact
- Product/business impact
- Approval status

No silent modifications.

---

# 11. Conflict Resolution

If requirements conflict:

1. Protect user safety and security.
2. Protect explicit consent and authorization.
3. Protect privacy and legal obligations.
4. Protect the approved core product value.
5. Evaluate technical feasibility.
6. Evaluate business impact.
7. Record the conflict and decision.
8. Obtain project-owner approval for material baseline changes.

---

# 12. MVP Rule

Requirements must clearly distinguish:

- **MVP mandatory**
- **MVP conditional/validation-dependent**
- **Post-MVP**
- **Deferred**
- **Rejected**

A requirement must not enter MVP merely because it is technically interesting.

---

# 13. AI & Collaborator Rule

Every AI or collaborator working on requirements must:

1. Read `SDLC/PROGRESS.md`.
2. Read `SDLC/AI-HANDOVER.md`.
3. Read the approved Phase 1 baseline.
4. Work only on the current Phase 2 step.
5. Preserve approved decisions.
6. Use stable requirement IDs.
7. Place artifacts in the correct Phase 2 category folder.
8. Avoid duplicate requirements.
9. Record assumptions and uncertainties.
10. Never mark a later step complete.
11. Update the tracker when the step's deliverable is ready.
12. Leave a clear handover.

---

# 14. Folder Organization

Phase 2 uses dedicated categories:

```text
SDLC/
└── 02-requirements-engineering/
    ├── 01-charter/
    ├── 02-actors-roles/
    ├── 03-functional/
    ├── 04-non-functional/
    ├── 05-networking/
    ├── 06-android/
    ├── 07-security/
    ├── 08-privacy/
    ├── 09-data/
    ├── 10-backend-infrastructure/
    ├── 11-reliability/
    ├── 12-performance/
    ├── 13-abuse-prevention/
    ├── 14-business/
    ├── 15-compliance/
    ├── 16-traceability/
    ├── 17-verification-acceptance/
    ├── 18-baseline/
    ├── 19-review/
    └── 20-final-approval/
```

No Phase 2 requirement document should be placed loosely in the Phase 2 root when an appropriate category folder exists.

---

# 15. Phase 2 Deliverable Standard

By the end of Phase 2, the repository must contain a coherent requirements baseline that can be handed to:

- Product team
- UX/UI team
- Android engineers
- Backend engineers
- Networking engineers
- Security engineers
- QA/test engineers
- Operations
- Business/legal reviewers
- Future AI collaborators

A new collaborator should be able to understand exactly what Linko is required to do without relying on private conversation history.

---

# 16. Definition of Done for Phase 2.1

Phase 2.1 is complete only when:

- [x] Phase objective defined
- [x] Scope defined
- [x] Source-of-truth hierarchy defined
- [x] Requirement quality standard defined
- [x] Identifier standard defined
- [x] Priority model defined
- [x] Requirement lifecycle defined
- [x] Verification rule defined
- [x] Traceability rule defined
- [x] Change-control rule defined
- [x] Conflict-resolution rule defined
- [x] MVP classification rule defined
- [x] AI/collaborator rules defined
- [x] Folder structure defined
- [x] Phase deliverable standard defined

---

# Review Gate

**Status: READY FOR PROJECT-OWNER REVIEW AND APPROVAL**

This document does not mark Phase 2.1 complete until the project owner explicitly approves it.

## Next step

**2.2 — System Actors & Roles**
