# Smart Hiring Assistant

[![Backend Services](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/backend-services.yml/badge.svg)](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/backend-services.yml)
[![Frontend Apps](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/frontend-apps.yml/badge.svg)](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/frontend-apps.yml)
[![Contracts](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/contracts.yml/badge.svg)](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/contracts.yml)
[![Phase 2 AI Integration Lambda](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/deploy-phase2-ai-integration.yml/badge.svg)](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/deploy-phase2-ai-integration.yml)
[![Phase 2 Job Analyzer Lambda](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/deploy-phase2-job-analyzer.yml/badge.svg)](https://github.com/vkputhenmadhom/smarthiringassistant/actions/workflows/deploy-phase2-job-analyzer.yml)

An event-driven hiring platform built as Java 21 / Spring Boot 4 microservices. Candidates upload resumes
and practise interviews in a React portal; HR staff manage jobs, matches and screening in an Angular
dashboard. Resumes are parsed and matched to jobs, matches start automated screening sessions, and an
LLM (OpenAI or Claude, selectable by configuration) powers resume analysis, skill extraction and
interview questions.

## Architecture

```
  React candidate portal (5173)      Angular HR dashboard (4200)
                 │  GraphQL / REST via nginx  │
                 └────────────┬───────────────┘
                              ▼
               API gateway (8000)  ── the only public entry point
               JWT verification, per-operation GraphQL authorization,
               Redis rate limiting, Resilience4j, CORS
                              │
   ┌──────────────┬───────────┼─────────────┬──────────────┬───────────────┐
   ▼              ▼           ▼             ▼              ▼               ▼
 auth-service  resume-     candidate-    screening-     job-analyzer   interview-prep
 (8001, OAuth) parser      matcher       bot            (MongoDB)      (MongoDB)
 (Postgres)    (Postgres)  (Postgres,    (MongoDB,          │              │
                           Redis)        Redis)             └──────┬───────┘
                                                                   ▼
                                                    ai-integration-service
                                                    Spring AI: OpenAI | Claude
   Events (RabbitMQ): auth ─► notification-service · resume-parser ─► candidate-matcher
                      candidate-matcher ─► screening-bot, notification-service, gateway (live updates)
                      screening-bot ─► notification-service, gateway
```

| Service | Port (internal) | Responsibility | Storage |
|---|---|---|---|
| api-gateway | 8000 (published) | Routing, JWT verification, GraphQL API and subscriptions | Redis |
| auth-service | 8001 (published for the Google OAuth redirect) | Accounts, login, Google OAuth, JWT issuing, HR account approval | PostgreSQL |
| resume-parser-service | 8002 | Resume upload and parsing (`.txt`, `.docx`) | PostgreSQL |
| candidate-matcher-service | 8003 | Matches parsed resumes to jobs | PostgreSQL, Redis |
| interview-prep-service | 8004 | Interview questions and answer feedback | MongoDB |
| job-analyzer-service | 8005 | Job description analysis, skills, salary estimate, job-board sync | MongoDB |
| screening-bot-service | 8006 | Multi-stage screening sessions | MongoDB, Redis |
| notification-service | 8007 | Email/SMS notifications from events | PostgreSQL |
| ai-integration-service | 8008 | LLM access for the other services (Spring AI) | MongoDB (response cache) |

Only the gateway, auth-service and the two frontends are published to the host. Everything else is reachable
only through the gateway or the internal Docker network.

### Highlights

- **Security:** the gateway verifies every JWT's signature and expiry and authorizes GraphQL per operation
  (public / authenticated / HR role / admin). HR accounts need an administrator's approval before they can
  sign in. Containers run as a non-root user with all Linux capabilities dropped.
- **Events:** transactional outbox in each producer; JSON messages; every consumer owns its queue (so each
  service gets every event); failed messages go to a dead-letter queue.
- **AI:** one internal AI service behind Spring AI's `ChatClient`. The provider is a configuration switch;
  the model and output limits are set server-side; responses are cached; per-user rate limiting.
- **Contracts:** the GraphQL schema is checked against `contracts/` and for breaking changes in CI.

## Quick start (Docker)

Requirements: Docker Desktop (8 GB memory is enough for the default stack). Java 21 and Node 20 are only
needed to run or test code outside containers.

```bash
cp .env.example .env
# edit .env (see "Configuration" below), then:
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d --build
```

The first build downloads Gradle and its dependencies once; later builds reuse a shared cache.
A one-shot `db-migrate` container applies the database schema before the services start.

| URL | What |
|---|---|
| http://localhost:5173 | Candidate portal |
| http://localhost:4200 | HR dashboard |
| http://localhost:8000/graphiql | GraphQL explorer |
| http://localhost:8000/actuator/health | Gateway health |
| http://localhost:15672 | RabbitMQ management (credentials from `.env`) |

**Optional services** are behind Compose profiles:

```bash
# Prometheus, Grafana, Alertmanager, Jaeger, Elasticsearch, Kibana
docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile observability up -d
# Kafka + Zookeeper (analytics pipeline)
docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile streaming up -d
```

To reach an internal service directly (debugging, the `scripts/smoke-*.sh` scripts), add
`-f docker-compose.debug-ports.yml`.

## Configuration

All settings live in `.env` (never committed); `.env.example` lists every variable.

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Signs and verifies tokens (shared by auth-service and the gateway). At least 32 random bytes, e.g. `openssl rand -base64 48`. |
| `RESUME_ENCRYPTION_KEY` | Encrypts resume files and personal data (AES-GCM). At least 32 characters; back it up. Rotation via `RESUME_ENCRYPTION_PREVIOUS_KEYS`, see [SECURITY_SETUP.md](SECURITY_SETUP.md#resume-data-encryption). |
| `POSTGRES_*`, `MONGO_*`, `RABBITMQ_*`, `REDIS_PASSWORD`, `GRAFANA_ADMIN_PASSWORD` | Infrastructure credentials, applied when a Docker volume is first created. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google sign-in (required by Compose). |
| `BOOTSTRAP_ADMIN_EMAILS` | Google-verified emails that become approved HR admins on first Google sign-in. |
| `AI_PROVIDER` | `openai` (default) or `anthropic`. |
| `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` | Key for the chosen provider. Optional overrides: `OPENAI_MODEL` (default `gpt-5.4-mini`), `ANTHROPIC_MODEL` (default `claude-opus-5-5`). |
| `MAIL_*`, `NOTIFICATION_EMAIL_ENABLED` | Outgoing email (off by default; notifications are then logged as SKIPPED). |

Without an AI key the platform still works: interview-prep and job-analyzer fall back to built-in heuristics.

## Accounts and roles

- **Candidates** register in the candidate portal (or with Google) and can sign in immediately.
- **HR accounts** (HR Admin, Recruiter) are created *pending*. They cannot sign in until an administrator
  approves them on the HR dashboard's **Approvals** page, optionally changing the role, or rejects them.
- **The first administrator** is created by putting their Google email in `BOOTSTRAP_ADMIN_EMAILS` and
  signing in to the HR dashboard with Google. Only emails Google has verified are trusted.

## Event flow

| Event | Producer | Consumers (each with its own queue) |
|---|---|---|
| `UserRegisteredEvent` | auth-service | notification-service (welcome email) |
| `ResumeParseEvent` | resume-parser-service | candidate-matcher-service |
| `CandidateMatchedEvent` | candidate-matcher-service | screening-bot (opens a session for matches ≥ 50%), notification-service, api-gateway (live updates), analytics-bridge |
| `ScreeningCompletedEvent` | screening-bot-service | notification-service, api-gateway, analytics-bridge |
| `JobAnalyzedEvent` | job-analyzer-service | (none yet) |

Exchanges and the dead-letter queue are declared for every service by `SharedRabbitAutoConfiguration` in
`shared/shared-commons`; topology names live in `RabbitMQConfiguration`.

## Development

```bash
./gradlew test                                      # all modules
./gradlew :services:auth-service:test               # one service
./gradlew :services:api-gateway:bootRun             # run a service on the host (needs the infra containers and .env values)
```

Frontends with hot reload (they proxy `/graphql` and `/api` to the gateway on port 8000):

```bash
cd frontend/hr-admin-dashboard && npm ci && npm start      # http://localhost:4200
cd frontend/candidate-portal   && npm ci && npm run dev    # http://localhost:5173
```

HR dashboard click-path smoke test (Playwright; also runs in CI):

```bash
node scripts/hr-click-validate.js
```

See [DEVELOPMENT.md](DEVELOPMENT.md) for more, [STARTUP_GUIDE.md](STARTUP_GUIDE.md) for running and
troubleshooting the stack, and [SECURITY_SETUP.md](SECURITY_SETUP.md) for secrets and OAuth setup.

### Branches

Work happens on feature branches with pull requests into `dev-working`, the integration branch where
features are tested together. `dev-working` is merged into `main` once it has been tested.

## CI and deployment

| Workflow | What it does |
|---|---|
| `backend-services.yml` | Builds and tests changed services, coverage, SonarQube quality gate; manual deploy (Kubernetes or CodeDeploy) |
| `frontend-apps.yml` | Builds both frontends; HR click-path smoke test against the Docker stack |
| `contracts.yml` | Checks the GraphQL schema matches `contracts/` and has no breaking changes |
| `deploy-phase2-*.yml`, `serverless-phase1.yml` | Build, test and deploy the AWS Lambda functions |

- **Kubernetes:** Helm chart in [`helm/smart-hiring-assistant`](helm/smart-hiring-assistant/README.md)
  (`values-staging.yaml`, `values-prod.yaml`), and Kustomize overlays in `infrastructure/kubernetes/overlays`,
  which the CI deploy job applies. See [docs/deployment/kubernetes-autoscaling.md](docs/deployment/kubernetes-autoscaling.md).
- **Serverless:** Quarkus Lambdas for resume parsing, AI integration and job analysis, deployed with SAM.
  See [serverless/phase2/README.md](serverless/phase2/README.md).
- **AWS CI/CD:** [docs/deployment/aws-cicd.md](docs/deployment/aws-cicd.md).

## Project structure

```
services/          Spring Boot microservices (one directory each, with its own Dockerfile)
shared/            shared-commons (Rabbit config, JSON messaging), shared-events (event classes),
                   analytics-events, grpc-definitions
frontend/          hr-admin-dashboard (Angular 17), candidate-portal (React 18 + Vite)
contracts/         GraphQL, AsyncAPI and gRPC contracts
infrastructure/    db (schema scripts), messaging, monitoring (Prometheus, Grafana), kubernetes (Kustomize)
helm/              Helm chart
serverless/        AWS Lambda functions (Quarkus) and SAM templates
streaming/, batch/, pipelines/   Flink, Spark and Beam analytics jobs
scripts/           smoke tests and the HR click-path validation
docs/              guides, deployment notes, frontend deep dives; docs/archive holds past reports
```

## Known limitations

- **Matching needs job data in the matcher's database.** Jobs live in job-analyzer's MongoDB and are not
  yet synced to candidate-matcher, so uploaded resumes only produce matches for jobs present in
  `hiring.job_requirements`.
- **PDF resumes are not parsed yet** (the PDF extractor is a stub); `.txt` and `.docx` work.
- **Logs and traces are not shipped anywhere yet.** The observability profile provides Prometheus metrics
  and Grafana dashboards; Elasticsearch/Kibana and Jaeger run but services do not send logs or traces to them.

## More documentation

- [STARTUP_GUIDE.md](STARTUP_GUIDE.md): running, stopping, resetting and troubleshooting the stack
- [DEVELOPMENT.md](DEVELOPMENT.md): local development, tests, debugging
- [SECURITY_SETUP.md](SECURITY_SETUP.md): secrets, OAuth, accounts, hardening
- [docs/](docs/): architecture flow, deployment, monitoring, frontend deep dives, interview kits
- [docs/archive/](docs/archive/README.md): past reports and plans (not maintained)

## Author

Vinod Puthenmadhom
