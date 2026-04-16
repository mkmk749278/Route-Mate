# Route Mates

Route Mates is a Hyderabad-first route-matching platform for daily city travel.

This repository is now in **implementation bootstrap stage**: the backend and mobile foundations are scaffolded so the next PRs can focus on auth, profiles, route APIs, and matching.

## Repository Structure

```text
Route-Mate/
├── apps/
│   ├── api/            # NestJS backend bootstrap
│   └── mobile/         # Flutter app bootstrap (Android-first, iOS-ready)
├── docs/               # Product and architecture docs
├── deploy/             # Deployment-related assets
├── scripts/            # Helper scripts
├── .env.example
├── docker-compose.yml
└── README.md
```

## Local Dependencies (PostgreSQL + Redis)

From repository root:

```bash
docker compose up -d postgres redis
```

Or run the full local stack (includes API container):

```bash
docker compose up --build
```

## Backend (NestJS) — `apps/api`

1. Install dependencies:

```bash
cd apps/api
npm install
```

2. Start in dev mode:

```bash
npm run start:dev
```

3. Health check:

```bash
curl http://localhost:3000/health
```

4. Build/lint/test:

```bash
npm run build
npm run lint
npm run test
npm run test:e2e
```

The API reads environment variables via `@nestjs/config` and validates key bootstrap settings (`APP_ENV`, `API_PORT`).

## Mobile (Flutter) — `apps/mobile`

1. Ensure Flutter SDK is installed and available in PATH.
2. Install dependencies:

```bash
cd apps/mobile
flutter pub get
```

3. Run on Android emulator/device:

```bash
flutter run
```

The mobile app includes:
- a clean `lib/app` + `lib/features` structure for MVP growth
- minimal Material 3 theme setup
- starter Route Mates home screen

## Environment Setup

Copy `.env.example` to `.env` and update values as needed for local development.

- Use `localhost` URLs when running API directly on your host (`npm run start:dev`).
- Use Docker service names (`postgres`, `redis`) when running API inside Docker Compose.

Examples:

```bash
# host-local API process
DATABASE_URL=postgresql://route_mates:route_mates@localhost:5432/route_mates
REDIS_URL=redis://localhost:6379

# API container in docker compose network
DATABASE_URL=postgresql://route_mates:route_mates@postgres:5432/route_mates
REDIS_URL=redis://redis:6379
```

## Next Stage

Bootstrap is complete; upcoming implementation PRs should cover:
- authentication and profiles
- route posting and retrieval APIs
- route matching flows and notifications
