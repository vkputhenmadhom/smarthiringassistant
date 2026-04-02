# Serverless Migration & Microservice Separation Roadmap

## Executive Summary

Transform your monolithic multi-service codebase into independently deployable, failure-isolated microservices with selective serverless migration. This document provides a phased approach to:

1. **Migrate stateless services to AWS Lambda** (Job Analyzer, Interview Prep, Screening Bot, AI Integration, Notifications)
2. **Keep stateful services in containers** (API Gateway, Auth, Candidate Matcher)
3. **Separate into independently deployable artifacts** with per-service CI/CD
4. **Reduce cascading failures** through event-driven communication and circuit breakers

---

## Part 1: Service Serverless Candidacy Analysis

### Current Services Inventory

```
┌─────────────────────────────────────────────────────────────────┐
│                     SMART HIRING ASSISTANT                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  STATELESS, COMPUTE-HEAVY (✅ SERVERLESS CANDIDATES)            │
│  ─────────────────────────────────────────────────────────────   │
│  • Job Analyzer Service        (100-500ms execution)            │
│  • Interview Prep Service      (2-5s, AI calls)                 │
│  • Screening Bot Service       (1-3s decision logic)            │
│  • AI Integration Service      (2-10s, external API calls)      │
│  • Notification Service        (500ms-2s delivery)             │
│                                                                  │
│  STATEFUL, PERSISTENT (❌ STAY IN CONTAINERS)                   │
│  ─────────────────────────────────────────────────────────────   │
│  • API Gateway                 (routing, session mgmt)          │
│  • Auth Service                (JWT, token management)          │
│  • Candidate Matcher           (database-heavy queries)         │
│  • Resume Parser               (hybrid - Lambda for parsing,    │
│                                 REST for intake)                │
│                                                                  │
│  DATA LAYER                                                     │
│  ─────────────────────────────────────────────────────────────   │
│  • PostgreSQL (shared for Auth, Candidates)                    │
│  • MongoDB (document storage for resumes, prep materials)      │
│  • DynamoDB (Lambda state, caches) - NEW                       │
│  • Redis (session, cache) - shared by containers               │
│                                                                  │
│  COMMUNICATION PATTERNS                                         │
│  ─────────────────────────────────────────────────────────────   │
│  • REST/GraphQL - API Gateway (sync)                          │
│  • gRPC - inter-service communication (sync)                   │
│  • RabbitMQ → SNS/SQS (async, event-driven) - MIGRATION        │
│  • S3 Triggers → Lambda (file-based workflows) - NEW            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Service-by-Service Analysis

| Service | Current | Serverless? | Reasoning | Migration Effort |
|---------|---------|-------------|-----------|------------------|
| **Resume Parser** | Container | ✅ Hybrid | Lightweight parsing, I/O-bound | DONE (Phase 1) |
| **Job Analyzer** | Container | ✅ Yes | Short-lived, compute, OpenAI calls | Medium |
| **Interview Prep** | Container | ✅ Yes | Short-lived, AI calls (2-5s) | Medium |
| **Screening Bot** | Container | ⚠️ Maybe | Multi-step state (up to 15min) | High |
| **AI Integration** | Container | ✅ Yes | Pass-through to OpenAI/Claude | Low |
| **Notification** | Container | ✅ Yes | Short-lived, no persistence | Low |
| **Candidate Matcher** | Container | ❌ No | Long-running queries, state | Keep as ECS |
| **Auth Service** | Container | ❌ No | Session management, JWT tokens | Keep as ECS |
| **API Gateway** | Container | ❌ No | Routing, load balancing, WebFlux | Keep as ECS |

### Serverless Candidacy Scoring

**Job Analyzer Service**
- ✅ Execution time: 100-500ms
- ✅ Stateless (query → analysis → response)
- ✅ Event-triggered (new job posting)
- ✅ Bursty traffic pattern
- **VERDICT**: **IMMEDIATE MIGRATION** (Phase 2)

**Interview Prep Service**
- ✅ Execution time: 2-5s (AI calls)
- ✅ Mostly stateless (question generation)
- ✅ Question caching in DynamoDB possible
- ⚠️ MongoDB reads for prep materials
- **VERDICT**: **MIGRATE** (Phase 2, with DynamoDB caching)

**Screening Bot Service**
- ✅ Execution time: 1-3s per decision
- ⚠️ Multi-step workflow (up to 15 minutes overall)
- ⚠️ Session state management
- ⚠️ Database updates needed
- **VERDICT**: **CONDITIONAL** (Phase 3, refactor to Step Functions + Lambda)

**AI Integration Service**
- ✅ Execution time: 2-10s (external API)
- ✅ Pure pass-through, no logic
- ✅ No state
- ✅ External API dependent
- **VERDICT**: **EASY MIGRATION** (Phase 2)

**Notification Service**
- ✅ Execution time: 500ms-2s
- ✅ Stateless
- ✅ Event-driven (email, SMS)
- ✅ SES/SNS integration ready
- **VERDICT**: **EASY MIGRATION** (Phase 2 or 3)

---

## Part 2: Migration Phases

### Phase 1: ✅ COMPLETED
**Resume Parser Service (Proof of Concept)**
- ✅ Quarkus Lambda function deployed
- ✅ API Gateway integration
- ✅ SAM template + deployment script
- ✅ Health endpoint working

**Lessons Learned**:
- Quarkus cold start: ~2s (acceptable)
- Zipfile size: 25-30MB (good)
- API Gateway contract working well
- Need event-driven trigger capability

---

### Phase 2: 🎯 PRIORITY (Immediate)
**Job Analyzer + Interview Prep + AI Integration**

**Timeline**: 4-6 weeks

**Services**:
1. **Job Analyzer Service** → Lambda
2. **Interview Prep Service** → Lambda (with DynamoDB caching)
3. **AI Integration Service** → Lambda

**Infrastructure Changes**:
- Add DynamoDB table for question cache
- Add SNS topic for job-posting events
- Create Lambda IAM roles with DynamoDB/SNS permissions
- Add S3 trigger for batch job analysis

**Communication**:
- REST API Gateway → Lambda (keep existing REST endpoints)
- SNS events from API Gateway → Lambda (new async path)
- Job Analyzer publishes to SNS for downstream services
- Interview Prep reads from DynamoDB cache

**Testing**:
- LocalStack for local Lambda testing
- Contract tests for API compatibility
- Load test: 100 concurrent job submissions

**Rollout Strategy**:
1. Deploy Lambda in parallel with existing service
2. Route 10% of traffic to Lambda (canary)
3. CloudWatch metrics + alarms
4. Gradual increase: 50% → 100%
5. Decommission existing container service

**Cost Estimate**:
- Current (ECS): ~$200/month per service
- Lambda: ~$50/month (assuming moderate load)
- **Savings**: $450/month for Phase 2

---

### Phase 3: 🔄 SECONDARY (Mid-term)
**Screening Bot + Notification Service**

**Timeline**: 6-8 weeks

**Key Challenge**: Screening Bot state management

**Options**:
1. **Option A**: AWS Step Functions + Lambda
   - Orchestrates multi-step workflows
   - Maintains state in DynamoDB
   - Natural session handling
   - Cost: ~$0.000025 per state transition
   - **RECOMMENDED**

2. **Option B**: Keep in ECS, extract core logic
   - Less refactoring needed
   - Still benefits from isolation
   - Better for long sessions (>15 min)

**Recommended Approach**:
```
User Interaction
    ↓
