import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { randomUUID } from "node:crypto";
import { ControlPlaneStore } from "./store.js";
import { isBootstrapSecret, issueDeviceToken, verifyDeviceToken } from "./auth.js";

const store = new ControlPlaneStore();
const port = Number(process.env.PORT ?? 8080);

function json(res: ServerResponse, status: number, body: unknown, requestId: string) {
  res.writeHead(status, { "content-type": "application/json", "x-request-id": requestId });
  res.end(JSON.stringify(body));
}

async function body(req: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(Buffer.from(chunk));
  if (!chunks.length) return {};
  const value: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("invalid_json_body");
  return value as Record<string, unknown>;
}

function bearer(req: IncomingMessage) {
  const value = req.headers.authorization;
  return value?.startsWith("Bearer ") ? verifyDeviceToken(value.slice(7)) : null;
}

const server = createServer(async (req, res) => {
  const requestId = req.headers["x-request-id"]?.toString() || randomUUID();
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

  try {
    if (req.method === "GET" && url.pathname === "/health") {
      return json(res, 200, { service: "linko-control-plane", status: "ok" }, requestId);
    }

    if (req.method === "POST" && url.pathname === "/v1/devices") {
      if (!isBootstrapSecret(req.headers["x-linko-bootstrap"]?.toString())) {
        return json(res, 401, { error: "bootstrap_auth_required", requestId }, requestId);
      }
      const input = await body(req);
      if (typeof input.userId !== "string" || typeof input.publicKey !== "string" || typeof input.name !== "string" || !Array.isArray(input.roles)) {
        return json(res, 400, { error: "invalid_device", requestId }, requestId);
      }
      const roles = input.roles.filter((role): role is "provider" | "receiver" => role === "provider" || role === "receiver");
      if (!roles.length) return json(res, 400, { error: "device_role_required", requestId }, requestId);
      const device = store.registerDevice({ userId: input.userId, publicKey: input.publicKey, name: input.name, roles });
      return json(res, 201, { device, accessToken: issueDeviceToken(device.userId, device.id) }, requestId);
    }

    const token = bearer(req);
    if (!token) return json(res, 401, { error: "authentication_required", requestId }, requestId);
    const authenticatedDevice = store.getDevice(token.deviceId);
    if (!authenticatedDevice || authenticatedDevice.userId !== token.sub || authenticatedDevice.revokedAt) {
      return json(res, 401, { error: "device_not_authorized", requestId }, requestId);
    }

    if (req.method === "POST" && url.pathname === "/v1/sessions") {
      const input = await body(req);
      if (typeof input.receiverDeviceId !== "string" || typeof input.providerDeviceId !== "string") {
        return json(res, 400, { error: "invalid_session_request", requestId }, requestId);
      }
      if (authenticatedDevice.id !== input.receiverDeviceId && authenticatedDevice.id !== input.providerDeviceId) {
        return json(res, 403, { error: "session_party_required", requestId }, requestId);
      }
      const session = store.createSession(input.receiverDeviceId, input.providerDeviceId);
      return json(res, 201, session, requestId);
    }

    const transitionMatch = url.pathname.match(/^\/v1\/sessions\/([^/]+)\/transition$/);
    if (req.method === "POST" && transitionMatch) {
      const session = store.getSession(transitionMatch[1]);
      if (!session) return json(res, 404, { error: "session_not_found", requestId }, requestId);
      if (session.receiverDeviceId !== authenticatedDevice.id && session.providerDeviceId !== authenticatedDevice.id) {
        return json(res, 403, { error: "session_party_required", requestId }, requestId);
      }
      const input = await body(req);
      if (typeof input.state !== "string") return json(res, 400, { error: "state_required", requestId }, requestId);
      const next = input.state as Parameters<ControlPlaneStore["transitionSession"]>[1];
      if (next === "approved" && session.providerDeviceId !== authenticatedDevice.id) return json(res, 403, { error: "provider_approval_required", requestId }, requestId);
      const updated = store.transitionSession(session.id, next);
      return json(res, 200, updated, requestId);
    }

    const sessionMatch = url.pathname.match(/^\/v1\/sessions\/([^/]+)$/);
    if (req.method === "GET" && sessionMatch) {
      const session = store.getSession(sessionMatch[1]);
      if (!session) return json(res, 404, { error: "session_not_found", requestId }, requestId);
      if (session.receiverDeviceId !== authenticatedDevice.id && session.providerDeviceId !== authenticatedDevice.id) {
        return json(res, 403, { error: "session_party_required", requestId }, requestId);
      }
      return json(res, 200, session, requestId);
    }

    return json(res, 404, { error: "not_found", requestId }, requestId);
  } catch (error) {
    const message = error instanceof Error ? error.message : "internal_error";
    const status = message.startsWith("invalid_") || message.includes("required") ? 400 : message.includes("not_found") ? 404 : 409;
    return json(res, status, { error: message, requestId }, requestId);
  }
});

server.listen(port, () => console.log(`LINKO control plane listening on :${port}`));
