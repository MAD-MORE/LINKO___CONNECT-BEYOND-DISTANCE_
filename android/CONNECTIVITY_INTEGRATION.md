# LINKO Android Connectivity Integration

This file tracks the runtime wiring between the Android client, LINKO engine, signaling backend, and relay/direct tunnel path.

## Required runtime path

Android UI -> Linko runtime/service -> signaling backend -> peer negotiation -> direct tunnel or relay -> provider internet path.

## Rules

- UI remains unchanged.
- No hard-coded localhost endpoint in release builds.
- Backend URL and signaling configuration must be injected through Android BuildConfig/environment.
- Engine lifecycle must be started before connection requests and stopped on app shutdown/session termination.
- Connection failures must expose actionable diagnostics to logs without leaking credentials.

## Current implementation checkpoint

The integration work is being performed on the `connectivity-integration` branch before merge to `main`.
