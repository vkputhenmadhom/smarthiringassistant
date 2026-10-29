# Development Guide

Working on the code: building, testing, running services outside Docker, and the frontends.
To run the whole stack in Docker, see [STARTUP_GUIDE.md](STARTUP_GUIDE.md).

## Tools

| Tool | Version | Used for |
|---|---|---|
| JDK | 21 | Backend services (Gradle toolchain) |
| Gradle | wrapper (`./gradlew`, 9.4) | Builds; no separate install needed |
| Docker Desktop | recent | Infrastructure and the full stack |
| Node.js | 20 | Frontends and `scripts/` |

## Repository layout

```
services/<name>/        one Spring Boot 4 service each: src/, build.gradle, Dockerfile
shared/shared-commons   RabbitMQ topology + JSON messaging (auto-configured in every service)
shared/shared-events    event classes shared by producers and consumers
contracts/graphql/      canonical GraphQL schema (must match the gateway's schema)
infrastructure/db/      init.sql (fresh volumes) and migrate-all-to-hiring.sql (idempotent, run by db-migrate)
frontend/               hr-admin-dashboard (Angular 17), candidate-portal (React 18 + Vite)
```

## Build and test

```bash
./gradlew build                                   # everything, with tests
./gradlew build -x test                           # faster
./gradlew :services:auth-service:test             # one module
./gradlew :services:api-gateway:test --tests '*GraphQLAuthorizationInterceptorTest'
./gradlew test jacocoTestReport                   # coverage: services/<name>/build/reports/jacoco
```

Tests run without Docker unless stated otherwise (Mockito, Spring test slices, MockMvc). The notification
service's `smokeTest` task uses Testcontainers.

When you change the gateway GraphQL schema, update `contracts/graphql/gateway/schema.graphqls` too:
`GraphqlSchemaContractTest` fails if they differ, and CI rejects breaking changes.

## Running a service on the host

Start the infrastructure and the other services in Docker, stop the one you're working on, then run it
with Gradle:

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d
docker compose -f docker-compose.yml -f docker-compose.apps.yml stop job-analyzer-service
set -a; source .env; set +a                       # JWT_SECRET, POSTGRES_*, RABBITMQ_*, REDIS_PASSWORD, ...
./gradlew :services:job-analyzer-service:bootRun
```

The service connects to `localhost`. Services read these variables:

| Settings | Variables |
|---|---|
| PostgreSQL (auth, resume-parser, candidate-matcher, notification) | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| MongoDB (interview-prep, job-analyzer, screening-bot) | `MONGODB_USER`, `MONGODB_PASSWORD` (set them to your `MONGO_USERNAME` / `MONGO_PASSWORD`), or `MONGODB_URI` |
| MongoDB (ai-integration) | `SPRING_MONGODB_URI` |
| RabbitMQ | `RABBITMQ_HOST`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` |
| Redis | `REDIS_HOST`, `REDIS_PASSWORD` |
| JWT | `JWT_SECRET` (gateway, auth, resume-parser, screening-bot) |

Note: other containers still reach the containerised copy of a service by its Docker name; a host-run
service is reachable from your machine but not from inside the Docker network.

## Frontends

```bash
cd frontend/hr-admin-dashboard
npm ci
npm start              # http://localhost:4200, proxies /graphql, /graphql-ws and /api to localhost:8000
npm test

cd frontend/candidate-portal
npm ci
npm run dev            # http://localhost:5173, same proxying (vite.config.ts)
npm test
```

Both need the gateway running on port 8000 (the Docker stack is enough).

## Messaging

- Producers write events to an outbox table; a relay publishes them as JSON (`JsonMessages.of`).
  screening-bot and job-analyzer publish objects with `convertAndSend`, which uses the shared JSON converter.
- Consumers declare their own queue with `SharedRabbitAutoConfiguration.consumerQueue(...)` and read
  typed events with `@RabbitListener`. Never share a queue between two services, or RabbitMQ will split
  the events between them.
- Rejected messages go to `events.dlq`. Inspect it in the RabbitMQ UI (http://localhost:15672).
- `EventMessagingContractTest` (candidate-matcher) checks the wire format for the pipeline events.

## AI integration

Other services call `ai-integration-service` (`POST /api/ai/completion`, `/analyze-resume`) over the
internal network; it uses Spring AI with the provider from `AI_PROVIDER`. Limits are configured in
`services/ai-integration-service/src/main/resources/application.yaml` (`ai.*`). When the service or the
provider is unavailable, callers fall back to heuristics, so features keep working without a key.

## Debugging

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml logs --tail=200 <service>
docker exec -it smart-hiring-postgres psql -U "$POSTGRES_USER" -d smart_hiring_db
docker exec -it smart-hiring-mongodb mongosh -u "$MONGO_USERNAME" -p "$MONGO_PASSWORD" --authenticationDatabase admin smart_hiring_db
```

Raise a service's log level with `LOGGING_LEVEL_ORG_VINOD=DEBUG` in its Compose `environment`.

## Git workflow

1. Branch from `dev-working`: `git switch dev-working && git pull && git switch -c feat/<topic>`.
2. Commit, push, and open a pull request into `dev-working`. CI must pass.
3. After merging, delete the branch. `dev-working` is merged into `main` once tested.
