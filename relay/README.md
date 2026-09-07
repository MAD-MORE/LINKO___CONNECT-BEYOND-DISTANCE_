# LINKO Relay

Stateless-ish UDP forwarding plane for LINKO. The relay forwards the already-encrypted tunnel payload and does not terminate the user tunnel.

## Protocol

Control frame:

`HELLO|<sessionId>|<token>|provider`

or

`HELLO|<sessionId>|<token>|receiver`

Data frame:

`DATA|<sessionId>|<token>|<role>|<opaque encrypted payload>`

`PING|<sessionId>|<token>|<role>` keeps the session alive.

The relay authenticates the session and role, records the endpoint, then forwards only the opaque payload to the opposite endpoint.

## Run

```bash
npm install
npm run build
RELAY_ADMIN_TOKEN=<strong-secret> npm start
```

UDP listens on `3479`; health is exposed on TCP `8080`.

## Production note

The bearer token in this first wire-compatible implementation is a short-lived session credential. Before exposing the relay to the public Internet, the Android client and backend should be upgraded to an authenticated encrypted control handshake (challenge/HMAC or an equivalent secure session-ticket design), plus replay protection and per-session sequence numbers.
