# Phase 3.5 — Networking & Tunnel Architecture

## Status
COMPLETE

## Objective
Define how Linko moves protected traffic from a Receiver through an authorized Provider connection without requiring physical proximity.

## Logical path

```text
Receiver App Traffic
 → Android VPN interface
 → Linko tunnel transport
 → Direct path OR encrypted relay
 → Provider tunnel endpoint
 → Provider network interface
 → Internet destination
```

Return traffic follows the reverse path.

## Path selection
1. Authenticate endpoints.
2. Authorize the session.
3. Exchange connection candidates through signaling.
4. Attempt direct connectivity using standards-based NAT traversal.
5. Validate the resulting path.
6. If direct connectivity fails, select an authorized relay.
7. Maintain session leases and re-key/reconnect according to security policy.

## Tunnel requirements
- Protected transport
- Session-specific identity
- Replay resistance
- Packet integrity
- Explicit MTU handling
- Keepalive/timeout strategy
- Reconnect after network changes
- Backpressure
- Resource limits

## Android boundary
The MVP shall use Android's supported VPN framework rather than root/system modification. The tunnel engine must handle packet capture and forwarding without granting arbitrary application control over authorization.

## Important limitation
The system cannot overcome carrier outages, unavailable Internet access, physical propagation delay, or a Provider with no usable upstream connection.

## Acceptance
A later implementation must demonstrate direct-path operation, relay fallback, network-change recovery, packet integrity, bounded resource usage and clean session termination.
