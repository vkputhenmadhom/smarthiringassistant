# Smart Hiring Assistant: Architecture Flow (One-Page)

## Stack at a Glance

- **Frontends**: Angular HR Admin (`:4200`), React Candidate Portal (`:5173`)
- **Edge/BFF**: GraphQL API Gateway (`services/api-gateway`, `:8000`)
- **Auth**: `services/auth-service` (`:8001`) with JWT + OAuth2
- **Domain services**: resume parser (`:8002`), candidate matcher (`:8003`), interview prep (`:8004`), job analyzer (`:8005`), screening bot (`:8006`), notification (`:8007`)
- **Data/infra**: PostgreSQL, MongoDB, Redis, RabbitMQ
- **Observability**: Prometheus, Grafana, Elasticsearch, Kibana, Jaeger

## Core Request Pattern

```mermaid
flowchart LR
  FE[Frontend\nAngular or React] -->|GraphQL query/mutation| GW[API Gateway\nGraphQL Resolvers]
  GW -->|REST call| SVC[Target Microservice]
  SVC --> DB[(Postgres/Mongo/Redis)]
  SVC --> MQ[(RabbitMQ Events)]
  SVC --> GW
  GW --> FE
```

- Gateway normalizes/aggregates backend responses into frontend-friendly GraphQL shapes.
- JWT is validated once at the edge, then propagated for downstream authorization context.

---

## Sequence 1: Login (Credentials)

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FE as Frontend (HR/Candidate)
  participant GW as API Gateway (GraphQL)
  participant AUTH as Auth Service

  U->>FE: Enter username + password
  FE->>GW: mutation login(username, password)
  GW->>AUTH: POST /api/auth/login
  AUTH->>AUTH: Validate credentials + load role
  AUTH-->>GW: token + refreshToken + expiresIn + user
  GW-->>FE: GraphQL AuthPayload
  FE->>FE: Persist sha_token/refresh token/user
```

**Why this path**
- Single auth implementation (`auth-service`) avoids duplicate auth logic in each frontend/service.
- Gateway keeps frontend contract stable even if auth-service payload evolves.

---

## Sequence 2: Apply Job (Candidate Flow)

```mermaid
sequenceDiagram
  autonumber
  participant C as Candidate
  participant FE as Candidate Portal
  participant GW as API Gateway (GraphQL)
  participant JM as Candidate Matcher Service
  participant SB as Screening Bot Service
  participant DB as Data Stores

  C->>FE: Click Apply on a job
  FE->>GW: mutation triggerMatching(candidateId, jobId)
  GW->>JM: POST /api/matches/trigger
  JM->>DB: Read profile + job, compute score
  JM-->>GW: CandidateMatch
  GW-->>FE: CandidateMatch

  FE->>GW: mutation startScreening(candidateId, jobId)
  GW->>SB: POST /api/screening/sessions/start
  SB->>DB: Create screening session
  SB-->>GW: ScreeningSession
  GW-->>FE: ScreeningSession (sessionId)
  FE->>FE: Navigate to interview prep/screening UI
```

**Why split matching and screening**
- Matching and screening scale independently.
- Teams can tune scoring logic without touching conversational screening workflows.

---

## Sequence 3: HR Analytics Dashboard

```mermaid
sequenceDiagram
  autonumber
  participant HR as HR Admin
  participant FE as HR Dashboard
  participant GW as API Gateway (GraphQL)
  participant JA as Job Analyzer Service
  participant SB as Screening Bot Service

  HR->>FE: Open Analytics page
  FE->>GW: query dashboardMetrics
  GW->>JA: GET /api/jobs/dashboard-metrics
  JA-->>GW: Job metrics (jobs, status, trends)
  GW->>SB: GET /api/screening/dashboard-metrics
  SB-->>GW: Screening metrics (sessions, pass/fail)
  GW->>GW: Merge/normalize into DashboardMetrics
  GW-->>FE: DashboardMetrics
  FE->>FE: Render charts/cards
```

**Why through gateway instead of direct calls**
- Frontend makes one request, gateway orchestrates many.
- Reduced frontend coupling to microservice endpoints and payload formats.

---

## Why This Architecture Works

- **Gateway as BFF**: fewer frontend round-trips, cleaner contracts.
- **Microservice boundaries**: each domain capability evolves independently.
- **Async-ready**: RabbitMQ decouples slow/non-blocking processes.
- **Polyglot data fit**: relational + document + cache where each makes sense.
- **Operability**: metrics, logs, and traces support production debugging.
