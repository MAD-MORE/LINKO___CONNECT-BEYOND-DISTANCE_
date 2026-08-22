import http from 'node:http';
import { createStore } from './db.js';
import { attachRealtime } from './realtime.js';

const TTL_MS = 5 * 60 * 1000;
const API_TOKEN = process.env.LINKO_API_TOKEN || '';
const store = process.env.DATABASE_URL ? createStore() : null;

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
function requireStore() {
  if (!store) throw new Error('DATABASE_URL is required for persistent signaling');
  return store;
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    if (req.method === 'GET' && url.pathname === '/health') {
      return json(res, 200, { service: 'linko-signaling', status: 'ok', persistence: Boolean(store), realtime: true });
    }
    if (!authorized(req)) return json(res, 401, { error: 'unauthorized' });

    const parts = url.pathname.split('/').filter(Boolean);

    if (req.method === 'POST' && url.pathname === '/v1/connections/request') {
      const body = await readBody(req);
      if (!body.receiverId || !body.providerId) return json(res, 400, { error: 'receiverId and providerId are required' });
      const expiresAt = new Date(Date.now() + TTL_MS).toISOString();
      return json(res, 201, await requireStore().createRequest({ receiverId: body.receiverId, providerId: body.providerId, expiresAt }));
    }

    if (req.method === 'GET' && parts[0] === 'v1' && parts[1] === 'providers' && parts[2] === 'requests' && parts[3] === 'pending' && parts[2]) {
      return json(res, 200, { items: await requireStore().listPendingRequests(parts[2]) });
    }

    if (parts[0] === 'v1' && parts[1] === 'connections' && parts[2]) {
      const request = await requireStore().getRequest(parts[2]);
      if (!request || new Date(request.expires_at).getTime() <= Date.now()) return json(res, 404, { error: 'request not found or expired' });
      if (req.method === 'GET' && parts.length === 3) return json(res, 200, request);
      if (req.method === 'POST' && (parts[3] === 'approve' || parts[3] === 'deny')) {
        return json(res, 200, await requireStore().setRequestStatus(parts[2], parts[3] === 'approve' ? 'approved' : 'denied'));
      }
      if (req.method === 'POST' && parts[3] === 'session') {
        if (request.status !== 'approved') return json(res, 409, { error: 'request is not approved' });
        return json(res, 201, await requireStore().createSession(request));
      }
    }

    if (parts[0] === 'v1' && parts[1] === 'sessions' && parts[2]) {
      const session = await requireStore().getSession(parts[2]);
      if (!session || new Date(session.expires_at).getTime() <= Date.now()) return json(res, 404, { error: 'session not found or expired' });
      if (req.method === 'POST' && parts[3] === 'negotiate') {
        const body = await readBody(req);
        if (!['offer', 'answer', 'candidate'].includes(body.type) || typeof body.payload !== 'string' || !body.payload) return json(res, 400, { error: 'invalid negotiation envelope' });
        return json(res, 200, { accepted: true, sessionId: session.id, type: body.type, payload: body.payload });
      }
      if (req.method === 'DELETE' && parts.length === 3) {
        await requireStore().closeSession(parts[2]);
        return json(res, 200, { closed: true });
      }
    }

    return json(res, 404, { error: 'not found' });
  } catch (error) {
    console.error(error);
    return json(res, 500, { error: 'internal server error' });
  }
});

attachRealtime(server);
server.listen(Number(process.env.PORT || 8080), '0.0.0.0', () => console.log(`LINKO signaling listening on port ${process.env.PORT || 8080}`));
