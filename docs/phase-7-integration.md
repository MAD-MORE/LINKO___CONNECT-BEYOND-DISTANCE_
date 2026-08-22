# Phase 7 — Full Integration

Status: IN PROGRESS

## System contract

Android receiver/provider clients use the LINKO signaling API for authenticated connection requests and session lifecycle. Supabase/PostgreSQL is control-plane state only. WebSocket signaling carries negotiation messages. A direct encrypted transport is preferred; an authenticated relay carries opaque encrypted frames when direct connectivity is unavailable.

## Integration invariants

1. No application traffic or private tunnel keys are persisted in Supabase.
2. The signaling service never receives plaintext application packets.
3. The relay never decrypts or persists application packets.
4. Sessions are short-lived and expire server-side.
5. A client must be authenticated before signaling or relay registration.
6. The Android VPN path must fail closed if an authenticated transport is unavailable.
7. Production endpoints and secrets are supplied only through environment/build configuration.

## Exit criteria

- Android production source is present and wired to the signaling client.
- Signaling request → approval → session → negotiation works against Supabase.
- Direct transport and relay fallback are connected to the VPN packet bridge.
- CI builds backend, relay, and Android artifacts.
- Automated protocol tests cover authorization, expiry, malformed messages, and relay isolation.
- Two authenticated devices complete an end-to-end session without mock components.

## Current repository constraint

The current GitHub code-search index does not expose the Android source tree on this branch, so no Android files are being fabricated or overwritten. The backend, relay, database, and integration contracts are implemented independently and are ready to be attached to the Android source when it is present in the repository.