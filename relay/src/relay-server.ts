import { createSocket, type Socket, type RemoteInfo } from "node:dgram";
import { type Server } from "node:http";
import { SessionRegistry } from "./session-registry.js";
import { startHealthServer, recordPacket } from "./health.js";

/**
 * LINKO Data-Plane Relay Server (V2)
 *
 * Wire Framing Format:
 * ┌──────────┬─────────┬──────────────┬──────────┬──────┬──────┬───────────┬─────────┬───────────────────────┐
 * │ Magic    │ Version │ Session ID   │ Key Hash │ Role │ Type │ Sequence  │ Nonce   │ Ciphertext + Auth Tag │
 * │ (4B)     │ (1B)    │ (36B UUID)   │ (32B)    │ (1B) │ (1B) │ (8B)      │ (12B)   │ (Variable + 16B tag)  │
 * └──────────┴─────────┴──────────────┴──────────┴──────┴──────┴───────────┴─────────┴───────────────────────┘
 * Total Header: 95 bytes. Minimum Datagram: 111 bytes (95B Header + 16B GCM Auth Tag).
 *
 * ZERO-KNOWLEDGE PRINCIPLES:
 * 1. The relay NEVER possesses private session keys or user credentials.
 * 2. The relay NEVER decrypts or inspects packet payloads.
 * 3. The relay NEVER logs packet contents or browsing metadata.
 * 4. The relay ONLY routes complete, unmodified encrypted datagrams between verified endpoints.
 */

export const MAGIC = Buffer.from([0x4C, 0x4B, 0x4F, 0x32]); // "LKO2"
export const HEADER_LENGTH = 95;
export const MIN_PACKET_LENGTH = HEADER_LENGTH + 16; // 111 bytes

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
    // 1. Minimum Length Validation
    if (msg.length < MIN_PACKET_LENGTH) {
      return; // Malformed / short packet
    }

    // 2. Verify Magic ("LKO2")
    if (!msg.subarray(0, 4).equals(MAGIC)) {
      return; // Invalid protocol magic
    }

    // 3. Parse Header Fields
    const sessionId = msg.subarray(5, 41).toString("ascii");
    const incomingKeyHash = msg.subarray(41, 73).toString("hex").toLowerCase();
    const role = msg[73]; // 1 = Provider, 2 = Client
    const type = msg[74]; // 1 = Data, 2 = Handshake, 3 = Keepalive, 4 = Close

    // Basic UUID validation
    if (sessionId.length !== 36) {
      return;
    }

    // 4. Session Lookup & Cryptographic Key-Hash Verification
    let entry = registry.getById(sessionId);
    if (!entry) {
      // Dynamically register session from first cryptographic datagram
      entry = registry.addSession(sessionId, incomingKeyHash);
    } else if (entry.keyHash !== incomingKeyHash) {
      // Key hash mismatch: drop unauthorized spoofed packet
      return;
    }

    // 5. Bandwidth Quota Enforcement
    if (entry.bytesForwarded + msg.length > maxSessionBytes) {
      registry.removeSession(sessionId);
      return;
    }

    // 6. Endpoint Registration & Network Roaming
    const party = registry.registerEndpoint(sessionId, remote, role);
    if (party === null) {
      return;
    }

    // 7. Route to Peer Endpoint
    const dest = (role === 1 || party === "a") ? entry.partyB : entry.partyA;
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

    // 8. Handle Session Teardown Datagram
    if (type === 4) {
      registry.removeSession(sessionId);
    }
  });

  const httpServer = startHealthServer(httpPort, registry, () => isUdpBound);

  return new Promise((resolve, reject) => {
    socket.once("error", reject);

    socket.bind(udpPort, "0.0.0.0", () => {
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
          await new Promise<void>((res) => {
            socket.close(() => res());
          });
          await new Promise<void>((res) => {
            httpServer.close(() => res());
          });
        },
      });
    });
  });
}

// Auto-run if executed directly as script entry point
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
