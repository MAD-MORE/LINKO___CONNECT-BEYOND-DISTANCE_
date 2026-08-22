import { createServer } from "node:http";
import { randomUUID } from "node:crypto";
import { ControlPlaneStore } from "./store.js";

const store = new ControlPlaneStore();
const port = Number(process.env.PORT ?? 8080);

function json(res: import("node:http").ServerResponse, status: number, body: unknown, requestId: string) {
  res.writeHead(status, { "content-type": "application/json", "x-request-id": requestId });
  res.end(JSON.stringify(body));
}

async function body(req: import("node:http").IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(Buffer.from(chunk));
  if (!chunks.length) return {};
  const value: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("invalid_json_body");
  return value as Record<string, unknown>;
}

const server = createServer(async (req, res) => {
  const requestId = req.headers["x-request-id"]?.toString() || randomUUID();
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

  try {
    if (req.method === "GET" && url.pathname === "/health") {
      return json(res, 200, { service: "linko-control-plane", status: "ok" }, requestId);
    }

    if (req.method === "POST" && url.pathname === "/v1/devices") {
      const input = await body(req);
      if (typeof input.userId !== "string" || typeof input.publicKey !== "string" || typeof input.name !== "string" || !Array.isArray(input.roles)) {
        return json(res, 400, { error: "invalid_device", requestId }, requestId);
      }
      const device = store.registerDevice({
        userId: input.userId,
        publicKey: input.publicKey,
        name: input.name,
        roles: input.roles.filter((role): role is "provider" | "receiver" => role === "provider" || role === "receiver")
      });
      return json(res, 201, device, requestId);
    }

    if (req.method === "POST" && url.pathname === "/v1/sessions") {
      const input = await body(req);
      if (typeof input.receiverDeviceId !== "string" || typeof input.providerDeviceId !== "string") {
        return json(res, 400, { error: "invalid_session_request", requestId }, requestId);
      }
      const session = store.createSession(input.receiverDeviceId, input.providerDeviceId);
      return json(res, 201, session, requestId);
    }

    const transitionMatch = url.pathname.match(/^\/v1\/sessions\/([^/]+)\/transition$/);
    if (req.method === "POST" && transitionMatch) {
      const input = await body(req);
      if (typeof input.state !== "string") return json(res, 400, { error: "state_required", requestId }, requestId);
      const session = store.transitionSession(transitionMatch[1], input.state as Parameters<ControlPlaneStore["transitionSession"]>[1]);
      return json(res, 200, session, requestId);
    }

    const sessionMatch = url.pathname.match(/^\/v1\/sessions\/([^/]+)$/);
    if (req.method === "GET" && sessionMatch) {
      const session = store.getSession(sessionMatch[1]);
      return session ? json(res, 200, session, requestId) : json(res, 404, { error: "session_not_found", requestId }, requestId);
    }

    return json(res, 404, { error: "not_found", requestId }, requestId);
  } catch (error) {
    const message = error instanceof Error ? error.message : "internal_error";
    const status = message.startsWith("invalid_") || message.includes("required") ? 400 : 409;
    return json(res, status, { error: message, requestId }, requestId);
  }
});

server.listen(port, () => console.log(`LINKO control plane listening on :${port}`));
