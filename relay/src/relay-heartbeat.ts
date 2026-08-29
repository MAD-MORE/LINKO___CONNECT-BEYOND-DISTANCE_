import { request } from "node:https";

/**
 * Relay -> Supabase liveness publisher.
 * Only operational metadata is sent; never tunnel keys or packet payloads.
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
  start(): void {
    if (this.timer) return;
    void this.send();
    this.timer = setInterval(() => void this.send(), this.options.intervalMs ?? 15_000);
    this.timer.unref();
  }
  stop(): void {
    if (this.timer) { clearInterval(this.timer); this.timer = null; }
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
        hostname: target.hostname,
        port: target.port || 443,
        path: `${target.pathname}${target.search}`,
        method: "POST",
        headers: {
          "content-type": "application/json",
          "content-length": Buffer.byteLength(body),
          "x-linko-relay-token": this.options.registrationToken,
        },
        timeout: 5_000,
      }, (res) => {
        res.resume();
        const ok = (res.statusCode ?? 500) >= 200 && (res.statusCode ?? 500) < 300;
        console.log(JSON.stringify({
          ts: new Date().toISOString(), level: ok ? "info" : "warn",
          message: ok ? "Relay heartbeat accepted" : "Relay heartbeat rejected",
          nodeId: this.options.nodeId, statusCode: res.statusCode,
        }));
        resolve();
      });
      req.on("timeout", () => req.destroy(new Error("heartbeat_timeout")));
      req.on("error", (error) => {
        // Failure is non-fatal; Supabase TTL will age this relay out automatically.
        console.error(JSON.stringify({ ts: new Date().toISOString(), level: "warn", message: "Relay heartbeat failed", nodeId: this.options.nodeId, error: error.message }));
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
