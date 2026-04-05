# FLOW.md - Learn and Follow the System Flow

Use this as a practical path to understand, run, and debug the Smart Hiring Assistant architecture.

## 1) Understand the Big Picture (10 min)

1. Read `docs/architecture-flow.md`.
2. Identify the three critical paths: Login, Apply Job, HR Analytics.
3. Keep this model: **Frontend -> GraphQL Gateway -> Microservice(s) -> Data/Event Infra**.

## 2) Start the Stack

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d --build
```

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml ps
```

## 3) Verify Entry Points

```bash
curl http://localhost:8000/actuator/health
curl -I http://localhost:8001/api/auth/clear-session
```

- Candidate Portal: `http://localhost:5173`
- HR Dashboard: `http://localhost:4200`

## 4) Follow Login Flow

- Frontend sends GraphQL `login` to gateway.
- Gateway calls `auth-service` login endpoint.
- Auth returns JWT/refresh/user payload.
- Frontend stores `sha_token` and proceeds.

## 5) Follow Apply Job Flow

- Candidate applies from jobs UI.
- Gateway calls matching and screening services.
- Session/match response returns to frontend.
- Frontend navigates into screening/prep path.

## 6) Follow HR Analytics Flow

- HR dashboard requests `dashboardMetrics`.
- Gateway aggregates metrics from multiple services.
- Frontend renders one merged analytics payload.

## 7) Fast Smoke Tests

```bash
chmod +x scripts/smoke-auth-e2e.sh
./scripts/smoke-auth-e2e.sh
```

```bash
node scripts/hr-click-validate.js
```

## 8) Debug Playbook

1. UI issue -> browser console/network.
2. GraphQL issue -> `api-gateway` logs.
3. Auth issue -> `auth-service` logs + JWT claims.
4. Workflow issue -> target service logs.
5. Infra issue -> `docker compose ps` + health endpoints.

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml logs --tail=150 <service-name>
```

## 9) Service Responsibility Map

- `auth-service`: login/register/OAuth/JWT
- `api-gateway`: GraphQL orchestration
- `job-analyzer-service`: job metrics/data
- `candidate-matcher-service`: matching scores
- `screening-bot-service`: screening sessions
- `interview-prep-service`: prep support
- `resume-parser-service`: resume extraction
- `notification-service`: notifications

## 10) New Contributor Learning Path

1. Read `docs/architecture-flow.md`.
2. Run stack.
3. Run smoke scripts.
4. Trace one GraphQL call end-to-end.
5. Inspect one resolver + downstream controller pair.
6. Make a tiny change and re-validate.

