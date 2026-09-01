import { createRelayServer } from "./relay-server.js";
import { createRelayHeartbeatFromEnv } from "./relay-heartbeat.js";

let registrationHealthy = false;

const heartbeat = createRelayHeartbeatFromEnv();
if (!heartbeat) {
  console.error(JSON.stringify({
    ts: new Date().toISOString(),
    level: "error",
    event: "relay_registration_not_configured",
    message: "Production relay requires LINKO_RELAY_REGISTRATION_TOKEN and LINKO_RELAY_HEARTBEAT_URL",
  }));
  process.exit(1);
}

const relay = await createRelayServer({
  isRegistrationHealthy: () => registrationHealthy,
});

// The relay must bind UDP before its first heartbeat, so the advertised endpoint
// is real. The health endpoint remains 503 until authenticated registration succeeds.
const configuredHeartbeat = createRelayHeartbeatFromEnv(() => relay.registry.count());
if (!configuredHeartbeat) {
  await relay.close();
  process.exit(1);
}

try {
  await configuredHeartbeat.start();
  registrationHealthy = true;
  console.log(JSON.stringify({
    ts: new Date().toISOString(),
    level: "info",
    event: "relay_registered",
    nodeId: process.env.RELAY_NODE_ID ?? "relay-1",
  }));

  // Keep health registration state tied to the heartbeat object. If heartbeats
  // become stale, /health becomes 503 and Fly can restart the machine.
  const healthWatchdog = setInterval(() => {
    registrationHealthy = configuredHeartbeat.isHealthy();
  }, 1_000);
  healthWatchdog.unref();

  const shutdown = async () => {
    registrationHealthy = false;
    clearInterval(healthWatchdog);
    configuredHeartbeat.stop();
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
