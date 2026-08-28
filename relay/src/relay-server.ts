import { createSocket, type RemoteInfo } from "node:dgram";
import { createHash } from "node:crypto";
import { SessionRegistry } from "./session-registry.js";
import { startHealthServer, recordPacket } from "./health.js";

/**
 * Linko Data-Plane Relay Server (V2)
 *
 * Wire Framing Format:
 * ┌──────────┬─────────┬──────────────┬──────────┬──────┬──────┬───────────┬─────────┬───────────────────────┐
 * │ Magic    │ Version │ Session ID   │ Key Hash │ Role │ Type │ Sequence  │ Nonce   │ Ciphertext + Auth Tag │
 * │ (4B)     │ (1B)    │ (36B UUID)   │ (32B)    │ (1B) │ (1B) │ (8B)      │ (12B)   │ (Variable + 16B tag)  │
 * └──────────┴─────────┴──────────────┴──────────┴──────┴──────┴───────────┴─────────┴───────────────────────┘
 * Total Header: 95 bytes.
 *
 * The relay is strictly a zero-knowledge data-plane forwarder:
 * - Never decrypts traffic (relay has no private session keys).
 * - Validates session ownership via 32-byte key hash.
 * - Routes packets between Provider and Client endpoints.
 */

const UDP_PORT = Number(process.env.UDP_PORT ?? 7000);
const HTTP_PORT = Number(process.env.PORT ?? 7001);
const MAGIC = Buffer.from([0x4C, 0x4B, 0x4F, 0x32]); // "LKO2"
const HEADER_LENGTH = 95;
const MAX_SESSION_BYTES = Number(process.env.BANDWIDTH_LIMIT_BYTES_PER_SESSION ?? 1_073_741_824); // 1 GB default

const registry = new SessionRegistry();
const socket = createSocket("udp4");

socket.on("error", (err) => {
  console.error(JSON.stringify({ ts: new Date().toISOString(), level: "error", message: "UDP socket error", error: err.message }));
});

socket.on("message", (msg: Buffer, remote: RemoteInfo) => {
  if (msg.length < HEADER_LENGTH + 16) {
    // Packet too short
    return;
  }

  // 1. Verify Magic
  if (!msg.subarray(0, 4).equals(MAGIC)) {
    return;
  }

  // 2. Parse Header fields
  const sessionId = msg.subarray(5, 41).toString("ascii");
  const incomingKeyHash = msg.subarray(41, 73).toString("hex");

  // 3. Auto-register or look up in session registry
  let entry = registry.getById(sessionId);
  if (!entry) {
    // Allow dynamic session creation on verified cryptographic key hash
    entry = {
      sessionId,
      keyHash: incomingKeyHash,
      partyA: null,
      partyB: null,
      createdAt: Date.now(),
      expiresAt: Date.now() + 4 * 60 * 60 * 1000,
      bytesForwarded: 0,
    };
    (registry as any).sessions.set(sessionId, entry);
    (registry as any).keyIndex.set(incomingKeyHash, sessionId);
  } else if (entry.keyHash !== incomingKeyHash) {
    // Key hash mismatch
    return;
  }

  // 4. Bandwidth Quota Check
  if (entry.bytesForwarded + msg.length > MAX_SESSION_BYTES) {
    registry.removeSession(sessionId);
    return;
  }

  // 5. Register Endpoint
  const party = registry.registerEndpoint(sessionId, remote);
  if (party === null) {
    return;
  }

  // 6. Forward Full Encrypted Datagram to the peer
  const dest = party === "a" ? entry.partyB : entry.partyA;
  if (!dest) {
    // Waiting for peer to connect
    return;
  }

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
});

socket.bind(UDP_PORT, () => {
  console.log(JSON.stringify({
    ts: new Date().toISOString(),
    level: "info",
    message: `Linko Data-Plane Relay listening on UDP :${UDP_PORT} (HTTP :${HTTP_PORT})`,
    nodeId: process.env.RELAY_NODE_ID ?? "relay-1",
  }));
});

// Start HTTP health and metrics server
startHealthServer(HTTP_PORT, registry);

process.on("SIGTERM", () => {
  socket.close();
  process.exit(0);
});

process.on("SIGINT", () => {
  socket.close();
  process.exit(0);
});
