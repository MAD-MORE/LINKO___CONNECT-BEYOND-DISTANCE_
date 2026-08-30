# Signaling API README additions

New endpoints added:
- POST /api/pair  { device_a, device_b }  => creates a session and returns { session, token }
- GET  /api/sessions  => list recent sessions (operator use)

WebSocket behavior:
- Clients connect to /ws?device_id=<id>&token=<optional_session_token>
- Messages must be JSON and can include "to", "type", "session_id", and other payload fields. The server forwards messages to the `to` device if connected and logs events to audits.

DB: Postgres is used by default in docker-compose; credentials are in infra/docker-compose.yml (dev only — rotate in prod!).
