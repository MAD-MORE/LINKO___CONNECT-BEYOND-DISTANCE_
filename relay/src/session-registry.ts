/**
 * Session registry for the LINKO relay node.
 *
 * Maintains an in-memory map of active sessions:
 *   sessionId → { sessionId, keyHash, partyA: RemoteInfo, partyB: RemoteInfo, ... }
 *
 * ZERO-KNOWLEDGE SECURITY:
 *   The relay NEVER stores, receives, or processes private session keys or user credentials.
 *   Session ownership is validated using a 32-byte SHA-256 key hash computed by the client devices.
 */

import type { RemoteInfo } from "node:dgram";

export interface SessionEntry {
  sessionId: string;
  keyHash: string;        // SHA-256 hex of the session key (64 hex characters)
  partyA: RemoteInfo | null; // Provider endpoint
  partyB: RemoteInfo | null; // Client endpoint
  createdAt: number;
  lastActiveAt: number;
  expiresAt: number;
  bytesForwarded: number;
}

const DEFAULT_SESSION_TTL_MS = 4 * 60 * 60 * 1000; // 4 hours max session lifetime
const INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000;      // 30 minutes inactivity timeout

export class SessionRegistry {
  private sessions = new Map<string, SessionEntry>();
  private keyIndex = new Map<string, string>();
  private cleanupTimer: NodeJS.Timeout | null = null;

  constructor(private ttlMs = DEFAULT_SESSION_TTL_MS) {
    this.cleanupTimer = setInterval(() => this.cleanup(), 60 * 1000);
    if (this.cleanupTimer.unref) {
      this.cleanupTimer.unref();
    }
  }

  /**
   * Register a new session by its cryptographic key hash.
   * NEVER accepts raw private session keys.
   */
  addSession(sessionId: string, keyHash: string, customTtlMs?: number): SessionEntry {
    const cleanHash = keyHash.trim().toLowerCase();
    const now = Date.now();
    const entry: SessionEntry = {
      sessionId,
      keyHash: cleanHash,
      partyA: null,
      partyB: null,
      createdAt: now,
      lastActiveAt: now,
      expiresAt: now + (customTtlMs ?? this.ttlMs),
      bytesForwarded: 0,
    };

    this.sessions.set(sessionId, entry);
    this.keyIndex.set(cleanHash, sessionId);
    return entry;
  }

  /**
   * Remove a session.
   */
  removeSession(sessionId: string): boolean {
    const entry = this.sessions.get(sessionId);
    if (entry) {
      this.keyIndex.delete(entry.keyHash);
      this.sessions.delete(sessionId);
      return true;
    }
    return false;
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

  getByKeyHash(keyHash: string): SessionEntry | null {
    const cleanHash = keyHash.trim().toLowerCase();
    const sessionId = this.keyIndex.get(cleanHash);
    if (!sessionId) return null;
    return this.getById(sessionId);
  }

  /**
   * Register or update an endpoint for a session.
   * Role:
   *   1 = Provider (Party A)
   *   2 = Client (Party B)
   *   Other = Dynamic first-two assignment
   */
  registerEndpoint(sessionId: string, remote: RemoteInfo, role?: number): "a" | "b" | null {
    const entry = this.getById(sessionId);
    if (!entry) return null;

    entry.lastActiveAt = Date.now();
    const addr = `${remote.address}:${remote.port}`;

    if (role === 1) {
      // Explicit Provider
      entry.partyA = { ...remote };
      return "a";
    }

    if (role === 2) {
      // Explicit Client
      entry.partyB = { ...remote };
      return "b";
    }

    // Role-agnostic or fallback:
    if (!entry.partyA) {
      entry.partyA = { ...remote };
      return "a";
    }
    if (`${entry.partyA.address}:${entry.partyA.port}` === addr) {
      return "a";
    }

    if (!entry.partyB) {
      entry.partyB = { ...remote };
      return "b";
    }
    if (`${entry.partyB.address}:${entry.partyB.port}` === addr) {
      return "b";
    }

    // Dynamic endpoint roaming: if Party A or B reconnects from a new port/IP
    return null;
  }

  recordBytes(sessionId: string, bytes: number): void {
    const entry = this.sessions.get(sessionId);
    if (entry) {
      entry.bytesForwarded += bytes;
      entry.lastActiveAt = Date.now();
    }
  }

  getAll(): SessionEntry[] {
    return Array.from(this.sessions.values());
  }

  count(): number {
    return this.sessions.size;
  }

  cleanup(): void {
    const now = Date.now();
    for (const [id, entry] of this.sessions) {
      if (now > entry.expiresAt || (now - entry.lastActiveAt > INACTIVITY_TIMEOUT_MS && entry.bytesForwarded > 0)) {
        this.keyIndex.delete(entry.keyHash);
        this.sessions.delete(id);
      }
    }
  }

  destroy(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }
    this.sessions.clear();
    this.keyIndex.clear();
  }
}
