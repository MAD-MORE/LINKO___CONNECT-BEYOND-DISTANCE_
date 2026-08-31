import { test, describe, before, after } from "node:test";
import assert from "node:assert/strict";
import { createSocket, type Socket } from "node:dgram";
import { createHash, randomBytes } from "node:crypto";
import { createRelayServer, MAGIC, HEADER_LENGTH, type RelayInstance } from "./relay-server.js";
import { SessionRegistry } from "./session-registry.js";

// Helper to build valid LINKO encrypted datagrams
function buildEncryptedDatagram(options: {
  sessionId: string;
  keyHash: string;
  role: number; // 1 = Provider, 2 = Client
  type?: number; // 1 = Data, 2 = Handshake, 3 = Keepalive, 4 = Close
  sequence?: bigint;
  nonce?: Buffer;
  ciphertextWithTag: Buffer;
}): Buffer {
  const buf = Buffer.alloc(HEADER_LENGTH + options.ciphertextWithTag.length);

  // 1. Magic (4B)
  MAGIC.copy(buf, 0);

  // 2. Version (1B)
  buf[4] = 0x02;

  // 3. Session ID (36B UUID ASCII)
  buf.write(options.sessionId, 5, 36, "ascii");

  // 4. Key Hash (32B raw bytes from 64-char hex)
  Buffer.from(options.keyHash, "hex").copy(buf, 41, 0, 32);

  // 5. Role (1B)
  buf[73] = options.role;

  // 6. Type (1B)
  buf[74] = options.type ?? 0x01;

  // 7. Sequence (8B BigInt)
  buf.writeBigUInt64BE(options.sequence ?? 1n, 75);

  // 8. Nonce (12B)
  const nonce = options.nonce ?? randomBytes(12);
  nonce.copy(buf, 83, 0, 12);

  // 9. Ciphertext + Auth Tag
  options.ciphertextWithTag.copy(buf, 95);

  return buf;
}

