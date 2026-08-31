import { createRelayServer } from "./relay-server.js";
import { createRelayHeartbeatFromEnv } from "./relay-heartbeat.js";

/**
 * Production entrypoint.
 *
 * Start the UDP/HTTP relay first, then advertise it to Supabase. This ordering
 * is deliberate: a relay is discoverable only after its data-plane socket is
 * actually listening.
 */
const relay = await createRelayServer();
const heartbeat = createRelayHeartbeatFromEnv(() => relay.registry.count());

if (!heartbeat) {
  console.error(JSON.stringify({
    ts: new Date().toISOString(),
    level: "error",
    event: "relay_registration_not_configured",
    message: "Production relay requires LINKO_RELAY_REGISTRATION_TOKEN and LINKO_RELAY_HEARTBEAT_URL",
  }));
  await relay.close();
  process.exit(1);
}

try {
  await heartbeat.start();
  console.log(JSON.stringify({
    ts: new Date().toISOString(),
    level: "info",
    event: "relay_registered",
    nodeId: process.env.RELAY_NODE_ID ?? "relay-1",
  }));
} catch (error) {
  // Fail closed: an unregistered relay must not be advertised as usable.
  console.error(JSON.stringify({
    ts: new Date().toISOString(),
    level: "error",
    event: "relay_registration_failed",
    error: error instanceof Error ? error.message : String(error),
  }));
  await relay.close();
  process.exit(1);
}

const shutdown = async () => {
  await heartbeat.stop();
  await relay.close();
};

process.once("SIGTERM", () => void shutdown());
process.once("SIGINT", () => void shutdown());
