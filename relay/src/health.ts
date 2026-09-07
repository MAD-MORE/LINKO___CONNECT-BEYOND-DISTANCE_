import type { RelaySession } from './session-registry.js';

export interface RelayHealth {
  status: 'healthy' | 'degraded';
  uptimeSeconds: number;
  activeSessions: number;
  maxSessions: number;
  bytesForwarded: number;
  packetsForwarded: number;
}

export function health(sessions: Iterable<RelaySession>, maxSessions: number, startedAt: number): RelayHealth {
  let activeSessions = 0, bytesForwarded = 0, packetsForwarded = 0;
  for (const session of sessions) {
    activeSessions++;
    bytesForwarded += session.bytesForwarded;
    packetsForwarded += session.packetsForwarded;
  }
  return {
    status: activeSessions >= maxSessions * 0.9 ? 'degraded' : 'healthy',
    uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000),
    activeSessions,
    maxSessions,
    bytesForwarded,
    packetsForwarded,
  };
}
