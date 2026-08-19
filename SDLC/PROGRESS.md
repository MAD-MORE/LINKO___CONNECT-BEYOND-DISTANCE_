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

**Current Phase:** Phase 1 — Product Discovery

**Current Step:** 1.14 — Phase 1 Requirements Summary

**Status:** CURRENT / READY TO START

**Latest completed:** 1.13 Success Metrics — PROJECT OWNER APPROVED

**Next exact task:** Create Phase 1.14 Phase 1 Requirements Summary.

## Phase 1 — Product Discovery

- [x] 1.1 — Problem Definition — COMPLETE
- [x] 1.2 — Product Vision — COMPLETE
- [x] 1.3 — Target Users — COMPLETE
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
- [ ] **1.14 — Phase 1 Requirements Summary — CURRENT**
- [ ] 1.15 — Phase 1 Review — LOCKED
- [ ] 1.16 — Phase 1 Approval — LOCKED

## Overall SDLC checklist

- [ ] Phase 1 — Product Discovery — CURRENT / IN PROGRESS
- [ ] Phase 2 — Requirements Engineering — LOCKED
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
Current phase: Phase 1 — Product Discovery
Current step: 1.14 — Phase 1 Requirements Summary
Status: READY TO START
Completed: 1.1–1.13
Incomplete: 1.14–1.16
Blockers: None currently recorded
Decisions: Strict sequential execution active; 1.13 Success Metrics approved by project owner
Files changed: SDLC/PROGRESS.md
Tests/reviews: 1.13 reviewed and approved
Next exact task: Create Phase 1.14 Phase 1 Requirements Summary
```

## Authoritative rule

**The current checkbox and status in this file determine where Linko has reached.** A future AI or collaborator must verify this tracker against the repository and continue from the recorded current step. It must not restart, skip, or unlock later work without explicit project-owner authorization.
