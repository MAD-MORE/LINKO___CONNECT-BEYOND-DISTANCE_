export type ConnectionStatus =
  | 'pending'
  | 'approved'
  | 'denied'
  | 'connecting'
  | 'connected'
  | 'expired'
  | 'closed';

export interface ConnectionRequest {
  id: string;
  receiverId: string;
  providerId: string;
  status: ConnectionStatus;
  createdAt: string;
  expiresAt: string;
}

export interface Session {
  id: string;
  requestId: string;
  receiverId: string;
  providerId: string;
  transport: 'direct' | 'relay' | 'pending';
  expiresAt: string;
}

export interface NegotiationEnvelope {
  sessionId: string;
  senderId: string;
  type: 'offer' | 'answer' | 'candidate';
  payload: string;
}

export interface RelayAssignment {
  sessionId: string;
  relayId: string;
  endpoint: string;
  expiresAt: string;
}