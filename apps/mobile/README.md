# Route Mates Mobile (Flutter)

Android-first MVP app integrated with backend APIs for:

- auth (`/auth/register`, `/auth/login`, `/auth/me`)
- profile (`/users/me` GET/PATCH)
- route posting (`/routes`, `/routes/me`)
- route discovery (`/routes/discover`)
- route interests (`/route-interests`, `/route-interests/incoming`, `/route-interests/outgoing`)

## Run locally

```bash
cd apps/mobile
flutter pub get
flutter run
```

Backend API is expected at port `3000`. See `/apps/api/README.md` for backend startup steps.

By default, debug runs use local simulator/emulator-safe URLs:
- Android emulator: `http://10.0.2.2:3000`
- iOS simulator: `http://localhost:3000`

## API base URL targets

The app reads backend URL from Dart define `API_BASE_URL`.

- Android emulator local dev:
  ```bash
  flutter run
  ```
- iOS simulator local dev:
  ```bash
  flutter run
  ```
- Physical device on LAN:
  ```bash
  flutter run --dart-define=API_BASE_URL=http://192.168.1.20:3000
  ```
- Deployed VPS/domain backend:
  ```bash
  flutter run --dart-define=API_BASE_URL=http://YOUR_VPS_IP
  # or
  flutter run --dart-define=API_BASE_URL=https://api.yourdomain.com
  ```

## Release / demo build (required production URL)

Always provide a deployed backend URL:

```bash
flutter build apk --release --dart-define=API_BASE_URL=https://api.yourdomain.com
```

Release/demo builds intentionally fail fast if `API_BASE_URL` is missing or points to local-only hosts like `10.0.2.2` or `localhost`.

## MVP flow

1. Launch app
2. Login/register if unauthenticated
3. If authenticated, use dashboard tabs for:
   - Profile view/update
   - Create Route post
   - Discover routes with optional filters + interest request action
   - Outgoing request status tracking
   - Incoming request review with accept/reject actions
4. Logout from app bar

## Quick demo flow

For a fast demo, use two users:
1. User A posts a route in **Create Route**.
2. User B finds it in **Discover** and taps **Interested**.
3. User A reviews and accepts in **Incoming**.
4. User B confirms accepted status in **Outgoing**.
