# Phase 9 — Testing & Quality

## Status
TEST STRATEGY BASELINE COMPLETE; passing evidence remains required.

## Test pyramid
Unit → component → integration → device → end-to-end → load → security → resilience.

## Critical scenarios
Provider authorization, Receiver connection, direct path, relay fallback, revocation, network switching, VPN lifecycle, process death, quota enforcement, malformed traffic, service outage, database recovery, and concurrent sessions.

## Quality gates
No critical security regression, deterministic session state, acceptable performance, reproducible builds, migration safety, crash-free critical flows, and verified recovery behavior.
