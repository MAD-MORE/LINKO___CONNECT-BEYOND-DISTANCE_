import type { IncomingMessage, ServerResponse } from "node:http";
import type { Pool } from "pg";

/**
 * Privacy endpoints for Linko (GDPR/CCPA compliance).
 *
 * GET  /v1/account/export  — Download all data for the authenticated user
 * DELETE /v1/account       — Delete account and all associated data
 */

export async function handleDataExport(
  req: IncomingMessage,
  res: ServerResponse,
  pool: Pool | null,
  userId: string,
  deviceId: string,
  requestId: string
): Promise<void> {
  const data: Record<string, unknown> = {
    exportedAt: new Date().toISOString(),
    userId,
    requestingDeviceId: deviceId,
  };

  if (pool) {
    // Fetch device records
    const devices = await pool.query(
      `SELECT id, name, roles, created_at, last_seen_at FROM devices WHERE user_id = $1`,
      [userId]
    );
    data.devices = devices.rows;

    // Fetch session records (metadata only — no traffic content)
    const sessions = await pool.query(
      `SELECT id, provider_device_id, receiver_device_id, state, created_at, ended_at
       FROM sessions
       WHERE provider_device_id = ANY(SELECT id FROM devices WHERE user_id = $1)
          OR receiver_device_id = ANY(SELECT id FROM devices WHERE user_id = $1)
       ORDER BY created_at DESC
       LIMIT 1000`,
      [userId]
    );
    data.sessions = sessions.rows;

    // Fetch usage records
    const usage = await pool.query(
      `SELECT ur.session_id, ur.role, ur.bytes_up, ur.bytes_down, ur.recorded_at
       FROM usage_records ur
       WHERE ur.device_id = $1
       ORDER BY ur.recorded_at DESC
       LIMIT 5000`,
      [deviceId]
    );
    data.usageRecords = usage.rows;
  } else {
    data.note = "In-memory store — no persistent data to export";
  }

  res.writeHead(200, {
    "Content-Type": "application/json",
    "Content-Disposition": `attachment; filename="linko-data-export-${userId}.json"`,
    "X-Request-Id": requestId,
    "Cache-Control": "no-store",
  });
  res.end(JSON.stringify(data, null, 2));
}

export async function handleAccountDeletion(
  req: IncomingMessage,
  res: ServerResponse,
  pool: Pool | null,
  supabaseUrl: string,
  supabaseSecretKey: string,
  userId: string,
  requestId: string
): Promise<void> {
  if (pool) {
    // Delete in dependency order (usage → sessions → devices → security events → blocks)
    await pool.query(
      `DELETE FROM usage_records WHERE device_id IN (SELECT id FROM devices WHERE user_id = $1)`,
      [userId]
    );
    await pool.query(
      `DELETE FROM sessions
       WHERE provider_device_id IN (SELECT id FROM devices WHERE user_id = $1)
          OR receiver_device_id IN (SELECT id FROM devices WHERE user_id = $1)`,
      [userId]
    );
    await pool.query(`DELETE FROM security_events WHERE user_id = $1`, [userId]);
    await pool.query(`DELETE FROM blocked_users WHERE user_id = $1`, [userId]);
    await pool.query(`DELETE FROM devices WHERE user_id = $1`, [userId]);
  }

  // Delete from Supabase auth (removes user account and email)
  if (supabaseUrl && supabaseSecretKey) {
    try {
      await fetch(`${supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(userId)}`, {
        method: "DELETE",
        headers: {
          apikey: supabaseSecretKey,
          Authorization: `Bearer ${supabaseSecretKey}`,
        },
      });
    } catch {
      // Non-fatal: log but don't fail — Supabase data may need manual cleanup
      console.error(`[privacy] Failed to delete Supabase user ${userId} — manual cleanup required`);
    }
  }

  res.writeHead(200, { "Content-Type": "application/json", "X-Request-Id": requestId });
  res.end(JSON.stringify({
    message: "Account and all associated data deleted",
    userId,
    deletedAt: new Date().toISOString(),
  }));
}
