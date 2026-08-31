import dgram from "node:dgram";
import { randomBytes } from "node:crypto";
import { decryptPacket, encryptPacket } from "./crypto.js";

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

/** Encrypted two-peer relay. It terminates LINKO's authenticated frame only to validate it,
 * then re-encrypts the plaintext with the same session key before delivering to the other peer. */
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
      const sessionEnd = wire.indexOf(0);
      if (sessionEnd <= 0 || sessionEnd > 128) return;
      const roleCode = wire[sessionEnd + 1];
      const senderRole: TunnelRole = roleCode === 0 ? "receiver" : roleCode === 1 ? "provider" : (() => { throw new Error("invalid_tunnel_role"); })();
      const sessionId = wire.subarray(0, sessionEnd).toString("utf8");
      const session = this.sessions.get(sessionId);
      if (!session) return;

      try {
        const plaintext = decryptPacket(session.key, wire.subarray(sessionEnd + 2));
        const peer = { address: remote.address, port: remote.port };
        if (senderRole === "receiver") session.receiver = peer;
        else session.provider = peer;

        const targetRole: TunnelRole = senderRole === "receiver" ? "provider" : "receiver";
        const target = targetRole === "receiver" ? session.receiver : session.provider;
        if (!target || (senderRole === "receiver" ? !session.provider : !session.receiver)) return;

        const frame = encryptPacket(session.key, plaintext);
        const header = Buffer.concat([
          Buffer.from(session.id), Buffer.from([0]), Buffer.from([targetRole === "receiver" ? 0 : 1])
        ]);
        this.socket.send(Buffer.concat([header, frame]), target.port, target.address);
      } catch {
        // Drop malformed, expired, or unauthenticated datagrams.
      }
    });
    this.socket.bind(this.bindPort);
  }

  close(): void {
    this.socket.close();
    for (const session of this.sessions.values()) session.key.fill(0);
    this.sessions.clear();
  }
}
