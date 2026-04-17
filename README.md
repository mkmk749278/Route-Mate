# Route Mates

Route Mates is a Hyderabad-first route-matching platform for daily city travel.

This repository is in MVP implementation stage with backend + mobile flows for:
- auth/session
- profile update
- route posting + route discovery
- route interest request, incoming review, and outgoing tracking

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

Route interest endpoints:

- `POST /route-interests` (JWT protected) with payload `{ routePostId }`
  - rejects requesting your own route post
  - rejects duplicate active requests (`pending` / `accepted`) for the same requester + route post
- `GET /route-interests/incoming` (JWT protected, only current owner's incoming requests)
- `GET /route-interests/outgoing` (JWT protected, only current requester's outgoing requests)
- `PATCH /route-interests/:routeInterestId/owner-decision` (JWT protected, owner only) with payload `{ status: "accepted" | "rejected" }`
- responses keep profile exposure limited (`id`, `name`, `city`, `avatarUrl`), with phone shown only to the counterparty after acceptance

## Mobile (Flutter) — `apps/mobile`

1. Ensure Flutter SDK is installed and available in PATH.
2. Install dependencies:

```bash
cd apps/mobile
flutter pub get
```

3. Run on Android emulator/device (with configurable backend URL):

```bash
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:3000
```

The mobile app includes:
- lightweight API client + service integration for auth/profile/routes/route-interests
- persisted auth token restore on app startup
- MVP dashboard tabs for profile update, route posting, route discovery, outgoing requests, and incoming requests

`API_BASE_URL` notes:
- Android emulator -> host API: `http://10.0.2.2:3000`
- iOS simulator -> host API: `http://localhost:3000`
- physical device -> use your machine LAN IP, e.g. `http://192.168.1.20:3000`

## Quick local run (API + Mobile)

From repository root:

1. Start dependencies:

```bash
docker compose up -d postgres redis
```

2. Start backend:

```bash
cd apps/api
npm install
npm run prisma:generate
npm run prisma:migrate:dev
npm run start:dev
```

3. In another terminal, run mobile:

```bash
cd apps/mobile
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:3000
```

If using a physical device, replace `10.0.2.2` with your host LAN IP (for example `192.168.1.20`).

## Demo walkthrough (no seed platform required)

Use two accounts and two devices/emulators (or one app + API calls):

1. User A registers and posts a route.
2. User B registers, opens Discover, and taps **Interested** on User A route.
3. User A opens **Incoming** tab and accepts the request.
4. User B opens **Outgoing** tab and sees accepted status (with contact visibility for accepted requests).

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

Post-MVP iterations can focus on advanced matching quality, notifications, and richer coordination features.
