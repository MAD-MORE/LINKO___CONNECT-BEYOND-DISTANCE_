# LinkShare coturn relay

This Docker Compose service runs coturn as an authenticated STUN/TURN server. It performs NAT traversal and forwards encrypted tunnel packets only; the tunnel protocol (for example WireGuard) remains responsible for payload confidentiality.

## Local run

1. Copy `.env.example` to `.env` and replace `TURN_SHARED_SECRET` with a long random value. Add the local TURN URLs to the same file:

   ```powershell
   TURN_SHARED_SECRET=your-long-random-value
   TURN_URLS=turn:127.0.0.1:3478?transport=udp,turn:127.0.0.1:3478?transport=tcp
   ```

2. Run `docker compose up -d` from this directory.
3. Start the backend from `backend/` with `npm run start:turn`. This Node 22 script reads the same `relay/.env` file, so no manual environment-variable copy is required.

The compose file exposes STUN/TURN on UDP and TCP `3478`, plus relay allocation ports `49160-49200`. It disables TLS locally because no development certificate is mounted. Android devices must use the host's LAN/public address—`localhost` cannot connect a second physical phone.

## Signaling integration

After Host approval, `session.approved` includes device-specific `turnCredentials` alongside the existing `relayUrl`. The credentials use coturn's shared-secret REST mechanism: the backend creates a 15-minute HMAC-SHA1 password from `expiry:deviceId:sessionId`. Each participant receives a distinct credential. `POST /v1/sessions/{sessionId}/turn-credentials` refreshes an authenticated participant's credential while its session is `handshaking` or `connected`; the signaling server rate-limits that endpoint.

The tunnel client should try its STUN candidates first and use the supplied TURN URLs only when direct connectivity fails. It reports `path: "direct"` or `path: "relay"` to `POST /v1/sessions/{sessionId}/state`; the backend logs metadata only and never accepts tunnel payloads.

## Production notes

Use a small VM with a static public IP for two-device testing. Open UDP/TCP `3478` and the configured relay range in the cloud firewall. Set coturn's `external-ip` to the VM's public/private mapping when it is behind cloud NAT.

For TLS, mount a valid certificate/key, remove `no-tls` and `no-dtls`, and add `tls-listening-port=5349`, `cert=/path/to/fullchain.pem`, and `pkey=/path/to/privkey.pem` to the production config. Publish `turns:` URLs only after certificate validation. Never publish the shared secret or static user/password credentials.

TURN bandwidth is paid egress/ingress on the VM. Direct paths cost no relay bandwidth; monitor relay use and apply provider-level network quotas as this grows past personal testing.
