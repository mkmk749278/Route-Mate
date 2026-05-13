# Route Mates — Roadmap

_Last updated: 2026-05-13_

A feature roadmap based on a deep read of the current codebase (see
`STATUS.md`) and online research into how the leading carpool / rideshare
apps (BlaBlaCar, Uber, Lyft, Quick Ride, sRide) shape their products in
2026, plus India-specific regulatory context (MV Aggregator Guidelines 2025,
RBI 2FA from April 2026, DigiLocker for KYC).

KYC and payments-v2 are intentionally **deferred** — they are heavier
integrations and not on the critical path for product validation. We'll
revisit once Phases 1–2 + 4–10 are in.

## Execution order

```
Phase 1 (stability)        →  Phase 5 (FCM end-to-end)   →  Phase 2 (safety v1)
   ↓
Phase 4 (smart matching)   →  Phase 6 (offline-first)    →  Phase 8 (RideCheck + audio)
   ↓
Phase 9 (observability + scale)  →  Phase 10 (polish + i18n)

[deferred]  Phase 3 (DigiLocker KYC) · Phase 7 (UPI Collect + corporate rides)
```

## Phase 1 — Stability + foundations (1–2 days)

Bare minimum so we can iterate safely on top.

1. **Fix the dynamic-color crash.** `MainActivity.kt:35` calls
   `dynamicLightColorScheme(ctx)` unguarded, but that API is `@RequiresApi(31)`.
   We crash on Android 8–11 (our `minSdk = 26`). Guard with
   `Build.VERSION.SDK_INT >= S` and supply a static Material 3 fallback.
2. **Structured JSON logs + request IDs** on the backend. FastAPI middleware
   in `app/main.py` emits `{ts, level, request_id, user_id, route, latency_ms}`
   per request. Propagate the request_id to downstream logs via contextvars.
3. **Crashlytics wired conditionally.** Add `firebase-crashlytics` library
   unconditionally; apply the Crashlytics gradle plugin only when
   `google-services.json` is present (mirrors the existing google-services
   guard at `android/app/build.gradle.kts:16`).
4. **Expand pytest** beyond health / geocode / recurrence:
   - booking lifecycle (book → accept → reject → cancel; uniqueness on
     `(ride_id, rider_id)`; "driver cannot book own ride")
   - ratings (one rating per `(ride_id, from_id)`; rating updates user's
     `rating_avg` / `rating_count`)
   - recurrence: `_materialize_next_recurring` is idempotent + skips when
     a clone already exists
   - WS auth: bad token → 1008, non-participant → 1008, participant → accept
5. **Cached auth token** so the OkHttp interceptor stops calling
   `runBlocking { authStore.token() }` on every HTTP request
   (`NetworkModule.kt:37`). `AuthStore` exposes a `cachedToken()` backed by
   an `AtomicReference` updated from the DataStore flow.

## Phase 5 — FCM end-to-end (3–4 days)

The plumbing is half there: `fcm_tokens` table exists, `POST /v1/devices/fcm`
upserts a token, but **nothing ever sends a push**. Finish it.

1. **Server-side send.** Helper in `app/services/notifications.py`:
   - `booking.created` → driver
   - `booking.accepted` / `rejected` / `cancelled` → rider
   - `ride.cancelled` → all booked riders (closes the gap from
     `cancel_ride` in `app/api/rides.py:274`)
   - `ride.started` → all accepted riders ("driver is on the way")
   - `message.created` → the other side of the WS, only if their session
     isn't currently connected (track presence in Redis)
2. **`FirebaseMessagingService` on Android.** Display the notification,
   deeplink into `ride/{id}` via `PendingIntent`.
3. **Quiet hours + per-event mute** in Profile.
4. **Notification permission UX.** Request `POST_NOTIFICATIONS` on first
   sign-in (already declared in manifest).

## Phase 2 — Safety v1 (1 week)

Closes the biggest "would I let my sister use this?" gap and aligns with
the MV Aggregator Guidelines 2025 expectations.

1. **Emergency button** in `RideDetailScreen` while ride is `started`.
   Tapping it (a) opens dialer to 112, (b) pushes a share link to all
   trusted contacts via FCM topic + SMS (deferred to a `noop` provider for
   now), (c) records an incident server-side.
2. **Trusted contacts** — `User.trusted_contacts JSONB` (list of
   `{name, phone}`). Notify on ride start and on SOS.
3. **Trip share link** — public read-only URL
   `GET /v1/share/{token}` that returns ride status + last known driver pin
   + ETA. Tokens signed with HMAC, expire at `completed_at + 30 min`.
   Android Profile shows a copyable link while a ride is active.
4. **Report incident** — `POST /v1/rides/{id}/incidents` with `kind`,
   `description`, optional audio blob. Stored under
   `/var/incidents/{ride_id}/{ts}.aac` on the VPS for now.
5. **Driver/rider block** — `users.blocked_user_ids UUID[]`. Filtered out
   of search results and booking creation.

