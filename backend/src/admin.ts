import { Pool } from "pg";

/**
 * Admin routes module for the Linko control plane.
 *
 * These routes require an admin JWT (separate from device JWTs).
 * The admin secret is set via LINKO_ADMIN_SECRET environment variable.
 *
 * Endpoints (all require X-Linko-Admin header):
 *   GET  /v1/admin/devices          — List all registered devices
 *   GET  /v1/admin/sessions         — List all sessions (with filters)
 *   POST /v1/admin/sessions/:id/revoke — Force-revoke a session
 *   GET  /v1/admin/users            — List users (from Supabase)
 *   POST /v1/admin/devices/:id/revoke — Revoke a device
 *   GET  /v1/admin/relay/nodes      — List relay node status
 *   GET  /v1/admin/metrics/summary  — Summary metrics
 */

const adminSecret = process.env.LINKO_ADMIN_SECRET ?? "";

export function isAdminRequest(headers: Record<string, string | string[] | undefined>): boolean {
  if (!adminSecret) return false;
  const provided = headers["x-linko-admin"];
  const token = Array.isArray(provided) ? provided[0] : provided;
  return typeof token === "string" && token === adminSecret;
}

export interface AdminSession {
  id: string;
  providerDeviceId: string;
  receiverDeviceId: string;
  state: string;
  createdAt: Date;
  endedAt: Date | null;
}

export interface AdminDevice {
  id: string;
  userId: string;
  name: string;
  roles: string[];
  createdAt: Date;
  lastSeenAt: number;
  revokedAt: Date | null;
}

export async function listAdminSessions(
  pool: Pool,
  filter?: { state?: string; limit?: number }
): Promise<AdminSession[]> {
  const limit = Math.min(filter?.limit ?? 100, 500);
  const conditions: string[] = [];
  const params: unknown[] = [];

  if (filter?.state) {
    params.push(filter.state);
    conditions.push(`state = $${params.length}`);
  }

  const where = conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : "";
  params.push(limit);

  const result = await pool.query<{
    id: string;
    provider_device_id: string;
    receiver_device_id: string;
    state: string;
    created_at: Date;
    ended_at: Date | null;
  }>(
    `SELECT id, provider_device_id, receiver_device_id, state, created_at, ended_at
     FROM sessions ${where}
     ORDER BY created_at DESC
     LIMIT $${params.length}`,
    params
  );

  return result.rows.map(r => ({
    id: r.id,
    providerDeviceId: r.provider_device_id,
    receiverDeviceId: r.receiver_device_id,
    state: r.state,
    createdAt: r.created_at,
    endedAt: r.ended_at,
  }));
}

export async function listAdminDevices(pool: Pool, limit = 100): Promise<AdminDevice[]> {
  const result = await pool.query<{
    id: string;
    user_id: string;
    name: string;
    roles: string[];
    created_at: Date;
    last_seen_at: string;
    revoked_at: Date | null;
  }>(
    `SELECT id, user_id, name, roles, created_at, last_seen_at, revoked_at
     FROM devices
     ORDER BY created_at DESC
     LIMIT $1`,
    [Math.min(limit, 500)]
  );

  return result.rows.map(r => ({
    id: r.id,
    userId: r.user_id,
    name: r.name,
    roles: r.roles,
    createdAt: r.created_at,
    lastSeenAt: parseInt(r.last_seen_at, 10),
    revokedAt: r.revoked_at,
  }));
}

export async function adminRevokeDevice(pool: Pool, deviceId: string): Promise<void> {
  await pool.query(
    `UPDATE devices SET revoked_at = NOW() WHERE id = $1`,
    [deviceId]
  );
  // Also terminate any active sessions involving this device
  await pool.query(
    `UPDATE sessions SET state = 'revoked', ended_at = NOW()
     WHERE (provider_device_id = $1 OR receiver_device_id = $1)
       AND state NOT IN ('revoked', 'expired', 'denied', 'disconnected')`,
    [deviceId]
  );
}

export async function getAdminMetricsSummary(pool: Pool): Promise<Record<string, number>> {
  const [devices, sessions, activeSessions, usage] = await Promise.all([
    pool.query<{ count: string }>(`SELECT COUNT(*) as count FROM devices WHERE revoked_at IS NULL`),
    pool.query<{ count: string }>(`SELECT COUNT(*) as count FROM sessions`),
    pool.query<{ count: string }>(`SELECT COUNT(*) as count FROM sessions WHERE state = 'connected'`),
    pool.query<{ total: string }>(`SELECT COALESCE(SUM(bytes_up + bytes_down), 0) as total FROM usage_records`),
  ]);

  return {
    totalDevices: parseInt(devices.rows[0].count, 10),
    totalSessions: parseInt(sessions.rows[0].count, 10),
    activeSessions: parseInt(activeSessions.rows[0].count, 10),
    totalBytesTransferred: parseInt(usage.rows[0].total, 10),
  };
}
