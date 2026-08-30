# Signaling service README

This is a minimal signaling service used by Linko to exchange session metadata and support WebSocket-based signaling.

Endpoints:
- POST /api/register  { device_id }
- GET  /ws?device_id=...  WebSocket endpoint to receive/send messages to other devices. Messages should be JSON with a `to` field.

Build and run (local):
  docker build -t linko-signaling ./backend/signaling
  docker build -t linko-relay ./backend/relay
  docker-compose -f infra/docker-compose.yml up --build
