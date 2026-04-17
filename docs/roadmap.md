# Route Mates Roadmap with PR Mapping

## Product Positioning
Route Mates is a **Hyderabad-first route-matching platform for daily city travel**.

It is:
- a route-matching product
- a trust-first local mobility network
- a recurring utility product for commuters

It is not:
- a taxi app
- a ride-hailing marketplace
- a fleet business
- a driver marketplace

---

## Phase 0 — Product Direction and Documentation Baseline
Define the product, boundaries, and initial architecture.

### Goals
- define Route Mates as a route-matching platform
- avoid taxi-marketplace drift
- create architecture and roadmap direction
- establish continuity docs

### PRs
- **PR #1** — *Establish Route Mates documentation baseline (README, owner brief, roadmap, architecture, Copilot continuity)*

### Status
**Completed**

---

## Phase 1 — Technical Foundation
Set up the repository, local infrastructure, backend scaffold, mobile scaffold, and base developer workflow.

### Goals
- backend scaffold
- Flutter scaffold
- local infra
- developer setup
- initial CI path

### PRs
- **PR #2** — *Bootstrap MVP implementation foundation: NestJS API, CI-ready Flutter scaffold, local infra, and dev setup*
- **PR #3** — *Add Android APK CI workflow for Flutter app + artifact docs*

### Status
**Completed**

---

## Phase 2 — Backend MVP Core: Authentication and Persistence
Create the real backend identity layer and move off in-memory state.

### Goals
- register/login/me
- JWT auth
- persisted users in PostgreSQL
- Prisma-backed auth storage

### PRs
- **PR #4** — *Add NestJS JWT auth foundation (register/login/me) for MVP backend*
- **PR #5** — *Persist auth users in PostgreSQL via Prisma (replace in-memory store)*

### Status
**Completed**

---

## Phase 3 — User Profile Domain
Give authenticated users a real profile they can read and update.

### Goals
- `/users/me` read/update
- profile persistence
- profile API layer for mobile integration

### PRs
- **PR #6** — *Add first user profile domain layer with authenticated `/users/me` read/update APIs*

### Status
**Completed**

---

## Phase 4 — Route Posting and Discovery
Make route creation and discovery work across users.

### Goals
- create route posts
- persist routes
- discover other users’ routes
- mobile-ready route list responses

### PRs
- **PR #7** — *Add MVP route-posting domain with Prisma persistence and authenticated `/routes` APIs*
- **PR #8** — *Add authenticated `/routes/discover` with basic cross-user filtering and mobile-ready list payload*

### Status
**Completed**

---

## Phase 5 — Mobile MVP Integration
Connect Flutter to the real backend so the core user journey becomes usable.

### Goals
- login/register in app
- token persistence / session restore
- profile fetch/update
- route creation
- route discovery
- basic dashboard flow

### PRs
- **PR #9** — *Integrate Flutter app with backend MVP APIs for auth, profile, route posting, and discovery*

### Status
**Completed**

---

## Phase 6 — Match Interest / Connection Request Flow
Turn route discovery into real user-to-user interaction.

### Goals
- express interest in another user’s route
- view outgoing requests
- view incoming requests
- owner accept/reject flow
- scoped visibility / limited contact handoff

### PRs
- **PR #10** — *Add MVP route interest request flow (API + Flutter) with scoped visibility and owner decisions*

### Status
**Completed**

---

## Phase 7 — MVP Hardening and Release Readiness
Polish the existing MVP so it is more stable, demoable, and easier to run.

### Goals
- input trimming and validation hardening
- better loading / error / empty states
- reduced mobile shared-state friction
- improved local setup docs
- demo walkthrough docs
- targeted tests

### PRs
- **PR #11** — *Polish MVP for release-readiness: stricter API input handling, resilient mobile tab states, and faster local demo setup*
- **PR #12** — *Add MVP readiness report to docs*

### Status
**Completed**

---

## Phase 8 — Deployment and Production Readiness
Make the MVP deployable on a VPS and operationally usable in a live environment.

### Goals
- production deployment config
- migration execution strategy
- reverse proxy config
- TLS / HTTPS setup
- backend CI workflow
- health checks with dependency awareness
- CORS / HTTP hardening
- backup / restore plan
- release and restart process

### PRs
- **PR #13** — *Phase 8 MVP deployment readiness: VPS scaffolding, API CI, migration wiring, and backend hardening*

### Status
**Completed**

---

## Phase 9 — Production Validation
Validate the deployed system in a real environment.

### Goals
- deploy to VPS
- point mobile app to live backend
- run two-user smoke test
- fix deployment-only blockers
- confirm logs, persistence, and restart behavior

### PRs
- **Current PR target** — production smoke-test support, runbook docs, backup/restore docs, and small operational safeguards

### Status
**Current priority**

---

## Phase 10 — Post-MVP Product Expansion
Only after the first deployed MVP is stable.

### Candidate areas
- recurring routes
- real route/time overlap matching engine
- notifications
- richer coordination flow
- trust/safety/reporting
- moderation controls
- ranking improvements
- analytics and beta instrumentation

### PRs
- none yet
- should come only after deployment success

### Status
**Future**

---

# Simple PR Timeline

1. **PR #1** — docs baseline  
2. **PR #2** — backend/mobile/local infra bootstrap  
3. **PR #3** — Android APK CI  
4. **PR #4** — JWT auth foundation  
5. **PR #5** — PostgreSQL auth persistence  
6. **PR #6** — user profile APIs  
7. **PR #7** — route posting  
8. **PR #8** — route discovery  
9. **PR #9** — Flutter backend integration  
10. **PR #10** — route interest/request flow  
11. **PR #11** — MVP polish/release readiness  
12. **PR #12** — MVP readiness report docs  
13. **PR #13** — deployment readiness (VPS assets, API CI, migration/runtime hardening)  

---

# Original Roadmap Concepts Preserved

The original roadmap themes remain valid and are now reflected in the updated execution order:
- foundation and product definition
- design and trust-first UX direction
- backend MVP
- Android app MVP
- matching and communication
- trust and safety
- testing and beta validation
- VPS deployment
- later public beta and monetization

The key change is that this roadmap now distinguishes between:
- what has already been implemented through merged PRs
- what is currently required to finish the first deployed MVP
- what should remain post-MVP expansion work

---

# Current Status Summary

## Completed PR-backed milestones
- documentation baseline
- technical foundation
- auth + persistence
- user profile domain
- route posting
- route discovery
- mobile integration
- route interest flow
- MVP hardening
- readiness reporting
- deployment readiness

## What is true right now
Route Mates is:

**deployed-MVP-ready**

but not yet:

**production-validated**

because first live smoke validation and early operations confidence steps are now the next focus.

---

# What the next roadmap item should be

## Next PR
A production-validation PR covering:
- in-repo smoke-test checklist for real MVP flow
- first-deployment verification runbook + log inspection basics
- rollback basics for single-VPS MVP
- PostgreSQL backup/restore baseline docs
- small backend safety safeguard(s) with minimal scope

## After that
Manual VPS work:
- provision VPS
- DNS / firewall / TLS
- secrets
- scheduled backup automation and restore drills
- first production validation run with two real test users

---

# Owner-Level Conclusion
If you ask "where are we on the roadmap?" the honest answer is:

- **Phases 0–7:** done
- **Phase 8:** done
- **Phase 9:** current priority
- **Phase 10:** later, optional until MVP is truly live
