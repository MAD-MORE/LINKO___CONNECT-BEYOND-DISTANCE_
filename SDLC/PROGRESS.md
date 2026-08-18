# LINKO SDLC — MASTER PROGRESS TRACKER

**Project:** Linko — Connect Beyond Distance

## STRICT EXECUTION RULE

Linko is executed **strictly sequentially**.

> **A phase cannot be marked complete until every required deliverable, acceptance criterion, review, and test for that phase is complete. The next phase MUST NOT begin before the current phase is explicitly marked complete here.**

This tracker is the persistent state for humans, AIs, and team members.

## Current position

**Current Phase:** Phase 1 — Product Discovery

**Current Status:** NOT STARTED

**Next allowed action:** Begin Phase 1 only.

**Blocked phases:** Phases 2–25 are locked until Phase 1 is completed and checked off.

---

## Phase checklist

- [ ] **Phase 1 — Product Discovery** — NOT STARTED ← **CURRENT**
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

---

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

Only after all required gates are satisfied may the status change to **COMPLETE** and the next phase become **CURRENT**.

## Status vocabulary

Use only these states:

- **LOCKED** — cannot start yet.
- **CURRENT** — the only phase actively being executed.
- **BLOCKED** — current phase has an identified blocker.
- **REVIEW** — work is complete and awaiting phase completion review/approval.
- **COMPLETE** — all gates passed and phase is checked off.

## AI/team continuation protocol

Before doing any Linko work, every AI/team member MUST:

1. Read `SDLC/AI-HANDOVER.md`.
2. Read this `SDLC/PROGRESS.md`.
3. Identify the single phase marked **CURRENT**.
4. Work only inside that phase unless the task is repository maintenance required to support it.
5. Never begin a LOCKED phase.
6. Update this tracker when the current phase status changes.
7. Record important decisions and handover information.
8. At the end of a session, clearly state what remains unfinished.

## No phase jumping

Requests such as "start Phase 5" do not override this rule.

If Phase 2 is current, Phase 5 remains locked until Phases 2–4 are completed.

If a later-phase dependency is discovered, document it as a dependency/blocker in the current phase rather than silently starting the later phase.

## Handover requirement

Every session should leave enough information for the next AI/team member to continue without guessing.

At minimum record:

```text
Current phase:
Status:
Completed:
Incomplete:
Blockers:
Decisions:
Files changed:
Tests:
Next exact task:
```

## Completion record

When a phase becomes complete, add a record below:

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

## IMPORTANT

**The checkbox is not decoration. It is the project state.**

A future AI must trust this tracker as the authoritative execution position, verify it against the repository, and continue from the current phase rather than restarting or skipping ahead.
