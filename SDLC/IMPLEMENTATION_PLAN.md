# Linko — Full SDLC Completion Plan (Phases 3–25)

## What this plan does

Completes all remaining SDLC phases by producing the actual required artifacts:
code, architecture documents, infrastructure config, CI/CD pipelines, test suites,
and checklists. Human-action items are clearly flagged with ⚠️.

---

## Execution Strategy — 5 Waves

| Wave | Phases | Theme |
|---|---|---|
| **1** | 3, 4 | Architecture docs baseline |
| **2** | 5–9 | Backend + relay + DB implementation |
| **3** | 10–13 | Security, abuse, privacy, Android live wiring |
| **4** | 14–16 | Testing + CI/CD + deployment infra |
| **5** | 17–25 | Performance, business, legal, launch |

---

## Wave 1 — Architecture Documentation

### Phase 3 — Technical Architecture
- [NEW] `docs/architecture/01-system-context.md` — system context diagram, component list, API contract index
- [NEW] `docs/architecture/02-threat-model.md` — assets, trust boundaries, mitigations
- [NEW] `docs/architecture/03-deployment-topology.md` — backend, relay, Supabase, DNS
- [NEW] `docs/architecture/04-failure-mode-analysis.md` — relay down, NAT fail, DB fail, provider drops
- [NEW] `docs/architecture/05-capacity-cost-model.md` — relay bandwidth cost, DB storage, session concurrency
- [NEW] `docs/architecture/adr/` — ADR-001 through ADR-010
- [MODIFY] `SDLC/PROGRESS.md` — mark Phase 3 complete

### Phase 4 — Android Architecture
- [NEW] `docs/architecture/android/01-module-map.md` — existing Kotlin module boundaries and lifecycle
- [NEW] `docs/architecture/android/02-vpn-lifecycle.md` — VpnService state machine diagram
- [NEW] `docs/architecture/android/03-security-model.md` — Android Keystore, session credentials

---

## Wave 2 — Backend + Relay + Database

### Phase 5 — Backend Control Plane
- [MODIFY] `backend/src/server.ts` — add /v1/usage, /v1/admin/sessions, CORS, rate limiting, body size limits
- [NEW] `backend/src/rate-limiter.ts` — token-bucket rate limiter
- [NEW] `backend/src/usage.ts` — session byte counters, quota enforcement
- [NEW] `backend/src/admin.ts` — admin routes: list users/devices/sessions, force-revoke
- [NEW] `backend/src/abuse.ts` — abuse detection and auto-block logic
- [NEW] `backend/src/notifications.ts` — FCM push notification stubs
- [NEW] `backend/src/relay-coordinator.ts` — relay node registration, health, assignment
- [MODIFY] `backend/src/postgres-store.ts` — add usage, quota, admin, relay queries

### Phase 6 — Signaling (extend existing)
- [MODIFY] `backend/src/signaling.ts` — ticket expiry, replay protection, audit log, offline-provider short-circuit

### Phase 7 — Relay Infrastructure
- [NEW] `relay/package.json`
- [NEW] `relay/src/relay-server.ts` — UDP relay: auth, packet forward, bandwidth tracking, graceful drain
- [NEW] `relay/src/session-registry.ts` — session→key map with TTL expiry
- [NEW] `relay/src/health.ts` — HTTP health + metrics endpoint
- [NEW] `relay/Dockerfile`
- [NEW] `relay/README.md`

### Phase 8 — Backend (complete)
- [NEW] `backend/src/security-middleware.ts` — input sanitization, never-log-secrets guard
- [NEW] `backend/src/privacy.ts` — GDPR data export (GET /v1/account/export), account delete (DELETE /v1/account)
- [NEW] `backend/src/observability.ts` — structured JSON logging, request timing, /metrics

### Phase 9 — Database Design
- [NEW] `backend/migrations/005_usage_accounting.sql`
- [NEW] `backend/migrations/006_relay_nodes.sql`
- [NEW] `backend/migrations/007_security_events.sql`
- [NEW] `backend/migrations/008_subscriptions.sql`
- [NEW] `backend/migrations/009_blocked_users.sql`
- [NEW] `backend/src/db-schema.md` — full schema docs, indexes, retention policies

---

## Wave 3 — Security, Abuse, Privacy, Android Live Wiring

### Phase 10 — Security SDLC
- [NEW] `android/app/src/main/java/com/linkshare/app/security/LinkoKeyManager.kt` — Android Keystore wrapper
- [NEW] `android/app/src/main/java/com/linkshare/app/security/LinkoSecurePrefs.kt` — EncryptedSharedPreferences
- [NEW] `.github/workflows/security-scan.yml` — npm audit, CodeQL, Dependabot config

### Phase 11 — Abuse Prevention
- [MODIFY] `backend/src/rate-limiter.ts` — sliding-window per-user limits
- [MODIFY] `backend/src/server.ts` — wire abuse checks into session flows

### Phase 12 — Privacy
- [NEW] `docs/privacy/data-retention-policy.md`

