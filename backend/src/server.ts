import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { randomUUID } from "node:crypto";
import { ControlPlaneStore } from "./store.js";
import { PostgresControlPlaneStore } from "./postgres-store.js";
import { SignalingBroker, type SignalKind } from "./signaling.js";
import { isBootstrapSecret, issueDeviceToken, verifyDeviceToken } from "./auth.js";
import { createSessionTunnelKey, getSessionTunnelKey, revokeSessionTunnelKey } from "./tunnel-key-store.js";
import { UdpTunnelEndpoint } from "./tunnel.js";
import type { SessionState } from "./types.js";

const production = process.env.NODE_ENV === "production";
const databaseUrl = process.env.LINKO_DATABASE_URL ?? process.env.DATABASE_URL;
if (production && !databaseUrl) throw new Error("LINKO_DATABASE_URL_required_in_production");
const postgresStore = databaseUrl ? new PostgresControlPlaneStore(databaseUrl) : null;
const store = postgresStore ?? new ControlPlaneStore();
const signaling = new SignalingBroker();
const port = Number(process.env.PORT ?? 8080);
const tunnelPort = Number(process.env.TUNNEL_PORT ?? 0);
const tunnelHost = process.env.TUNNEL_HOST;
const tunnelEndpoint = tunnelPort > 0 ? new UdpTunnelEndpoint(tunnelPort) : null;
if (tunnelEndpoint) tunnelEndpoint.start();
const supabaseUrl = (process.env.SUPABASE_URL ?? "").replace(/\/$/, "");
const supabasePublishableKey = process.env.SUPABASE_PUBLISHABLE_KEY ?? "";

