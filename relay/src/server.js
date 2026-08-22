import http from 'node:http';
import { WebSocketServer } from 'ws';

const token = process.env.LINKO_RELAY_TOKEN || '';
const maxFrame = 64 * 1024;
const peers = new Map();

function authorized(req) {
  return Boolean(token) && req.headers.authorization === `Bearer ${token}`;
}

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ service: 'linko-relay', status: 'ok' }));
  }
  res.writeHead(404);
  res.end();
});

const wss = new WebSocketServer({ server, path: '/v1/relay' });
wss.on('connection', (socket, req) => {
  if (!authorized(req)) return socket.close(1008, 'unauthorized');
  let peerId = null;
  let sessionId = null;

  socket.once('message', raw => {
    try {
      const hello = JSON.parse(raw.toString());
      if (typeof hello.peerId !== 'string' || typeof hello.sessionId !== 'string') throw new Error();
      peerId = hello.peerId;
      sessionId = hello.sessionId;
      if (!peers.has(sessionId)) peers.set(sessionId, new Map());
      peers.get(sessionId).set(peerId, socket);
      socket.send(JSON.stringify({ type: 'ready', sessionId }));
    } catch {
      socket.close(1003, 'invalid registration');
    }
  });

  socket.on('message', raw => {
    if (!peerId || !sessionId || raw.length > maxFrame) return socket.close(1009, 'frame too large');
    const sessionPeers = peers.get(sessionId);
    if (!sessionPeers) return;

    // After registration, relay frames are opaque binary ciphertext. The relay
    // deliberately does not decrypt, parse, or persist application traffic.
    if (Buffer.isBuffer(raw) || raw instanceof ArrayBuffer) {
      for (const [otherId, target] of sessionPeers) {
        if (otherId !== peerId && target.readyState === 1) target.send(raw, { binary: true });
      }
    }
  });

  socket.on('close', () => {
    if (!sessionId) return;
    const sessionPeers = peers.get(sessionId);
    sessionPeers?.delete(peerId);
    if (sessionPeers?.size === 0) peers.delete(sessionId);
  });
});

server.listen(Number(process.env.PORT || 8090), '0.0.0.0', () => {
  console.log(`LINKO relay listening on ${process.env.PORT || 8090}`);
});
