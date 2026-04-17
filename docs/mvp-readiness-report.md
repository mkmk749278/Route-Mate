# Route Mates — MVP Readiness Report

> Generated: 2026-04-17

---

## 1. Current Implemented State

### Backend (NestJS) — `apps/api`

| Module | Status | Key files |
|--------|--------|-----------|
| Health | ✅ Implemented | `src/modules/health/health.controller.ts`, `health.service.ts` |
| Auth (JWT register / login / me) | ✅ Implemented | `src/modules/auth/auth.controller.ts`, `auth.service.ts`, `strategies/jwt.strategy.ts` |
| User profile (get / update) | ✅ Implemented | `src/modules/users/users.controller.ts`, `users.service.ts` |
| Route posts (create / my / discover) | ✅ Implemented | `src/modules/routes/routes.controller.ts`, `routes.service.ts` |
| Route interests (create / incoming / outgoing / owner decision) | ✅ Implemented | `src/modules/route-interests/route-interests.controller.ts`, `route-interests.service.ts` |
| Contact handoff (phone visible only after acceptance) | ✅ Implemented | `route-interests.service.ts` `toView()` |
| Recurring routes | ❌ Not implemented | — |
| Route/time overlap matching engine | ❌ Not implemented | — |
| Notifications (FCM / Redis queue) | ❌ Not implemented | — |
| Trust / safety / reporting | ❌ Not implemented | — |
| Redis integration in app code | ❌ Not implemented (provisioned only) | — |

### Data Model — `apps/api/prisma/schema.prisma`

Three models exist: `User`, `RoutePost`, `RouteInterest`.

Documented but **not** implemented models: `MatchCandidate`, `ConversationLink`, `Report` (from `docs/architecture.md`).

### Mobile (Flutter) — `apps/mobile`

| Feature | Status | Key files |
|---------|--------|-----------|
| Auth (login / register) | ✅ Implemented | `lib/features/auth/presentation/auth_screen.dart` |
| Session restore (persisted JWT) | ✅ Implemented | `lib/core/storage/auth_token_storage.dart` |
| Profile view / update | ✅ Implemented | `lib/features/dashboard/presentation/tabs/profile_tab.dart` |
| Create route post | ✅ Implemented | `lib/features/dashboard/presentation/tabs/create_route_tab.dart` |
| Discover routes (filter + search) | ✅ Implemented | `lib/features/dashboard/presentation/tabs/discover_routes_tab.dart` |
| Route interest action ("Interested") | ✅ Implemented | `discover_routes_tab.dart` |
| Outgoing request tracking | ✅ Implemented | `lib/features/dashboard/presentation/tabs/outgoing_requests_tab.dart` |
| Incoming request review (accept / reject) | ✅ Implemented | `lib/features/dashboard/presentation/tabs/incoming_requests_tab.dart` |
| Push notifications | ❌ Not implemented | — |
| In-app chat / messaging | ❌ Not implemented | — |

### Tests

- **API unit specs**: `health.service.spec.ts`, `auth.service.spec.ts`.
- **API e2e suite**: `test/app.e2e-spec.ts` — covers health, auth flow, profile flow, route CRUD + discover, route-interest create/duplicate/scope/accept/reject, auth-guard enforcement.
- **Mobile tests**: `test/widget_test.dart`, `test/app_controller_test.dart`.

### CI / Workflows

- **Android APK CI**: `.github/workflows/android-apk.yml` — triggers on `apps/mobile/**` changes; runs `flutter analyze`, `flutter test`, and `flutter build apk --debug`; uploads artifact.
- **Backend CI**: ❌ No workflow exists for API lint / test / build.

---

## 2. What "Complete" Should Mean for This Repo Right Now

### Local-MVP-complete (nearly true)

The core user flow works end-to-end locally:

```
register → login → update profile → post route → discover routes
  → express interest → owner accept/reject → contact handoff
```

Database schema and migrations are stable and reproducible. Mobile and API tests cover the critical path.

### Deployed-MVP-complete (not true yet)

For the current product boundary, deployed MVP means:

1. Same core flow works on a VPS over HTTPS.
2. Repeatable deployment with secrets, migrations, persistence, restart, and rollback path.
3. Minimal operations baseline: dependency-aware health checks, log retention, backup/restore process, release gate for API + mobile.

---

## 3. Exact Deployment Gaps

### What exists

