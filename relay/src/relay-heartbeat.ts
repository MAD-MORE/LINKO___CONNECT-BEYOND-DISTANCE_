import { request } from "node:https";

/**
 * Publishes relay liveness to the Supabase control plane.
 *
 * The relay is considered discoverable only while these heartbeats succeed.
 * Registration uses a dedicated shared secret; the secret is never committed.
 */
export interface RelayHeartbeatOptions {
  nodeId: string;
  host: string;
  port: number;
  region: string;
  heartbeatUrl: string;
  registrationToken: string;
  intervalMs?: number;
  maxSessions?: number;
  currentSessions?: () => number;
}

export class RelayHeartbeat {
  private timer: NodeJS.Timeout | null = null;

  constructor(private readonly options: RelayHeartbeatOptions) {}

  /** Perform the first registration synchronously from the lifecycle's point of view. */
  async start(): Promise<void> {
    if (this.timer) return;

    // Do not advertise the node until the first authenticated heartbeat succeeds.
    await this.send();
    this.timer = setInterval(() => {
      void this.send().catch((error) => {
        // A transient failure is logged; the database TTL will eventually remove
        // this node from selection if heartbeats stop succeeding.
        console.error(JSON.stringify({
          ts: new Date().toISOString(),
          level: "warn",
          event: "relay_heartbeat_failed",
          error: error instanceof Error ? error.message : String(error),
        }));
      });
    }, this.options.intervalMs ?? 15_000);
    this.timer.unref();
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private async send(): Promise<void> {
    const body = JSON.stringify({
      nodeId: this.options.nodeId,
      host: this.options.host,
      port: this.options.port,
      region: this.options.region,
      currentSessions: this.options.currentSessions?.() ?? 0,
      maxSessions: this.options.maxSessions ?? 1000,
    });

    const url = new URL(this.options.heartbeatUrl);
    if (url.protocol !== "https:") throw new Error("relay_heartbeat_https_required");

    await new Promise<void>((resolve, reject) => {
      const req = request({
        hostname: url.hostname,
        port: url.port || 443,
        path: url.pathname + url.search,
        method: "POST",
        headers: {
          "content-type": "application/json",
          "content-length": Buffer.byteLength(body),
          "x-linko-relay-token": this.options.registrationToken,
        },
        timeout: 5_000,
      }, (res) => {
        res.resume();
        if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
          resolve();
        } else {
          reject(new Error(`relay_heartbeat_http_${res.statusCode ?? 0}`));
        }
      });

      req.on("timeout", () => req.destroy(new Error("heartbeat_timeout")));
      req.on("error", reject);
      req.write(body);
      req.end();
    });
  }
}

export function createRelayHeartbeatFromEnv(currentSessions?: () => number): RelayHeartbeat | null {
  const registrationToken = process.env.LINKO_RELAY_REGISTRATION_TOKEN;
  const heartbeatUrl = process.env.LINKO_RELAY_HEARTBEAT_URL;
  if (!registrationToken || !heartbeatUrl) return null;

  return new RelayHeartbeat({
    nodeId: process.env.RELAY_NODE_ID ?? "relay-1",
    host: process.env.RELAY_PUBLIC_HOST ?? "linko-relay.fly.dev",
    port: Number(process.env.UDP_PORT ?? 7000),
    region: process.env.RELAY_REGION ?? "iad",
    heartbeatUrl,
    registrationToken,
    intervalMs: Number(process.env.RELAY_HEARTBEAT_INTERVAL_MS ?? 15_000),
    maxSessions: Number(process.env.RELAY_MAX_SESSIONS ?? 1000),
    currentSessions,
  });
}
