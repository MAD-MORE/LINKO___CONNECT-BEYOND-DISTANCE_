const { randomBytes, timingSafeEqual } = require('node:crypto');
const repository = require('./db');

const PHASES = new Set(['requesting', 'handshaking', 'connected', 'failed', 'ended']);
const SESSION_TTL_MS = 15 * 60 * 1000;

class ApiError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

function toSession(row) {
  if (!row) return null;
  return { id: String(row.id), hostId: row.host_device_id, clientId: row.client_device_id, state: row.status, startedAt: row.started_at, endedAt: row.ended_at, bytesUsed: row.bytes_used };
}

class ControlPlane {
  constructor(database = repository, now = () => Date.now()) { this.database = database; this.now = now; }
  issueToken(deviceId) {
    if (!this.database.getDeviceById(deviceId)) throw new ApiError(401, 'DEVICE_UNKNOWN', 'Device is not registered.');
    const value = randomBytes(32).toString('base64url');
    const expiresAt = new Date(this.now() + SESSION_TTL_MS).toISOString().replace('T', ' ').replace(/\.\d{3}Z$/, '');
    this.database.createToken(deviceId, value, expiresAt);
    return { accessToken: value, expiresAtEpochSeconds: Math.floor(new Date(expiresAt).getTime() / 1000) };
  }
  authenticate(value) {
    const token = this.database.isTokenValid(value);
    if (!token) throw new ApiError(401, 'TOKEN_INVALID', 'Authentication is required.');
    return token.device_id;
  }
  revokeToken(value) { this.database.revokeToken(value); }
  friendsFor(deviceId) {
    return this.database.getFriendsForUser(deviceId).flatMap(({ friend_id: friendId }) => {
      const friend = this.database.getDeviceById(friendId);
      if (!friend) return [];
      return [{ id: friend.device_id, name: friend.device_id, initials: friend.device_id.slice(0, 2).toUpperCase(), cityHint: 'Remote', trustNote: 'Available when they approve', isSharing: true, accentHex: 4284927925 }];
    });
  }
  requestAccess(clientId, { hostId, clientPublicKey }) {
    const relationship = this.database.getFriendship(clientId, hostId);
    if (!relationship || relationship.status !== 'accepted') throw new ApiError(403, 'HOST_NOT_AUTHORIZED', 'The selected host is not an authorized friend.');
    const host = this.database.getDeviceById(hostId);
    const client = this.database.getDeviceById(clientId);
    if (!host || !client) throw new ApiError(409, 'HOST_UNAVAILABLE', 'The host is not available.');
    if (!clientPublicKey || clientPublicKey.length > 512 || !safeEqual(client.public_key, clientPublicKey)) throw new ApiError(400, 'INVALID_PUBLIC_KEY', 'The request public key must match the registered client device key.');
    const result = this.database.createSession(hostId, clientId);
    return { id: String(result.lastInsertRowid), clientId, hostId, state: 'requesting' };
  }
  approveRequest(hostId, requestId) {
    const session = this.sessionForParticipant(hostId, requestId);
    if (session.hostId !== hostId || session.state !== 'requesting') throw new ApiError(404, 'REQUEST_NOT_FOUND', 'Connection request was not found.');
    this.database.updateSessionStatus(session.id, 'handshaking');
    const host = this.database.getDeviceById(hostId);
    const client = this.database.getDeviceById(session.clientId);
    return { ...this.sessionForParticipant(hostId, session.id), hostPublicKey: host.public_key, clientPublicKey: client.public_key, expiresAt: this.now() + SESSION_TTL_MS, path: 'direct' };
  }
  denyRequest(hostId, requestId) {
    const session = this.sessionForParticipant(hostId, requestId);
    if (session.hostId !== hostId || session.state !== 'requesting') throw new ApiError(404, 'REQUEST_NOT_FOUND', 'Connection request was not found.');
    this.database.updateSessionStatus(session.id, 'failed');
    return { ...session, state: 'failed' };
  }
  sessionForParticipant(deviceId, sessionId) {
    const session = toSession(this.database.getSessionForParticipant(sessionId, deviceId));
    if (!session) throw new ApiError(404, 'SESSION_NOT_FOUND', 'Session was not found.');
    return session;
  }
  setSessionState(deviceId, sessionId, state, path) {
    if (!PHASES.has(state)) throw new ApiError(400, 'INVALID_SESSION_STATE', 'Invalid connection state.');
    const session = this.sessionForParticipant(deviceId, sessionId);
    const transitions = { handshaking: ['connected', 'failed'], connected: ['failed', 'ended'] };
    if (!transitions[session.state]?.includes(state)) throw new ApiError(409, 'INVALID_SESSION_TRANSITION', 'Invalid session transition.');
    this.database.updateSessionStatus(session.id, state);
    return { ...this.sessionForParticipant(deviceId, session.id), path: path || 'direct' };
  }
}

function safeEqual(left, right) { const first = Buffer.from(left); const second = Buffer.from(right); return first.length === second.length && timingSafeEqual(first, second); }
module.exports = { ApiError, ControlPlane, PHASES, safeEqual };
