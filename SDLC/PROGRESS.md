# LINKO SDLC — MASTER PROGRESS TRACKER

**Project:** Linko — Connect Beyond Distance

## STRICT SEQUENTIAL EXECUTION RULE — RESTORED

Linko development is strictly sequential.

> **Only the current step may be actively executed. A later step MUST NOT be started until the current step is completed, reviewed, and explicitly approved by the project owner.**

This rule applies to every AI, developer, collaborator, and team member.

The repository is the source of truth. Before doing any Linko work, read `SDLC/AI-HANDOVER.md` and this tracker.

## Folder organization rule

Nothing should be scattered.

- Every SDLC phase has its own folder.
- Every category within a phase gets its own subfolder when appropriate.
- New categories must create a new folder/subfolder rather than placing unrelated files together.
- Do not move documents across categories without recording the decision.
- Keep implementation code separate from SDLC documentation.

## Current position

**Current Phase:** Phase 1 — Product Discovery

**Current Step:** 1.7 — Value Proposition

**Status:** CURRENT / READY TO START

**Latest completed:** 1.6 Core Use Cases — PROJECT OWNER APPROVED

**Next exact task:** Create and complete the 1.7 Value Proposition deliverable.

## Phase 1 — Product Discovery

- [x] **1.1 — Problem Definition** — COMPLETE
- [x] **1.2 — Product Vision** — COMPLETE
- [x] **1.3 — Target Users** — COMPLETE
- [x] **1.4 — User Personas** — COMPLETE — APPROVED
- [x] **1.5 — User Journeys** — COMPLETE — APPROVED
- [x] **1.6 — Core Use Cases** — COMPLETE — APPROVED
- [ ] **1.7 — Value Proposition** — CURRENT
- [ ] 1.8 — MVP Scope — LOCKED
- [ ] 1.9 — Market & Competitor Research — LOCKED
- [ ] 1.10 — Technical Feasibility — LOCKED
- [ ] 1.11 — Business Model Hypotheses — LOCKED
- [ ] 1.12 — Risk Register — LOCKED
- [ ] 1.13 — Success Metrics — LOCKED
- [ ] 1.14 — Phase 1 Requirements Summary — LOCKED
- [ ] 1.15 — Phase 1 Review — LOCKED
- [ ] 1.16 — Phase 1 Approval — LOCKED

## Overall SDLC checklist

- [ ] **Phase 1 — Product Discovery — CURRENT / IN PROGRESS**
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

A step can be marked `[x] COMPLETE` only when:

- [ ] Required deliverable exists
- [ ] Objectives are satisfied
- [ ] Acceptance criteria are satisfied
- [ ] Required research/design/testing is complete
- [ ] Documentation is updated
- [ ] Dependencies and blockers are resolved or explicitly accepted
- [ ] Project owner reviews the actual deliverable
- [ ] Project owner explicitly approves completion

Only then is the next step unlocked.

## AI / team continuation protocol

Every AI or collaborator MUST:

1. Read `SDLC/AI-HANDOVER.md`.
2. Read `SDLC/PROGRESS.md`.
3. Identify the single step marked **CURRENT**.
4. Work only on that current step.
5. Do not start, implement, or mark later steps complete.
6. Do not skip unfinished work.
7. Do not rewrite approved decisions without explicit project-owner authorization.
8. Keep files in the correct phase/category folder.
9. Update the tracker after meaningful progress.
10. Leave a clear handover for the next AI/team member.

## No phase jumping

Even if a later phase is technically interesting or requested, it remains locked until the current step passes its completion gate.

If a later-phase dependency is discovered, record it as a dependency or blocker in the current step. Do not silently jump ahead.

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
Current step: 1.7 — Value Proposition
Status: READY TO START
Completed: 1.1 Problem Definition; 1.2 Product Vision; 1.3 Target Users; 1.4 User Personas; 1.5 User Journeys; 1.6 Core Use Cases
Incomplete: 1.7 through 1.16
Blockers: None currently recorded
Decisions: Project owner restored strict sequential execution; only the current step may be executed
Files changed: SDLC/PROGRESS.md
Next exact task: Create Phase 1.7 — Value Proposition deliverable
```

## Authoritative rule

**The current checkbox and status in this file determine where Linko has reached.**

A future AI or collaborator must verify this tracker against the repository and continue from the recorded current step. It must not restart, skip, or unlock later work without the project owner's explicit rule change.
