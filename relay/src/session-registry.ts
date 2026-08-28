/**
 * Session registry for the Linko relay node.
 *
 * Maintains an in-memory map of:
 *   sessionId → { key: Buffer, partyA: RemoteInfo, partyB: RemoteInfo, expiresAt }
 *
 * Keys are 32-byte AES-256-GCM session keys issued by the control plane.
 * The relay uses them only to verify session ownership (not to decrypt traffic).
 *
 * Sessions expire automatically after TTL.
 */

import type { RemoteInfo } from "node:dgram";

export interface SessionEntry {
  sessionId: string;
  keyHash: string;        // SHA-256 hex of the session key (used for fast lookup, never the key itself)
  partyA: RemoteInfo | null;
  partyB: RemoteInfo | null;
  createdAt: number;
  expiresAt: number;
  bytesForwarded: number;
}

const DEFAULT_SESSION_TTL_MS = 4 * 60 * 60 * 1000; // 4 hours max session lifetime

export class SessionRegistry {
  private sessions = new Map<string, SessionEntry>();
  // Map from keyHash to sessionId for fast key-based lookup on first packet
  private keyIndex = new Map<string, string>();

  constructor(private ttlMs = DEFAULT_SESSION_TTL_MS) {
    // Clean up expired sessions every 5 minutes
    setInterval(() => this.cleanup(), 5 * 60 * 1000).unref();
  }

  /**
   * Register a new session. Called by the control plane relay coordinator.
   * @param sessionId - Linko session UUID
   * @param keyBase64 - Base64url-encoded 32-byte session key
   */
  async addSession(sessionId: string, keyBase64: string): Promise<void> {
    const { createHash } = await import("node:crypto");
    const keyBytes = Buffer.from(keyBase64, "base64url");
    if (keyBytes.length !== 32) {
      throw new Error(`Invalid session key length: ${keyBytes.length}, expected 32`);
    }
    const keyHash = createHash("sha256").update(keyBytes).digest("hex");

    const entry: SessionEntry = {
      sessionId,
      keyHash,
      partyA: null,
      partyB: null,
      createdAt: Date.now(),
      expiresAt: Date.now() + this.ttlMs,
      bytesForwarded: 0,
    };

    this.sessions.set(sessionId, entry);
    this.keyIndex.set(keyHash, sessionId);
  }

  /**
   * Remove a session (called on revocation or expiry from control plane signal).
   */
  removeSession(sessionId: string): void {
    const entry = this.sessions.get(sessionId);
    if (entry) {
      this.keyIndex.delete(entry.keyHash);
      this.sessions.delete(sessionId);
    }
  }

  /**
   * Look up a session by its key hash.
   * Returns null if not found or expired.
   */
  getByKeyHash(keyHash: string): SessionEntry | null {
    const sessionId = this.keyIndex.get(keyHash);
    if (!sessionId) return null;
    const entry = this.sessions.get(sessionId);
    if (!entry) return null;
    if (Date.now() > entry.expiresAt) {
      this.removeSession(sessionId);
      return null;
    }
    return entry;
  }

  getById(sessionId: string): SessionEntry | null {
    const entry = this.sessions.get(sessionId);
    if (!entry) return null;
    if (Date.now() > entry.expiresAt) {
      this.removeSession(sessionId);
      return null;
    }
    return entry;
  }

  /**
   * Record that a packet arrived from a remote address.
   * The first two unique addresses to connect for a session become partyA and partyB.
   */
  registerEndpoint(sessionId: string, remote: RemoteInfo): "a" | "b" | null {
    const entry = this.sessions.get(sessionId);
    if (!entry) return null;

    const addr = `${remote.address}:${remote.port}`;

    if (!entry.partyA) {
      entry.partyA = remote;
      return "a";
    }
    if (`${entry.partyA.address}:${entry.partyA.port}` === addr) return "a";

    if (!entry.partyB) {
      entry.partyB = remote;
      return "b";
    }
    if (`${entry.partyB.address}:${entry.partyB.port}` === addr) return "b";

    return null; // Unknown third party — reject
  }

  recordBytes(sessionId: string, bytes: number): void {
    const entry = this.sessions.get(sessionId);
    if (entry) entry.bytesForwarded += bytes;
  }

  getAll(): SessionEntry[] {
    return Array.from(this.sessions.values());
  }

  count(): number {
    return this.sessions.size;
  }

  private cleanup(): void {
    const now = Date.now();
    for (const [id, entry] of this.sessions) {
      if (now > entry.expiresAt) {
        this.keyIndex.delete(entry.keyHash);
        this.sessions.delete(id);
      }
    }
  }
}
