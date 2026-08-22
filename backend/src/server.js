import { randomUUID } from 'node:crypto';
import http from 'node:http';

const requests = new Map();
const sessions = new Map();
const TTL_MS = 5 * 60 * 1000;
const API_TOKEN = process.env.LINKO_API_TOKEN || '';

function json(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store' });
  res.end(JSON.stringify(body));
}
function authorized(req) {
  if (!API_TOKEN) return process.env.NODE_ENV !== 'production';
  return req.headers.authorization === `Bearer ${API_TOKEN}`;
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
function cleanup() {
  const now = Date.now();
  for (const [id, value] of requests) if (value.expiresMs <= now) requests.delete(id);
  for (const [id, value] of sessions) if (value.expiresMs <= now) sessions.delete(id);
}
setInterval(cleanup, 30_000).unref();

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    if (req.method === 'GET' && url.pathname === '/health') return json(res, 200, { service: 'linko-signaling', status: 'ok' });
    if (!authorized(req)) return json(res, 401, { error: 'unauthorized' });

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
      const request = requests.get(parts[2]);
      if (!request || !fresh(request.expiresMs)) return json(res, 404, { error: 'request not found or expired' });
      if (req.method === 'GET' && parts.length === 3) return json(res, 200, request);
      if (req.method === 'POST' && parts[3] === 'approve') { request.status = 'approved'; return json(res, 200, request); }
      if (req.method === 'POST' && parts[3] === 'deny') { request.status = 'denied'; return json(res, 200, request); }
      if (req.method === 'POST' && parts[3] === 'session') {
        if (request.status !== 'approved') return json(res, 409, { error: 'request is not approved' });
        const now = Date.now();
        const session = { id: randomUUID(), requestId: request.id, receiverId: request.receiverId, providerId: request.providerId, transport: 'pending', expiresAt: new Date(now + TTL_MS).toISOString() };
        sessions.set(session.id, { ...session, expiresMs: now + TTL_MS });
        return json(res, 201, session);
      }
    }

    if (parts[0] === 'v1' && parts[1] === 'sessions' && parts[2]) {
      const session = sessions.get(parts[2]);
      if (!session || !fresh(session.expiresMs)) return json(res, 404, { error: 'session not found or expired' });
      if (req.method === 'POST' && parts[3] === 'negotiate') {
        const body = await readBody(req);
        if (!body.type || !body.payload) return json(res, 400, { error: 'type and payload are required' });
        return json(res, 200, { accepted: true, sessionId: session.id, type: body.type, payload: body.payload });
      }
      if (req.method === 'DELETE' && parts.length === 3) {
        sessions.delete(parts[2]);
        return json(res, 200, { closed: true });
      }
    }

    return json(res, 404, { error: 'not found' });
  } catch (error) { return json(res, 400, { error: error.message }); }
});

server.listen(Number(process.env.PORT || 8080), '0.0.0.0', () => console.log('LINKO signaling listening on port ' + (process.env.PORT || 8080)));