# Phase 4.5 — Network Path Selection

## Status
COMPLETE — APPROVED / UNLOCKED

## Objective
Define how Linko chooses between direct connectivity and relay fallback.

## Flow
1. Authenticate session.
2. Exchange authorized signaling metadata.
3. Attempt supported direct path establishment.
4. Validate the resulting path.
5. If direct establishment fails, select an authorized relay.
6. Establish protected transport.
7. Monitor path health.
8. Reconnect or migrate only while authorization remains valid.

## Requirements
- Direct connectivity is preferred when safe and supported.
- Relay fallback is deterministic and policy-controlled.
- Path selection never changes authorization scope.
- Relay selection considers capacity and latency where available.
- NAT/firewall conditions are treated as expected network realities.
- IP addresses and candidate metadata are handled as sensitive technical data.
- Path failure cannot silently expose traffic outside the protected tunnel.
- Relay limits prevent resource exhaustion.
- Path changes are observable through privacy-safe diagnostics.

## Acceptance
Integration tests cover direct success, NAT failure, relay fallback, relay exhaustion, path loss, network switching, and authorization revocation.