API Gateway (ECS)
    ↓
Step Functions (state machine)
    ├─ Lambda 1: Initial screening (0.5s)
    ├─ Lambda 2: Question generation (1s)
    ├─ Lambda 3: Answer evaluation (2s)
    └─ Database update (in final Lambda)
    ↓
Response to Client
```

**Implementation**:
- Refactor Screening Bot into 3-4 Lambda functions
- Create Step Functions state machine
- DynamoDB for session storage (TTL = 24 hours)
- SNS notifications on completion

**Notification Service**:
- Simple event consumer
- Call AWS SES for email
- Call AWS SNS for SMS
- No refactoring needed, just repackage as Lambda

---

### Phase 4: ✨ OPTIMIZATION (Long-term)
**Refactor API Gateway + Cost Optimization**

**Activities**:
- Consolidate API Gateway (Spring Cloud Gateway → API Gateway native routing)
- Implement circuit breakers for Lambda calls
- Analyze cost vs. performance
- Consider Graviton2 processors for ECS
- Lambda version management and aliases

---

## Part 3: Microservice Separation Strategy

### Current Problem
```
MONOLITHIC REPOSITORY
└── All services + shared deps in one Git repo
    ├── Single build.gradle with all projects
    ├── Shared database connections
    ├── Tight coupling via shared-commons
    └── One failing test blocks all deployments ❌
