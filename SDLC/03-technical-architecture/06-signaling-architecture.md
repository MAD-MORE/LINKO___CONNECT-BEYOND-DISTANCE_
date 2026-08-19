# Phase 3.6 — Signaling Architecture

## Status
COMPLETE

## Purpose
Coordinate authorized endpoints without carrying normal application payloads.

## Flow
1. Receiver requests a session.
2. Backend validates identity, relationship, policy and quota.
3. Provider receives an explicit request/authorization prompt.
4. Backend creates an authorized session lease.
5. Endpoints authenticate the session.
6. Candidates/capabilities are exchanged through signaling.
7. Endpoints attempt direct connectivity.
8. Relay is selected when necessary.
9. Signaling records final path state.
10. Session expires or is explicitly terminated.

## Message properties
Every signaling message must be authenticated, session-bound, replay-resistant, size-limited and versioned.

## Failure behavior
Duplicate messages are safe; stale messages are rejected; unauthorized participants receive no useful session information; signaling outage cannot grant permission.

## Acceptance
Signaling never becomes an implicit authorization bypass and does not become the routine application-payload transport.
