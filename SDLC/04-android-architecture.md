# Phase 4 — Android Architecture

## Objective
Build a maintainable Android application around Kotlin and Android networking APIs, with clear separation between UI, session management, VPN/tunnel components, security, and local state.

## Recommended structure

```text
android/
├── app/
├── core/
├── networking/
├── vpn/
├── tunnel/
├── authentication/
├── friends/
├── sessions/
├── usage/
├── security/
└── ui/
```

## Core responsibilities

- UI presents state and user actions.
- Session layer manages connection lifecycle.
- VPN layer integrates Android `VpnService`.
- Tunnel layer handles the selected production transport.
- Security layer manages device identity and session credentials.
- Networking layer communicates with Linko APIs.
- Usage layer records local counters.

## Android requirements

- Foreground-service strategy where required.
- Explicit VPN consent.
- Clear persistent connection state.
- Battery-aware background behavior.
- Network callback handling.
- Secure local storage for secrets/keys.
- Reconnect and shutdown paths.

## Exit criteria

A clean Android skeleton builds successfully, has defined module boundaries, can authenticate, registers a device, and has a tested lifecycle for starting/stopping the future VPN service.
