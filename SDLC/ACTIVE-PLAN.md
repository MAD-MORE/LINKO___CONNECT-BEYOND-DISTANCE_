# LINKO — Active Development Plan

## Source of truth
- UI source of truth: `Implement Prototype.zip`
- Android implementation: `phase-5.1-production-implementation`
- Prototype reference branch: `prototype-ui-final`
- Backend/signaling is separate from UI verification.

## Current phase
**Phase 5.1 — Production implementation**

### Current gate
Build → install APK → compare every production screen against the frozen prototype → correct mismatches → repeat until UI is accepted.

### Do not do yet
- Do not redesign the UI.
- Do not accept the light/simplified `Share your data` screen as final.
- Do not move to signaling/relay implementation until the UI gate is accepted.
- Do not create parallel implementation branches for the same phase.

## Execution order
1. Build Android APK from `phase-5.1-production-implementation`.
2. Install on device.
3. Compare against `Implement Prototype.zip`.
4. Record and fix visual/state/navigation mismatches.
5. Rebuild and retest.
6. UI acceptance.
7. Only after acceptance: implement/verify signaling host, relay, and transport behavior.
8. Run end-to-end Receiver ↔ Provider verification.

## Branch policy
Keep only these active branches:
- `main` — stable baseline/releases.
- `phase-5.1-production-implementation` — current working branch.
- `prototype-ui-final` — historical prototype implementation reference only.

Obsolete branches identified:
- `figma-ui-full-conversion`
- `linko-foundation`
- `prototype-ui-integration`

These must not receive new development work.

## Definition of done for Phase 5.1
- Frozen prototype states have native production representations.
- Receiver and Provider primary flows use the frozen visual language.
- APK builds successfully.
- Installed APK matches the frozen prototype closely enough for explicit UI acceptance.
- No legacy `Share your data` root is used by production navigation.
