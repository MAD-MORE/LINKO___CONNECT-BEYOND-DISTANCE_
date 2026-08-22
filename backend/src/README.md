# Backend implementation

`contract.ts` contains the transport-neutral contract shared by the eventual HTTP/WebSocket implementation.

The service must use a persistent store for trusted-user relationships and active request/session state, but session state must expire automatically. A production implementation should expose HTTPS REST endpoints plus an authenticated WebSocket channel for real-time signaling events.

Do not place VPN packet forwarding in this service. Encrypted packet forwarding belongs to `relay/`.