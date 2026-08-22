export type DeviceRole = "provider" | "receiver";

export type SessionState =
  | "requested"
  | "approved"
  | "signaling"
  | "connected"
  | "revoked"
  | "expired"
  | "denied";

export interface Device {
  id: string;
  userId: string;
  publicKey: string;
  name: string;
  roles: DeviceRole[];
  lastSeenAt: number;
  revokedAt?: number;
}

export interface Session {
  id: string;
  receiverDeviceId: string;
  providerDeviceId: string;
  state: SessionState;
  createdAt: number;
  expiresAt: number;
  approvedAt?: number;
  revokedAt?: number;
}

export interface SignalingSession {
  sessionId: string;
  hostPublicKey: string;
  relayUrl: string | null;
  expiresAtEpochSeconds: number;
}

export interface HostSession {
  sessionId: string;
  clientPublicKey: string;
  allowedUntilEpochSeconds: number;
}

export interface ApiError {
  error: string;
  requestId: string;
}
