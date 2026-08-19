# Phase 3.9 — API & Service Contracts

## Status
COMPLETE

## API domains
- Authentication/device registration
- User relationships
- Session creation/authorization
- Session status/termination
- Relay allocation
- Policy/quota
- Notifications
- Administration

## Contract rules
All APIs require explicit authentication and authorization. Inputs are validated. Responses avoid unnecessary private data. Contracts are versioned. Mutating operations use idempotency where retries are possible.

## Service-to-service
Internal calls use authenticated service identities, least privilege, timeouts and bounded retries.

## Error model
Errors expose stable machine-readable codes and safe human-readable messages. Internal stack traces and secrets never reach clients.

## Acceptance
API contracts define request/response schema, authentication, authorization, rate limits, idempotency, timeout, failure and compatibility behavior before implementation.
