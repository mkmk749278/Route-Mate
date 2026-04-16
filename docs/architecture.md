# Route Mates MVP Architecture

## Recommended MVP Stack
- **Mobile App:** Flutter (Android-first for MVP, iOS-ready path later)
- **Backend API:** Node.js + TypeScript (NestJS or Express-based modular service)
- **Database:** PostgreSQL
- **Cache/Queue:** Redis
- **Notifications:** Firebase Cloud Messaging (FCM)
- **Deployment:** VPS with Docker + Docker Compose, Nginx reverse proxy

## System Overview
Route Mates follows a mobile-client + API backend model:
1. Flutter mobile app (Android MVP target) sends authenticated requests to backend APIs.
2. Backend stores users, routes, and matches in PostgreSQL.
3. Matching service computes route/time overlaps.
4. Notification service uses Redis jobs and FCM to notify users.
5. Nginx fronts API containers with TLS and routing on VPS.

## Core Components

### 1) Mobile App (Android)
- Flutter implementation targeting Android MVP delivery first
- Onboarding and profile
- Route post/edit (recurring + one-time)
- Match discovery and coordination entry point
- Notification handling

### 2) Backend API
- Auth and profile management
- Route lifecycle endpoints
- Match generation and retrieval
- Safety/reporting endpoints

### 3) PostgreSQL
- Source of truth for users, routes, and match records
- Indexed geospatial/time-window query support (incremental)

### 4) Redis
- Short-lived cache for hot queries
- Queue backbone for notifications and asynchronous tasks

### 5) Notifications
- Push notifications through FCM for:
  - new potential matches
  - route updates
  - trust/safety notifications

### 6) Deployment Layer
- Dockerized backend services
- Nginx reverse proxy for TLS termination and routing
- VPS-hosted PostgreSQL and Redis (or managed services if needed later)

## Initial Entities
- **User**: identity, profile, verification markers, preferences
- **RoutePost**: source, destination, schedule window, recurrence, visibility
- **MatchCandidate**: overlap score, route references, status
- **ConversationLink**: connection handoff or chat metadata
- **Report**: trust/safety flags and moderation status

## MVP Architectural Principles
- Keep the service boundaries simple and evolvable
- Prioritize reliability for route posting and match retrieval
- Optimize for local corridor density and recurring behavior
- Add complexity only when it directly improves trust or match quality
- Keep auditability for safety-critical events

## Deployment Direction (VPS)
Target initial deployment on a single VPS with clear upgrade path:
- Docker Compose for service orchestration
- Nginx for reverse proxy and HTTPS
- PostgreSQL for durable storage
- Redis for queue/cache
- Daily backups, basic monitoring, and log retention from day one

This keeps costs low and operations practical for MVP while allowing later migration to multi-node infrastructure if growth requires it.
