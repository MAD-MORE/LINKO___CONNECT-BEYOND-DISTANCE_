# LINKO Android

Client-side Android frontend for LINKO.

This pass includes:

- Kotlin + Jetpack Compose UI.
- Provider and Receiver home modes.
- Provider sharing toggle, incoming request consent, and live sharing stats.
- Receiver friend list, connect/disconnect flow, distinct tunnel states, weak-signal retry feedback, and live byte counters.
- Direct UDP P2P negotiation with authenticated encrypted transport.
- Supabase-backed device registration, presence, session approval, and signaling.
- Android `VpnService` receiver tunnel with protected direct UDP socket and real traffic routing.
- No server relay is used by the active connection path.

## Build

Use the included Gradle wrapper from this directory: `./gradlew.bat :app:assembleDebug` on Windows or `./gradlew :app:assembleDebug` on macOS/Linux. Android Gradle Plugin `8.7.3` requires JDK 17; configure `JAVA_HOME` to a JDK 17 installation before building.