### Phase 13 — UI/UX + Android Live Integration
- [MODIFY] `android/app/src/main/java/com/linkshare/app/network/LinkoRuntime.kt` — replace all mock paths with live API calls
- [MODIFY] `android/app/src/main/java/com/linkshare/app/viewmodel/LinkShareViewModel.kt` — use live LinkoRuntime
- [NEW] `android/app/src/main/java/com/linkshare/app/data/LinkoLiveRepository.kt` — live data repository
- [DELETE] `android/app/src/main/java/com/linkshare/app/network/LinkoRuntime.fixed.tmp`
- [DELETE] `android/app/src/main/java/com/linkshare/app/network/LINKO_LINEAR_RETRY_README.tmp`

---

## Wave 4 — Testing + CI/CD + Deployment

### Phase 14 — MVP Integration
- [NEW] `tests/integration/backend/` — Node.js integration tests for all backend routes
- [NEW] `tests/integration/e2e/` — E2E: register two devices, create+approve session, verify tunnel

### Phase 15 — Testing
- [NEW] `android/app/src/test/` — JVM unit tests: LinkoStateMachine, LinkoAuth, LinkoDeviceIdentity
- [NEW] `android/app/src/androidTest/` — instrumented VpnService lifecycle test
- [NEW] `backend/src/server.test.ts` — HTTP integration tests for all server routes

### Phase 16 — Real-World Testing
⚠️ HUMAN ACTION: Real-device tests on 2 Android phones
- Build + install APK on two devices (minSdk 26+)
- Complete sign-up → add friend → request connection → approve → verify tunnel
- Test on mobile data across carriers
- Verify no data leaks when tunnel is active

### Phase 16 — CI/CD + Deployment Infra
- [NEW] `.github/workflows/backend-ci.yml`
- [NEW] `.github/workflows/android-ci.yml`
- [NEW] `.github/workflows/relay-ci.yml`
- [NEW] `infra/docker-compose.yml` — local dev stack: Postgres + backend + relay
- [NEW] `infra/backend.dockerfile`
- [NEW] `infra/relay.dockerfile`
- [NEW] `infra/fly.backend.toml` — Fly.io backend config
- [NEW] `infra/fly.relay.toml` — Fly.io relay config
- [NEW] `infra/postgres-setup.sql`
- [NEW] `infra/runbooks/01-deploy-backend.md`
- [NEW] `infra/runbooks/02-deploy-relay.md`
- [NEW] `infra/runbooks/03-database-migration.md`
- [NEW] `infra/runbooks/04-incident-response.md`
- [NEW] `infra/runbooks/05-rollback.md`

---

## Wave 5 — Performance, Business, Legal, Launch

### Phase 17 — Performance Engineering
- [NEW] `docs/performance/benchmarks.md` — latency/throughput targets
- [NEW] `tests/performance/load-test.js` — autocannon backend load test

### Phase 18 — Business & Monetization
- [NEW] `docs/business/monetization-model.md` — free tier + paid plans

### Phase 19 — Linko Economy
- [NEW] `docs/business/linko-economy.md` — Provider credit/incentive model

### Phase 20 — Legal & Compliance
⚠️ HUMAN ACTION: Legal review required before publication
- [NEW] `docs/legal/privacy-policy-template.md` — GDPR/CCPA-aligned (needs legal review)
- [NEW] `docs/legal/terms-of-service-template.md` — (needs legal review)
- [NEW] `docs/legal/google-play-compliance-checklist.md`

### Phase 21 — Google Play Launch
⚠️ HUMAN ACTION: Requires Google Play developer account + keystore
- [NEW] `docs/launch/google-play-checklist.md` — full submission checklist
- [NEW] `android/app/signing/README.md` — release signing guide (keystore, local.properties, never commit keystore)

### Phase 22 — Monetization Implementation
- [NEW] `android/app/src/main/java/com/linkshare/app/billing/LinkoBillingManager.kt` — Play Billing stub
- [NEW] `android/app/src/main/java/com/linkshare/app/billing/LinkoSubscriptionState.kt`

### Phase 23 — Observability
- [NEW] `infra/monitoring/alerts.yml` — error rate, relay health alerts
- [NEW] `infra/monitoring/dashboard.json` — Grafana dashboard definition

### Phase 24 — Beta Program
⚠️ HUMAN ACTION: Recruit beta users
- [NEW] `docs/launch/beta-program.md` — enrollment, feedback channels, bug template
- [NEW] `.github/ISSUE_TEMPLATE/bug_report.md`

### Phase 25 — Scale & Global Expansion
- [NEW] `docs/scale/scaling-playbook.md` — horizontal scaling, relay regions, DB read replicas
- [NEW] `infra/terraform/` — Terraform stubs for multi-region relay

---

## Human Action Items Summary

| # | Phase | Action | Who |
|---|---|---|---|
| 1 | 16 | Real-device test on 2 Android phones | **You** |
| 2 | 20 | Legal review of privacy policy + ToS | **You + lawyer** |
| 3 | 21 | Google Play developer account + keystore | **You** |
| 4 | 24 | Recruit beta testers | **You** |

---

## Open Questions (answer before I execute)

1. **Relay/backend deployment target** — Fly.io, Railway, self-hosted VPS, or GCP/AWS?
   (Default plan: Fly.io — best for cheap long-lived UDP relay nodes)

2. **Supabase vs own auth** — Backend already uses Supabase for auth. Keep it or migrate to fully custom JWT auth?

3. **Monetization timing** — Wire Google Play Billing now (Phase 22), or just stub it and focus on MVP first?

4. **Push notifications** — FCM for connection request notifications? Or poll-only for MVP?
