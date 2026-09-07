# LINKO Relay

Optional UDP forwarding plane for LINKO. The relay is isolated from the Android direct-P2P implementation and is not a required dependency for direct connections.

## Data-plane behavior

LINKO v2 tunnel frames are already AES-GCM authenticated and encrypted by the Android client. After an authenticated `HELLO`, the relay forwards those binary frames byte-for-byte. It does **not** decrypt, terminate, rewrite, or inspect tunnel payloads.

Control frame:

`HELLO|<sessionId>|<token>|provider`

or

`HELLO|<sessionId>|<token>|receiver`

Legacy wrapped frames are also accepted:

`DATA|<sessionId>|<token>|<role>|<opaque encrypted payload>`

`PING|<sessionId>|<token>|<role>` keeps the authenticated session alive.

## Android integration

`android/app/src/main/java/com/linkshare/app/network/LinkoRelayClient.kt` contains the optional relay bootstrap client. It derives a short-lived session token from the 32-byte LINKO session key and sends the authenticated `HELLO` on the same UDP socket that will carry the encrypted tunnel frames.

After `prepare(...)` succeeds, the caller can construct the existing `EncryptedDatagramTunnel` with the returned relay endpoint. The existing tunnel encryption and packet format do not need to change.

The client is intentionally opt-in: no relay hostname is hard-coded and direct P2P remains the primary transport.

## Run locally

```bash
npm install
npm run build
RELAY_PORT=3479 npm start
```

UDP listens on `3479`; health is exposed on TCP `8080`.

## Security

Relay session credentials are derived from the per-session tunnel key and stored only as a SHA-256 hash. Endpoint forwarding requires a prior authenticated `HELLO` and an exact provider/receiver socket binding. Expired sessions are removed automatically.

For Internet deployment, put the relay behind your own infrastructure and firewall. LINKO does not require a website for the relay itself; it is a UDP service.
