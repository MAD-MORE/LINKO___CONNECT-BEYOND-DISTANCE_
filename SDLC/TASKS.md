# Linko SDLC — Execution Task List

## Wave 1 — Architecture Documentation
- [/] Phase 3 — Technical Architecture docs
  - [/] docs/architecture/01-system-context.md
  - [ ] docs/architecture/02-threat-model.md
  - [ ] docs/architecture/03-deployment-topology.md
  - [ ] docs/architecture/04-failure-mode-analysis.md
  - [ ] docs/architecture/05-capacity-cost-model.md
  - [ ] docs/architecture/adr/ (ADR-001 to ADR-010)
  - [ ] SDLC/PROGRESS.md — mark Phase 3 complete
- [ ] Phase 4 — Android Architecture docs
  - [ ] docs/architecture/android/01-module-map.md
  - [ ] docs/architecture/android/02-vpn-lifecycle.md
  - [ ] docs/architecture/android/03-security-model.md

## Wave 2 — Backend + Relay + Database
- [ ] Phase 5 — Backend Control Plane
  - [ ] backend/src/rate-limiter.ts
  - [ ] backend/src/usage.ts
  - [ ] backend/src/admin.ts
  - [ ] backend/src/abuse.ts
  - [ ] backend/src/notifications.ts
  - [ ] backend/src/relay-coordinator.ts
  - [ ] backend/src/server.ts (add usage, admin, CORS, rate limiting)
  - [ ] backend/src/postgres-store.ts (add usage/admin/relay queries)
- [ ] Phase 6 — Signaling hardening
  - [ ] backend/src/signaling.ts (expiry, replay protection, audit)
- [ ] Phase 7 — Relay Infrastructure
  - [ ] relay/package.json
  - [ ] relay/src/relay-server.ts
  - [ ] relay/src/session-registry.ts
  - [ ] relay/src/health.ts
  - [ ] relay/Dockerfile
  - [ ] relay/README.md
- [ ] Phase 8 — Backend security/privacy/observability
  - [ ] backend/src/security-middleware.ts
  - [ ] backend/src/privacy.ts
  - [ ] backend/src/observability.ts
- [ ] Phase 9 — Database migrations
  - [ ] backend/migrations/005_usage_accounting.sql
  - [ ] backend/migrations/006_relay_nodes.sql
  - [ ] backend/migrations/007_security_events.sql
  - [ ] backend/migrations/008_subscriptions.sql
  - [ ] backend/migrations/009_blocked_users.sql
  - [ ] backend/src/db-schema.md

## Wave 3 — Security, Abuse, Privacy, Android Live Wiring
- [ ] Phase 10 — Security SDLC
  - [ ] android/.../security/LinkoKeyManager.kt
  - [ ] android/.../security/LinkoSecurePrefs.kt
  - [ ] .github/workflows/security-scan.yml
- [ ] Phase 11 — Abuse Prevention (server.ts wiring)
- [ ] Phase 12 — Privacy docs
  - [ ] docs/privacy/data-retention-policy.md
- [ ] Phase 13 — Android live wiring
  - [ ] LinkoRuntime.kt (replace mocks with live API)
  - [ ] LinkoLiveRepository.kt (new)
  - [ ] LinkShareViewModel.kt (wire to live)
  - [ ] Delete .tmp files

## Wave 4 — Testing + CI/CD + Infra
- [ ] Phase 14/15 — Tests
  - [ ] tests/integration/backend/
  - [ ] tests/integration/e2e/
  - [ ] android unit tests
  - [ ] backend/src/server.test.ts
- [ ] Phase 16 — CI/CD + Infra
  - [ ] .github/workflows/backend-ci.yml
  - [ ] .github/workflows/android-ci.yml
  - [ ] .github/workflows/relay-ci.yml
  - [ ] infra/docker-compose.yml
  - [ ] infra/backend.dockerfile
  - [ ] infra/relay.dockerfile
  - [ ] infra/fly.backend.toml
  - [ ] infra/fly.relay.toml
  - [ ] infra/postgres-setup.sql
  - [ ] infra/runbooks/ (5 runbooks)

## Wave 5 — Performance, Business, Legal, Launch
- [ ] Phase 17 — docs/performance/benchmarks.md, load-test.js
- [ ] Phase 18 — docs/business/monetization-model.md
- [ ] Phase 19 — docs/business/linko-economy.md
- [ ] Phase 20 — docs/legal/ (3 files)
- [ ] Phase 21 — docs/launch/google-play-checklist.md, signing README
- [ ] Phase 22 — Android billing stubs
- [ ] Phase 23 — infra/monitoring/
- [ ] Phase 24 — docs/launch/beta-program.md, issue templates
- [ ] Phase 25 — docs/scale/, infra/terraform/
