# Project Status

_Last updated: 2026-05-13_

A snapshot of where Route Mates is today. See `README.md` for setup and the
deployment story; this file tracks what has shipped, what is in flight, and
what remains.

## Current release

**v0.11.2** — `386c681` apply `google-services` plugin only when
`google-services.json` is present (unblocks dev builds without Firebase
config). Latest commit on `main` is `d87e4b9`: SignIn defaults the country
code to `+91` and normalises to E.164.

Latest APK is published on GitHub Releases by `android.yml`; the API runs as
`ghcr.io/<owner>/routemate-api:sha-…` on the VPS, redeployed by `backend.yml`
on every push to `main`.

## Shipped milestones

| Tag      | Commit    | Highlight |
|----------|-----------|-----------|
| v0.1     | `14e3e3d` | Dev-mode scaffold — app + API end-to-end without Firebase OTP. |
| v0.2     | `cc776fc` | Geocoding + functional Find / Offer flows. |
| v0.3     | `d2a8651` | Active-ride flow with map + live driver location. |
| v0.4     | `d98df32` | Instant booking + in-app chat over WebSocket. |
| v0.5     | `f4dd987` | Riders rate the driver after completion. |
| v0.6     | `d5f0854` | UPI deeplink payments — drivers add UPI ID, riders tap Pay. |
| v0.7     | `b04b095` | "Use my location" chip on Find + Offer. |
| v0.8     | `a4bb7e6` | Drivers rate accepted riders — rating loop closed. |
| v0.9     | `d80a875` | Cancel rides + bookings before start. |
| v0.10    | `c5d983a` | Recurring commute rides. |
| v0.11    | `daf308c` | Firebase Phone OTP with graceful dev-login fallback. |
| v0.11.1  | `0d8b18a` | Mount Firebase Admin SDK into api container. |
| v0.11.2  | `386c681` | Conditionally apply `google-services` plugin. |

## What's in the repo today

### Android (`android/`)
Kotlin 2.0.21 · AGP 8.6.1 · Compose BOM 2024.10 · Hilt · Navigation Compose ·
Retrofit + OkHttp · Room · DataStore · WorkManager · OSMDroid · Firebase BOM
33.5 (Auth).

Screens and view models live under `app/routemate/ui/`:
- `signin/` — phone OTP + dev-login fallback
- `find/` — search rides by from/to + time
- `offer/` — driver creates a ride (single or recurring)
- `ride/` — active-ride detail: map, chat, live driver location, ratings, UPI pay
- `trips/` — past + upcoming rides
- `profile/`

Supporting layers: `data/` (Api, repos, AuthStore, RideSocket, LocationProvider,
PlaceRepository), `service/RideLocationService.kt` (Android-14 compliant
foreground service, only while a ride is `started`), `di/NetworkModule.kt`.

### Backend (`backend/`)
Python 3.12 · FastAPI · SQLAlchemy 2 (async) · asyncpg · GeoAlchemy2 ·
Alembic · Redis · firebase-admin · python-jose.

- API routers: `auth`, `me`, `rides`, `bookings`, `ratings`, `devices`,
  `geocode`
- WebSocket: `ws/ride.py` for chat + driver-location fan-out
- Services: `services/rides.py`, `services/geocode.py`, `services/geo.py`
- Migrations: `0001_initial`, `0002_add_upi_id`, `0003_add_recurrence_days`
- Tests: `test_health.py`, `test_geocode.py`, `test_recurrence.py`

### Infra (`infra/`)
`docker-compose.yml` (api + postgres/postgis + redis + caddy), `Caddyfile`
(auto TLS), `bootstrap.sh` (one-shot Ubuntu 22.04+ VPS setup).

### CI (`.github/workflows/`)
- `ci.yml` — lint + tests on PRs
- `backend.yml` — build → push to GHCR → SSH deploy → `docker compose up -d`
- `android.yml` — decode keystore + `google-services.json`, sign APK, attach
  to a new GitHub Release

## In flight

Branch `claude/document-project-status-bYMcQ` (this commit) — documentation
only, no behavioural changes.

No open PRs or uncommitted work on `main` beyond what's tagged above.

## Known follow-ups / not yet built

Drawn from the README's "Future-proof" notes and gaps observed in the code:

- **Self-hosted OSRM** — currently hits a public OSRM endpoint client-side.
- **Self-hosted tiles** — using OSMDroid against OSM tiles directly; revisit
  if usage grows.
- **i18n** — `strings.xml` hooks are in place but no translations beyond
  English.
- **Payments beyond UPI deeplink** — no in-app settlement, no payment status
  reconciliation.
- **Private groups / KYC** — explicitly post-MVP.
- **Push notifications** — `POST /v1/devices/fcm` exists; end-to-end FCM
  delivery wiring still to verify across the booking + chat events.
- **Production hardening** — rate limiting, structured logs, error reporting,
  and a backup story for Postgres are not yet in the repo.

## Operating notes

- v0.1 ships with `DEV_LOGIN_ENABLED=true`. Disable in prod by editing
  `/opt/routemate/.env` and running `docker compose up -d`.
- The Android live-location foreground service only runs while a ride is in
  the `started` state, to stay within Android 14 foreground-service rules.
- Firebase is optional at build time — the app and API both degrade to
  dev-login when keys are absent, so phone-only development still works.
