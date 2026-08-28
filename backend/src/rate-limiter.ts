import type { IncomingMessage } from "node:http";

/**
 * Token-bucket rate limiter for Linko control plane.
 * Each "bucket" is keyed by a string identifier (device ID or IP).
 *
 * Algorithm: tokens are added at `refillRate` per `refillIntervalMs`.
 * A request costs 1 token. If no tokens remain, the request is rejected.
 */

interface Bucket {
  tokens: number;
  lastRefill: number;
}

interface RateLimitOptions {
  /** Maximum tokens in the bucket (burst capacity). */
  capacity: number;
  /** Tokens added per refill interval. */
  refillRate: number;
  /** How often (ms) tokens are refilled. */
  refillIntervalMs: number;
}

export class RateLimiter {
  private buckets = new Map<string, Bucket>();
  private readonly opts: RateLimitOptions;

  constructor(opts: RateLimitOptions) {
    this.opts = opts;
    // Prune old buckets every 5 minutes to prevent unbounded memory growth.
    setInterval(() => this.prune(), 5 * 60 * 1000).unref();
  }

  /**
   * Returns true if the request should be allowed, false if rate-limited.
   */
  allow(key: string): boolean {
    const now = Date.now();
    let bucket = this.buckets.get(key);
    if (!bucket) {
      bucket = { tokens: this.opts.capacity, lastRefill: now };
      this.buckets.set(key, bucket);
    }

    // Refill tokens based on elapsed time.
    const elapsed = now - bucket.lastRefill;
    const refills = Math.floor(elapsed / this.opts.refillIntervalMs);
    if (refills > 0) {
      bucket.tokens = Math.min(
        this.opts.capacity,
        bucket.tokens + refills * this.opts.refillRate
      );
      bucket.lastRefill += refills * this.opts.refillIntervalMs;
    }

    if (bucket.tokens < 1) return false;
    bucket.tokens -= 1;
    return true;
  }

  private prune() {
    const cutoff = Date.now() - 10 * 60 * 1000; // 10 minutes idle
    for (const [key, bucket] of this.buckets) {
      if (bucket.lastRefill < cutoff) this.buckets.delete(key);
    }
  }
}

// ---------------------------------------------------------------------------
// Pre-configured limiters
// ---------------------------------------------------------------------------

/** Sensitive auth endpoints: 10 attempts per 15 minutes. */
export const authLimiter = new RateLimiter({
  capacity: 10,
  refillRate: 10,
  refillIntervalMs: 15 * 60 * 1000,
});

/** Session creation: 30 per hour per device. */
export const sessionLimiter = new RateLimiter({
  capacity: 30,
  refillRate: 30,
  refillIntervalMs: 60 * 60 * 1000,
});

/** General API: 120 requests per minute per device. */
export const generalLimiter = new RateLimiter({
  capacity: 120,
  refillRate: 120,
  refillIntervalMs: 60 * 1000,
});

/** Presence heartbeat: 4 per minute per device (one every 15s). */
export const presenceLimiter = new RateLimiter({
  capacity: 4,
  refillRate: 4,
  refillIntervalMs: 60 * 1000,
});

/**
 * Extract the best available key from a request for rate limiting.
 * Prefers device ID from token; falls back to IP.
 */
export function rateLimitKey(req: IncomingMessage, deviceId?: string): string {
  if (deviceId) return `device:${deviceId}`;
  const forwarded = req.headers["x-forwarded-for"];
  const ip = Array.isArray(forwarded) ? forwarded[0] : forwarded?.split(",")[0];
  return `ip:${ip ?? "unknown"}`;
}
