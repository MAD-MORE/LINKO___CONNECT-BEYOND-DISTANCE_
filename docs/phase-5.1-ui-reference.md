# LINKO — Phase 5.1 UI Reference

## Status

**Phase:** 5.1 — Production implementation  
**UI status:** FROZEN  
**Reference:** `Implement Prototype.zip`  
**Reference SHA-256:** `6379a3b2af1b4f6e048addaed08322d7f6fcf555bffe2fca12a5b1e974427ef6`

The prototype archive is the visual source of truth for production implementation. Production code must reproduce its layout, typography, spacing, colors, interaction states, navigation model, and screen intent. Do not redesign the UI while implementing functionality.

## Prototype inventory

The frozen prototype defines 41 application states:

### Onboarding
- `welcome`
- `create-account`
- `verify`
- `profile`
- `register-device`
- `permissions`

### Friends / trust
- `friends`
- `find-friends`
- `friend-profile`
- `request-sent`
- `incoming-request`
- `blocked-removed`

### Receiver flow
- `home-engine`
- `rx-select-friend`
- `rx-request`
- `rx-waiting`
- `rx-approved`
- `rx-connecting`
- `rx-direct-path`
- `rx-relay-fallback`
- `connected`
- `network-quality`
- `usage`

### Provider flow
- `provider-incoming`
- `provider-authorization`
- `provider-sharing-setup`
- `provider-sharing-active`
- `provider-live-usage`

### Sessions / failure states
- `session-details`
- `session-history`
- `connection-lost`
- `reconnecting`
- `network-switching`
- `session-expired`

### Device / security / privacy
- `device-identity`
- `security-engine`
- `key-revoked`
- `privacy`
- `data-retention`
- `settings`
- `delete-account`

## Implementation rule

The Android production implementation may replace the prototype's web/React mechanics with native Kotlin/Jetpack Compose behavior, but it must preserve the frozen visual contract.

The networking layer, backend, VPN/tunnel implementation, and persistence are allowed to evolve independently behind this UI contract.

## Current production scaffold

The repository already contains an Android Kotlin/Jetpack Compose scaffold with Host/Client state handling, a VPN permission entry point, a signaling interface, and a VPN service stub. That scaffold is the implementation base; it is not the visual source of truth. The frozen prototype above takes precedence for UI decisions.

## Phase 5.1 exit gate

Do not advance to the next phase until:

1. All frozen prototype states have an equivalent production navigation/state representation.
2. The primary Receiver and Provider flows are implemented without changing the frozen visual language.
3. VPN permission, connection status, disconnect, and failure states are represented by real application state rather than demo-only navigation.
4. A real Android build succeeds.
5. The implementation is ready for device-level functional testing.
