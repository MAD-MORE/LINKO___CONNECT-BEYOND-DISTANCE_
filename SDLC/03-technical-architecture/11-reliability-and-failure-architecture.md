# Phase 3.11 — Reliability & Failure Architecture

## Status
COMPLETE

## Failure classes
- Android process death
- Wi-Fi/mobile handoff
- Provider upstream loss
- Receiver network loss
- Signaling outage
- API outage
- Database outage
- Relay failure
- Regional outage

## Principles
Use timeouts, bounded retries, exponential backoff, health checks, circuit breaking where appropriate, state reconciliation and clean termination.

## Session leases
Connectivity authorization should be represented by bounded session/authorization leases so stale clients cannot retain indefinite access after control-plane failure.

## Recovery
Restart-safe services reconstruct state from authoritative stores. Ephemeral transport state may be recreated. Security state is never inferred from stale client cache.

## Availability target
Concrete SLA/SLO values are deferred until traffic estimates and cost modeling are complete.

## Acceptance
Each critical component has defined detection, isolation, recovery, degradation and user-visible failure behavior.
