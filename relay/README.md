# LINKO Relay

The relay is a transport fallback for peers that cannot establish a direct path.

## Rules

- Relay only encrypted tunnel packets.
- Never terminate or inspect the application tunnel encryption.
- Authenticate every session with a short-lived relay credential issued by signaling.
- Enforce session expiry and bandwidth/rate limits.
- Remove relay state immediately when the signaling session is closed.

## Data path

Receiver VPN/TUN → encrypted tunnel → relay → encrypted tunnel → provider → internet.

The relay must not become the signaling server and must not issue its own long-lived credentials.