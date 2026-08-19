# Phase 3.14 — Observability & Operations Architecture

## Status
COMPLETE

## Signals
Metrics: active sessions, connection success, direct/relay ratio, latency, throughput, errors, saturation and resource usage.

Logs: structured operational/security events with correlation identifiers and sensitive-field filtering.

Traces: control-plane request paths and service dependencies; payload contents are excluded.

## Alerts
Alerts cover service availability, abnormal authentication failures, relay saturation, error spikes, database health, queue backlog and security anomalies.

## Operations
On-call procedures define diagnosis, containment, rollback, failover and incident documentation.

## Privacy
Telemetry is deliberately less detailed than user traffic. Application payloads and secrets are prohibited from observability systems.

## Acceptance
Operators can determine whether a problem is in client, control plane, signaling, relay, storage or external networking without inspecting protected user traffic.