describe("LINKO Zero-Knowledge Data-Plane Relay Test Suite", () => {
  const TEST_UDP_PORT = 7990;
  const TEST_HTTP_PORT = 7991;
  let relay: RelayInstance;

  before(async () => {
    relay = await createRelayServer({
      udpPort: TEST_UDP_PORT,
      httpPort: TEST_HTTP_PORT,
      maxSessionBytes: 5000, // Small limit for testing quota
    });
  });

  after(async () => {
    await relay.close();
  });

  test("1. Health endpoint returns 200 OK with relay metrics", async () => {
    const res = await fetch(`http://127.0.0.1:${TEST_HTTP_PORT}/health`);
    assert.equal(res.status, 200);
    const body = await res.json() as any;
    assert.equal(body.service, "linko-relay");
    assert.equal(body.status, "ok");
    assert.equal(typeof body.uptimeSeconds, "number");
    assert.equal(typeof body.activeSessions, "number");
  });

  test("2. Rejects malformed and short datagrams without crashing", async () => {
    const client = createSocket("udp4");

    // Send too-short packet (< 111 bytes)
    await new Promise<void>((resolve) => {
      client.send(Buffer.from("short"), TEST_UDP_PORT, "127.0.0.1", () => resolve());
    });

    // Send invalid magic (95 bytes of zeroes)
    const invalidMagic = Buffer.alloc(120, 0);
    await new Promise<void>((resolve) => {
      client.send(invalidMagic, TEST_UDP_PORT, "127.0.0.1", () => resolve());
    });

    client.close();

    // Verify relay is still alive and responsive
    const res = await fetch(`http://127.0.0.1:${TEST_HTTP_PORT}/health`);
    assert.equal(res.status, 200);
  });

  test("3. Dynamic session registration and peer-to-peer forwarding with exact ciphertext match", async () => {
    const sessionId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    const sessionKey = randomBytes(32);
    const keyHash = createHash("sha256").update(sessionKey).digest("hex");

    const providerSock = createSocket("udp4");
    const clientSock = createSocket("udp4");

    await new Promise<void>((res) => providerSock.bind(0, "127.0.0.1", res));
    await new Promise<void>((res) => clientSock.bind(0, "127.0.0.1", res));

    const simulatedCiphertext = Buffer.concat([
      Buffer.from("ENCRYPTED_USER_PAYLOAD_CIPHERTEXT_12345"),
      randomBytes(16), // 16-byte simulated AES-GCM auth tag
    ]);

    // Step 1: Provider sends initial datagram to register its endpoint with the relay
    const providerPacket = buildEncryptedDatagram({
      sessionId,
      keyHash,
      role: 1, // Provider
      type: 2, // Handshake
      ciphertextWithTag: simulatedCiphertext,
    });

    await new Promise<void>((res) => providerSock.send(providerPacket, TEST_UDP_PORT, "127.0.0.1", () => res()));

    // Wait a brief moment for relay to process endpoint A
    await new Promise((r) => setTimeout(r, 50));

    // Step 2: Client sends datagram to Provider through the relay
    const clientPacket = buildEncryptedDatagram({
      sessionId,
      keyHash,
      role: 2, // Client
      type: 1, // Data
      ciphertextWithTag: simulatedCiphertext,
    });

    const receivedOnProvider = new Promise<Buffer>((resolve) => {
      providerSock.once("message", (msg) => resolve(msg));
    });

    await new Promise<void>((res) => clientSock.send(clientPacket, TEST_UDP_PORT, "127.0.0.1", () => res()));

    const forwardedToProvider = await receivedOnProvider;

    // Verify ZERO-KNOWLEDGE: The forwarded packet is byte-for-byte IDENTICAL to the transmitted packet!
    assert.equal(forwardedToProvider.length, clientPacket.length);
    assert.deepEqual(forwardedToProvider, clientPacket);

    // Step 3: Provider sends response packet back to Client
    const providerReply = buildEncryptedDatagram({
      sessionId,
      keyHash,
      role: 1, // Provider
      type: 1, // Data
      sequence: 2n,
      ciphertextWithTag: simulatedCiphertext,
    });

    const receivedOnClient = new Promise<Buffer>((resolve) => {
      clientSock.once("message", (msg) => resolve(msg));
    });

    await new Promise<void>((res) => providerSock.send(providerReply, TEST_UDP_PORT, "127.0.0.1", () => res()));

    const forwardedToClient = await receivedOnClient;
    assert.deepEqual(forwardedToClient, providerReply);

    providerSock.close();
    clientSock.close();
  });

  test("4. Drops packets with mismatched key-hash (anti-spoof protection)", async () => {
    const sessionId = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    const validKeyHash = createHash("sha256").update(Buffer.from("VALID_SESSION_KEY_32_BYTES_OK!")).digest("hex");
    const fakeKeyHash = createHash("sha256").update(Buffer.from("ATTACKER_FAKE_KEY_32_BYTES_XX")).digest("hex");

    const providerSock = createSocket("udp4");
    const attackerSock = createSocket("udp4");

    await new Promise<void>((res) => providerSock.bind(0, "127.0.0.1", res));
    await new Promise<void>((res) => attackerSock.bind(0, "127.0.0.1", res));

    // Register legitimate session
    const validPacket = buildEncryptedDatagram({
      sessionId,
      keyHash: validKeyHash,
      role: 1,
      ciphertextWithTag: randomBytes(32),
    });
    await new Promise<void>((res) => providerSock.send(validPacket, TEST_UDP_PORT, "127.0.0.1", () => res()));
    await new Promise((r) => setTimeout(r, 50));

    // Attacker tries sending with mismatched keyHash
    let receivedOnProvider = false;
    providerSock.once("message", () => {
      receivedOnProvider = true;
    });

    const spoofPacket = buildEncryptedDatagram({
      sessionId,
      keyHash: fakeKeyHash,
      role: 2,
      ciphertextWithTag: randomBytes(32),
    });
    await new Promise<void>((res) => attackerSock.send(spoofPacket, TEST_UDP_PORT, "127.0.0.1", () => res()));

    await new Promise((r) => setTimeout(r, 100));
    assert.equal(receivedOnProvider, false, "Provider must not receive packet with spoofed key hash!");

    providerSock.close();
    attackerSock.close();
  });

  test("5. Endpoint roaming: allows device to update its IP/port seamlessly", async () => {
    const sessionId = "c3d4e5f6-a7b8-9012-cdef-123456789012";
    const keyHash = createHash("sha256").update(randomBytes(32)).digest("hex");

    const providerSock = createSocket("udp4");
    const clientSock1 = createSocket("udp4");
    const clientSock2 = createSocket("udp4"); // Simulated network switch (e.g. WiFi to LTE)

    await new Promise<void>((res) => providerSock.bind(0, "127.0.0.1", res));
    await new Promise<void>((res) => clientSock1.bind(0, "127.0.0.1", res));
    await new Promise<void>((res) => clientSock2.bind(0, "127.0.0.1", res));

    // Initialize session with Provider and Client 1
    const pInit = buildEncryptedDatagram({ sessionId, keyHash, role: 1, ciphertextWithTag: randomBytes(24) });
    const c1Init = buildEncryptedDatagram({ sessionId, keyHash, role: 2, ciphertextWithTag: randomBytes(24) });

    await new Promise<void>((res) => providerSock.send(pInit, TEST_UDP_PORT, "127.0.0.1", () => res()));
    await new Promise<void>((res) => clientSock1.send(c1Init, TEST_UDP_PORT, "127.0.0.1", () => res()));
    await new Promise((r) => setTimeout(r, 50));

    // Client switches to new socket (Client 2) and sends packet
    const c2Packet = buildEncryptedDatagram({ sessionId, keyHash, role: 2, sequence: 10n, ciphertextWithTag: randomBytes(24) });
    await new Promise<void>((res) => clientSock2.send(c2Packet, TEST_UDP_PORT, "127.0.0.1", () => res()));
    await new Promise((r) => setTimeout(r, 50));

    // Provider replies -> must be forwarded to the NEW client endpoint (clientSock2)
    const pReply = buildEncryptedDatagram({ sessionId, keyHash, role: 1, sequence: 11n, ciphertextWithTag: randomBytes(24) });

    const receivedOnClient2 = new Promise<Buffer>((resolve) => {
      clientSock2.once("message", (msg) => resolve(msg));
    });

    await new Promise<void>((res) => providerSock.send(pReply, TEST_UDP_PORT, "127.0.0.1", () => res()));

    const forwarded = await receivedOnClient2;
    assert.deepEqual(forwarded, pReply);

    providerSock.close();
    clientSock1.close();
    clientSock2.close();
  });

  test("6. Session removal on Type 4 (Close) packet", async () => {
    const sessionId = "d4e5f6a7-b8c9-0123-def1-234567890123";
    const keyHash = createHash("sha256").update(randomBytes(32)).digest("hex");

    const sock = createSocket("udp4");
    const initPacket = buildEncryptedDatagram({ sessionId, keyHash, role: 1, ciphertextWithTag: randomBytes(24) });
    await new Promise<void>((res) => sock.send(initPacket, TEST_UDP_PORT, "127.0.0.1", () => res()));
    await new Promise((r) => setTimeout(r, 50));

    assert.ok(relay.registry.getById(sessionId) !== null, "Session should exist");

    // Send Close datagram
    const closePacket = buildEncryptedDatagram({ sessionId, keyHash, role: 1, type: 4, ciphertextWithTag: randomBytes(24) });
    await new Promise<void>((res) => sock.send(closePacket, TEST_UDP_PORT, "127.0.0.1", () => res()));
    await new Promise((r) => setTimeout(r, 50));

    assert.equal(relay.registry.getById(sessionId), null, "Session should be purged on Close datagram");
    sock.close();
  });

  test("7. SessionRegistry unit tests: TTL cleanup and key indexing", () => {
    const reg = new SessionRegistry(50); // 50ms TTL
    const sid = "test-ttl-1234-5678";
    const kh = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    reg.addSession(sid, kh);
    assert.ok(reg.getById(sid) !== null);
    assert.ok(reg.getByKeyHash(kh) !== null);

    // Wait for TTL expiration
    return new Promise<void>((resolve) => {
      setTimeout(() => {
        reg.cleanup();
        assert.equal(reg.getById(sid), null);
        assert.equal(reg.getByKeyHash(kh), null);
        reg.destroy();
        resolve();
      }, 70);
    });
  });
});
