# Route Mates Production Smoke Test (Phase 9)

Use this checklist right after deploying to VPS to validate the real MVP flow with two users.

## Preconditions

- VPS stack is up (`postgres`, `api`, `nginx`)
- `GET /health` returns `status: healthy`
- API base URL is reachable (example: `https://api.example.com`)

Set once:

```bash
export API_BASE_URL="https://api.example.com"
```

## Smoke-test checklist (manual, scriptable with curl)

- [ ] **Register + login user A and user B**
- [ ] **Update profile for both users**
- [ ] **User A posts a route**
- [ ] **User B discovers A's route**
- [ ] **User B sends interest request**
- [ ] **User A sees incoming and accepts**
- [ ] **User B sees accepted in outgoing**
- [ ] **Repeat request flow with a second route and reject path**

### 1) Register/login

```bash
# user A
USER_A_REGISTER=$(curl -sS -X POST "$API_BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"prod-smoke-a@example.com","name":"Prod Smoke A","password":"StrongPass123"}')
USER_A_TOKEN=$(echo "$USER_A_REGISTER" | jq -r '.accessToken')

# user B
USER_B_REGISTER=$(curl -sS -X POST "$API_BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"prod-smoke-b@example.com","name":"Prod Smoke B","password":"StrongPass123"}')
USER_B_TOKEN=$(echo "$USER_B_REGISTER" | jq -r '.accessToken')
```

### 2) Profile update

```bash
curl -sS -X PATCH "$API_BASE_URL/users/me" \
  -H "Authorization: Bearer $USER_A_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"city":"Hyderabad","bio":"Daily commute","phone":"+910000000001"}'

curl -sS -X PATCH "$API_BASE_URL/users/me" \
  -H "Authorization: Bearer $USER_B_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"city":"Hyderabad","bio":"Office commute","phone":"+910000000002"}'
```

### 3) User A route post

```bash
ROUTE_A=$(curl -sS -X POST "$API_BASE_URL/routes" \
  -H "Authorization: Bearer $USER_A_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"origin":"Miyapur","destination":"HITEC City","travelDate":"2026-06-12T00:00:00.000Z","preferredDepartureTime":"09:00","seatCount":2}')
ROUTE_A_ID=$(echo "$ROUTE_A" | jq -r '.route.id')
```

### 4) User B discover

```bash
curl -sS "$API_BASE_URL/routes/discover?origin=miyapur&travelDate=2026-06-12&limit=20&offset=0" \
  -H "Authorization: Bearer $USER_B_TOKEN"
```

Confirm the response includes `ROUTE_A_ID`.

### 5) User B interest request

```bash
INTEREST_ACCEPT=$(curl -sS -X POST "$API_BASE_URL/route-interests" \
  -H "Authorization: Bearer $USER_B_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"routePostId\":\"$ROUTE_A_ID\"}")
INTEREST_ACCEPT_ID=$(echo "$INTEREST_ACCEPT" | jq -r '.interest.id')
```

### 6) User A accepts

```bash
curl -sS "$API_BASE_URL/route-interests/incoming" \
  -H "Authorization: Bearer $USER_A_TOKEN"

curl -sS -X PATCH "$API_BASE_URL/route-interests/$INTEREST_ACCEPT_ID/owner-decision" \
  -H "Authorization: Bearer $USER_A_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"accepted"}'
```

### 7) User B sees accepted outgoing

```bash
curl -sS "$API_BASE_URL/route-interests/outgoing" \
  -H "Authorization: Bearer $USER_B_TOKEN"
```

### 8) Reject path (second route)

Create a second route as user A, request as user B, then set owner decision to `rejected` and verify in outgoing.

## Post-smoke operational checks

- [ ] Restart API container and re-check `/health`
- [ ] Confirm routes/interests created during smoke test remain in DB
- [ ] Inspect API and Nginx logs for 4xx/5xx spikes during test window
