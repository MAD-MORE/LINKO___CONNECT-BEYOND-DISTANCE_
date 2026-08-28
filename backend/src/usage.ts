import { Pool } from "pg";

/**
 * Usage accounting for Linko sessions.
 * Devices report bytes_up (sent by device) and bytes_down (received by device).
 * The control plane records these for quota enforcement, billing, and display.
 */

export interface UsageRecord {
  id: string;
  sessionId: string;
  deviceId: string;
  role: "provider" | "receiver";
  bytesUp: number;
  bytesDown: number;
  recordedAt: number;
}

export interface SessionUsageSummary {
  sessionId: string;
  totalBytesUp: number;
  totalBytesDown: number;
  totalBytes: number;
  records: UsageRecord[];
}

export class UsageService {
  private pool: Pool;

  constructor(pool: Pool) {
    this.pool = pool;
  }

  /**
   * Record a usage update for a session party.
   * Called by devices via PATCH /v1/sessions/:id/usage
   */
  async recordUsage(
    sessionId: string,
    deviceId: string,
    role: "provider" | "receiver",
    bytesUp: number,
    bytesDown: number
  ): Promise<UsageRecord> {
    const result = await this.pool.query<{
      id: string;
      session_id: string;
      device_id: string;
      role: string;
      bytes_up: string;
      bytes_down: string;
      recorded_at: Date;
    }>(
      `INSERT INTO usage_records (session_id, device_id, role, bytes_up, bytes_down, recorded_at)
       VALUES ($1, $2, $3, $4, $5, NOW())
       RETURNING *`,
      [sessionId, deviceId, role, bytesUp, bytesDown]
    );
    return this.mapRecord(result.rows[0]);
  }

  /**
   * Get aggregated usage for a session.
   */
  async getSessionUsage(sessionId: string): Promise<SessionUsageSummary> {
    const result = await this.pool.query<{
      id: string;
      session_id: string;
      device_id: string;
      role: string;
      bytes_up: string;
      bytes_down: string;
      recorded_at: Date;
    }>(
      `SELECT * FROM usage_records WHERE session_id = $1 ORDER BY recorded_at ASC`,
      [sessionId]
    );
    const records = result.rows.map(r => this.mapRecord(r));
    const totalBytesUp = records.reduce((sum, r) => sum + r.bytesUp, 0);
    const totalBytesDown = records.reduce((sum, r) => sum + r.bytesDown, 0);
    return {
      sessionId,
      totalBytesUp,
      totalBytesDown,
      totalBytes: totalBytesUp + totalBytesDown,
      records,
    };
  }

  /**
   * Get total data used by a device in the current billing period (calendar month).
   */
  async getDeviceMonthlyUsage(deviceId: string): Promise<{ bytesUp: number; bytesDown: number; total: number }> {
    const result = await this.pool.query<{ bytes_up: string; bytes_down: string }>(
      `SELECT COALESCE(SUM(bytes_up), 0) AS bytes_up,
              COALESCE(SUM(bytes_down), 0) AS bytes_down
       FROM usage_records
       WHERE device_id = $1
         AND recorded_at >= DATE_TRUNC('month', NOW())`,
      [deviceId]
    );
    const row = result.rows[0];
    const bytesUp = parseInt(row.bytes_up, 10);
    const bytesDown = parseInt(row.bytes_down, 10);
    return { bytesUp, bytesDown, total: bytesUp + bytesDown };
  }

  /**
   * Check if a device has exceeded its plan quota.
   * Returns true if usage is within quota, false if exceeded.
   */
  async isWithinQuota(deviceId: string, quotaBytes: number): Promise<boolean> {
    const usage = await this.getDeviceMonthlyUsage(deviceId);
    return usage.total < quotaBytes;
  }

  private mapRecord(row: {
    id: string;
    session_id: string;
    device_id: string;
    role: string;
    bytes_up: string;
    bytes_down: string;
    recorded_at: Date;
  }): UsageRecord {
    return {
      id: row.id,
      sessionId: row.session_id,
      deviceId: row.device_id,
      role: row.role as "provider" | "receiver",
      bytesUp: parseInt(row.bytes_up, 10),
      bytesDown: parseInt(row.bytes_down, 10),
      recordedAt: row.recorded_at.getTime(),
    };
  }
}

// ---------------------------------------------------------------------------
// Free tier quota constant (1 GB relay per month)
// ---------------------------------------------------------------------------
export const FREE_TIER_QUOTA_BYTES = 1_073_741_824; // 1 GB
