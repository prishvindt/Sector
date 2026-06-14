# Sector Backend Skeleton

This directory contains the first skeleton for the future Sector backend. It is intentionally separate from the Android application and does not implement registration, login, contacts, object sync, live location, media upload, or the web sector map.

The current goal is only to provide a minimal NestJS service boundary with environment configuration, health/version endpoints, Docker Compose for local dependencies, and placeholder modules for future work.

## Stack

- NestJS
- PostgreSQL through Prisma and `@prisma/client`
- Redis as a future cache/session/websocket dependency
- Docker Compose for local development

The Prisma schema contains only a placeholder model so the client can be generated. It is not a business schema and no migrations are included yet.

## Local Run With Docker

```bash
cd server
docker compose up --build
```

Docker Compose uses values from `.env` automatically if that file exists. Otherwise it falls back to development defaults defined in `docker-compose.yml`. Do not commit real `.env` files.

Endpoints:

```text
GET http://localhost:3000/api/health
GET http://localhost:3000/api/version
```

## Local Run Without Docker

```bash
cd server
npm install
npm run build
npm run start:prod
```

For local development without Docker, provide PostgreSQL and Redis separately or use the default env values from `.env.example` as a starting point. The current health endpoint reports database and Redis as `not_checked`; real readiness checks are a separate future task.

## Environment

```env
NODE_ENV=development
PORT=3000
API_PREFIX=/api
SERVER_VERSION=0.1.0
DATABASE_URL=postgresql://sector:sector_password@postgres:5432/sector
REDIS_URL=redis://redis:6379
JWT_ACCESS_SECRET=change_me_access_secret
JWT_REFRESH_SECRET=change_me_refresh_secret
ENABLE_SWAGGER=true
LOG_LEVEL=debug
```

`JWT_ACCESS_SECRET` and `JWT_REFRESH_SECRET` are placeholders only. Real secrets must be provided outside git.

## Implemented Endpoints

### `GET /api/health`

```json
{
  "status": "ok",
  "service": "sector-backend",
  "timestamp": "2026-06-12T00:00:00.000Z",
  "database": "not_checked",
  "redis": "not_checked"
}
```

### `GET /api/version`

```json
{
  "service": "sector-backend",
  "version": "0.1.0",
  "nodeEnv": "development"
}
```

## Security Constraints

- Do not store plaintext coordinates.
- Do not store plaintext notes.
- Do not store user private keys.
- Do not log full `encrypted_payload` values.
- Do not add live location before auth, contacts, and production-ready crypto are designed.
- Treat encrypted payloads as opaque blobs on the server.
- Validate envelope metadata only; do not inspect plaintext payload contents.

These constraints follow `docs/SERVER_BACKEND_ARCHITECTURE.md` and `docs/SECURITY_AND_SYNC_ARCHITECTURE.md`.

## Relay-Only And Custom Server Direction

The current backend skeleton does not implement relay, auth, contacts, encrypted objects, media delivery, or live location yet. The future server model must support relay-only operation for private user data.

Direction for future implementation:

- the server must not become a permanent cloud backup for user data;
- encrypted payload and encrypted media must have TTL;
- custom/self-hosted servers must declare capabilities;
- the client must verify capabilities before sending sensitive payload;
- private keys are never stored on the server;
- plaintext coordinates, notes, live location, azimuth rays, or media are forbidden in production;
- relay-only storage is limited to temporary delivery queues and minimal service metadata;
- delete-after-delivery and TTL cleanup must be part of the server policy.

Permanent user backups are expected to be local encrypted Sector backup zip files managed by the user. Server backups should cover service database, configuration, and audit logs, not a permanent user archive.

## Future Endpoint

Future custom/self-hosted support should expose:

```text
GET /api/server/capabilities
```

The endpoint should describe server name, operator name, deployment mode, data residency, crypto profile, relay-only status, TTL policy, delete-after-delivery support, and feature flags. It is intentionally not implemented in this documentation-only task.

## Placeholder Modules

The skeleton includes empty modules for future backend areas:

- `auth`
- `users`
- `devices`
- `keys`
- `contacts`
- `objects`
- `live`
- `crypto`

No public endpoints are exposed for these modules yet.

## Intentionally Not Implemented

- registration
- login
- JWT guards
- refresh token storage
- public key upload
- contacts
- encrypted object upload/download
- live websocket
- live GPS
- web sector-map
- media upload
- real business migrations
- production deployment
- CI/CD
- HTTPS, Nginx, or Caddy

## Next Steps

1. Auth, users, devices, and public keys.
2. Contacts.
3. Encrypted object upload/download.
4. Android sync client.
5. Live location.
6. Encrypted media.
7. Sector-map web.
