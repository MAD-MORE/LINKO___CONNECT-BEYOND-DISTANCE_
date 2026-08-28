import { createSocket, type RemoteInfo } from "node:dgram";
import { createHash } from "node:crypto";
import { SessionRegistry } from "./session-registry.js";
import { startHealthServer, recordPacket } from "./health.js";

/**
 * Linko Relay Server
 *
 * Protocol:
 * ┌─────────────────────────────────────────────────────────────┐
 * │ Packet format (relay framing, prepended to encrypted payload)│
 * │                                                             │
 * │  Bytes 0-35:  Session ID (36-byte UUID string, ASCII)       │
 * │  Bytes 36-67: Key hash (SHA-256 of session key, 32 bytes)   │
 * │  Bytes 68+:   Encrypted payload (AES-GCM, relay is blind)   │
 * └─────────────────────────────────────────────────────────────┘
 *
 * On first packet from each party:
 * 1. Relay verifies session ID exists in registry
 * 2. Relay verifies key hash matches the registered session key hash
 * 3. Relay registers the sender as partyA or partyB
 * 4. Relay stores the sender's (address, port) as the endpoint for this party
 *
 * On subsequent packets:
 * 1. Relay looks up session by session ID
 * 2. Identifies sender as partyA or partyB
 * 3. Forwards the ENCRYPTED PAYLOAD ONLY (strips the relay framing header)
 *    to the other party
 * 4. Records bytes forwarded
 *
 * Security:
 * - Relay NEVER decrypts payloads (AES-GCM, relay has no key)
 * - Relay verifies key hash on first packet to prevent session ID spoofing
 * - Maximum session bandwidth enforced
 * - Unknown third-party packets are silently dropped
 */

const UDP_PORT = Number(process.env.UDP_PORT ?? 7000);
const HTTP_PORT = Number(process.env.PORT ?? 7001);
const MAX_PACKET_BYTES = 65_507;           // Max UDP payload
const RELAY_HEADER_LENGTH = 36 + 32;       // UUID (36 bytes) + key hash (32 bytes)
const MAX_SESSION_BYTES = Number(process.env.BANDWIDTH_LIMIT_BYTES_PER_SESSION ?? 1_073_741_824); // 1 GB default

const registry = new SessionRegistry();

const socket = createSocket("udp4");

socket.on("error", (err) => {
  console.error(JSON.stringify({ ts: new Date().toISOString(), level: "error", message: "UDP socket error", error: err.message }));
});

socket.on("message", (msg: Buffer, remote: RemoteInfo) => {
  if (msg.length < RELAY_HEADER_LENGTH + 1) {
    // Packet too small to contain relay header + any payload
    return;
  }

  // Parse relay header
  const sessionId = msg.subarray(0, 36).toString("ascii");
  const incomingKeyHash = msg.subarray(36, 68).toString("hex");
  const payload = msg.subarray(RELAY_HEADER_LENGTH);

  // Look up session by ID
  const entry = registry.getById(sessionId);
  if (!entry) {
    // Unknown or expired session — silently drop
    return;
  }

  // Verify key hash matches (prevents session ID spoofing)
  if (entry.keyHash !== incomingKeyHash) {
    // Bad key — silently drop
    return;
  }

  // Check bandwidth quota
  if (entry.bytesForwarded + payload.length > MAX_SESSION_BYTES) {
    // Session over bandwidth limit — send termination signal and remove
    // (In production: notify control plane to revoke session)
    registry.removeSession(sessionId);
    return;
  }

  // Register endpoint (first packet from each party)
  const party = registry.registerEndpoint(sessionId, remote);
  if (party === null) {
    // Third party trying to inject into session — drop
    return;
  }

  // Determine the destination (the other party)
  const dest = party === "a" ? entry.partyB : entry.partyA;
  if (!dest) {
    // Other party hasn't connected yet — buffer? For MVP: drop and let client retry
    return;
  }

  // Forward the ENCRYPTED payload (relay never sees plaintext)
  socket.send(payload, dest.port, dest.address, (err) => {
    if (err) {
      console.error(JSON.stringify({
        ts: new Date().toISOString(),
        level: "error",
        message: "UDP send error",
        sessionId,
        error: err.message,
      }));
    }
  });

  registry.recordBytes(sessionId, payload.length);
  recordPacket(payload.length);
});

socket.bind(UDP_PORT, () => {
  console.log(JSON.stringify({
    ts: new Date().toISOString(),
    level: "info",
    message: `Linko relay UDP listening on :${UDP_PORT}`,
    nodeId: process.env.RELAY_NODE_ID ?? "relay-1",
    region: process.env.RELAY_REGION ?? "default",
    maxSessionBytes: MAX_SESSION_BYTES,
  }));
});

// Start HTTP health server
startHealthServer(HTTP_PORT, registry);

// Graceful shutdown
process.on("SIGTERM", () => {
  console.log(JSON.stringify({ ts: new Date().toISOString(), level: "info", message: "Relay shutting down (SIGTERM)" }));
  socket.close();
  process.exit(0);
});

process.on("SIGINT", () => {
  socket.close();
  process.exit(0);
});
