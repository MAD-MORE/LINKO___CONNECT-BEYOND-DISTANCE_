# Phase 6 — Networking & Relay

## Status
ARCHITECTURE/IMPLEMENTATION PLAN COMPLETE; deployment evidence remains required.

## Scope
Direct connectivity, NAT traversal, signaling candidates, transport negotiation, relay allocation, protected forwarding, health checks, resource limits, regional placement, and path recovery.

## Core policy
Prefer a direct authorized path when feasible; otherwise use an authorized relay. Relay infrastructure forwards protected traffic and does not require application payload inspection.

## Safety boundaries
No carrier bypass, no unauthorized network access, bounded relay resources, strict session isolation, authenticated transport, and deterministic shutdown.

## Completion evidence
Device-to-device tests across representative networks, relay failover tests, load tests, packet-loss tests, security tests, and resource-exhaustion tests are required.
