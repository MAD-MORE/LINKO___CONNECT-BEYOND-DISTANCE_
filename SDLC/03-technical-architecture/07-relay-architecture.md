# Phase 3.7 — Relay Architecture

## Status
COMPLETE

## Role
Relays are fallback data-plane infrastructure for sessions that cannot establish an acceptable direct path.

## Requirements
- Authenticate/validate relay sessions.
- Isolate each session.
- Forward protected packets without routine payload inspection.
- Enforce bandwidth, connection, CPU, memory and duration limits.
- Reject unknown/expired sessions.
- Support health checks and draining.
- Support regional placement.
- Produce privacy-safe operational metrics.

## Scaling
Relay capacity shall scale independently from the control plane. A scheduler may select relays using capacity, health, region, latency and policy.

## Failure
A relay failure must not corrupt account or authorization state. Sessions should attempt migration/reconnection when supported; otherwise terminate cleanly.

## Abuse boundary
Relays must not become unrestricted open proxies. Session authorization, quotas, abuse controls and destination/network policies defined later must be enforced.
