# Phase 3.8 — Data & Storage Architecture

## Status
COMPLETE

## Logical data domains
- Identity/accounts
- Devices
- Provider/Receiver relationships
- Consent/authorization
- Connectivity sessions
- Policy/quota
- Security/audit events
- Operational telemetry

## Rules
Critical authorization state has one authoritative store. Ephemeral transport state belongs in short-lived infrastructure. Cache is never the sole source of permission.

## Storage characteristics
Durable data requires encryption, access control, backups, migration strategy and retention policy. High-volume telemetry is separated from transactional state.

## Privacy
No routine application traffic payload storage. Sensitive metadata is minimized and access-controlled.

## Consistency
Authorization, session state and quotas require strong correctness guarantees. Analytics can tolerate eventual consistency where it does not affect security or user-visible correctness.

## Acceptance
Every entity has owner, sensitivity, lifecycle, authoritative source, access policy, retention and recovery behavior.
