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
