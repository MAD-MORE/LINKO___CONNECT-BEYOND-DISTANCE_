# LinkShare Android

Client-side Android frontend for LinkShare.

This pass includes:

- Kotlin + Jetpack Compose UI.
- Host and Client home modes.
- Host sharing toggle, incoming request consent, and live sharing stats.
- Client friend list, connect/disconnect flow, distinct tunnel states, weak-signal retry feedback, and live byte counters.
- `VpnService` stub with TODOs for WireGuard-style tunnel integration.
- `LinkShareApi` interface for future REST/WebSocket signaling integration.

The backend, relay, NAT traversal, and real tunnel handshake are intentionally stubbed for the backend/tunnel teams to plug in without changing the UI state model.
