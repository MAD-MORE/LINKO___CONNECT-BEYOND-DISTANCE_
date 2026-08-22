# LINKO Android

Client-side Android frontend for LINKO.

This pass includes:

- Kotlin + Jetpack Compose UI.
- Provider and Receiver home modes.
- Provider sharing toggle, incoming request consent, and live sharing stats.
- Receiver friend list, connect/disconnect flow, distinct tunnel states, weak-signal retry feedback, and live byte counters.
- `VpnService` stub with TODOs for WireGuard-style tunnel integration.
- `LinkoApi` interface for future REST/WebSocket signaling integration.

The backend, relay, NAT traversal, and real tunnel handshake are intentionally stubbed for the backend/tunnel teams to plug in without changing the UI state model.
