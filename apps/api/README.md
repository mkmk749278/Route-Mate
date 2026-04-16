# Route Mates API

NestJS backend bootstrap for Route Mates MVP.

## What exists now

- Global config bootstrap via `@nestjs/config` + Joi validation
- Health endpoint: `GET /health`
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

## Scripts

```bash
npm run dev
npm run build
npm run lint
npm run test
npm run test:e2e
```