| Asset | Location | Notes |
|-------|----------|-------|
| Docker Compose (postgres + redis + api) | `docker-compose.yml` | Restart policy, persistent volumes |
| API Dockerfile | `apps/api/Dockerfile` | Multi-stage Node 22 Alpine build |
| Env template | `.env.example` | Covers DB, Redis, JWT |
| Health endpoint | `GET /health` | Static status only |

### What is missing

| Gap | Details |
|-----|---------|
| **`deploy/` directory is empty** | Only contains `.gitkeep` — no scripts, runbooks, or configs |
| **No reverse proxy / TLS config** | Docs reference Nginx + TLS (`docs/architecture.md`) but no config exists |
| **No migration execution in container startup** | Compose `api` service has no migrate command; Dockerfile final stage does not copy Prisma migration assets |
| **Redis provisioned but unused** | `REDIS_URL` is passed via compose/env but `app.module.ts` config validation does not include it and no module uses Redis |
| **Health endpoint is shallow** | No DB or Redis connectivity checks |
| **No CORS / HTTP hardening** | `main.ts` does not call `enableCors()`, use Helmet, or configure proxy trust |
| **No backup / restore tooling** | Docs call for daily backups (`docs/architecture.md`) but nothing is implemented |
| **No secrets strategy beyond `.env`** | No documented VPS secret provisioning or rotation mechanism |
| **No backend CI workflow** | Only the Android APK workflow exists |
| **No release / deploy automation** | No workflow for docker image build/publish/deploy, migration job, or rollback |

### Additional likely production blockers

- No pagination or rate limiting on list endpoints (`routes.service.ts`, `route-interests.service.ts`).
- Duplicate-interest protection is application-level only; no DB unique constraint on `(routePostId, requesterUserId, status)`.

---

## 4. Recommended Next Work in Priority Order

### P0 — Required for first real deployment

1. **Deployment baseline**
   - Add production Docker Compose variant or deployment documentation.
   - Wire `prisma migrate deploy` into container startup or a dedicated release step.
   - Ensure Dockerfile copies Prisma schema and migration files into the production image.

2. **Nginx / TLS / domain wiring**
   - Commit an Nginx reverse proxy config template (or Caddy equivalent).
   - Document HTTPS setup with Let's Encrypt or manual cert provisioning.

3. **Backend CI workflow**
   - Add GitHub Actions workflow for `apps/api` covering lint, test, build (and optionally Docker build smoke check).

### P1 — Important for production quality

4. **Health / operability hardening**
   - Upgrade `/health` to check Postgres connectivity (and Redis if kept).
   - Add minimal structured logging.

5. **CORS and HTTP hardening**
   - Enable CORS for the mobile app's expected origins.
   - Consider Helmet middleware for security headers.

6. **Clarify Redis decision**
   - Either implement Redis-backed functionality (queue/cache/notifications) or remove it from the MVP deployment stack and docs.

### P2 — Housekeeping

7. **Documentation alignment**
   - Update roadmap and architecture docs to clearly separate "implemented MVP" from "post-MVP" items (matching engine, notifications, trust/reporting).

8. **Pagination and rate limiting**
   - Add cursor/offset pagination to list endpoints.
   - Add basic rate limiting middleware.

---

## 5. Suggested Split: Repo PR Work vs Manual VPS Ops

### Repo PR work

- Add deployment manifests / templates (production compose, Nginx config, `env.prod.example`, migration command wiring in Dockerfile or entrypoint).
- Add backend CI workflow.
- Improve health endpoint and config validation.
- Update docs to match actual MVP boundaries and deployment checklist.

### Manual VPS ops work

- Provision VPS, DNS, firewall, TLS certificates, and domain.
- Configure secret injection on host (secrets must not be in the repo).
- Set up backups (Postgres dump schedule + restore test) and retention.
- Set up runtime monitoring / alerting and log shipping / rotation.
- Execute first deployment and smoke-test full user flow on real network.

---

## Summary

**Local MVP core flow is implemented and test-covered.**

**Deployed MVP is not yet complete.** The repo currently lacks production deployment and operational scaffolding: Nginx/TLS config, migration automation, backend CI, backup tooling, and ops runbook. The `deploy/` and `scripts/` directories are empty placeholders.

The gap between local-working and production-deployed is well-scoped and primarily operational — it does not require new product features for a first real deployment.
