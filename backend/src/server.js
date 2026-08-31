const http = require('node:http');
const { createHash } = require('node:crypto');
const { ApiError, ControlPlane, safeEqual } = require('./domain');
const database = require('./db');
const { issueTurnCredentials, parseTurnUrls } = require('./turn');

const MAX_BODY_BYTES = 5 * 1024;
const MAX_WS_BYTES = 5 * 1024;
const WINDOW_MS = 60_000;

function json(response, status, body) { response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }); response.end(JSON.stringify(body)); }
function error(response, error) { json(response, error.status || 500, { error: { code: error.code || 'INTERNAL', message: error.message || 'Unexpected error.' } }); }
function readJson(request) { return new Promise((resolve, reject) => { let size = 0; let body = ''; request.on('data', chunk => { size += chunk.length; if (size > MAX_BODY_BYTES) { reject(new ApiError(413, 'PAYLOAD_TOO_LARGE', 'Payload exceeds 5 KB.')); request.destroy(); } else body += chunk; }); request.on('end', () => { try { resolve(body ? JSON.parse(body) : {}); } catch { reject(new ApiError(400, 'INVALID_JSON', 'Request body must be valid JSON.')); } }); request.on('error', reject); }); }
function bearer(request) { const value = request.headers.authorization; if (!value?.startsWith('Bearer ')) throw new ApiError(401, 'TOKEN_MISSING', 'Authentication is required.'); return value.slice(7); }
function clientAddress(request) { return request.socket.remoteAddress || 'unknown'; }
function sessionDto(session, relayUrl, turnCredentials) { return { sessionId: session.id, hostPublicKey: session.hostPublicKey, relayUrl, expiresAtEpochSeconds: Math.floor(session.expiresAt / 1000), turnCredentials }; }
function hostSessionDto(session, turnCredentials) { return { sessionId: session.id, clientPublicKey: session.clientPublicKey, allowedUntilEpochSeconds: Math.floor(session.expiresAt / 1000), turnCredentials }; }

class RateLimiter { constructor() { this.entries = new Map(); } check(key, maximum = 30) { const now = Date.now(); const entry = this.entries.get(key) || { startedAt: now, count: 0 }; if (now - entry.startedAt >= WINDOW_MS) { entry.startedAt = now; entry.count = 0; } entry.count += 1; this.entries.set(key, entry); if (entry.count > maximum) throw new ApiError(429, 'RATE_LIMITED', 'Too many requests.'); } }

