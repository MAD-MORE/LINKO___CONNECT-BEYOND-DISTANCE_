import { request } from "node:https";

/**
 * Publishes relay liveness to the Supabase control plane.
 *
 * A relay is usable only while:
 * 1. its UDP socket is listening, and
 * 2. its authenticated heartbeat is fresh.
 *
 * The heartbeat loop is deliberately self-healing: transient control-plane
 * failures are retried with bounded backoff, and the health server can expose
 * registration loss so Fly can restart a wedged relay process.
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
const MIN_RETRY_MS = 2_000;
const MAX_RETRY_MS = 15_000;
const FRESHNESS_MS = 45_000;
const INITIAL_ATTEMPTS = 5;

export class RelayHeartbeat {
  private timer: NodeJS.Timeout | null = null;
  private stopped = true;
  private lastSuccessAt = 0;
  private consecutiveFailures = 0;

  constructor(private readonly options: RelayHeartbeatOptions) {}

  /** Perform authenticated registration with bounded startup retry before readiness. */
  async start(): Promise<void> {
    if (this.timer) return;

    this.stopped = false;
    let lastError: unknown = null;

    for (let attempt = 0; attempt < INITIAL_ATTEMPTS; attempt += 1) {
      if (this.stopped) throw new Error("relay_heartbeat_stopped");
      try {
        await this.send();
        lastError = null;
        this.consecutiveFailures = 0;
        break;
      } catch (error) {
        lastError = error;
        this.consecutiveFailures += 1;
        console.error(JSON.stringify({
          ts: new Date().toISOString(),
          level: "warn",
          event: "relay_initial_heartbeat_failed",
          attempt: attempt + 1,
          error: error instanceof Error ? error.message : String(error),
        }));
        if (attempt < INITIAL_ATTEMPTS - 1) {
          const retryDelay = Math.min(
            MAX_RETRY_MS,
            MIN_RETRY_MS * 2 ** attempt,
          );
          await new Promise(resolve => setTimeout(resolve, retryDelay));
        }
      }
    }

    if (lastError) {
      this.stopped = true;
      throw lastError;
    }

    this.schedule(this.options.intervalMs ?? DEFAULT_INTERVAL_MS);
  }

  stop(): void {
    this.stopped = true;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  /** True only while an authenticated heartbeat has succeeded recently. */
  isHealthy(maxAgeMs = FRESHNESS_MS): boolean {
    return !this.stopped && this.lastSuccessAt > 0 &&
      Date.now() - this.lastSuccessAt <= maxAgeMs;
  }

  getLastSuccessAt(): number {
    return this.lastSuccessAt;
  }

  getConsecutiveFailures(): number {
    return this.consecutiveFailures;
  }

  private schedule(delayMs: number): void {
    if (this.stopped) return;
    if (this.timer) clearTimeout(this.timer);

    this.timer = setTimeout(() => {
      this.timer = null;
      void this.tick();
    }, Math.max(0, delayMs));
    this.timer.unref();
  }

  private async tick(): Promise<void> {
    if (this.stopped) return;

    try {
      await this.send();
      this.consecutiveFailures = 0;
      this.schedule(this.options.intervalMs ?? DEFAULT_INTERVAL_MS);
    } catch (error) {
      this.consecutiveFailures += 1;
      console.error(JSON.stringify({
        ts: new Date().toISOString(),
        level: "warn",
        event: "relay_heartbeat_failed",
        consecutiveFailures: this.consecutiveFailures,
        error: error instanceof Error ? error.message : String(error),
      }));

      const retryDelay = Math.min(
        MAX_RETRY_MS,
        MIN_RETRY_MS * 2 ** Math.min(this.consecutiveFailures - 1, 3),
      );
      this.schedule(retryDelay);
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
          this.lastSuccessAt = Date.now();
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
  const rawRegistrationToken = process.env.LINKO_RELAY_REGISTRATION_TOKEN;
  const heartbeatUrl = process.env.LINKO_RELAY_HEARTBEAT_URL?.trim();
  if (!rawRegistrationToken || !heartbeatUrl) return null;

  const registrationToken = rawRegistrationToken.replace(/\s/g, "");
  if (!/^[0-9A-Fa-f]+$/.test(registrationToken)) {
    throw new Error("relay_registration_token_invalid_format");
  }

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