```

### Proposed Solution: Multi-Module Monorepo with Independent CI/CD

```
SmartHiringAssistant/
├── services/
│   ├── api-gateway/              → ECS (always deployed)
│   ├── auth-service/             → ECS (always deployed)
│   ├── candidate-matcher/        → ECS (always deployed)
│   │
│   ├── resume-parser/            → Lambda (Phase 1) ✅
│   ├── job-analyzer/             → Lambda (Phase 2) 🎯
│   ├── interview-prep/           → Lambda (Phase 2) 🎯
│   ├── ai-integration/           → Lambda (Phase 2) 🎯
│   ├── notification/             → Lambda (Phase 3)
│   └── screening-bot/            → Step Functions (Phase 3)
│
├── shared/
│   ├── shared-commons/           → Core DTOs, exceptions
│   ├── grpc-definitions/         → gRPC proto files
│   ├── shared-events/            → Event schemas (AsyncAPI)
│   └── contracts/                → API contracts (OpenAPI, GraphQL)
│
├── infrastructure/
│   ├── cloudformation/           → IaC for Lambda, DynamoDB, SNS, SQS
│   ├── kubernetes/               → ECS task definitions
│   └── terraform/                → VPC, RDS, security groups
│
├── frontend/
│   ├── admin-dashboard/          → Angular (separate deploy)
│   └── candidate-portal/         → React (separate deploy)
│
└── scripts/
    ├── deploy-lambda.sh          → Selective Lambda deployment
    ├── deploy-ecs.sh             → ECS service deployment
    ├── test-integration.sh        → Cross-service tests
    └── rollback.sh               → Safe rollback mechanism
```

### Benefits

| Benefit | How |
|---------|-----|
| **Independent Deployments** | Service-level CI/CD pipelines in GitHub Actions |
| **Reduced Blast Radius** | Failing Job Analyzer doesn't block Auth Service |
| **Technology Flexibility** | Lambda services can use different runtimes than ECS |
| **Cost Control** | Pay only for what you use (Lambda), reduce container overhead |
| **Team Scaling** | Teams own specific services end-to-end |
| **Faster Tests** | Each service tests independently |

### Implementation: Build Gradle Restructuring

**Current**: All projects in root `build.gradle`
```groovy
project(':services:job-analyzer-service') {
    dependencies {
        // All dependencies mixed
    }
}
```

**Proposed**: Each service has own `build.gradle` with selective sharing
```
services/job-analyzer/
├── build.gradle                  ← Service-specific deps
├── src/
├── Dockerfile
├── samconfig.toml               ← Lambda config (serverless)
└── .github/workflows/deploy.yml ← Service-level CI/CD
```

**Root build.gradle becomes**:
```groovy
// Only shared definitions
ext {
    springBootVersion = '4.0.5'
    // ... common versions
}

// Only shared modules
project(':shared:shared-commons') { ... }
project(':shared:grpc-definitions') { ... }
```

### CI/CD Separation

**Current** (monolithic):
```
Push → Build All → Test All → Deploy All (or nothing)
```

**Proposed** (per-service):
```
Push to services/job-analyzer/
    ↓
Job Analyzer Pipeline
├─ Build JAR/ZIP
├─ Unit tests
├─ Integration tests (with LocalStack)
├─ Deploy to Lambda Staging
├─ Smoke tests
└─ Deploy to Production (approval gate)

Meanwhile:

Push to services/auth-service/
    ↓
Auth Service Pipeline
├─ Build Docker image
├─ Integration tests
└─ Deploy to ECS (approval gate)

(Independent, non-blocking)
```

---

## Part 4: Communication Patterns

### Eliminate Cascading Failures

**Problem**: Service A depends on Service B depends on Service C
```
Request → A → B → C
           │  │  │
           └──┼──┴──→ If C fails, entire chain fails
