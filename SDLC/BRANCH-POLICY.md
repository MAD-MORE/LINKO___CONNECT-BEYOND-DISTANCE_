# LINKO Branch Policy

LINKO uses a single active implementation branch per SDLC phase.

## Active branches

### `main`
Stable baseline. No experimental UI work.

### `phase-5.1-production-implementation`
The only active development branch for the current Android production implementation.

### `prototype-ui-final`
Reference-only branch. Use it to inspect historical prototype UI work when necessary. Do not develop new production features here.

## Rules
1. Never create another branch for the same Phase 5.1 implementation task.
2. Prototype/UI corrections go into `phase-5.1-production-implementation` after the frozen prototype is checked.
3. Backend/signaling work stays separate until the Phase 5.1 UI gate is passed.
4. Temporary experiments must be deleted after their useful changes are merged or intentionally abandoned.
5. Before creating a branch, verify that the task cannot be completed on the active phase branch.
6. Commit messages should identify the phase and purpose when practical.

## Cleanup status
The following branches are obsolete and should be deleted from GitHub:
- `figma-ui-full-conversion`
- `linko-foundation`
- `prototype-ui-integration`

Deletion is a repository-maintenance action; no development work should be based on these branches.
