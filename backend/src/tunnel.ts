import dgram from "node:dgram";
import { randomBytes } from "node:crypto";
import { decryptPacket, encryptPacket } from "./crypto.js";

export interface TunnelPeer { address: string; port: number }
export interface TunnelSession { id: string; key: Buffer; receiver?: TunnelPeer }

export function createTunnelKey(): Buffer {
  return randomBytes(32);
}

export interface ProviderPacketForwarder {
  forward(sessionId: string, packet: Buffer): Promise<Buffer | null>;
}

/** Authenticated UDP tunnel endpoint. Network access stays behind the provider adapter. */
export class UdpTunnelEndpoint {
  private readonly socket = dgram.createSocket("udp4");
  private readonly sessions = new Map<string, TunnelSession>();
  private forwarder?: ProviderPacketForwarder;

  constructor(private readonly bindPort: number) {}

  addSession(sessionId: string, key: Buffer): void {
    if (key.length !== 32) throw new Error("invalid_tunnel_key");
    this.sessions.set(sessionId, { id: sessionId, key });
  }

  removeSession(sessionId: string): void {
    this.sessions.delete(sessionId);
  }

  setForwarder(forwarder: ProviderPacketForwarder): void {
    this.forwarder = forwarder;
  }

  start(): void {
    this.socket.on("message", async (wire, remote) => {
      const separator = wire.indexOf(0);
      if (separator <= 0 || separator > 128) return;
      const sessionId = wire.subarray(0, separator).toString("utf8");
      const session = this.sessions.get(sessionId);
      if (!session) return;

      try {
        const plaintext = decryptPacket(session.key, wire.subarray(separator + 1));
        session.receiver = { address: remote.address, port: remote.port };
        const response = await this.forwarder?.forward(sessionId, Buffer.from(plaintext));
        if (response) this.send(sessionId, response);
      } catch {
        // Invalid authentication, malformed frames and unavailable sessions are dropped.
      }
    });
    this.socket.bind(this.bindPort);
  }

  send(sessionId: string, plaintext: Uint8Array): void {
    const session = this.sessions.get(sessionId);
    if (!session?.receiver) throw new Error("tunnel_peer_unavailable");
    const frame = encryptPacket(session.key, plaintext);
    const wire = Buffer.concat([Buffer.from(sessionId), Buffer.from([0]), frame]);
    this.socket.send(wire, session.receiver.port, session.receiver.address);
  }

  close(): void {
    this.socket.close();
    this.sessions.clear();
  }
}

/** Safe default: prevents accidental forwarding until a real provider adapter is configured. */
export class UnconfiguredProviderForwarder implements ProviderPacketForwarder {
  async forward(): Promise<Buffer | null> {
    throw new Error("provider_network_forwarder_not_configured");
  }
}
