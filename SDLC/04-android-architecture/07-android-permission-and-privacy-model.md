# Phase 4.7 — Android Permission & Privacy Model

## Status
COMPLETE — APPROVED / UNLOCKED

## Principles

Linko requests only permissions required for an explicitly described feature and handles denial safely.

## Requirements
- VPN authorization is requested only when the user starts the relevant connectivity feature.
- Notifications use Android-supported mechanisms required for visible long-running networking.
- Contacts, location, camera, microphone, and unrelated sensitive permissions are not prerequisites for basic connectivity unless a future feature explicitly requires them.
- Permission rationale is clear and contextual.
- Denied permissions do not trigger repeated coercive prompts.
- Network metadata is minimized and protected.
- Traffic contents are not collected merely because they are forwarded.
- Privacy-sensitive state is excluded from logs and analytics.
- Revocation and account deletion propagate to local state appropriately.

## Acceptance
Permission tests cover grant, denial, revocation, reinstall, account deletion, and OS-version differences.
