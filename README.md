# Route Mates

Route Mates is a Hyderabad-first route-matching platform for daily city travel.

This repository is now in **implementation stage**: the backend and mobile foundations are scaffolded, with core auth/profile/route posting APIs in place for upcoming matching and coordination features.

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
npm run prisma:generate
npm run prisma:migrate:dev
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
npm run prisma:generate
npm run prisma:migrate:dev
npm run prisma:migrate:deploy
```

The API reads environment variables via `@nestjs/config` and validates key bootstrap settings (`APP_ENV`, `API_PORT`, `DATABASE_URL`, `JWT_SECRET`, `JWT_EXPIRES_IN`).

Auth endpoints:

- `POST /auth/register` (email, name, password)
- `POST /auth/login` (email, password)
- `GET /auth/me` (JWT protected)

Profile endpoints:

- `GET /users/me` (JWT protected)
- `PATCH /users/me` (JWT protected)
  - accepted fields: `name`, `phone`, `city`, `gender` (`male` | `female` | `non_binary` | `prefer_not_to_say`), `bio`, `avatarUrl`
  - profile completion is persisted as `isProfileComplete` and is set when `name`, `city`, and `bio` are all present

Route post endpoints:

- `POST /routes` (JWT protected)
- `GET /routes/me` (JWT protected, only current user's route posts)
- `GET /routes/discover` (JWT protected, only route posts from other users)
  - optional query filters: `origin`, `destination` (case-insensitive contains), `travelDate` (ISO-8601 date/datetime for same UTC day)
  - deterministic sort: `travelDate` ascending, then `createdAt` descending
  - includes route listing fields plus limited owner info for mobile browse cards: `owner.id`, `owner.name`, `owner.city`, `owner.avatarUrl`
  - create payload: `origin`, `destination`, `travelDate` (ISO-8601), `preferredDepartureTime` (`HH:mm`), optional `seatCount`, optional `notes`
  - response includes route post metadata (`id`, `userId`, `status`, `createdAt`, `updatedAt`)

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

## Android APK CI (GitHub Actions)

Route Mates uses GitHub Actions as the primary Android build path right now.

- Workflow file: `.github/workflows/android-apk.yml`
- Trigger: `push` and `pull_request` changes related to `apps/mobile`
- CI steps: `flutter pub get`, `flutter analyze`, `flutter test`, and `flutter build apk --debug`
- Output: downloadable APK artifact from each workflow run (`route-mates-android-debug-apk`)

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
- mobile integration for auth/profile/routes
- route matching flows
- notifications and downstream ride coordination
