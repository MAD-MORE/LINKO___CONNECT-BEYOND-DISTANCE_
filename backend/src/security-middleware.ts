import type { IncomingMessage, ServerResponse } from "node:http";

/**
 * Security middleware for the Linko control plane.
 *
 * - Enforces request body size limits
 * - Sets secure HTTP response headers
 * - Validates Content-Type on POST/PUT/PATCH
 * - Never logs Authorization headers or secrets
 */

const MAX_BODY_BYTES = 64 * 1024; // 64 KB — more than enough for all API payloads

/**
 * Apply secure response headers to every response.
 */
export function applySecurityHeaders(res: ServerResponse): void {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("X-XSS-Protection", "0"); // Modern browsers: disable legacy XSS filter (CSP is better)
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("Content-Security-Policy", "default-src 'none'");
  res.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
  res.setHeader("Cache-Control", "no-store");
  // CORS: allow requests from the Linko Android app (any origin is fine for a mobile API)
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-Id, X-Linko-Bootstrap");
  res.setHeader("Access-Control-Max-Age", "86400");
}

/**
 * Read and validate request body with size limit.
 * Returns parsed JSON or throws.
 */
export async function safeBody(
  req: IncomingMessage
): Promise<Record<string, unknown>> {
  const contentType = req.headers["content-type"] ?? "";
  if (!contentType.includes("application/json")) {
    throw Object.assign(new Error("content_type_must_be_json"), { status: 415 });
  }

  let totalBytes = 0;
  const chunks: Buffer[] = [];

  for await (const chunk of req) {
    totalBytes += chunk.length;
    if (totalBytes > MAX_BODY_BYTES) {
      throw Object.assign(new Error("request_body_too_large"), { status: 413 });
    }
    chunks.push(Buffer.from(chunk));
  }

  if (chunks.length === 0) return {};

  try {
    const value: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      throw new Error("invalid_json_body");
    }
    return value as Record<string, unknown>;
  } catch {
    throw Object.assign(new Error("invalid_json_body"), { status: 400 });
  }
}

/**
 * Sanitize a value for safe logging (redact sensitive keys).
 * Never logs Authorization, password, secret, key, token.
 */
const SENSITIVE_KEYS = new Set([
  "authorization", "password", "secret", "key", "token",
  "access_token", "refresh_token", "jwt", "database_url",
]);

export function sanitizeForLog(obj: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(obj)) {
    if (SENSITIVE_KEYS.has(k.toLowerCase())) {
      result[k] = "[REDACTED]";
    } else if (v && typeof v === "object" && !Array.isArray(v)) {
      result[k] = sanitizeForLog(v as Record<string, unknown>);
    } else {
      result[k] = v;
    }
  }
  return result;
}

/**
 * Validate that a string is a plausible UUID v4.
 */
export function isValidUuid(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
  );
}

/**
 * Validate that a string is a safe non-empty identifier (alphanumeric + hyphens/underscores).
 */
export function isSafeId(value: unknown, maxLen = 128): value is string {
  return typeof value === "string" && value.length > 0 && value.length <= maxLen && /^[\w-]+$/.test(value);
}
