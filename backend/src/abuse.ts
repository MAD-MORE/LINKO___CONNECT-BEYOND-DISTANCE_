import { Pool } from "pg";

/**
 * Abuse detection for Linko.
 *
 * Detects:
 * - Repeated rejected connection requests (potential spam)
 * - Abnormal usage spikes
 * - Failed authentication attempts
 *
 * Can automatically block users/devices after thresholds are exceeded.
 */

export interface AbuseEvent {
  eventType: AbuseEventType;
  userId?: string;
  deviceId?: string;
  metadata?: Record<string, unknown>;
}

export type AbuseEventType =
  | "connection_request_rejected"
  | "auth_failed"
  | "session_creation_throttled"
  | "usage_spike"
  | "relay_bandwidth_exceeded";

// In-memory event counters (reset on restart; use DB for persistence in production)
const eventCounters = new Map<string, { count: number; firstSeen: number }>();

const THRESHOLDS: Record<AbuseEventType, { limit: number; windowMs: number }> = {
  connection_request_rejected: { limit: 10, windowMs: 60 * 60 * 1000 }, // 10 rejections/hour
  auth_failed: { limit: 5, windowMs: 15 * 60 * 1000 },                  // 5 failures/15min
  session_creation_throttled: { limit: 20, windowMs: 60 * 60 * 1000 },  // 20 throttles/hour
  usage_spike: { limit: 3, windowMs: 60 * 60 * 1000 },
  relay_bandwidth_exceeded: { limit: 3, windowMs: 24 * 60 * 60 * 1000 },
};

export class AbuseService {
  private pool: Pool | null;

  constructor(pool?: Pool) {
    this.pool = pool ?? null;
    // Clean up in-memory counters every 30 minutes
    setInterval(() => this.pruneCounters(), 30 * 60 * 1000).unref();
  }

  /**
   * Record an abuse event and check if the actor should be blocked.
   * Returns true if the actor should be blocked.
   */
  async recordEvent(event: AbuseEvent): Promise<boolean> {
    const key = this.eventKey(event);
    const threshold = THRESHOLDS[event.eventType];
    const now = Date.now();

    // Track in memory
    const counter = eventCounters.get(key) ?? { count: 0, firstSeen: now };
    if (now - counter.firstSeen > threshold.windowMs) {
      counter.count = 1;
      counter.firstSeen = now;
    } else {
      counter.count += 1;
    }
    eventCounters.set(key, counter);

    // Persist to DB if available
    if (this.pool) {
      try {
        await this.pool.query(
          `INSERT INTO security_events (user_id, device_id, event_type, metadata, created_at)
           VALUES ($1, $2, $3, $4, NOW())`,
          [
            event.userId ?? null,
            event.deviceId ?? null,
            event.eventType,
            JSON.stringify(event.metadata ?? {}),
          ]
        );
      } catch {
        // Non-fatal: abuse recording failure should not block the main request
      }
    }

    const shouldBlock = counter.count >= threshold.limit;
    if (shouldBlock && this.pool) {
      await this.autoBlock(event);
    }
    return shouldBlock;
  }

  /**
   * Check if a device or user is currently blocked.
   */
  async isBlocked(deviceId?: string, userId?: string): Promise<boolean> {
    if (!this.pool) return false;
    if (!deviceId && !userId) return false;
    const result = await this.pool.query(
      `SELECT 1 FROM blocked_users
       WHERE (device_id = $1 OR user_id = $2)
         AND (expires_at IS NULL OR expires_at > NOW())
       LIMIT 1`,
      [deviceId ?? null, userId ?? null]
    );
    return result.rows.length > 0;
  }

  /**
   * Manually block a user or device.
   */
  async block(params: {
    userId?: string;
    deviceId?: string;
    reason: string;
    expiresInHours?: number;
  }): Promise<void> {
    if (!this.pool) return;
    const expiresAt = params.expiresInHours
      ? new Date(Date.now() + params.expiresInHours * 60 * 60 * 1000).toISOString()
      : null;
    await this.pool.query(
      `INSERT INTO blocked_users (user_id, device_id, reason, expires_at, created_at)
       VALUES ($1, $2, $3, $4, NOW())
       ON CONFLICT (user_id, device_id) DO UPDATE
       SET reason = $3, expires_at = $4`,
      [params.userId ?? null, params.deviceId ?? null, params.reason, expiresAt]
    );
  }

  /**
   * Unblock a user or device.
   */
  async unblock(deviceId?: string, userId?: string): Promise<void> {
    if (!this.pool) return;
    await this.pool.query(
      `DELETE FROM blocked_users WHERE device_id = $1 OR user_id = $2`,
      [deviceId ?? null, userId ?? null]
    );
  }

  private async autoBlock(event: AbuseEvent): Promise<void> {
    const reason = `auto:${event.eventType}`;
    const expiresInHours = event.eventType === "auth_failed" ? 1 : 24;
    await this.block({ userId: event.userId, deviceId: event.deviceId, reason, expiresInHours });
  }

  private eventKey(event: AbuseEvent): string {
    const actor = event.deviceId ?? event.userId ?? "unknown";
    return `${event.eventType}:${actor}`;
  }

  private pruneCounters(): void {
    const now = Date.now();
    for (const [key, counter] of eventCounters) {
      const eventType = key.split(":")[0] as AbuseEventType;
      const threshold = THRESHOLDS[eventType];
      if (threshold && now - counter.firstSeen > threshold.windowMs * 2) {
        eventCounters.delete(key);
      }
    }
  }
}
