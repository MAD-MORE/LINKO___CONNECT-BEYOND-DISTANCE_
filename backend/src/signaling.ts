import { randomUUID } from "node:crypto";
import type { Session } from "./types.js";

export type SignalKind = "offer" | "answer" | "ice";

export interface SignalEnvelope {
  id: string;
  sessionId: string;
  senderDeviceId: string;
  recipientDeviceId: string;
  kind: SignalKind;
  payload: unknown;
  createdAt: number;
}

export interface SignalingTicket {
  sessionId: string;
  deviceId: string;
  expiresAt: number;
}

export class SignalingBroker {
  private readonly queues = new Map<string, SignalEnvelope[]>();

  createTicket(session: Session, deviceId: string): SignalingTicket {
    if (![session.receiverDeviceId, session.providerDeviceId].includes(deviceId)) {
      throw new Error("session_party_required");
    }
    if (session.state !== "signaling") throw new Error("session_not_signaling");
    if (session.expiresAt <= Date.now()) throw new Error("session_expired");
    return { sessionId: session.id, deviceId, expiresAt: Math.min(session.expiresAt, Date.now() + 60_000) };
  }

  publish(session: Session, senderDeviceId: string, kind: SignalKind, payload: unknown): SignalEnvelope {
    if (![session.receiverDeviceId, session.providerDeviceId].includes(senderDeviceId)) {
      throw new Error("session_party_required");
    }
    if (session.state !== "signaling") throw new Error("session_not_signaling");
    if (session.expiresAt <= Date.now()) throw new Error("session_expired");
    const recipientDeviceId = senderDeviceId === session.receiverDeviceId ? session.providerDeviceId : session.receiverDeviceId;
    const envelope: SignalEnvelope = {
      id: randomUUID(), sessionId: session.id, senderDeviceId, recipientDeviceId,
      kind, payload, createdAt: Date.now()
    };
    const queue = this.queues.get(recipientDeviceId) ?? [];
    queue.push(envelope);
    this.queues.set(recipientDeviceId, queue);
    return envelope;
  }

  drain(session: Session, deviceId: string): SignalEnvelope[] {
    if (![session.receiverDeviceId, session.providerDeviceId].includes(deviceId)) {
      throw new Error("session_party_required");
    }
    const queue = this.queues.get(deviceId) ?? [];
    const messages = queue.filter((message) => message.sessionId === session.id);
    this.queues.set(deviceId, queue.filter((message) => message.sessionId !== session.id));
    return messages;
  }
}
