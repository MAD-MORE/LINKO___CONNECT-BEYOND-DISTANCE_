import { request } from "node:https";

/**
 * LINKO Relay -> Supabase control-plane heartbeat.
 *
 * The relay is deliberately the only component that can assert:
 * "this relay node is alive and serving UDP traffic."
 *
 * Security model:
 * - The registration token is supplied only through the Fly secret
 *   LINKO_RELAY_REGISTRATION_TOKEN; it is never committed to source.
 * - This process sends health metadata only. It never sends user data,
 *   tunnel keys, or packet contents to Supabase.
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

const DEFAULT_INTERVAL_MS = 15_000;

export class RelayHeartbeat {
  private readonly options: RelayHeartbeatOptions;
  private timer: NodeJS.Timeout | null = null;

  constructor(options: RelayHeartbeatOptions) {
    this.options = options;
  }

  /** Start immediately, then refresh liveness periodically. */
  start(): void {
    if (this.timer) return;

    // Register before advertising this relay as healthy to the rest of LINKO.
    void this.send();
    this.timer = setInterval(() => void this.send(), this.options.intervalMs ?? DEFAULT_INTERVAL_MS);
    this.timer.unref();
  }

  stop(): void {
    if (!this.timer) return;
    clearInterval(this.timer);
    this.timer = null;
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

    const target = new URL(this.options.heartbeatUrl);
    await new Promise<void>((resolve) => {
      const req = request({
        protocol: target.protocol,
        hostname: target.hostname,
        port: target.port || 443,
        path: `${target.pathname}${target.search}`,
        method: "POST",
        headers: {
          "content-type": "application/json",
          "content-length": Buffer.byteLength(body),
          // Secret is accepted only by the registration boundary.
          "x-linko-relay-token": this.options.registrationToken,
        },
        timeout: 5_000,
      }, (res) => {
        // Drain the response so Node can reuse the connection.
        res.resume();
        if ((res.statusCode ?? 500) >= 200 && (res.statusCode ?? 500) < 300) {
          console.log(JSON.stringify({
            ts: new Date().toISOString(),
            level: "info",
            message: "Relay heartbeat accepted",
            nodeId: this.options.nodeId,
          }));
        } else {
          console.error(JSON.stringify({
            ts: new Date().toISOString(),
            level: "warn",
            message: "Relay heartbeat rejected",
            nodeId: this.options.nodeId,
            statusCode: res.statusCode,
          }));
        }
        resolve();
      });

      req.on("timeout", () => {
        req.destroy(new Error("heartbeat_timeout"));
      });
      req.on("error", (error) => {
        // Heartbeat failure must not crash the packet relay. The database TTL
        // will naturally age the node out of the healthy pool.
        console.error(JSON.stringify({
          ts: new Date().toISOString(),
          level: "warn",
          message: "Relay heartbeat failed",
          nodeId: this.options.nodeId,
          error: error.message,
        }));
        resolve();
      });

      req.write(body);
      req.end();
    });
  }
}

export function createRelayHeartbeatFromEnv(currentSessions?: () => number): RelayHeartbeat | null {
  const registrationToken = process.env.LINKO_RELAY_REGISTRATION_TOKEN;
  const heartbeatUrl = process.env.LINKO_RELAY_HEARTBEAT_URL;

  // Local development can run without control-plane registration. Production
  // deployments should provide both environment variables so the relay becomes
  // discoverable by linko_tunnel_config only after successful registration.
  if (!registrationToken || !heartbeatUrl) return null;

  return new RelayHeartbeat({
    nodeId: process.env.RELAY_NODE_ID ?? "relay-1",
    host: process.env.RELAY_PUBLIC_HOST ?? "linko-relay.fly.dev",
    port: Number(process.env.UDP_PORT ?? 7000),
    region: process.env.RELAY_REGION ?? "iad",
    heartbeatUrl,
    registrationToken,
    intervalMs: Number(process.env.RELAY_HEARTBEAT_INTERVAL_MS ?? DEFAULT_INTERVAL_MS),
    maxSessions: Number(process.env.RELAY_MAX_SESSIONS ?? 1000),
    currentSessions,
  });
}
