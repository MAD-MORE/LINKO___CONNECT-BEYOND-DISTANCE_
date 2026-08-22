import { randomUUID } from 'node:crypto';
import http from 'node:http';

const requests = new Map();
const sessions = new Map();
const TTL_MS = 5 * 60 * 1000;

function json(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store' });
  res.end(JSON.stringify(body));
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', chunk => { raw += chunk; if (raw.length > 65536) reject(new Error('payload too large')); });
    req.on('end', () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch { reject(new Error('invalid json')); } });
    req.on('error', reject);
  });
}
function fresh(expiresAt) { return Date.now() < expiresAt; }

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    if (req.method === 'GET' && url.pathname === '/health') return json(res, 200, { service: 'linko-signaling', status: 'ok' });

    const parts = url.pathname.split('/').filter(Boolean);
    if (req.method === 'POST' && url.pathname === '/v1/connections/request') {
      const body = await readBody(req);
      if (!body.receiverId || !body.providerId) return json(res, 400, { error: 'receiverId and providerId are required' });
      const now = Date.now();
      const request = { id: randomUUID(), receiverId: body.receiverId, providerId: body.providerId, status: 'pending', createdAt: new Date(now).toISOString(), expiresAt: new Date(now + TTL_MS).toISOString() };
      requests.set(request.id, { ...request, expiresMs: now + TTL_MS });
      return json(res, 201, request);
    }

    if (parts[0] === 'v1' && parts[1] === 'connections' && parts[2]) {
      const id = parts[2]; const request = requests.get(id);
      if (!request || !fresh(request.expiresMs)) return json(res, 404, { error: 'request not found or expired' });
      if (req.method === 'GET' && parts.length === 3) return json(res, 200, request);
      if (req.method === 'POST' && parts[3] === 'approve') { request.status = 'approved'; return json(res, 200, request); }
      if (req.method === 'POST' && parts[3] === 'deny') { request.status = 'denied'; return json(res, 200, request); }
    }

    if (req.method === 'POST' && parts[0] === 'v1' && parts[1] === 'sessions' && parts[2] === 'negotiate') {
      const request = requests.get(parts[2]);
      if (!request || request.status !== 'approved' || !fresh(request.expiresMs)) return json(res, 409, { error: 'request must be approved and active' });
      const now = Date.now();
      const session = { id: randomUUID(), requestId: request.id, receiverId: request.receiverId, providerId: request.providerId, transport: 'pending', expiresAt: new Date(now + TTL_MS).toISOString() };
      sessions.set(session.id, { ...session, expiresMs: now + TTL_MS });
      return json(res, 201, session);
    }

    if (req.method === 'DELETE' && parts[0] === 'v1' && parts[1] === 'sessions' && parts[2]) {
      if (!sessions.delete(parts[2])) return json(res, 404, { error: 'session not found' });
      return json(res, 200, { closed: true });
    }

    return json(res, 404, { error: 'not found' });
  } catch (error) { return json(res, 400, { error: error.message }); }
});

server.listen(Number(process.env.PORT || 8080), '0.0.0.0', () => console.log('LINKO signaling listening on port ' + (process.env.PORT || 8080)));