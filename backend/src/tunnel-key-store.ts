import { randomBytes } from "node:crypto";

const KEY_BYTES = 32;
const keys = new Map<string, Buffer>();

export function createSessionTunnelKey(sessionId: string): Buffer {
  const key = randomBytes(KEY_BYTES);
  keys.set(sessionId, key);
  return Buffer.from(key);
}

export function getSessionTunnelKey(sessionId: string): Buffer | undefined {
  const key = keys.get(sessionId);
  return key ? Buffer.from(key) : undefined;
}

export function hasSessionTunnelKey(sessionId: string): boolean {
  return keys.has(sessionId);
}

export function revokeSessionTunnelKey(sessionId: string): void {
  const key = keys.get(sessionId);
  if (key) key.fill(0);
  keys.delete(sessionId);
}
