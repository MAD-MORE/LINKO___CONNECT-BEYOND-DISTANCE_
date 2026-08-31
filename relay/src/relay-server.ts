import { createSocket, type Socket, type RemoteInfo } from "node:dgram";
import { type Server } from "node:http";
import { SessionRegistry } from "./session-registry.js";
import { startHealthServer, recordPacket } from "./health.js";

/**
 * LINKO Data-Plane Relay Server (V2)
 *
 * Wire Framing Format is intentionally identical to Android's EncryptedDatagramTunnel.
 * The relay never decrypts payloads; it only validates the framing metadata needed
 * to route the already-authenticated datagram safely.
 *
 * Packet type contract:
 *   1 = DATA
 *   2 = PING
 *   3 = PONG
 *   4 = HANDSHAKE
 *   5 = CLOSE
 */

export const MAGIC = Buffer.from([0x4C, 0x4B, 0x4F, 0x32]); // "LKO2"
export const HEADER_LENGTH = 95;
export const MIN_PACKET_LENGTH = HEADER_LENGTH + 16; // GCM authentication tag

const PACKET_TYPE_CLOSE = 5;

export interface RelayOptions {
  udpPort?: number;
  httpPort?: number;
  maxSessionBytes?: number;
  nodeId?: string;
  region?: string;
}

export interface RelayInstance {
  socket: Socket;
  httpServer: Server;
  registry: SessionRegistry;
  close: () => Promise<void>;
}

export function createRelayServer(options: RelayOptions = {}): Promise<RelayInstance> {
  const udpPort = options.udpPort ?? Number(process.env.UDP_PORT ?? 7000);
  const httpPort = options.httpPort ?? Number(process.env.PORT ?? 7001);
  const maxSessionBytes = options.maxSessionBytes ?? Number(process.env.BANDWIDTH_LIMIT_BYTES_PER_SESSION ?? 1_073_741_824);
  const nodeId = options.nodeId ?? process.env.RELAY_NODE_ID ?? "relay-1";
  const region = options.region ?? process.env.RELAY_REGION ?? "iad";
  // Fly.io UDP services must bind to fly-global-services. Keep 0.0.0.0 as the local/test default.
  const udpBindHost = process.env.RELAY_UDP_BIND_HOST ?? "0.0.0.0";

  const registry = new SessionRegistry();
  const socket = createSocket("udp4");
  let isUdpBound = false;

  socket.on("error", (err) => {
    console.error(JSON.stringify({
      ts: new Date().toISOString(),
      level: "error",
      message: "UDP socket error",
      error: err.message,
    }));
  });

  socket.on("message", (msg: Buffer, remote: RemoteInfo) => {
    // 1. Minimum Length Validation.
    if (msg.length < MIN_PACKET_LENGTH) return;

    // 2. Verify protocol magic.
    if (!msg.subarray(0, 4).equals(MAGIC)) return;

    // 3. Parse fields shared with the Android tunnel implementation.
    const sessionId = msg.subarray(5, 41).toString("ascii");
    const incomingKeyHash = msg.subarray(41, 73).toString("hex").toLowerCase();
    const role = msg[73]; // 1 = Provider, 2 = Receiver
    const type = msg[74]; // 1 = DATA, 2 = PING, 3 = PONG, 4 = HANDSHAKE, 5 = CLOSE

    if (sessionId.length !== 36) return;
    if (role !== 1 && role !== 2) return;

    // 4. Bind the session to the first observed key hash.
    // The relay sees only a SHA-256 hash, never the AES key itself.
    let entry = registry.getById(sessionId);
    if (!entry) {
      entry = registry.addSession(sessionId, incomingKeyHash);
    } else if (entry.keyHash !== incomingKeyHash) {
      // Different key hash means this datagram does not belong to this session.
      return;
    }

    // 5. Enforce per-session bandwidth quota before forwarding.
    if (entry.bytesForwarded + msg.length > maxSessionBytes) {
      registry.removeSession(sessionId);
      return;
    }

    // 6. Record the authenticated endpoint and support normal NAT port roaming.
    const party = registry.registerEndpoint(sessionId, remote, role);
    if (party === null) return;

    // 7. Forward the complete encrypted datagram unchanged.
    const dest = party === "a" ? entry.partyB : entry.partyA;
    if (dest) {
      socket.send(msg, dest.port, dest.address, (err) => {
        if (err) {
          console.error(JSON.stringify({
            ts: new Date().toISOString(),
            level: "error",
            message: "Relay UDP forward error",
            sessionId,
            error: err.message,
          }));
        }
      });

      registry.recordBytes(sessionId, msg.length);
      recordPacket(msg.length);
    }

    // 8. Android sends CLOSE as packet type 5. Type 4 is HANDSHAKE and must
    // never tear down a session.
    if (type === PACKET_TYPE_CLOSE) {
      registry.removeSession(sessionId);
    }
  });

  const httpServer = startHealthServer(httpPort, registry, () => isUdpBound);

  return new Promise((resolve, reject) => {
    socket.once("error", reject);

    socket.bind(udpPort, udpBindHost, () => {
      isUdpBound = true;
      socket.removeListener("error", reject);

      console.log(JSON.stringify({
        ts: new Date().toISOString(),
        level: "info",
        message: `LINKO Data-Plane Relay listening on UDP :${udpPort} (HTTP :${httpPort})`,
        nodeId,
        region,
      }));

      resolve({
        socket,
        httpServer,
        registry,
        close: async () => {
          isUdpBound = false;
          registry.destroy();
          await new Promise<void>((res) => socket.close(() => res()));
          await new Promise<void>((res) => httpServer.close(() => res()));
        },
      });
    });
  });
}

// Auto-run if executed directly as script entry point.
const isDirectEntry = process.argv[1] && (
  process.argv[1].endsWith("relay-server.ts") ||
  process.argv[1].endsWith("relay-server.js")
);

if (isDirectEntry) {
  createRelayServer().catch((err) => {
    console.error("Failed to start LINKO relay server:", err);
    process.exit(1);
  });
}
