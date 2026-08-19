# Phase 5 — Backend & Control Plane

## Status
ARCHITECTURE/IMPLEMENTATION PLAN COMPLETE; code implementation remains an execution gate.

## Scope
Authentication, accounts, devices, sessions, authorization, signaling, quotas, API contracts, service boundaries, persistence, background jobs, and administrative controls.

## Required services
- API service
- Authentication/identity integration
- Session coordinator
- Signaling service
- Policy/quota service
- Device registry
- Persistence layer
- Background worker
- Administrative/audit interface

## Invariants
- Backend is authoritative for authorization.
- Session commands are authenticated and idempotent.
- Revocation propagates to active sessions.
- No traffic payload is stored by the control plane.
- Secrets remain outside source control.

## Completion evidence
Unit/integration tests, API contract tests, security tests, migration tests, and staging deployment evidence are required before calling Phase 5 production-complete.
