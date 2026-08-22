# LINKO Control Plane

Phase 5 control-plane backend for authentication, device identity, authorization, session coordination and signaling.

## Database

LINKO uses PostgreSQL for production persistence. Set `DATABASE_URL` in the backend deployment environment. The production backend selects `PostgresControlPlaneStore` whenever `DATABASE_URL` is present and refuses to start in production without it.

The production schema is tracked in `migrations/002_linko_production.sql` and is also applied to the dedicated Supabase project used by LINKO.

Required environment variables:

```bash
PORT=8080
DATABASE_URL=postgresql://<backend-db-user>:<password>@<linko-db-host>:5432/postgres
DATABASE_SSL=true
LINKO_AUTH_SECRET=<random-secret>
LINKO_BOOTSTRAP_SECRET=<random-secret>
```

Never commit `DATABASE_URL`, database passwords, auth secrets, or bootstrap secrets.

## API

- `GET /health`
- `POST /v1/devices`
- `POST /v1/sessions`
- `GET /v1/sessions/:id`
- `POST /v1/sessions/:id/transition`
- `POST /v1/sessions/:id/signaling/ticket`
- `POST /v1/sessions/:id/signaling`
- `GET /v1/sessions/:id/signaling`
- `GET /v1/sessions/:id/tunnel`

## Production database

The control plane stores metadata only: device identity, roles, session state and tunnel authorization metadata. It does not store application traffic payloads.

RLS is enabled on the public control-plane tables as defense in depth. Anonymous and authenticated Data API roles are not granted access to these tables; the trusted backend is the system that accesses the database.

## Development

```bash
npm install
npm test
npm run build
npm start
```
