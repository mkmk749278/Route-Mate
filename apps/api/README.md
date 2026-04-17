# Route Mates API

NestJS backend foundation for Route Mates MVP.

## What exists now

- Global config bootstrap via `@nestjs/config` + Joi validation
- Health endpoint: `GET /health`
- Auth foundation with JWT + persisted users in PostgreSQL:
  - `POST /auth/register`
  - `POST /auth/login`
  - `GET /auth/me` (protected)
- User profile endpoints for authenticated users:
  - `GET /users/me` (protected)
  - `PATCH /users/me` (protected)
    - updatable fields: `name`, `phone`, `city`, `gender`, `bio`, `avatarUrl`
    - `gender` accepted values: `male`, `female`, `non_binary`, `prefer_not_to_say`
    - `isProfileComplete` becomes true when `name`, `city`, and `bio` are all available
- Route post endpoints for authenticated users:
  - `POST /routes` (protected)
  - `GET /routes/me` (protected, only current user's route posts)
  - `GET /routes/discover` (protected, returns route posts from other users)
    - supported query filters (all optional):
      - `origin` (case-insensitive contains match)
      - `destination` (case-insensitive contains match)
      - `travelDate` (ISO-8601 date/datetime; filters to that UTC date)
      - `limit` (integer, default `20`, max `50`)
      - `offset` (integer, default `0`, max `1000`)
    - ordered by `travelDate` ascending, then `createdAt` descending
    - response includes route fields for browse cards and limited owner profile info: `owner.id`, `owner.name`, `owner.city`, `owner.avatarUrl`
    - create payload fields:
      - `origin` (string)
      - `destination` (string)
      - `travelDate` (ISO-8601 datetime string)
      - `preferredDepartureTime` (`HH:mm`)
      - optional: `seatCount` (integer >= 1), `notes` (string)
    - persisted route post fields include: `id`, `userId`, `status`, `createdAt`, `updatedAt`
- Base scripts for dev, build, lint, unit tests, and e2e tests
- Dockerfile for containerized local/dev deployment

## Local run

```bash
npm install
npm run prisma:generate
npm run prisma:migrate:dev
npm run start:dev
```

Health check:

```bash
curl http://localhost:3000/health
```

Auth quick check:

```bash
curl -X POST http://localhost:3000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","name":"Route User","password":"StrongPass123"}'
```

Route post quick check:

```bash
curl -X POST http://localhost:3000/routes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"origin":"Kukatpally","destination":"Gachibowli","travelDate":"2026-05-02T00:00:00.000Z","preferredDepartureTime":"09:30","seatCount":2,"notes":"Can start +/- 15 mins"}'

curl "http://localhost:3000/routes/discover?origin=miyapur&destination=hitec&travelDate=2026-06-12" \
  -H "Authorization: Bearer <JWT_TOKEN>"

curl "http://localhost:3000/routes/discover?travelDate=2026-06-12&limit=20&offset=0" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

Route interest quick check:

```bash
curl -X POST http://localhost:3000/route-interests \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"routePostId":"<ROUTE_POST_ID>"}'

curl http://localhost:3000/route-interests/outgoing \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

## Scripts

```bash
npm run dev
npm run build
npm run lint
npm run test
npm run test:e2e
npm run prisma:generate
npm run prisma:migrate:dev
npm run prisma:migrate:deploy
```