## Phase 4 — Smart matching (1 week)

The product differentiator vs. Quick Ride / sRide.

1. **Self-host OSRM.** Drop the public OSRM dependency. Add `osrm-backend`
   to `infra/docker-compose.yml`; preprocess an India OSM extract once via
   a one-shot `osrm/osrm-backend osrm-extract && osrm-contract` job.
2. **Polyline-aware search.** At ride-post time, call OSRM to get the
   route polyline; store as
   `rides.polyline_geom Geography(LINESTRING, 4326)` with a GIST index.
   Search becomes: rider's `from` is within `N` meters of the polyline,
   and rider's `to` is also within `N` meters AND comes *after* `from` on
   the line (using `ST_LineLocatePoint` to enforce direction).
3. **Detour cost** — for each candidate match, ask OSRM for the
   `driver_origin → rider_pickup → rider_drop → driver_dest` route and
   compare to the baseline. Show "+5 min for driver" in `Find` results.
4. **Learned ranker, v1.** Re-rank top-50 candidates by a hand-tuned
   linear combination of (price, detour, driver rating, on-time history).
   Start collecting data for an ML upgrade later.

## Phase 6 — Offline-first Android (1 week)

Room is already in `libs.versions.toml` but unused. Wire it up.

1. **Room entities** for `RideEntity`, `BookingEntity`, `MessageEntity`,
   `TripsCacheEntity` with `DAO`s.
2. **Single source of truth.** ViewModels observe Room flows; repositories
   refresh from network and write through. `TripsScreen`,
   `RideDetailScreen.chat`, and `FindScreen.results` go offline-first.
3. **WorkManager** for outbound chat send — queue on send failure, retry
   on reconnect.
4. **WS reconnect.** `RideSocket.kt` currently swallows `onFailure` /
   `onClosed`. Add exponential backoff with jitter (1s → 30s cap).

## Phase 8 — RideCheck-style anomaly + audio (1 week)

1. **Anomaly detection.** Backend cron compares the last known driver
   location (Redis `ride:{id}:loc`) against the planned polyline. If
   off-route by > 500 m for > 3 min, or stopped for > 5 min during a
   `started` ride, push to both sides ("Everything OK?").
2. **Audio recording, opt-in.** Toggle in Profile. Client encrypts with a
   server-derived per-ride key; uploads only when an incident is
   reported. Server-side retention 30 days, then auto-deleted. Match
   Uber's posture re: privacy.

## Phase 9 — Observability + scale (3–4 days)

1. **OpenTelemetry.** Auto-instrument FastAPI + asyncpg + redis + httpx
   with OTLP → Grafana Tempo/Loki/Prometheus (sidecar VPS or Grafana
   Cloud free tier).
2. **Rate limiting.** `slowapi` on `/auth/exchange`, `/auth/dev-login`,
   `/geocode`, `/rides/search`, WS connect.
3. **uvicorn workers > 1.** Validate WS scale with the existing Redis
   pubsub fan-out (already correctly designed in `app/ws/ride.py:32` —
   one channel per ride). Load test with `k6`.
4. **Play Integrity attestation** on `/auth/exchange` — verify the
   request comes from a real Play-installed APK. Hedge against
   dev-login abuse if the flag is ever flipped on in prod.

## Phase 10 — Polish, growth, i18n (1 week)

1. **Baseline Profiles.** Macrobenchmark module; expect ~30 % faster
   cold-start per Google's data.
2. **i18n.** Hindi + Kannada + Tamil strings. Compose preview locale
   switcher.
3. **Referral codes.** `users.referral_code`, `referred_by`; credit on
   first completed ride.
4. **Saved routes.** Driver shortcut ("Home → Office, M–F 9 AM").
5. **Multi-stop pickup ordering.** When a ride has ≥ 2 accepted riders,
   compute the optimal pickup order (cheap nearest-neighbour TSP) and
   show it to the driver.

## Migrations this implies

```
0004  users.trusted_contacts JSONB, blocked_user_ids UUID[]
0005  incidents (id, ride_id, reporter_id, kind, audio_url, created_at)
0006  share_tokens (token, ride_id, expires_at)
0007  rides.polyline_geom Geography(LINESTRING, 4326) + GIST
0008  ride_telemetry (ride_id, off_route_since, stopped_since)
0009  push_log (id, user_id, ride_id, kind, sent_at, delivered_at)
0010  user_settings (user_id, quiet_hours, mute_kinds JSONB)
0011  saved_routes (id, user_id, name, origin, destination, recurrence_days)
```

## Deferred

- **Phase 3 (Real KYC via DigiLocker / Aadhaar / DL + RC).** Required by
  MV Aggregator Guidelines 2025 before commercial scale; not on the
  critical path for product validation. Revisit after Phase 10.
- **Phase 7 (UPI Collect Request, auto-split, corporate / company groups).**
  Requires a PSP partner (Cashfree / Razorpay). UPI deeplink (v0.6) is
  enough until we have real volume.