```

**Solution**: Event-driven + Circuit Breakers

```
┌─────────────────────────────────────────────────┐
│           API Gateway (ECS, Central)            │
│  (Handles sync requests, routes to Lambda/ECS)  │
└────────────┬────────────────────────────────────┘
             │
      ┌──────┴──────┐
      │             │
      ↓             ↓
  (Sync)        (Async Events)
  ├─ Resume →  ├─ "resume_submitted" → SNS
  ├─ Auth      ├─ Job Analyzer listens, processes async
  └─ Matcher   └─ Result published back to SNS

New Flow:
1. API Gateway accepts resume upload
2. Publishes "resume_submitted" event to SNS
3. Job Analyzer Lambda subscribes, triggers automatically
4. Analyzer publishes results to SNS
5. Other services (Matcher, Interview Prep) consume results
6. No blocking calls, no cascading failures ✅
```

### Migration Path: RabbitMQ → SNS/SQS

**Phase 2-3**: Parallel operation
```
RabbitMQ (current)
└─ Resume Parser → Job Analyzer → Screening Bot
                                      ↓
SNS/SQS (new Lambda path)
├─ Resume Parser Lambda publishes event
├─ Job Analyzer Lambda subscribes
└─ Results published for downstream
```

**Phase 4**: Full migration
```
SNS/SQS only (Lambda-native)
├─ API Gateway publishes events
├─ Lambda functions consume via SQS/SNS
└─ ECS services (Auth, Matcher) consume via SQS
```

---

## Part 5: Database Strategy

### Current (Single Shared Database)
```
PostgreSQL
├─ Users (Auth Service)
├─ Candidates (Matcher Service)
├─ Jobs (Analyzer Service) ← Problem: Job Analyzer tightly coupled
└─ Interview Data (Prep Service)

MongoDB
├─ Resumes (Resume Parser)
├─ Interview Materials (Prep Service)
└─ Job Descriptions (Analyzer Service)
```

**Problem**: Schema changes affect multiple services

### Proposed (Hybrid Database)

**Transactional (PostgreSQL - RDS Proxy)**
```
PostgreSQL (shared, read-heavy)
├─ Users (Auth Service read/write)
├─ Candidates (Matcher Service read/write)
├─ Sessions (Auth Service read/write)
└─ Jobs (API Gateway read-only)
```

**Document (MongoDB - managed)**
```
MongoDB (shared for document storage)
├─ Resumes (Resume Parser write, Matcher read)
├─ Interview Materials (Prep Service write, read)
└─ Job Descriptions (API Gateway read)
```

**Cache (DynamoDB - per-service)**
```
DynamoDB Table 1: job-analysis-cache
├─ Job ID → Analysis results
├─ TTL: 7 days
└─ Used by: Job Analyzer Lambda, Matcher service

DynamoDB Table 2: interview-questions
├─ Topic → Questions
├─ Version tracking
└─ Used by: Interview Prep Lambda

DynamoDB Table 3: screening-sessions
├─ Session ID → State
├─ TTL: 24 hours
└─ Used by: Screening Bot Step Function
```

**Benefits**:
- ✅ Lambda services don't need PostgreSQL connection pools
- ✅ DynamoDB auto-scales for Lambda burst
- ✅ Reduced RDS load
- ✅ Cost-effective for Lambda (pay per request)

---

## Part 6: Risk Assessment & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|-----------|
| **Cold starts increase latency** | Users wait 2-5s | Medium | Pre-warming with CloudWatch Events, async processing |
| **Lambda timeout (15 min limit)** | Jobs > 15min fail | Low | Use Step Functions for long workflows |
| **State inconsistency** | Job Analyzer sees stale Job data | Medium | Strong eventual consistency pattern, cache invalidation |
| **DynamoDB costs spike** | Budget overrun | Low | Auto-scaling alarms, DAX for hot data |
| **Cross-service API breaks** | Cascading failures | Medium | Contract testing (Pact), versioned APIs |
| **RDS connection pool exhaustion** | Auth service fails | Medium | RDS Proxy with pooling, separate read replicas |
| **Notification service SES limits** | Emails not sent | Low | SES rate limiting, queue for retry |

---

## Part 7: Success Metrics

### Phase 2 (Job Analyzer, Interview Prep, AI Integration)
- ✅ Lambda functions deployed, health checks passing
- ✅ 99.5% uptime (vs 99% container baseline)
- ✅ 40% cost reduction for these services
- ✅ <2s p95 latency (vs ~4s with container startup)
- ✅ Zero data loss during migration
- ✅ All integration tests passing

### Phase 3 (Screening Bot, Notifications)
- ✅ No cascade failures (isolated deployments)
- ✅ Step Functions managing state correctly
- ✅ <1s SNS event latency
- ✅ Notification delivery SLA met (95% within 5s)

### Phase 4 (Full Optimization)
- ✅ 50% overall cost reduction
- ✅ <100ms API response time (p95)
- ✅ Zero manual rollbacks in 30 days
- ✅ Automated canary deployments working
- ✅ Each team deploys independently (no blockers)

---

## Part 8: Implementation Timeline

```
Month 1: Phase 2 (Weeks 1-4)
├─ Week 1-2: Job Analyzer Lambda dev + testing
├─ Week 2-3: Interview Prep Lambda + DynamoDB setup
├─ Week 3-4: AI Integration Lambda
└─ Week 4: Canary testing, monitoring setup

