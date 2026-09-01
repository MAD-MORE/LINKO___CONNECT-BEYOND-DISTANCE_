import { createRelayServer } from "./relay-server.js";
import { createRelayHeartbeatFromEnv, type RelayHeartbeat } from "./relay-heartbeat.js";

let registrationHealthy = false;
let heartbeat: RelayHeartbeat | null = null;

const relay = await createRelayServer({
  isRegistrationHealthy: () => registrationHealthy,
});

heartbeat = createRelayHeartbeatFromEnv(() => relay.registry.count());
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
  registrationHealthy = heartbeat.isHealthy();

  console.log(JSON.stringify({
    ts: new Date().toISOString(),
    level: "info",
    event: "relay_registered",
    nodeId: process.env.RELAY_NODE_ID ?? "relay-1",
  }));

  // If control-plane heartbeats become stale, /health changes to 503.
  // Fly's health monitor can then restart the process and force a fresh registration.
  const healthWatchdog = setInterval(() => {
    registrationHealthy = heartbeat?.isHealthy() ?? false;
  }, 1_000);
  healthWatchdog.unref();

  const shutdown = async () => {
    registrationHealthy = false;
    clearInterval(healthWatchdog);
    heartbeat?.stop();
    await relay.close();
  };

  process.once("SIGTERM", () => void shutdown());
  process.once("SIGINT", () => void shutdown());
} catch (error) {
  console.error(JSON.stringify({
    ts: new Date().toISOString(),
    level: "error",
    event: "relay_registration_failed",
    error: error instanceof Error ? error.message : String(error),
  }));
  await relay.close();
  process.exit(1);
}
