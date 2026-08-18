# Phase 9 — Database Design

## Objective
Create a durable, secure data model for control-plane state and business operations without storing unnecessary traffic content.

## Core entities

- users
- devices
- friendships
- connection_requests
- sessions
- usage_records
- plans
- subscriptions
- payments
- relay_nodes
- security_events
- blocked_users

## Example session fields

`id`, `provider_id`, `receiver_id`, `started_at`, `ended_at`, `bytes_up`, `bytes_down`, `status`.

## Design rules

- Foreign keys and integrity constraints
- Appropriate indexes
- Least-privilege database access
- Audit timestamps
- Explicit deletion/retention policies
- Migration-based schema changes
- Backups and recovery testing

## Exit criteria

The schema supports MVP workflows, has migrations, indexes for critical queries, tested authorization boundaries, backup strategy, and documented retention rules.