Month 2: Phase 2 Rollout (Weeks 5-8)
├─ Week 5: 10% traffic → Lambda (canary)
├─ Week 6: 50% traffic
├─ Week 7: 100% traffic
└─ Week 8: Old services decommissioned

Month 3: Phase 3 Prep (Weeks 9-12)
├─ Week 9-10: Screening Bot refactor (Step Functions)
├─ Week 10-11: Notification Service Lambda dev
└─ Week 11-12: Integration testing

Month 4: Phase 3 Rollout (Weeks 13-16)
├─ Week 13: Screening Bot deployment
├─ Week 14-15: Notification service rollout
└─ Week 16: Monitoring + optimization

Month 5: Phase 4 (Weeks 17-20)
├─ Week 17-18: API Gateway refactor
├─ Week 18-19: Cost optimization analysis
└─ Week 19-20: Team knowledge transfer
```

---

## Part 9: Cost Analysis

### Current State (Estimated)
```
ECS Services (9 services × 2 tasks each)
├─ Job Analyzer:      $200/month (medium instance)
├─ Interview Prep:    $200/month
├─ Screening Bot:     $150/month (low traffic)
├─ AI Integration:    $100/month
├─ Notifications:     $100/month
├─ API Gateway:       $200/month (heavy traffic)
├─ Auth Service:      $200/month
├─ Resume Parser:     $150/month
└─ Candidate Matcher: $250/month (query-heavy)
                      ──────────
                      $1,550/month (+ RDS, MongoDB, RabbitMQ)
```

### After Phase 2 (Job Analyzer → Lambda)
```
ECS Services: $1,350/month (one service removed)
Lambda:
├─ Job Analyzer:    $50/month (100 daily jobs × 300ms × $0.0000166/GB-s)
└─ Interview Prep:  $75/month
Savings:            $200/month (~13%)
```

### After Phase 3 & 4 (Full Migration)
```
ECS Services:       $800/month (only 3 core services)
Lambda Services:    $200/month (5 services)
Notifications SES:  $10/month (100k emails/month free tier)
DynamoDB:           $50/month (auto-scaling)
                    ──────────
Total:              $1,060/month (~30% savings)

Breakeven Point: Month 3 of migration (savings offset dev cost)
```

---

## Next Steps

1. **Immediate** (This Week)
   - [ ] Review this roadmap with team
   - [ ] Identify Pain Points in current deployment
   - [ ] Set Phase 2 start date

2. **Preparation** (Week 1-2)
   - [ ] Provision DynamoDB tables
   - [ ] Set up SNS topics
   - [ ] Create Lambda IAM roles
   - [ ] Set up LocalStack for testing

3. **Phase 2 Kickoff** (Week 3)
   - [ ] Assign service owners (Job Analyzer, Interview Prep, AI Integration)
   - [ ] Create GitHub issues from this roadmap
   - [ ] Begin Lambda development

---

## Document Management

- **Last Updated**: April 2, 2026
- **Status**: Ready for Review
- **Owner**: Architecture Team
- **Next Review**: When Phase 2 starts

---

## Questions?

Refer to:
- `MICROSERVICE_SEPARATION_STRATEGY.md` - Detailed separation implementation
- `SERVERLESS_PHASE2_LAMBDA_MIGRATION.md` - Job Analyzer Lambda dev guide
- `DATABASE_MIGRATION_GUIDE.md` - DynamoDB + RDS migration details

