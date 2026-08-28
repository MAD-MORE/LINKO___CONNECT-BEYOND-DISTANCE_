import { Pool } from "pg";

/**
 * Relay node coordinator for the Linko control plane.
 *
 * Responsibilities:
 * - Maintain a registry of known relay nodes
 * - Track node health via heartbeats
 * - Assign sessions to the best available relay node
 * - Detect and mark degraded relay nodes
 */

export interface RelayNode {
  id: string;
  host: string;
  port: number;
  region: string;
  status: "healthy" | "degraded" | "offline";
  lastHealthAt: number;
  currentSessions: number;
  maxSessions: number;
}

export interface RelayAssignment {
  nodeId: string;
  host: string;
  port: number;
  region: string;
}

const HEALTH_TTL_MS = 30_000; // Node is degraded if no heartbeat for 30s
const OFFLINE_TTL_MS = 90_000; // Node is offline if no heartbeat for 90s

export class RelayCoordinator {
  private pool: Pool | null;
  // In-memory node registry (supplemented by DB in production)
  private nodes = new Map<string, RelayNode>();

  constructor(pool?: Pool) {
    this.pool = pool ?? null;

    // Seed from environment if provided (single-node MVP configuration)
    const envHost = process.env.TUNNEL_HOST;
    const envPort = Number(process.env.TUNNEL_PORT ?? 0);
    if (envHost && envPort > 0) {
      const node: RelayNode = {
        id: "default",
        host: envHost,
        port: envPort,
        region: process.env.RELAY_REGION ?? "default",
        status: "healthy",
        lastHealthAt: Date.now(),
        currentSessions: 0,
        maxSessions: 1000,
      };
      this.nodes.set("default", node);
    }

    // Periodic health check
    setInterval(() => this.checkNodeHealth(), 15_000).unref();
  }

  /**
   * Register or update a relay node's heartbeat.
   * Called by relay nodes via POST /v1/relay/heartbeat (internal endpoint).
   */
  async registerHeartbeat(params: {
    nodeId: string;
    host: string;
    port: number;
    region: string;
    currentSessions: number;
    maxSessions: number;
  }): Promise<void> {
    const node: RelayNode = {
      ...params,
      status: "healthy",
      lastHealthAt: Date.now(),
    };
    this.nodes.set(params.nodeId, node);

    if (this.pool) {
      await this.pool.query(
        `INSERT INTO relay_nodes (id, host, port, region, status, last_health_at, current_sessions, max_sessions)
         VALUES ($1, $2, $3, $4, 'healthy', NOW(), $5, $6)
         ON CONFLICT (id) DO UPDATE
         SET host = $2, port = $3, region = $4, status = 'healthy',
             last_health_at = NOW(), current_sessions = $5, max_sessions = $6`,
        [params.nodeId, params.host, params.port, params.region, params.currentSessions, params.maxSessions]
      );
    }
  }

  /**
   * Assign the best relay node for a session.
   * Prefers nodes in the same region, then least-loaded healthy node.
   *
   * Returns null if no healthy relay is available (direct-only session).
   */
  assignRelay(preferredRegion?: string): RelayAssignment | null {
    const healthy = Array.from(this.nodes.values()).filter(
      n => n.status === "healthy" && n.currentSessions < n.maxSessions
    );

    if (healthy.length === 0) return null;

    // Prefer nodes in the preferred region
    const regional = preferredRegion
      ? healthy.filter(n => n.region === preferredRegion)
      : [];

    const candidates = regional.length > 0 ? regional : healthy;

    // Pick least-loaded node
    const selected = candidates.reduce((best, node) =>
      node.currentSessions < best.currentSessions ? node : best
    );

    return {
      nodeId: selected.id,
      host: selected.host,
      port: selected.port,
      region: selected.region,
    };
  }

  /**
   * Get all relay nodes (for admin/monitoring).
   */
  getAllNodes(): RelayNode[] {
    return Array.from(this.nodes.values());
  }

  private checkNodeHealth(): void {
    const now = Date.now();
    for (const [id, node] of this.nodes) {
      const age = now - node.lastHealthAt;
      if (age > OFFLINE_TTL_MS) {
        node.status = "offline";
      } else if (age > HEALTH_TTL_MS) {
        node.status = "degraded";
      }
      this.nodes.set(id, node);
    }
  }
}