function json(res: ServerResponse, status: number, body: unknown, requestId: string) { res.writeHead(status, { "content-type": "application/json", "x-request-id": requestId, "cache-control": "no-store" }); res.end(JSON.stringify(body)); }
async function body(req: IncomingMessage): Promise<Record<string, unknown>> { const chunks: Buffer[] = []; for await (const chunk of req) chunks.push(Buffer.from(chunk)); if (!chunks.length) return {}; const value: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8")); if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("invalid_json_body"); return value as Record<string, unknown>; }
function bearer(req: IncomingMessage) { const value = req.headers.authorization; return value?.startsWith("Bearer ") ? verifyDeviceToken(value.slice(7)) : null; }
function supabaseBearer(req: IncomingMessage) { const value = req.headers.authorization; return value?.startsWith("Bearer ") ? value.slice(7).trim() : null; }
async function verifySupabaseUser(accessToken: string): Promise<{ id: string; email?: string }> { if (!supabaseUrl || !supabasePublishableKey) throw new Error("supabase_auth_not_configured"); const response = await fetch(`${supabaseUrl}/auth/v1/user`, { headers: { apikey: supabasePublishableKey, Authorization: `Bearer ${accessToken}`, Accept: "application/json" } }); if (!response.ok) throw new Error("supabase_auth_invalid"); const user = await response.json() as { id?: string; email?: string }; if (!user.id) throw new Error("supabase_user_invalid"); return { id: user.id, email: user.email }; }

const server = createServer(async (req, res) => {
  const requestId = req.headers["x-request-id"]?.toString() || randomUUID();
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  try {
    if (req.method === "GET" && url.pathname === "/health") { let database = "memory"; if (postgresStore) { try { await postgresStore.health(); database = "postgres"; } catch { return json(res, 503, { service: "linko-control-plane", status: "degraded", database: "unreachable", relay: tunnelEndpoint ? "enabled" : "disabled", requestId }, requestId); } } return json(res, 200, { service: "linko-control-plane", status: "ok", database, persistence: database, relay: tunnelEndpoint ? "enabled" : "disabled" }, requestId); }
    if (req.method === "POST" && url.pathname === "/v1/devices/register") { const accessToken = supabaseBearer(req); if (!accessToken) return json(res, 401, { error: "supabase_auth_required", requestId }, requestId); const user = await verifySupabaseUser(accessToken); const input = await body(req); if (typeof input.publicKey !== "string" || typeof input.name !== "string" || !Array.isArray(input.roles)) return json(res, 400, { error: "invalid_device", requestId }, requestId); const roles = input.roles.filter((role): role is "provider" | "receiver" => role === "provider" || role === "receiver"); if (!roles.length) return json(res, 400, { error: "device_role_required", requestId }, requestId); const device = await store.registerDevice({ userId: user.id, publicKey: input.publicKey, name: input.name, roles }); return json(res, 201, { device, accessToken: issueDeviceToken(device.userId, device.id), user: { id: user.id, email: user.email } }, requestId); }
    if (req.method === "POST" && url.pathname === "/v1/devices") { if (!isBootstrapSecret(req.headers["x-linko-bootstrap"]?.toString())) return json(res, 401, { error: "bootstrap_auth_required", requestId }, requestId); const input = await body(req); if (typeof input.userId !== "string" || typeof input.publicKey !== "string" || typeof input.name !== "string" || !Array.isArray(input.roles)) return json(res, 400, { error: "invalid_device", requestId }, requestId); const roles = input.roles.filter((role): role is "provider" | "receiver" => role === "provider" || role === "receiver"); if (!roles.length) return json(res, 400, { error: "device_role_required", requestId }, requestId); const device = await store.registerDevice({ userId: input.userId, publicKey: input.publicKey, name: input.name, roles }); return json(res, 201, { device, accessToken: issueDeviceToken(device.userId, device.id) }, requestId); }

    const token = bearer(req); if (!token) return json(res, 401, { error: "authentication_required", requestId }, requestId);
    const authenticatedDevice = await store.getDevice(token.deviceId); if (!authenticatedDevice || authenticatedDevice.userId !== token.sub || authenticatedDevice.revokedAt) return json(res, 401, { error: "device_not_authorized", requestId }, requestId);

    if (req.method === "GET" && url.pathname === "/v1/provider/requests") {
      if (!authenticatedDevice.roles.includes("provider")) return json(res, 403, { error: "provider_role_required", requestId }, requestId);
      const sessions = await store.listPendingProviderSessions(authenticatedDevice.id);
      return json(res, 200, { requests: sessions }, requestId);
    }
    if (req.method === "POST" && url.pathname === "/v1/devices/presence") { const device = await store.touchDevice(authenticatedDevice.id); return json(res, 200, { deviceId: device.id, lastSeenAt: device.lastSeenAt }, requestId); }

    if (req.method === "POST" && url.pathname === "/v1/sessions") { const input = await body(req); if (typeof input.receiverDeviceId !== "string" || typeof input.providerDeviceId !== "string") return json(res, 400, { error: "invalid_session_request", requestId }, requestId); if (authenticatedDevice.id !== input.receiverDeviceId && authenticatedDevice.id !== input.providerDeviceId) return json(res, 403, { error: "session_party_required", requestId }, requestId); const session = await store.createSession(input.receiverDeviceId, input.providerDeviceId); const key = createSessionTunnelKey(session.id); if (tunnelEndpoint) tunnelEndpoint.addSession(session.id, key); return json(res, 201, session, requestId); }
    const transitionMatch = url.pathname.match(/^\/v1\/sessions\/([^/]+)\/transition$/);
    if (req.method === "POST" && transitionMatch) { const session = await store.getSession(transitionMatch[1]); if (!session) return json(res, 404, { error: "session_not_found", requestId }, requestId); if (session.receiverDeviceId !== authenticatedDevice.id && session.providerDeviceId !== authenticatedDevice.id) return json(res, 403, { error: "session_party_required", requestId }, requestId); const input = await body(req); if (typeof input.state !== "string") return json(res, 400, { error: "state_required", requestId }, requestId); const next = input.state as SessionState; if (next === "approved" && session.providerDeviceId !== authenticatedDevice.id) return json(res, 403, { error: "provider_approval_required", requestId }, requestId); const updated = await store.transitionSession(session.id, next); if (["revoked", "expired", "denied"].includes(next)) { revokeSessionTunnelKey(session.id); tunnelEndpoint?.removeSession(session.id); } return json(res, 200, updated, requestId); }
    const tunnelConfigMatch = url.pathname.match(/^\/v1\/sessions\/([^/]+)\/tunnel$/);
    if (req.method === "GET" && tunnelConfigMatch) { const session = await store.getSession(tunnelConfigMatch[1]); if (!session) return json(res, 404, { error: "session_not_found", requestId }, requestId); if (session.receiverDeviceId !== authenticatedDevice.id && session.providerDeviceId !== authenticatedDevice.id) return json(res, 403, { error: "session_party_required", requestId }, requestId); if (!["signaling", "connected"].includes(session.state)) return json(res, 409, { error: "tunnel_not_ready", requestId }, requestId); const key = getSessionTunnelKey(session.id); if (!key) return json(res, 410, { error: "tunnel_key_unavailable", requestId }, requestId); if (!tunnelHost || !tunnelEndpoint) return json(res, 503, { error: "tunnel_endpoint_not_configured", requestId }, requestId); return json(res, 200, { sessionId: session.id, endpoint: { host: tunnelHost, port: tunnelPort }, key: key.toString("base64url"), role: authenticatedDevice.id === session.receiverDeviceId ? "receiver" : "provider", expiresAt: session.expiresAt }, requestId); }
    const signalTicketMatch = url.pathname.match(/^\/v1\/sessions\/([^/]+)\/signaling\/ticket$/);
    if (req.method === "POST" && signalTicketMatch) { const session = await store.getSession(signalTicketMatch[1]); if (!session) return json(res, 404, { error: "session_not_found", requestId }, requestId); if (session.receiverDeviceId !== authenticatedDevice.id && session.providerDeviceId !== authenticatedDevice.id) return json(res, 403, { error: "session_party_required", requestId }, requestId); return json(res, 200, signaling.createTicket(session, authenticatedDevice.id), requestId); }
    const signalMatch = url.pathname.match(/^\/v1\/sessions\/([^/]+)\/signaling$/);
    if (signalMatch && (req.method === "POST" || req.method === "GET")) { const session = await store.getSession(signalMatch[1]); if (!session) return json(res, 404, { error: "session_not_found", requestId }, requestId); if (session.receiverDeviceId !== authenticatedDevice.id && session.providerDeviceId !== authenticatedDevice.id) return json(res, 403, { error: "session_party_required", requestId }, requestId); if (req.method === "POST") { const input = await body(req); if (!["offer", "answer", "ice"].includes(input.kind as string)) return json(res, 400, { error: "invalid_signal_kind", requestId }, requestId); return json(res, 201, signaling.publish(session, authenticatedDevice.id, input.kind as SignalKind, input.payload ?? null), requestId); } return json(res, 200, { signals: signaling.drain(session, authenticatedDevice.id) }, requestId); }
    const sessionMatch = url.pathname.match(/^\/v1\/sessions\/([^/]+)$/);
    if (req.method === "GET" && sessionMatch) { const session = await store.getSession(sessionMatch[1]); if (!session) return json(res, 404, { error: "session_not_found", requestId }, requestId); if (session.receiverDeviceId !== authenticatedDevice.id && session.providerDeviceId !== authenticatedDevice.id) return json(res, 403, { error: "session_party_required", requestId }, requestId); return json(res, 200, session, requestId); }
    return json(res, 404, { error: "not_found", requestId }, requestId);
  } catch (error) { const message = error instanceof Error ? error.message : "internal_error"; const status = message.startsWith("invalid_") || message.includes("required") ? 400 : message.includes("not_found") ? 404 : message.startsWith("supabase_auth") ? 401 : 409; return json(res, status, { error: message, requestId }, requestId); }
});
server.listen(port, () => console.log(`LINKO control plane listening on :${port}`));
