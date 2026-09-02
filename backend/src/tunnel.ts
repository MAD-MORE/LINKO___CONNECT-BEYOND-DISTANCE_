import dgram from "node:dgram";
import { randomBytes } from "node:crypto";

export type TunnelRole = "receiver" | "provider";
export interface TunnelPeer { address: string; port: number }
export interface TunnelSession {
  id: string;
  key: Buffer;
  receiver?: TunnelPeer;
  provider?: TunnelPeer;
}

export function createTunnelKey(): Buffer {
  return randomBytes(32);
}

const LKO2_MAGIC = Buffer.from([0x4c, 0x4b, 0x4f, 0x32]); // "LKO2"
const LKO2_HEADER_LEN = 95;

/**
 * Zero-Knowledge Encrypted UDP Relay.
 * Inspects only the unencrypted routing header (Magic + SessionId + Role)
 * and forwards the authenticated ciphertext directly between paired peers.
 */
export class UdpTunnelEndpoint {
  private readonly socket = dgram.createSocket("udp4");
  private readonly sessions = new Map<string, TunnelSession>();

  constructor(private readonly bindPort: number) {}

  addSession(sessionId: string, key: Buffer): void {
    if (key.length !== 32) throw new Error("invalid_tunnel_key");
    this.sessions.set(sessionId, { id: sessionId, key });
  }

  removeSession(sessionId: string): void {
    this.sessions.delete(sessionId);
  }

  start(): void {
    this.socket.on("message", (wire, remote) => {
      // 1. Check for LKO2 Wire Framing (95-byte header)
      if (wire.length >= LKO2_HEADER_LEN && wire.subarray(0, 4).equals(LKO2_MAGIC)) {
        const sessionId = wire.subarray(5, 41).toString("ascii");
        const senderRoleByte = wire[73]; // 1 = PROVIDER, 2 = RECEIVER
        const senderRole: TunnelRole = senderRoleByte === 2 ? "receiver" : senderRoleByte === 1 ? "provider" : "receiver";

        const session = this.sessions.get(sessionId);
        if (!session) return;

        const peer = { address: remote.address, port: remote.port };
        if (senderRole === "receiver") session.receiver = peer;
        else session.provider = peer;

        const target = senderRole === "receiver" ? session.provider : session.receiver;
        if (!target) return;

        // Zero-knowledge forwarding: pass through exact authenticated wire datagram
        this.socket.send(wire, target.port, target.address);
        return;
      }

      // 2. Fallback: Legacy framing with null-delimiter
      const sessionEnd = wire.indexOf(0);
      if (sessionEnd <= 0 || sessionEnd > 128) return;
      const roleCode = wire[sessionEnd + 1];
      const senderRole: TunnelRole = roleCode === 0 ? "receiver" : roleCode === 1 ? "provider" : "receiver";
      const sessionId = wire.subarray(0, sessionEnd).toString("utf8");
      const session = this.sessions.get(sessionId);
      if (!session) return;

      const peer = { address: remote.address, port: remote.port };
      if (senderRole === "receiver") session.receiver = peer;
      else session.provider = peer;

      const target = senderRole === "receiver" ? session.provider : session.receiver;
      if (!target) return;

      this.socket.send(wire, target.port, target.address);
    });
    this.socket.bind(this.bindPort);
  }

  close(): void {
    this.socket.close();
    for (const session of this.sessions.values()) session.key.fill(0);
    this.sessions.clear();
  }
}
