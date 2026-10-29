# Startup Guide

How to run, inspect, reset and troubleshoot the Smart Hiring Assistant stack with Docker Compose.
For an overview of the system, see the [README](README.md).

All commands are run from the repository root. They use both Compose files; to save typing:

```bash
alias dc='docker compose -f docker-compose.yml -f docker-compose.apps.yml'
```

## 1. First-time setup

1. Start Docker Desktop. The default stack fits in 8 GB of Docker memory.
2. Create your environment file and fill in the values:

   ```bash
   cp .env.example .env
   ```

   | Required | Notes |
   |---|---|
   | `JWT_SECRET` | `openssl rand -base64 48` |
   | `RESUME_ENCRYPTION_KEY` | `openssl rand -base64 48`, and keep a copy. Upgrading with existing resumes? See [SECURITY_SETUP.md](SECURITY_SETUP.md#resume-data-encryption) |
   | `POSTGRES_USER`/`POSTGRES_PASSWORD`, `MONGO_USERNAME`/`MONGO_PASSWORD`, `RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`, `REDIS_PASSWORD`, `GRAFANA_ADMIN_PASSWORD` | Any values; they are applied when the volumes are first created |
   | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | See [SECURITY_SETUP.md](SECURITY_SETUP.md#google-sign-in) |
   | `BOOTSTRAP_ADMIN_EMAILS` | Your Google email, so you become the first HR admin |

   Optional: `OPENAI_API_KEY` (or `AI_PROVIDER=anthropic` + `ANTHROPIC_API_KEY`) for AI features, and
   `MAIL_*` + `NOTIFICATION_EMAIL_ENABLED=true` for real email.

3. Build and start everything:

   ```bash
   dc up -d --build
   ```

   - The first build downloads Gradle and the dependencies once (a few minutes). Later image builds
     reuse a shared BuildKit cache, so a service rebuild takes about 20 s.
   - `db-migrate` runs the database schema script and exits; the Postgres-backed services wait for it.
   - Services report healthy after 30–60 s.

4. Sign in to the HR dashboard (http://localhost:4200) with Google using the email from
   `BOOTSTRAP_ADMIN_EMAILS`. You are now an approved admin and can approve other HR sign-ups on the
   **Approvals** page.

## 2. What runs where

**Published to your machine**

| URL / port | Service |
|---|---|
| http://localhost:5173 | Candidate portal (nginx, proxies `/graphql` and `/api` to the gateway) |
| http://localhost:4200 | HR dashboard (nginx, same proxying) |
| http://localhost:8000 | API gateway: `/graphql`, `/graphql-ws`, `/graphiql`, `/api/**`, `/actuator/health` |
| http://localhost:8001 | auth-service (published only for the Google OAuth redirect) |
| 5432, 27017, 5672 / 15672, 6379 | Postgres, MongoDB, RabbitMQ (+ management UI), Redis |

**Internal only:** resume-parser (8002), candidate-matcher (8003), interview-prep (8004),
job-analyzer (8005), screening-bot (8006), notification (8007), ai-integration (8008).
To publish them for debugging, add the overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.debug-ports.yml up -d
```

**Optional profiles**

| Profile | Services | URLs |
|---|---|---|
| `observability` | Prometheus, Alertmanager, Grafana, Jaeger, Elasticsearch, Kibana | Grafana http://localhost:3000, Prometheus http://localhost:9090 |
| `streaming` | Zookeeper, Kafka | Kafka `localhost:9094` |
| `mailhog` | MailHog (local SMTP) | http://localhost:8025 |

```bash
dc --profile observability up -d
COMPOSE_PROFILES=observability,streaming dc up -d
```

## 3. Everyday commands

```bash
dc ps                                     # status and health
dc logs --tail=100 <service>              # recent logs of one service
dc logs --follow                          # follow everything
dc up -d --build <service>                # rebuild and restart one service
dc restart <service>
dc stop                                   # stop, keep containers and data
dc down                                   # remove containers, keep data
dc down -v                                # remove containers AND data volumes
```

`dc build --no-cache` ignores the shared Gradle cache and downloads everything again; plain
`dc build` is normally what you want.

### Data persistence

| Command | Containers | Data volumes | Schema / init scripts re-run? |
|---|---|---|---|
| `dc stop` | kept | kept | no |
| `dc down` | removed | kept | `db-migrate` re-runs (idempotent) |
| `dc down -v` | removed | **deleted** | yes, `init.sql` / `mongo-init.js` on next `up` |

Infrastructure credentials from `.env` are applied only when a volume is created. To change them later,
either keep the old values or recreate the volumes with `dc down -v`.

### Syncing jobs to the matcher

Job changes reach candidate-matcher through `JobPostingChangedEvent`. To load jobs that existed before the
sync, or to refill the matcher after its Postgres data was reset, republish every job (needs an ADMIN token).
It upserts only; it does not remove matcher rows for jobs that are gone from MongoDB.

```bash
curl -X POST http://localhost:8000/api/jobs/sync/matcher -H "Authorization: Bearer $ADMIN_TOKEN"
# {"published": 526}
```

### Email testing with MailHog

```bash
COMPOSE_PROFILES=mailhog NOTIFICATION_SPRING_PROFILES_ACTIVE=docker,mailhog dc up -d --build notification-service mailhog
```

Then open http://localhost:8025.

## 4. Smoke tests

| Command | Checks | Needs |
|---|---|---|
| `node scripts/hr-click-validate.js` | HR dashboard: login → dashboard → jobs → screening → analytics (browser, Playwright) | default stack |
| `./scripts/smoke-auth-e2e.sh` | register → login → JWT → protected resume parse | `-f docker-compose.debug-ports.yml` (calls resume-parser on 8002) |
| `./scripts/linkedin-demo.sh` | register → resume → matching → screening → decision | `-f docker-compose.debug-ports.yml`; see [docs/demo/linkedin-demo.md](docs/demo/linkedin-demo.md) |
| `AUTH_TOKEN=… USER_ID=… ./scripts/smoke-ws-auth-init.sh` | GraphQL WebSocket auth | default stack |
| `./scripts/serverless-phase1-smoke.sh` | Phase 1 Lambda build and local invoke (SAM) | see [serverless/phase1/README.md](serverless/phase1/README.md) |

The HR click test approves its own recruiter account directly in Postgres, standing in for an admin.

## 5. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `required variable … is missing a value` | A variable from `.env.example` is missing in `.env`. |
| A service is `unhealthy` | `dc logs --tail=200 <service>`. Typical causes: wrong credentials after changing `.env` on existing volumes, or the AI service without an API key (callers still work, using heuristics). |
| `password authentication failed` / `role … does not exist` | `.env` credentials differ from the ones the volume was created with: restore them or `dc down -v`. |
| Gradle download timeouts during `dc build` | Slow connection to services.gradle.org; the wrapper waits 60 s. Retry; once the cache is warm, builds don't download again. |
| AI calls return 503 | Check `dc logs ai-integration-service`. `429 … no credits` means the OpenAI account needs credit; `401` means a wrong key. |
| HR sign-up can't log in | The account is pending; an admin must approve it on the **Approvals** page. |
| Uploaded resume produces no matches | The matcher only matches against jobs in `hiring.job_requirements` (see Known limitations in the README). |
| Port already in use | `lsof -i :<port>` and stop the other process. |
