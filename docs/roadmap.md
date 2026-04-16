# Route Mates Roadmap

## Phase 1: Foundation & Product Definition
- Finalize owner brief, roadmap, and architecture direction
- Lock product boundaries (route matching, not taxi marketplace)
- Define MVP success metrics (match rate, recurrence, retention)

## Phase 2: Design & UX
- Create user journeys for onboarding, route posting, and matching
- Design trust-first UX (verification indicators, reporting access, clear profile signals)
- Build lightweight Flutter UI prototypes for Android MVP feedback

## Phase 3: Backend MVP
- Set up API foundation and authentication
- Implement route posting (one-time and recurring)
- Implement basic route + schedule overlap matching
- Add notification pipeline and moderation-ready event logging

## Phase 4: Android App MVP
- Build Android app core flows using Flutter (Android-first delivery):
  - onboarding and profile
  - create/manage routes
  - view matches
  - coordinate after match
- Add push notifications for new matches and updates

## Phase 5: Matching & Communication
- Improve match ranking quality for recurring travel behavior
- Add practical communication flow (in-app chat or controlled contact handoff)
- Support preference filters (e.g., women-only/community-only visibility)

## Phase 6: Trust & Safety
- Profile verification markers and reporting tools
- Abuse/spam controls and moderation workflows
- Safety messaging and user education in product flows

## Phase 7: Testing & Closed Beta
- Internal QA and pilot testing
- Closed beta on selected Hyderabad corridors
- Instrument core analytics and iterate on conversion, match quality, and retention

## Phase 8: VPS Deployment
- Deploy backend and supporting services to VPS with Docker
- Configure Nginx reverse proxy, TLS, observability basics, and backups
- Establish release process for backend and Android beta builds

## Phase 9: Public Beta & Monetization (Later)
- Expand public beta within Hyderabad based on corridor density
- Introduce premium power-user features after consistent match success
- Launch paid private networks for communities/organizations in later stage
