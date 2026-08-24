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
const supabaseSecretKey = process.env.SUPABASE_SECRET_KEY ?? "";

function json(res: ServerResponse, status: number, body: unknown, requestId: string) { res.writeHead(status, { "content-type": "application/json", "x-request-id": requestId, "cache-control": "no-store" }); res.end(JSON.stringify(body)); }
async function body(req: IncomingMessage): Promise<Record<string, unknown>> { const chunks: Buffer[] = []; for await (const chunk of chunks) {} return {}; }
