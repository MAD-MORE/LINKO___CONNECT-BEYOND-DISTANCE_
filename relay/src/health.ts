import { createServer, type IncomingMessage, type ServerResponse, type Server } from "node:http";
import type { SessionRegistry } from "./session-registry.js";

/**
 * HTTP health and metrics endpoint for the LINKO relay node.
 * Runs on port 7001 (or process.env.PORT).
 *
 * GET /health  → JSON health status (returns 200 if UDP is listening, 503 if not)
 * GET /metrics → Prometheus metrics
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

export function resetMetrics(): void {
  totalPacketsForwarded = 0;
  totalBytesForwarded = 0;
  totalSessionsServed = 0;
}

export function startHealthServer(
  port: number,
  registry: SessionRegistry,
  isUdpHealthy: () => boolean = () => true
): Server {
  const server = createServer((req: IncomingMessage, res: ServerResponse) => {
    // 1. Health check
    if (req.method === "GET" && (req.url === "/health" || req.url === "/")) {
      const udpHealthy = isUdpHealthy();
      const uptime = Math.floor((Date.now() - startTime) / 1000);

      if (!udpHealthy) {
        res.writeHead(503, { "Content-Type": "application/json" });
        res.end(JSON.stringify({
          service: "linko-relay",
          status: "error",
          error: "UDP relay socket is not listening",
          timestamp: new Date().toISOString(),
        }));
        return;
      }

      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({
        service: "linko-relay",
        status: "ok",
        uptimeSeconds: uptime,
        activeSessions: registry.count(),
        totalPacketsForwarded,
        totalBytesForwarded,
        region: process.env.RELAY_REGION ?? "iad",
        nodeId: process.env.RELAY_NODE_ID ?? "relay-1",
        timestamp: new Date().toISOString(),
      }));
      return;
    }

    // 2. Prometheus metrics
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

    // 3. Control-plane pre-registration endpoint (accepts ONLY session ID & key hash, NEVER private keys)
    if (req.method === "POST" && req.url === "/sessions") {
      let body = "";
      req.on("data", chunk => { body += chunk; });
      req.on("end", () => {
        try {
          const parsed = JSON.parse(body) as { sessionId: string; keyHash?: string };
          if (!parsed.sessionId) {
            res.writeHead(400, { "Content-Type": "application/json" });
            res.end(JSON.stringify({ error: "sessionId is required" }));
            return;
          }
          if (parsed.keyHash) {
            registry.addSession(parsed.sessionId, parsed.keyHash);
            recordNewSession();
          }
          res.writeHead(201, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ ok: true, sessionId: parsed.sessionId }));
        } catch (err) {
          res.writeHead(400, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "Invalid JSON request body" }));
        }
      });
      return;
    }

    // 4. Session deletion
    if (req.method === "DELETE" && req.url?.startsWith("/sessions/")) {
      const sessionId = req.url.slice("/sessions/".length);
      const removed = registry.removeSession(sessionId);
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ ok: true, removed }));
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

  return server;
}
