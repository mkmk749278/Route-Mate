# Route Mates Mobile (Flutter)

Android-first MVP app integrated with backend APIs for:

- auth (`/auth/register`, `/auth/login`, `/auth/me`)
- profile (`/users/me` GET/PATCH)
- route posting (`/routes`, `/routes/me`)
- route discovery (`/routes/discover`)

## Run locally

```bash
cd apps/mobile
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:3000
```

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
   - Discover routes with optional filters
4. Logout from app bar
