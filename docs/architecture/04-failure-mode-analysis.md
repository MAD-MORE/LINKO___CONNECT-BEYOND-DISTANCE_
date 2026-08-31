# Failure Mode Analysis — Linko

## Failure Modes and Responses

### F1 — Backend API Unreachable

**Trigger:** Control plane down, network partition, or Fly.io outage.

**Impact:** 
- New sessions cannot be created
- Existing in-flight tunnels may continue briefly until session expires
- Device presence heartbeats fail (device appears offline)

**Detection:** Health check at `/health` returns non-200 or times out. Fly.io uptime monitor alerts.

**Response:**
1. Fly.io auto-restarts crashed instances (automatic)
2. If persistent: `fly deploy --strategy=immediate` to force new release
3. Rollback: `fly releases list && fly deploy --image <previous-image>`

**User experience:** App shows "Cannot connect to Linko — please try again" and disables new session creation. Active tunnel packets still flow until tunnel key expires.

---

### F2 — PostgreSQL Database Unreachable

**Trigger:** Fly Postgres primary failure, disk full, OOM.

**Impact:**
- All API requests that require DB access fail with 503
- `/health` endpoint returns `{"status":"degraded","database":"unreachable"}`
- In-memory store is NOT used as fallback in production (data integrity risk)

**Detection:** `/health` check; Fly Postgres monitoring alerts.

**Response:**
1. Fly Postgres: `fly postgres failover -a linko-pg` (automatic in most cases)
2. Manual: `fly ssh console -a linko-pg` to inspect disk/logs
3. Restore from backup if data loss occurred

**User experience:** 503 errors on all endpoints except health check.

---

### F3 — Relay Node Down

**Trigger:** Relay process crash, Fly.io region outage, network issue.

**Impact:**
- Sessions assigned to that relay node lose their data-plane connection
- Control plane detects dead relay via health check TTL
- Session is marked `relay_failed` (new state)

**Detection:** Relay health endpoint `/health` stops responding. Control plane relay-coordinator marks node `degraded` after 3 missed heartbeats (15 seconds).

**Response:**
1. Fly.io auto-restarts relay process
2. Control plane reallocates session to healthy relay node
3. Android client reconnects automatically via `LinkoStateMachine` reconnect path

**User experience:** Brief disconnect (≤30 seconds), then auto-reconnect with "Reconnecting..." UI state.

---

### F4 — Provider Device Drops Connection

**Trigger:** Provider's network drops, battery dies, Provider manually disconnects, app killed by Android.

**Impact:**
- Tunnel data plane goes silent
- Receiver sees packet loss → eventual timeout
- Provider device stops sending heartbeats to control plane

**Detection:** 
- Receiver-side: tunnel read times out after 30 seconds
- Backend: Provider device `lastSeenAt` > 90 seconds old → device considered offline

**Response:**
1. `LinkoStateMachine` on Receiver moves to `RECONNECTING` state
2. Receiver polls backend for session state every 5 seconds
3. If Provider device comes back online: session can resume (within TTL)
4. If TTL exceeded: session transitions to `expired`, Receiver shows "Provider disconnected" screen

**User experience:** "Provider disconnected" with option to retry.

---

### F5 — NAT Traversal Failure (Direct Path Fails)

**Trigger:** Symmetric NAT on one or both devices; carrier-grade NAT; firewall blocks UDP.

**Impact:** Direct UDP path between Provider and Receiver cannot be established.

**Detection:** `LinkoSignalingClient` completes ICE exchange but no connectivity is established within timeout (10 seconds).

**Response:**
1. `LinkoEngineBridge` falls back to relay path automatically
2. Backend assigns nearest healthy relay node
3. Session continues over relay

**User experience:** Transparent fallback — user may notice slightly higher latency, no explicit error shown.

---

### F6 — Session Key Expired / Revoked Mid-Session

**Trigger:** Provider manually revokes session, session TTL expires, admin force-revoke.

**Impact:** Relay node rejects packets with unknown session key. Tunnel goes dead.

**Detection:** Relay returns error code on packet; tunnel engine detects repeated failures.

**Response:**
1. `LinkoStateMachine` polls session state → detects `revoked` or `expired`
2. Tunnel engine tears down cleanly
3. Android VpnService stops

**User experience:** "Connection ended by Provider" or "Session expired" screen.

---

### F7 — Android VpnService Killed by OS

**Trigger:** Android system kills foreground service due to low memory (rare), system policy, or battery optimization.

**Impact:** Receiver's traffic routing stops immediately. VPN interface is torn down by Android.

**Detection:** `onDestroy()` called on `LinkShareVpnService`. `LinkoStateMachine` transitions to `DISCONNECTED`.

**Response:**
1. App detects disconnection and shows "Disconnected" state
2. Session is not automatically reconnected (re-authorization required per Android policy)
3. User must tap "Connect" again

**User experience:** "Disconnected — tap to reconnect" screen.

---

### F8 — Supabase Auth Service Degraded

**Trigger:** Supabase outage affecting JWT verification.

**Impact:** 
- `/v1/devices/register` (requires Supabase JWT verify) fails
- Existing device JWTs continue to work (our backend verifies them independently)
- New signups fail

**Detection:** Supabase status page; `/v1/auth/signup` returns 503.

**Response:**
1. No automated action — Supabase is a managed dependency
2. Monitor Supabase status at status.supabase.com
3. New users cannot sign up until Supabase recovers

**User experience:** "Sign up is temporarily unavailable" for new users. Existing users are unaffected.

---

## Failure Mode Matrix

| Failure | Data-plane impact | Control-plane impact | Auto-recover? | User impact |
|---|---|---|---|---|
| Backend down | None (existing tunnels continue) | New sessions blocked | Yes (Fly restart) | New sessions fail |
| DB down | None | All API fails | Partial (Fly failover) | 503 on API |
| Relay down | Active relay sessions disconnect | None | Yes (Fly restart + reassign) | Brief disconnect |
| Provider drops | Tunnel silent | Heartbeat missing | No (user action) | "Provider disconnected" |
| NAT fail | Direct path fails | None | Yes (relay fallback) | Transparent |
| Key revoked | Tunnel rejected | None | No (by design) | "Connection ended" |
| VpnService killed | Traffic routing stops | None | No (OS policy) | "Disconnected" |
| Supabase down | None | New signup/register fails | No (external) | New users blocked |
