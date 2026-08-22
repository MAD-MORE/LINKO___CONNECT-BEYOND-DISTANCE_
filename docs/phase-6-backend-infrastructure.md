# Phase 6 — Backend & Infrastructure

Status: IN PROGRESS

## Objectives

- Deploy the LINKO signaling service over HTTPS.
- Add persistent trusted-user and active-session storage.
- Add authenticated real-time signaling/WebSocket transport.
- Deploy an encrypted relay service for direct-path fallback.
- Configure production secrets without committing credentials.
- Add health/readiness endpoints and basic observability.
- Define environment-specific configuration for development, staging, and production.

## Exit criteria

- Signaling service is deployed and reachable over HTTPS.
- Authentication and authorization are enforced.
- Connection requests, approvals, sessions, and expiry work against persistent storage.
- Real-time negotiation works between two authenticated clients.
- Relay service accepts only valid short-lived session credentials.
- Health checks and structured error handling are operational.
- Android can use the deployed service without mock transport.

## Current blockers

- Production hosting/credentials have not yet been provisioned.
- The relay transport implementation still needs to be built and deployed.
- Android end-to-end integration requires the deployed signaling endpoint.

Phase 6 may not be marked complete until the exit criteria are verified.