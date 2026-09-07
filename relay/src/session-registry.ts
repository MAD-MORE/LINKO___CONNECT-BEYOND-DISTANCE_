import { createHash, timingSafeEqual } from 'node:crypto';
import type { Socket, RemoteInfo } from 'node:dgram';

export type PeerRole = 'provider' | 'receiver';

export interface Peer {
  role: PeerRole;
  address: string;
  port: number;
  lastSeen: number;
}

export interface RelaySession {
  id: string;
  tokenHash: string;
  createdAt: number;
  lastSeen: number;
  provider?: Peer;
  receiver?: Peer;
  bytesForwarded: number;
  packetsForwarded: number;
}

const hashToken = (token: string) => createHash('sha256').update(token).digest('hex');

export class SessionRegistry {
  private readonly sessions = new Map<string, RelaySession>();
  constructor(private readonly ttlMs = 2 * 60 * 1000, private readonly maxSessions = 1000) {}

  register(id: string, token: string): RelaySession {
    if (!/^[A-Za-z0-9_-]{8,128}$/.test(id) || token.length < 16) throw new Error('invalid_session_credentials');
    const existing = this.sessions.get(id);
    if (existing) {
      existing.lastSeen = Date.now();
      if (!this.verify(existing, token)) throw new Error('invalid_session_token');
      return existing;
    }
    if (this.sessions.size >= this.maxSessions) throw new Error('relay_capacity_reached');
    const now = Date.now();
    const session: RelaySession = { id, tokenHash: hashToken(token), createdAt: now, lastSeen: now, bytesForwarded: 0, packetsForwarded: 0 };
    this.sessions.set(id, session);
    return session;
  }

  authenticate(id: string, token: string): RelaySession | undefined {
    const session = this.sessions.get(id);
    if (!session || Date.now() - session.lastSeen > this.ttlMs || !this.verify(session, token)) return undefined;
    session.lastSeen = Date.now();
    return session;
  }

  bind(session: RelaySession, role: PeerRole, remote: RemoteInfo): void {
    const peer = { role, address: remote.address, port: remote.port, lastSeen: Date.now() };
    if (role === 'provider') session.provider = peer;
    else session.receiver = peer;
    session.lastSeen = Date.now();
  }

  peerFor(session: RelaySession, role: PeerRole): Peer | undefined {
    return role === 'provider' ? session.receiver : session.provider;
  }

  touch(session: RelaySession, role: PeerRole, remote: RemoteInfo): void {
    const peer = role === 'provider' ? session.provider : session.receiver;
    if (peer && peer.address === remote.address && peer.port === remote.port) peer.lastSeen = Date.now();
    session.lastSeen = Date.now();
  }

  removeExpired(now = Date.now()): number {
    let removed = 0;
    for (const [id, session] of this.sessions) {
      if (now - session.lastSeen > this.ttlMs) {
        this.sessions.delete(id);
        removed++;
      }
    }
    return removed;
  }

  size(): number { return this.sessions.size; }
  values(): IterableIterator<RelaySession> { return this.sessions.values(); }

  private verify(session: RelaySession, token: string): boolean {
    const actual = Buffer.from(hashToken(token));
    const expected = Buffer.from(session.tokenHash);
    return actual.length === expected.length && timingSafeEqual(actual, expected);
  }
}
