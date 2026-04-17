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
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:3000
```

Backend API is expected at port `3000`. See `/apps/api/README.md` for backend startup steps.

## API base URL notes

The app reads backend URL from Dart define `API_BASE_URL`.

- Android emulator to host machine API: `http://10.0.2.2:3000`
- iOS simulator to host machine API: `http://localhost:3000`
- Physical device: use your machine LAN IP, e.g. `http://192.168.1.20:3000`

Example:

```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.20:3000
```

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
