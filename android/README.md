# LinkShare Android

Client-side Android frontend for LinkShare.

This pass includes:

- Kotlin + Jetpack Compose UI.
- Host and Client home modes.
- Host sharing toggle, incoming request consent, and live sharing stats.
- Client friend list, connect/disconnect flow, distinct tunnel states, weak-signal retry feedback, and live byte counters.
- `VpnService` stub with TODOs for WireGuard-style tunnel integration.
- `LinkShareApi` interface for future REST/WebSocket signaling integration.
- SoundPool connection effects when `connection_success.mp3` and `connection_failed.mp3` are supplied in `app/src/main/res/raw/`.

The backend, relay, NAT traversal, and real tunnel handshake are intentionally stubbed for the backend/tunnel teams to plug in without changing the UI state model. Place the supplied short UI sound files at `app/src/main/res/raw/connection_success.mp3` and `app/src/main/res/raw/connection_failed.mp3`; missing files are silently skipped in development.

## Build

Use the included Gradle wrapper from this directory: `./gradlew.bat :app:assembleDebug` on Windows or `./gradlew :app:assembleDebug` on macOS/Linux. Android Gradle Plugin `8.7.3` requires JDK 17; configure `JAVA_HOME` to a JDK 17 installation before building.
