# LINKO SDLC — MASTER PROGRESS TRACKER

**Project:** Linko — Connect Beyond Distance

## STRICT EXECUTION RULE

Linko is executed strictly sequentially.

> A phase cannot be marked complete until every required deliverable, acceptance criterion, review, and test for that phase is complete. The next phase MUST NOT begin before the current phase is explicitly marked complete here.

This tracker is the persistent state for humans, AIs, and team members.

## Current position

**Current Phase:** Phase 1 — Product Discovery

**Current Status:** IN PROGRESS — 1.4 USER PERSONAS APPROVED; NEXT IS 1.5 USER JOURNEYS

**Next allowed action:** Begin Phase 1.5 only.

**Blocked phases:** Phase 2 and all later phases remain locked until Phase 1 is fully completed and approved.

---

## Phase 1 — Product Discovery checklist

- [x] **1.1 — Problem Definition** — COMPLETE
- [x] **1.2 — Product Vision** — COMPLETE
- [x] **1.3 — Target Users** — COMPLETE
- [x] **1.4 — User Personas** — COMPLETE — PROJECT OWNER APPROVED
- [ ] **1.5 — User Journeys** — CURRENT / NEXT
- [ ] 1.6 — Core Use Cases — LOCKED
- [ ] 1.7 — Value Proposition — LOCKED
- [ ] 1.8 — MVP Scope — LOCKED
- [ ] 1.9 — Market & Competitor Research — LOCKED
- [ ] 1.10 — Technical Feasibility — LOCKED
- [ ] 1.11 — Business Model Hypotheses — LOCKED
- [ ] 1.12 — Risk Register — LOCKED
- [ ] 1.13 — Success Metrics — LOCKED
- [ ] 1.14 — Phase 1 Requirements Summary — LOCKED
- [ ] 1.15 — Phase 1 Review — LOCKED
- [ ] 1.16 — Phase 1 Approval — LOCKED

## Overall phase checklist

- [ ] **Phase 1 — Product Discovery** — IN PROGRESS ← CURRENT
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

## Phase completion gate

A phase may be checked `[x]` only when all applicable items below are complete:

- [ ] Phase objectives completed
- [ ] Required deliverables completed
- [ ] Requirements/acceptance criteria satisfied
- [ ] Security review completed where applicable
- [ ] Privacy review completed where applicable
- [ ] Tests completed where applicable
- [ ] Known blockers resolved or explicitly accepted by project owner
- [ ] Documentation updated
- [ ] Handover recorded
- [ ] Project owner explicitly approves phase completion

Only after all required gates are satisfied may the phase status change to COMPLETE and the next phase become CURRENT.

## Status vocabulary

- **LOCKED** — cannot start yet.
- **CURRENT** — the only phase actively being executed.
- **BLOCKED** — current phase has an identified blocker.
- **REVIEW** — work is complete and awaiting review/approval.
- **COMPLETE** — all gates passed and project-owner approval received.

## AI/team continuation protocol

Before doing any Linko work, every AI/team member MUST:

1. Read `SDLC/AI-HANDOVER.md`.
2. Read this `SDLC/PROGRESS.md`.
3. Identify the single phase/step marked CURRENT.
4. Work only inside that phase/step unless repository maintenance is required to support it.
5. Never begin a LOCKED phase/step.
6. Update this tracker when progress changes.
7. Record important decisions and handover information.
8. At the end of a session, clearly state what remains unfinished.

## No phase jumping

A request to start a later phase does not override this rule.

If a later-phase dependency is discovered, document it as a dependency/blocker in the current phase rather than silently starting the later phase.

## Handover requirement

Every session must leave enough information for the next AI/team member to continue without guessing.

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

## Completion record

When a phase becomes complete, record:

```text
Phase:
Completed date:
Approved by:
Commit/PR:
Deliverables:
Tests/reviews:
Key decisions:
Next phase unlocked:
```

## Current handover

```text
Current phase: Phase 1 — Product Discovery
Current step: 1.5 — User Journeys
Status: READY TO START
Completed: 1.1 Problem Definition; 1.2 Product Vision; 1.3 Target Users; 1.4 User Personas
Incomplete: 1.5 through 1.16
Blockers: None currently recorded
Decision: Project owner approved Phase 1.4 User Personas
Files changed in latest transition: SDLC/01-product-discovery/user-personas/personas.md; SDLC/PROGRESS.md
Next exact task: Execute Phase 1.5 — User Journeys
```

## IMPORTANT

**The checkbox is the project state.**

A future AI must trust this tracker as the authoritative execution position, verify it against the repository, and continue from the current step rather than restarting or skipping ahead.