function createServer({ controlPlane = new ControlPlane(), relayUrl = null, enrollmentToken = process.env.LINKSHARE_ENROLLMENT_TOKEN, turnSharedSecret = process.env.TURN_SHARED_SECRET, turnUrls = parseTurnUrls(process.env.TURN_URLS) } = {}) {
  const sockets = new Map(); const limiter = new RateLimiter();
  const publish = (deviceId, event, data) => { const socket = sockets.get(deviceId); if (socket) sendWs(socket, JSON.stringify({ version: 1, event, data })); };
  const turnCredentialsFor = (session, deviceId) => issueTurnCredentials({ sessionId: session.id, deviceId, sharedSecret: turnSharedSecret, urls: turnUrls });
  const server = http.createServer(async (request, response) => {
    try {
      if (request.method === 'GET' && request.url === '/healthz') return json(response, 200, { status: 'ok' });
      if (!request.url.startsWith('/v1/')) throw new ApiError(404, 'NOT_FOUND', 'Route was not found.');
      limiter.check(`${clientAddress(request)}:${request.url.split('?')[0]}`);
      if (request.method === 'POST' && request.url === '/v1/devices/register') {
        if (!enrollmentToken || !safeEqual(request.headers['x-enrollment-token'] || '', enrollmentToken)) throw new ApiError(401, 'ENROLLMENT_DENIED', 'Device enrollment is not authorized.');
        const body = await readJson(request);
        if (!/^[a-zA-Z0-9_-]{3,80}$/.test(body.deviceId || '') || typeof body.publicKey !== 'string' || body.publicKey.length < 16 || body.publicKey.length > 512) throw new ApiError(400, 'INVALID_DEVICE', 'A valid device ID and public key are required.');
        const existing = controlPlane.database.getDeviceById(body.deviceId);
        if (existing && !safeEqual(existing.public_key, body.publicKey)) throw new ApiError(409, 'DEVICE_KEY_CONFLICT', 'Device is already registered with a different key.');
        if (!existing) controlPlane.database.insertDevice(body.deviceId, body.publicKey);
        return json(response, 201, controlPlane.issueToken(body.deviceId));
      }
      const token = bearer(request); const deviceId = controlPlane.authenticate(token);
      if (request.method === 'GET' && request.url === '/v1/friends') return json(response, 200, controlPlane.friendsFor(deviceId));
      if (request.method === 'POST' && request.url === '/v1/auth/revoke') { controlPlane.revokeToken(token); return json(response, 204, {}); }
      if (request.method === 'POST' && request.url === '/v1/connection-requests') {
        const pending = controlPlane.requestAccess(deviceId, await readJson(request));
        publish(pending.hostId, 'connection.requested', { id: pending.id, friendName: deviceId, initials: deviceId.slice(0, 2).toUpperCase(), deviceName: 'Android device', distanceLabel: 'Remote', requestedAtLabel: 'Now' });
        return json(response, 202, { requestId: pending.id, state: pending.state });
      }
      const approveMatch = request.url.match(/^\/v1\/connection-requests\/([^/]+)\/approve$/);
      if (request.method === 'POST' && approveMatch) { const session = controlPlane.approveRequest(deviceId, approveMatch[1]); publish(session.clientId, 'session.approved', sessionDto(session, relayUrl, turnCredentialsFor(session, session.clientId))); return json(response, 200, hostSessionDto(session, turnCredentialsFor(session, deviceId))); }
      const denyMatch = request.url.match(/^\/v1\/connection-requests\/([^/]+)\/deny$/);
      if (request.method === 'POST' && denyMatch) { const pending = controlPlane.denyRequest(deviceId, denyMatch[1]); publish(pending.clientId, 'session.denied', { requestId: pending.id, state: 'failed' }); return json(response, 204, {}); }
      const turnCredentialsMatch = request.url.match(/^\/v1\/sessions\/([^/]+)\/turn-credentials$/);
      if (request.method === 'POST' && turnCredentialsMatch) { limiter.check(`${deviceId}:turn-credentials`, 10); const session = controlPlane.sessionForParticipant(deviceId, turnCredentialsMatch[1]); if (!['handshaking', 'connected'].includes(session.state)) throw new ApiError(409, 'SESSION_NOT_ACTIVE', 'TURN credentials require an active session.'); const turnCredentials = turnCredentialsFor(session, deviceId); if (!turnCredentials) throw new ApiError(503, 'TURN_UNAVAILABLE', 'TURN is not configured.'); return json(response, 200, turnCredentials); }
      const stateMatch = request.url.match(/^\/v1\/sessions\/([^/]+)\/state$/);
      if (request.method === 'POST' && stateMatch) { const body = await readJson(request); if (body.path && !['direct', 'relay'].includes(body.path)) throw new ApiError(400, 'INVALID_TRANSPORT_PATH', 'Transport path must be direct or relay.'); const session = controlPlane.setSessionState(deviceId, stateMatch[1], body.state, body.path); const peerId = session.hostId === deviceId ? session.clientId : session.hostId; console.info(JSON.stringify({ event: 'connection_path_reported', sessionId: session.id, deviceId, state: session.state, path: session.path, at: new Date().toISOString() })); publish(peerId, 'session.state', { sessionId: session.id, state: session.state, path: session.path }); return json(response, 200, { sessionId: session.id, state: session.state, path: session.path }); }
      throw new ApiError(404, 'NOT_FOUND', 'Route was not found.');
    } catch (caught) { error(response, caught); }
  });
  server.on('upgrade', (request, socket) => {
    try {
      if (request.url !== '/v1/signaling') throw new ApiError(404, 'NOT_FOUND', 'Route was not found.');
      limiter.check(`${clientAddress(request)}:websocket`, 10); const deviceId = controlPlane.authenticate(bearer(request));
      const key = request.headers['sec-websocket-key']; if (!key || request.headers['sec-websocket-version'] !== '13') throw new ApiError(400, 'INVALID_WEBSOCKET', 'WebSocket handshake is invalid.');
      const accept = createHash('sha1').update(`${key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11`).digest('base64');
      socket.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
      sockets.get(deviceId)?.end(); sockets.set(deviceId, socket); socket.on('data', buffer => handleWs(buffer, deviceId, controlPlane, publish)); socket.on('close', () => { if (sockets.get(deviceId) === socket) sockets.delete(deviceId); }); socket.on('error', () => socket.destroy());
      sendWs(socket, JSON.stringify({ version: 1, event: 'ready', data: { deviceId } }));
    } catch (caught) { socket.write(`HTTP/1.1 ${(caught.status || 401)} Unauthorized\r\nConnection: close\r\n\r\n`); socket.destroy(); }
  });
  return { server, controlPlane };
}
function sendWs(socket, text) { const data = Buffer.from(text); if (data.length > MAX_WS_BYTES) throw new Error('Outbound WebSocket payload exceeds 5 KB.'); const header = data.length < 126 ? Buffer.from([0x81, data.length]) : Buffer.from([0x81, 126, data.length >> 8, data.length & 0xff]); socket.write(Buffer.concat([header, data])); }
function handleWs(buffer, deviceId, controlPlane, publish) { try { if (buffer.length > MAX_WS_BYTES + 8 || (buffer[0] & 0x0f) !== 1) throw new ApiError(400, 'INVALID_SIGNAL', 'Invalid signaling message.'); const frameLength = buffer[1] & 0x7f; const lengthBytes = frameLength === 126 ? 2 : frameLength === 127 ? 8 : 0; if (frameLength === 127) throw new ApiError(400, 'INVALID_SIGNAL', 'Signaling message is too large.'); const payloadSize = lengthBytes ? buffer.readUInt16BE(2) : frameLength; const maskOffset = 2 + lengthBytes; if (!(buffer[1] & 0x80) || payloadSize > MAX_WS_BYTES || buffer.length !== maskOffset + 4 + payloadSize) throw new ApiError(400, 'INVALID_SIGNAL', 'Invalid signaling frame.'); const mask = buffer.subarray(maskOffset, maskOffset + 4); const data = Buffer.alloc(payloadSize); for (let index = 0; index < payloadSize; index += 1) data[index] = buffer[maskOffset + 4 + index] ^ mask[index % 4]; const message = JSON.parse(data); if (message.version !== 1 || message.event !== 'session.candidate' || typeof message.data?.sessionId !== 'string') throw new ApiError(400, 'INVALID_SIGNAL', 'Invalid signaling message.'); const session = controlPlane.sessionForParticipant(deviceId, message.data.sessionId); if (session.state !== 'handshaking') throw new ApiError(409, 'SESSION_NOT_HANDSHAKING', 'Session cannot accept candidates.'); const peerId = session.hostId === deviceId ? session.clientId : session.hostId; publish(peerId, 'session.candidate', { sessionId: session.id, candidate: message.data.candidate }); } catch { /* Invalid peer messages are intentionally ignored; no tunnel data is accepted here. */ } }

function seedDemo(controlPlane) { const store = controlPlane.database; if (!store.getDeviceById('nora')) store.insertDevice('nora', 'demo-host-public-key'); if (!store.getDeviceById('client-demo')) store.insertDevice('client-demo', 'demo-client-public-key'); store.createFriendRequest('client-demo', 'nora'); store.updateFriendStatus('client-demo', 'nora', 'accepted'); store.createFriendRequest('nora', 'client-demo'); store.updateFriendStatus('nora', 'client-demo', 'accepted'); }
if (require.main === module) { const controlPlane = new ControlPlane(database); const turnUrls = parseTurnUrls(process.env.TURN_URLS); seedDemo(controlPlane); const { server } = createServer({ controlPlane, relayUrl: process.env.LINKSHARE_RELAY_URL || turnUrls[0] || null, turnUrls }); server.listen(Number(process.env.PORT || 8080), () => console.log('LinkShare control plane listening on port ' + (process.env.PORT || 8080))); }
module.exports = { createServer, seedDemo };
