# LINKO SDLC — MASTER PROGRESS TRACKER

**Project:** Linko — Connect Beyond Distance

## STRICT SEQUENTIAL EXECUTION RULE

Only the current step may be actively executed. A later step MUST NOT be started until the current step is completed, reviewed, and explicitly approved by the project owner.

This rule applies to every AI, developer, collaborator, and team member.

The repository is the source of truth. Before Linko work, read `SDLC/AI-HANDOVER.md` and this tracker.

## Folder organization rule

Nothing should be scattered.

- Every SDLC phase has its own folder.
- Categories within a phase receive their own subfolders where appropriate.
- New categories must create a new folder/subfolder.
- Implementation code stays separate from SDLC documentation.

## Current position

**Current Phase:** Phase 2 — Requirements Engineering

**Current Step:** 2.5 — Connectivity & Networking Requirements

**Status:** CURRENT / READY TO START

**Latest completed:** 2.4 Non-Functional Requirements — PROJECT OWNER APPROVED

**Next exact task:** Create Phase 2.5 Connectivity & Networking Requirements.

## Phase 1 — Product Discovery

- [x] 1.1 — Problem Definition — COMPLETE — APPROVED
- [x] 1.2 — Product Vision — COMPLETE — APPROVED
- [x] 1.3 — Target Users — COMPLETE — APPROVED
- [x] 1.4 — User Personas — COMPLETE — APPROVED
- [x] 1.5 — User Journeys — COMPLETE — APPROVED
- [x] 1.6 — Core Use Cases — COMPLETE — APPROVED
- [x] 1.7 — Value Proposition — COMPLETE — APPROVED
- [x] 1.8 — MVP Scope — COMPLETE — APPROVED
- [x] 1.9 — Market & Competitor Research — COMPLETE — APPROVED
- [x] 1.10 — Technical Feasibility — COMPLETE — APPROVED
- [x] 1.11 — Business Model Hypotheses — COMPLETE — APPROVED
- [x] 1.12 — Risk Register — COMPLETE — APPROVED
- [x] 1.13 — Success Metrics — COMPLETE — APPROVED
- [x] 1.14 — Phase 1 Requirements Summary — COMPLETE — APPROVED
- [x] 1.15 — Phase 1 Review — COMPLETE — APPROVED
- [x] 1.16 — Phase 1 Final Approval — COMPLETE — APPROVED

**PHASE 1 STATUS: COMPLETE / LOCKED**

## Phase 2 — Requirements Engineering

- [x] 2.1 — Requirements Engineering Charter — COMPLETE — APPROVED
- [x] 2.2 — System Actors & Roles — COMPLETE — APPROVED
- [x] 2.3 — Functional Requirements — COMPLETE — APPROVED
- [x] 2.4 — Non-Functional Requirements — COMPLETE — APPROVED
- [ ] **2.5 — Connectivity & Networking Requirements — CURRENT**
- [ ] 2.6 — Android Platform Requirements
- [ ] 2.7 — Security Requirements
- [ ] 2.8 — Privacy Requirements
- [ ] 2.9 — Data Requirements
- [ ] 2.10 — Backend & Infrastructure Requirements
- [ ] 2.11 — Reliability & Availability Requirements
- [ ] 2.12 — Performance & Resource Requirements
- [ ] 2.13 — Abuse Prevention Requirements
- [ ] 2.14 — Business & Monetization Requirements
- [ ] 2.15 — Compliance & Store Requirements
- [ ] 2.16 — Requirements Traceability Matrix
- [ ] 2.17 — Requirements Verification & Acceptance Criteria
- [ ] 2.18 — Phase 2 Requirements Baseline
- [ ] 2.19 — Phase 2 Review
- [ ] 2.20 — Phase 2 Final Approval

## Overall SDLC checklist

- [x] Phase 1 — Product Discovery — COMPLETE / LOCKED
- [ ] Phase 2 — Requirements Engineering — CURRENT / IN PROGRESS
- [ ] Phase 3 — Technical Architecture — LOCKED
- [ ] Phase 4 — Android Architecture — LOCKED
- [ ] Phase 5 — Linko Tunnel Engine — LOCKED
- [ ] Phase 6 — Signaling — LOCKED
- [ ] Phase 7 — Relay Infrastructure — LOCKED
- [ ] Phase 8 — Backend — LOCKED
- [ ] Phase 9 — Database Design — LOCKED
- [ ] Phase 10 — Security SDLC — LOCKED
- [ ] Phase 11 — Abuse Prevention — LOCKED
- [ ] Phase 12 — Privacy — LOCKED
- [ ] Phase 13 — UI/UX Development — LOCKED
- [ ] Phase 14 — MVP Development — LOCKED
- [ ] Phase 15 — Testing — LOCKED
- [ ] Phase 16 — Real-World Testing — LOCKED
- [ ] Phase 17 — Performance Engineering — LOCKED
- [ ] Phase 18 — Business & Monetization — LOCKED
- [ ] Phase 19 — Linko Economy — LOCKED
- [ ] Phase 20 — Legal & Compliance — LOCKED
- [ ] Phase 21 — Google Play Launch — LOCKED
- [ ] Phase 22 — Monetization Implementation — LOCKED
- [ ] Phase 23 — Observability — LOCKED
- [ ] Phase 24 — Beta Program — LOCKED
- [ ] Phase 25 — Scale & Global Expansion — LOCKED

## Completion gate

A step becomes `[x] COMPLETE` only when:

- Required deliverable exists.
- Objectives are satisfied.
- Acceptance criteria are satisfied.
- Required research/design/testing is complete.
- Documentation is updated.
- Dependencies/blockers are resolved or explicitly accepted.
- Project owner reviews the actual deliverable.
- Project owner explicitly approves completion.

Only then is the next step unlocked.

## AI/team continuation protocol

Every AI/collaborator MUST:

1. Read `SDLC/AI-HANDOVER.md`.
2. Read `SDLC/PROGRESS.md`.
3. Identify the single step marked CURRENT.
4. Work only on that current step.
5. Do not start or mark later steps complete.
6. Do not skip unfinished work.
7. Do not rewrite approved decisions without explicit project-owner authorization.
8. Keep files in the correct phase/category folder.
9. Update the tracker after meaningful progress.
10. Leave a clear handover.

## No phase jumping

A later-phase dependency must be recorded as a dependency/blocker in the current step. It does not authorize silently starting that later phase.

## Handover template

```text
Current phase:
Current step:
Status:
Completed:
Incomplete:
Blockers:
Decisions:
Files changed:
Tests/reviews:
Next exact task:
```

## Current handover

```text
Current phase: Phase 2 — Requirements Engineering
Current step: 2.5 — Connectivity & Networking Requirements
Status: READY TO START
Completed: 2.1–2.4
Incomplete: 2.5–2.20
Blockers: None currently recorded
Decisions: 2.4 Non-Functional Requirements approved; strict sequential execution remains active
Files changed: SDLC/PROGRESS.md
Tests/reviews: 2.4 reviewed and approved by project owner
Next exact task: Create Phase 2.5 Connectivity & Networking Requirements
```

## Authoritative rule

**The current checkbox and status in this file determine where Linko has reached.** A future AI or collaborator must verify this tracker against the repository and continue from the recorded current step. It must not restart, skip, or unlock later work without explicit project-owner authorization.
