import type { IncomingMessage, ServerResponse } from "node:http";

/**
 * Observability for Linko control plane.
 *
 * - Structured JSON request logging
 * - Request timing
 * - Simple in-process counters for /metrics (Prometheus-compatible text format)
 * - Never logs Authorization headers or secrets
 */

// ---------------------------------------------------------------------------
// Metrics counters
// ---------------------------------------------------------------------------

interface Counter {
  name: string;
  help: string;
  value: number;
  labels?: Record<string, string>;
}

const counters = new Map<string, Counter>();

function counter(name: string, help: string, labels?: Record<string, string>): string {
  const key = labels
    ? `${name}{${Object.entries(labels).map(([k, v]) => `${k}="${v}"`).join(",")}}`
    : name;
  if (!counters.has(key)) {
    counters.set(key, { name, help, value: 0, labels });
  }
  return key;
}

export function increment(name: string, labels?: Record<string, string>): void {
  const key = counter(name, name, labels);
  const c = counters.get(key)!;
  c.value += 1;
}

export function renderMetrics(): string {
  const lines: string[] = [];
  const seen = new Set<string>();
  for (const [key, c] of counters) {
    if (!seen.has(c.name)) {
      lines.push(`# HELP ${c.name} ${c.help}`);
      lines.push(`# TYPE ${c.name} counter`);
      seen.add(c.name);
    }
    lines.push(`${key} ${c.value}`);
  }
  return lines.join("\n") + "\n";
}

// Pre-declare known metrics
counter("linko_requests_total", "Total HTTP requests", { status: "200" });
counter("linko_errors_total", "Total HTTP errors");
counter("linko_sessions_created_total", "Total sessions created");
counter("linko_sessions_approved_total", "Total sessions approved");
counter("linko_sessions_revoked_total", "Total sessions revoked");
counter("linko_rate_limit_hits_total", "Total rate limit rejections");
counter("linko_abuse_blocks_total", "Total abuse auto-blocks");

// ---------------------------------------------------------------------------
// Request logging
// ---------------------------------------------------------------------------

const REDACTED_HEADERS = new Set(["authorization", "cookie", "x-linko-bootstrap"]);

export function logRequest(params: {
  requestId: string;
  method: string;
  path: string;
  status: number;
  durationMs: number;
  deviceId?: string;
  error?: string;
}): void {
  const level = params.status >= 500 ? "error" : params.status >= 400 ? "warn" : "info";
  const entry = {
    ts: new Date().toISOString(),
    level,
    requestId: params.requestId,
    method: params.method,
    path: params.path,
    status: params.status,
    durationMs: params.durationMs,
    ...(params.deviceId ? { deviceId: params.deviceId } : {}),
    ...(params.error ? { error: params.error } : {}),
  };
  console.log(JSON.stringify(entry));

  // Update metrics
  increment("linko_requests_total", { status: String(params.status) });
  if (params.status >= 500) increment("linko_errors_total");
}

/**
 * Wrap a request handler to automatically log timing and status.
 */
export function withTiming(
  req: IncomingMessage,
  res: ServerResponse,
  requestId: string,
  deviceId?: string
): { finish: (status: number, error?: string) => void } {
  const start = Date.now();
  const method = req.method ?? "UNKNOWN";
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  return {
    finish(status: number, error?: string) {
      logRequest({
        requestId,
        method,
        path: url.pathname,
        status,
        durationMs: Date.now() - start,
        deviceId,
        error,
      });
    },
  };
}

// ---------------------------------------------------------------------------
// Startup validation (ensure no secrets are empty in production)
// ---------------------------------------------------------------------------

export function validateStartupSecrets(): void {
  const required = ["LINKO_JWT_SECRET"];
  const production = process.env.NODE_ENV === "production";
  if (!production) return;

  for (const key of required) {
    const value = process.env[key];
    if (!value || value.length < 32) {
      // Deliberately do NOT log the value
      throw new Error(`Startup validation failed: ${key} is missing or too short (min 32 chars). Set this environment variable.`);
    }
  }

  // Log startup (without any secret values)
  console.log(JSON.stringify({
    ts: new Date().toISOString(),
    level: "info",
    message: "Linko control plane startup validation passed",
    production: true,
    nodeVersion: process.version,
  }));
}
