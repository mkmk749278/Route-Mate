# Route Mates

City-focused ride sharing for Android, designed to be developed entirely from a
phone: every change is a `git push` away from a built APK on GitHub Releases
and a deployed backend on a single Ubuntu VPS.

```
android/      Native Kotlin + Jetpack Compose + Material 3
backend/      FastAPI + PostgreSQL/PostGIS + Redis
infra/        docker-compose, Caddyfile, one-shot VPS bootstrap
.github/      CI + deploy workflows
```

## How a change ships
1. Edit a file from your phone via github.com.
2. Commit to `main`.
3. GitHub Actions:
   - `backend.yml` builds `ghcr.io/<owner>/routemate-api:sha-…`, SSHes to the
     VPS, and runs `docker compose pull && up -d`. Alembic migrates on boot.
   - `android.yml` decodes the keystore + `google-services.json`, builds a
     signed APK, and attaches it to a new GitHub Release.
4. Install the APK from the Release page on your phone.

## One-time VPS bootstrap
On a fresh Ubuntu 22.04+ VPS, run:

```bash
curl -fsSL https://raw.githubusercontent.com/<owner>/Route-Mate/main/infra/bootstrap.sh \
  | sudo bash -s -- \
      --owner <github-owner> \
      --domain api.example.com \
      --postgres-pass '<strong-pw>' \
      --jwt-secret '<32-byte-pw>' \
      --ghcr-pat '<read:packages PAT>' \
      --firebase-admin-base64 "$(base64 -w0 firebase-admin.json)"
```

DNS the domain to the VPS first; Caddy issues TLS automatically on boot.

## GitHub secrets to set (Settings → Secrets and variables → Actions)
| Name | Used by |
|---|---|
| `VPS_SSH_HOST` | backend.yml deploy |
| `VPS_SSH_USER` | backend.yml deploy |
| `VPS_SSH_KEY` | backend.yml deploy (private key, full PEM) |
| `ANDROID_KEYSTORE_BASE64` | android.yml signing |
| `ANDROID_KEYSTORE_PASSWORD` | android.yml signing |
| `ANDROID_KEY_ALIAS` | android.yml signing |
| `ANDROID_KEY_PASSWORD` | android.yml signing |
| `FIREBASE_GOOGLE_SERVICES_JSON` | android.yml (base64 of `google-services.json`) |
| `API_BASE_URL` | android.yml (e.g. `https://api.example.com`) |

`GITHUB_TOKEN` is provided automatically and is used to push the image to GHCR.

`FIREBASE_ADMIN_SDK_JSON` is *not* a GitHub secret in v1 — it's written to
`/opt/routemate/secrets/firebase-admin.json` once during VPS bootstrap, and
mounted into the api container as a Docker secret. To rotate it, re-run the
bootstrap snippet with a new `--firebase-admin-base64`.

## Local-free generation of the keystore
From any phone with Termux or even a free GitHub Codespace:

```bash
keytool -genkey -v \
  -keystore release.keystore -alias routemate \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$STORE_PW" -keypass "$KEY_PW" \
  -dname "CN=Route Mates"
base64 -w0 release.keystore   # paste into ANDROID_KEYSTORE_BASE64
```

## API surface (v1)
- `POST /v1/auth/exchange` — Firebase ID token → app JWT
- `GET/PATCH /v1/me`
- `POST /v1/rides`, `GET /v1/rides/{id}`, `GET /v1/rides/search`
- `POST /v1/rides/{id}/bookings`, `POST /v1/bookings/{id}/{accept|reject|cancel}`
- `POST /v1/rides/{id}/{start|complete}`
- `POST /v1/rides/{id}/ratings`
- `POST /v1/devices/fcm`
- `WS  /v1/ws/ride/{id}?token=<jwt>` — chat + driver location

## Tech notes
- Maps: OSMDroid (OpenStreetMap tiles, no API key).
- Routing: hit a public OSRM endpoint client-side; swap to self-hosted later.
- Live location: Foreground service required by Android 14 — only runs while
  a ride is `started`.
- Future-proof: i18n hooks via `strings.xml`, payments deferred (UPI deeplink
  planned), recurring rides + private groups + KYC are post-MVP.

See `/root/.claude/plans/i-want-build-new-sleepy-pizza.md` for the full plan
and roadmap.
