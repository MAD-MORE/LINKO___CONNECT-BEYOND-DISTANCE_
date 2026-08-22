# Tunnel handoff — Phase 5.1

The signaling service is now contract-aligned and protected by an environment token in production.

The remaining transport boundary is intentionally explicit:

1. Provider approval creates a short-lived session.
2. Peers exchange ephemeral negotiation payloads through `/v1/sessions/{sessionId}/negotiate`.
3. A tunnel implementation establishes authenticated peer-to-peer or relay transport.
4. Android `VpnService` must only route packets after that tunnel is authenticated.
5. No plaintext VPN packet may be sent to the relay.

## Release gate

Phase 5.1 must remain open until a real tunnel implementation is present and verified on two Android devices. The current Android VPN service creates the TUN interface but does not yet forward packets through an authenticated peer/relay transport. This is a deliberate fail-closed boundary.