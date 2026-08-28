import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import type { SessionRegistry } from "./session-registry.js";

/**
 * HTTP health and metrics endpoint for the Linko relay node.
 * Runs on a separate port (HTTP_PORT, default 7001) from the UDP relay.
 *
 * GET /health  → JSON health status
 * GET /metrics → Prometheus text metrics
 */

let totalPacketsForwarded = 0;
let totalBytesForwarded = 0;
let totalSessionsServed = 0;
const startTime = Date.now();

export function recordPacket(bytes: number): void {
  totalPacketsForwarded += 1;
  totalBytesForwarded += bytes;
}

export function recordNewSession(): void {
  totalSessionsServed += 1;
}

export function startHealthServer(port: number, registry: SessionRegistry): void {
  const server = createServer((req: IncomingMessage, res: ServerResponse) => {
    if (req.method === "GET" && req.url === "/health") {
      const uptime = Math.floor((Date.now() - startTime) / 1000);
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({
        service: "linko-relay",
        status: "ok",
        uptimeSeconds: uptime,
        activeSessions: registry.count(),
        totalPacketsForwarded,
        totalBytesForwarded,
        region: process.env.RELAY_REGION ?? "default",
        nodeId: process.env.RELAY_NODE_ID ?? "relay-1",
        timestamp: new Date().toISOString(),
      }));
      return;
    }

    if (req.method === "GET" && req.url === "/metrics") {
      const uptime = Math.floor((Date.now() - startTime) / 1000);
      const metrics = [
        "# HELP linko_relay_uptime_seconds Relay node uptime in seconds",
        "# TYPE linko_relay_uptime_seconds gauge",
        `linko_relay_uptime_seconds ${uptime}`,
        "# HELP linko_relay_active_sessions Current active sessions",
        "# TYPE linko_relay_active_sessions gauge",
        `linko_relay_active_sessions ${registry.count()}`,
        "# HELP linko_relay_packets_forwarded_total Total packets forwarded",
        "# TYPE linko_relay_packets_forwarded_total counter",
        `linko_relay_packets_forwarded_total ${totalPacketsForwarded}`,
        "# HELP linko_relay_bytes_forwarded_total Total bytes forwarded",
        "# TYPE linko_relay_bytes_forwarded_total counter",
        `linko_relay_bytes_forwarded_total ${totalBytesForwarded}`,
        "# HELP linko_relay_sessions_served_total Total sessions ever served",
        "# TYPE linko_relay_sessions_served_total counter",
        `linko_relay_sessions_served_total ${totalSessionsServed}`,
      ].join("\n") + "\n";

      res.writeHead(200, { "Content-Type": "text/plain; version=0.0.4" });
      res.end(metrics);
      return;
    }

    if (req.method === "POST" && req.url === "/sessions") {
      // Control plane registers a session key
      let body = "";
      req.on("data", chunk => { body += chunk; });
      req.on("end", async () => {
        try {
          const { sessionId, key } = JSON.parse(body) as { sessionId: string; key: string };
          await registry.addSession(sessionId, key);
          recordNewSession();
          res.writeHead(201, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ ok: true, sessionId }));
        } catch (err) {
          res.writeHead(400, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: String(err) }));
        }
      });
      return;
    }

    if (req.method === "DELETE" && req.url?.startsWith("/sessions/")) {
      const sessionId = req.url.slice("/sessions/".length);
      registry.removeSession(sessionId);
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ ok: true }));
      return;
    }

    res.writeHead(404, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "not_found" }));
  });

  server.listen(port, () => {
    console.log(JSON.stringify({
      ts: new Date().toISOString(),
      level: "info",
      message: `Relay health server listening on :${port}`,
    }));
  });
}
