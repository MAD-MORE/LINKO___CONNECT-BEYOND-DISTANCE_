import { request } from "node:https";

/** Relay -> Supabase liveness publisher. No tunnel keys or packet data are sent. */
export interface RelayHeartbeatOptions {
  nodeId: string; host: string; port: number; region: string;
  heartbeatUrl: string; registrationToken: string;
  intervalMs?: number; maxSessions?: number; currentSessions?: () => number;
}

export class RelayHeartbeat {
  private timer: NodeJS.Timeout | null = null;
  constructor(private readonly options: RelayHeartbeatOptions) {}
  start(): void {
    if (this.timer) return;
    void this.send();
    this.timer = setInterval(() => void this.send(), this.options.intervalMs ?? 15000);
    this.timer.unref();
  }
  stop(): void { if (this.timer) { clearInterval(this.timer); this.timer = null; } }
  private async send(): Promise<void> {
    const body = JSON.stringify({ nodeId: this.options.nodeId, host: this.options.host, port: this.options.port, region: this.options.region, currentSessions: this.options.currentSessions?.() ?? 0, maxSessions: this.options.maxSessions ?? 1000 });
    const target = new URL(this.options.heartbeatUrl);
    await new Promise<void>((resolve) => {
      const req = request({ hostname: target.hostname, port: target.port || 443, path: `${target.pathname}${target.search}`, method: "POST", headers: { "content-type": "application/json", "content-length": Buffer.byteLength(body), "x-linko-relay-token": this.options.registrationToken }, timeout: 5000 }, (res) => {
        res.resume();
        if ((res.statusCode ?? 500) >= 200 && (res.statusCode ?? 500) < 300) console.log(JSON.stringify({ ts: new Date().toISOString(), level: "info", message: "Relay heartbeat accepted", nodeId: this.options.nodeId }));
        else console.error(JSON.stringify({ ts: new Date().toISOString(), level: "warn", message: "Relay heartbeat rejected", nodeId: this.options.nodeId, statusCode: res.statusCode }));
        resolve();
      });
      req.on("timeout", () => req.destroy(new Error("heartbeat_timeout")));
      req.on("error", (error) => { console.error(JSON.stringify({ ts: new Date().toISOString(), level: "warn", message: "Relay heartbeat failed", nodeId: this.options.nodeId, error: error.message })); resolve(); });
      req.write(body); req.end();
    });
  }
}

export function createRelayHeartbeatFromEnv(currentSessions?: () => number): RelayHeartbeat | null {
  const token = process.env.LINKO_RELAY_REGISTRATION_TOKEN;
  const url = process.env.LINKO_RELAY_HEARTBEAT_URL;
  if (!token || !url) return null;
  return new RelayHeartbeat({ nodeId: process.env.RELAY_NODE_ID ?? "relay-1", host: process.env.RELAY_PUBLIC_HOST ?? "linko-relay.fly.dev", port: Number(process.env.UDP_PORT ?? 7000), region: process.env.RELAY_REGION ?? "iad", heartbeatUrl: url, registrationToken: token, intervalMs: Number(process.env.RELAY_HEARTBEAT_INTERVAL_MS ?? 15000), maxSessions: Number(process.env.RELAY_MAX_SESSIONS ?? 1000), currentSessions });
}
