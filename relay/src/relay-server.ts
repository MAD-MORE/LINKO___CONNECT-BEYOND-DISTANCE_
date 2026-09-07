import dgram from 'node:dgram';
import http from 'node:http';
import { SessionRegistry, type PeerRole } from './session-registry.js';
import { health } from './health.js';

const PORT = Number(process.env.RELAY_PORT ?? 3479);
const HEALTH_PORT = Number(process.env.HEALTH_PORT ?? 8080);
const MAX_PACKET = Number(process.env.MAX_PACKET_BYTES ?? 65535);
const MAX_SESSIONS = Number(process.env.MAX_SESSIONS ?? 1000);
const SESSION_TTL = Number(process.env.SESSION_TTL_MS ?? 120000);
const ADMIN_TOKEN = process.env.RELAY_ADMIN_TOKEN ?? '';

// Control frames:
// HELLO|sessionId|token|role
// PING|sessionId|token|role
// DATA|sessionId|token|role|<opaque encrypted payload>
//
// LINKO v2 encrypted tunnel frames are also accepted directly after HELLO.
// They already contain the session id and sender role in their authenticated header,
// so the relay can forward them unchanged without wrapping or decrypting them.
const LINKO_MAGIC = Buffer.from([0x4c, 0x4b, 0x4f, 0x32]);
const LINKO_VERSION = 0x02;
const LINKO_SESSION_OFFSET = 5;
const LINKO_SESSION_LEN = 36;
const LINKO_ROLE_OFFSET = 73;
const LINKO_HEADER_LEN = 95;
const LINKO_TAG_LEN = 16;
const registry = new SessionRegistry(SESSION_TTL, MAX_SESSIONS);
const startedAt = Date.now();
const socket = dgram.createSocket('udp4');

function splitControl(packet: Buffer): { command: string; sessionId: string; token: string; role: PeerRole; payload?: Buffer } | null {
  const separator = Buffer.from('|');
  const first = packet.indexOf(separator);
  if (first < 0) return null;
  const second = packet.indexOf(separator, first + 1);
  const third = packet.indexOf(separator, second + 1);
  const fourth = packet.indexOf(separator, third + 1);
  if (second < 0 || third < 0) return null;
  const command = packet.subarray(0, first).toString();
  const sessionId = packet.subarray(first + 1, second).toString();
  const token = packet.subarray(second + 1, third).toString();
  const role = packet.subarray(third + 1, fourth < 0 ? packet.length : fourth).toString() as PeerRole;
  if (role !== 'provider' && role !== 'receiver') return null;
  return { command, sessionId, token, role, payload: fourth < 0 ? undefined : packet.subarray(fourth + 1) };
}

function frame(command: string, sessionId: string, payload = Buffer.alloc(0)): Buffer {
  return Buffer.concat([Buffer.from(`${command}|${sessionId}|`), payload]);
}

function parseLinkoFrame(packet: Buffer): { sessionId: string; role: PeerRole } | null {
  if (packet.length < LINKO_HEADER_LEN + LINKO_TAG_LEN) return null;
  if (!packet.subarray(0, LINKO_MAGIC.length).equals(LINKO_MAGIC)) return null;
  if (packet[4] !== LINKO_VERSION) return null;
  const sessionId = packet.subarray(LINKO_SESSION_OFFSET, LINKO_SESSION_OFFSET + LINKO_SESSION_LEN).toString('ascii');
  if (!/^[0-9a-fA-F-]{36}$/.test(sessionId)) return null;
  const roleCode = packet[LINKO_ROLE_OFFSET];
  const role = roleCode === 1 ? 'provider' : roleCode === 2 ? 'receiver' : null;
  if (!role) return null;
  return { sessionId, role };
}

socket.on('message', (packet, remote) => {
  if (packet.length > MAX_PACKET) return;

  const linkoFrame = parseLinkoFrame(packet);
  if (linkoFrame) {
    const session = registry.get(linkoFrame.sessionId);
    if (!session || !registry.isBoundTo(session, linkoFrame.role, remote)) return;
    registry.touch(session, linkoFrame.role, remote);
    const destination = registry.peerFor(session, linkoFrame.role);
    if (!destination) return;
    session.bytesForwarded += packet.length;
    session.packetsForwarded++;
    // Opaque AES-GCM tunnel frame: forward byte-for-byte. The relay never decrypts it.
    socket.send(packet, destination.port, destination.address);
    return;
  }

  const message = splitControl(packet);
  if (!message) return;

  try {
    const session = message.command === 'HELLO'
      ? registry.register(message.sessionId, message.token)
      : registry.authenticate(message.sessionId, message.token);
    if (!session) return socket.send(frame('ERROR', message.sessionId, Buffer.from('unauthorized')),
      remote.port, remote.address);

    registry.touch(session, message.role, remote);
    if (message.command === 'HELLO' || message.command === 'PING') {
      registry.bind(session, message.role, remote);
      socket.send(frame('OK', message.sessionId), remote.port, remote.address);
      return;
    }

    if (message.command !== 'DATA' || !message.payload) return;
    registry.bind(session, message.role, remote);
    const destination = registry.peerFor(session, message.role);
    if (!destination) return;
    session.bytesForwarded += message.payload.length;
    session.packetsForwarded++;
    // The relay never decrypts or modifies the opaque tunnel payload.
    socket.send(message.payload, destination.port, destination.address);
  } catch {
    socket.send(frame('ERROR', message.sessionId, Buffer.from('rejected')), remote.port, remote.address);
  }
});

socket.on('error', (error) => console.error('[LINKO_RELAY] UDP error', error));
socket.bind(PORT, '0.0.0.0', () => console.log(`[LINKO_RELAY] UDP listening on :${PORT}`));

setInterval(() => registry.removeExpired(), Math.max(5000, Math.min(30000, SESSION_TTL / 2))).unref();

const server = http.createServer((req, res) => {
  if (req.url !== '/healthz' && req.url !== '/metrics') {
    res.writeHead(404); res.end(); return;
  }
  if (req.url === '/metrics' && ADMIN_TOKEN && req.headers.authorization !== `Bearer ${ADMIN_TOKEN}`) {
    res.writeHead(401); res.end(); return;
  }
  const payload = JSON.stringify(health(registry.values(), MAX_SESSIONS, startedAt));
  res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
  res.end(payload);
});
server.listen(HEALTH_PORT, '0.0.0.0', () => console.log(`[LINKO_RELAY] health listening on :${HEALTH_PORT}`));

function shutdown(signal: string) {
  console.log(`[LINKO_RELAY] ${signal}; shutting down`);
  socket.close();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 5000).unref();
}
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
