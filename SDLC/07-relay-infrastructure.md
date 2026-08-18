# Phase 7 — Relay Infrastructure

## Objective
Provide a reliable fallback path when Provider and Receiver cannot establish a suitable direct path.

## Strategy

Prefer direct connectivity where safe and feasible. Fall back to geographically appropriate Linko relay nodes when NAT, firewall, carrier, or network conditions prevent direct transport.

## Relay responsibilities

- Authenticate relay session
- Forward encrypted tunnel traffic
- Enforce bandwidth/session limits
- Report health and aggregate usage
- Avoid unnecessary inspection of payload contents
- Support graceful node failure

## Infrastructure requirements

- Stateless or horizontally scalable relay design where possible
- Health checks
- Regional placement
- Capacity limits
- Abuse controls
- Cost monitoring
- Automatic draining of unhealthy nodes

## Cost principle

Relay bandwidth is a major cost driver. Direct paths should be preferred and relay-heavy users/plans must be modeled so Linko does not lose money as usage grows.

## Exit criteria

A controlled test can establish a secure session through a relay, survive relay/node failure according to defined policy, and report bandwidth and health metrics.
