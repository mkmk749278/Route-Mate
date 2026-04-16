# Route Mates API

NestJS backend foundation for Route Mates MVP.

## What exists now

- Global config bootstrap via `@nestjs/config` + Joi validation
- Health endpoint: `GET /health`
- Auth foundation with JWT:
  - `POST /auth/register`
  - `POST /auth/login`
  - `GET /auth/me` (protected)
- Base scripts for dev, build, lint, unit tests, and e2e tests
- Dockerfile for containerized local/dev deployment

## Local run

```bash
npm install
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

## Scripts

```bash
npm run dev
npm run build
npm run lint
npm run test
npm run test:e2e
```
