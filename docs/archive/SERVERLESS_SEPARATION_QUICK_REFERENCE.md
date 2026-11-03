# Serverless Migration & Separation - Quick Reference Guide

## TL;DR - Answers to Your Questions

### Q1: What else can move to serverless?

**PHASE 2 (Immediate - Next 4-6 weeks):**
- ✅ **Job Analyzer** → AWS Lambda (compute-heavy, 100-500ms)
- ✅ **Interview Prep** → AWS Lambda (AI calls, 2-5s)
- ✅ **AI Integration** → AWS Lambda (pure pass-through, no state)

**PHASE 3 (Mid-term - 6-8 weeks):**
- ✅ **Notification Service** → AWS Lambda (SES/SNS integration)
- ⚠️ **Screening Bot** → AWS Step Functions + Lambda (multi-step workflow)

**Stay in Containers (ECS):**
- ❌ **API Gateway** (routing, session mgmt)
- ❌ **Auth Service** (JWT tokens, persistent connections)
- ❌ **Candidate Matcher** (database-heavy queries)

**Cost Savings:**
- Phase 2: -$200/month (13% reduction)
- Phase 3: -$350/month (23% reduction)
- Phase 4: -$450/month (30% overall reduction)

---

### Q2: How to separate into independently deployable components?

**Current Problem:**
```
Monolithic Repository
└─ All services in one git repo
   ├─ One failing test blocks all deployments
   ├─ Can't upgrade Spring Boot without coordinating everyone
   └─ Cascading failures (if Auth is down, API Gateway can't start)
```

**Solution: Multi-Module Monorepo with Independent CI/CD**
```
One Repository (easier management)
├── Each service has independent build.gradle
├── Each service has independent GitHub Actions workflow
├── Each service can deploy without others
└── Failures are isolated (not cascading)
```

**Implementation:**
1. **Week 1**: Refactor build.gradle (each service independent)
2. **Week 2**: Create service-level CI/CD pipelines
3. **Week 3-4**: Implement event-driven communication (SNS/SQS)
4. **Week 4**: Add circuit breakers (graceful failure handling)
5. **Week 5-6**: Stress test + monitoring

**Benefits:**
- Resume Parser deployment doesn't affect Auth Service
- 5-minute deployments instead of 30-minute coordinated deploys
- Teams own services end-to-end
- Clear failure isolation

---

## Service Categorization Matrix

| Service | Current | Phase | Lambda? | Container? | Effort | Dependencies |
|---------|---------|-------|---------|-----------|--------|--------------|
| Resume Parser | ✅ | 1 | ✅ Done | - | - | - |
| Job Analyzer | ECS | 2 | ✅ Yes | - | Medium | PostgreSQL (read), SNS |
| Interview Prep | ECS | 2 | ✅ Yes | - | Medium | MongoDB (read), DynamoDB (cache) |
| AI Integration | ECS | 2 | ✅ Yes | - | Low | OpenAI API |
| Screening Bot | ECS | 3 | ⚠️ Step Fn | - | High | PostgreSQL, MongoDB |
| Notification | ECS | 3 | ✅ Yes | - | Low | SES, SNS |
| API Gateway | ECS | - | ❌ No | ✅ ECS/ALB | - | All services |
| Auth Service | ECS | - | ❌ No | ✅ ECS/RDS | - | PostgreSQL, Redis |
| Candidate Matcher | ECS | - | ❌ No | ✅ ECS/RDS | - | PostgreSQL, MongoDB |

---

## Architecture After Migration

```
┌──────────────────────────────────────────────────────────────┐
│                    USER REQUESTS                             │
│                   (API, Web, Mobile)                         │
└────────────────────────┬─────────────────────────────────────┘
                         │
        ┌────────────────┴────────────────┐
        │                                 │
        ↓                                 ↓
   ┌────────────┐               ┌─────────────────┐
   │ REST API   │               │  GraphQL        │
   │ Port 8000  │               │  Port 8000      │
   └────────────┘               └─────────────────┘
        │                                 │
        └────────────────┬────────────────┘
                         │
        ┌────────────────▼────────────────┐
        │     API Gateway (ECS)            │
        │  Spring Cloud Gateway WebFlux   │
        │   - Routing                      │
        │   - Auth validation              │
        │   - Circuit breakers             │
        └────┬───────────────────┬────────┘
             │                   │
    ┌────────▼──────┐   ┌───────▼────────┐
    │ REST Routes   │   │  GraphQL SDL   │
    └────────┬──────┘   └───────┬────────┘
             │                  │
    ┌────────▼──────────────────▼──────────┐
    │     Service Discovery (DNS/ECS)       │
    └────┬──────────────────┬───────┬────────┘
         │                  │       │
    ┌────▼─────┐   ┌────────▼──┐  ┌───▼────────┐
    │   Auth   │   │ Candidate │  │    Core    │
    │ Service  │   │  Matcher  │  │ Services   │
    │  (ECS)   │   │  (ECS)    │  │  (REST)    │
    └────┬─────┘   └────┬──────┘  └────┬───────┘
         │               │              │
         │        ┌──────▼──────┐       │
         │        │ PostgreSQL  │       │
         │        │   (RDS)     │       │
         │        └─────────────┘       │
         │                              │
    ┌────▼──────────────────────────────▼──────────────────┐
    │            Event Bus (SNS/SQS - NEW)                  │
    │  Events: resume_submitted, job_analyzed, etc         │
    └────┬────────────────────────────────────┬────────────┘
         │                                    │
    ┌────▼──────────────┐   ┌────────────────▼───────────┐
    │ Lambda Services   │   │ ECS Services (Containers)  │
    │                  │   │                             │
    │ • Job Analyzer   │   │ (Already covered above)     │
    │ • Interview Prep │   │                             │
    │ • AI Integration │   │                             │
    │ • Notifications  │   │                             │
    └─────────┬────────┘   └─────────────────────────────┘
              │                     │
         ┌────▼──────────────────────▼──────┐
         │   Data Layer                      │
         │  PostgreSQL (Shared RDS)          │
         │  MongoDB (DocumentDB)             │
         │  DynamoDB (Lambda caches)         │
         │  Redis (Session cache)            │
         │  S3 (Resume storage)              │
         └───────────────────────────────────┘
```

**Key Points:**
- ✅ API Gateway (ECS) stays front and center
- ✅ Auth/Matcher (ECS) handle complex queries
- ✅ Lambda services process async via SNS/SQS
- ✅ If Lambda slow → doesn't block API Gateway
- ✅ If Auth down → other services still work
- ✅ Databases are shared but with smart caching

---

## Phase 2 Implementation (Start Now)

### Job Analyzer Service → Lambda

**Current (ECS):**
```
POST /api/analyzer/analyze
├─ 1. Get job from PostgreSQL
├─ 2. Get resume from MongoDB
├─ 3. Call OpenAI API
├─ 4. Save results to PostgreSQL
└─ Return results (takes 2-5 seconds)
```

**New (Lambda):**
```
1. API Gateway publishes "job_posting_created" event to SNS
   ├─ jobId, jobDescription, skills_required, salaryRange
   
2. Job Analyzer Lambda subscribes to event
   ├─ Read from SQS queue
   ├─ Call OpenAI API for analysis
   ├─ Store results in DynamoDB cache
   ├─ Publish "job_analyzed" event
   └─ No blocking, no database writes (read-only)

3. Candidate Matcher subscribes to "job_analyzed" event
   ├─ Match candidates (from PostgreSQL)
   ├─ Publish "candidates_matched" event

Benefits:
- API Gateway returns immediately (user doesn't wait)
- Job Analyzer processes async (no timeout risk)
- Results available via webhook/SSE when ready
- If Lambda cold start → doesn't affect user
```

**Cost:**
- Current: ~$200/month (medium ECS instance)
- Lambda: ~$50/month (assuming 100 jobs/day × 3s × $0.0000166/GB-s)
- **Savings: $150/month**

**Timeline:**
- Week 1: Lambda function + SAM template
- Week 2: Integration with SNS/SQS
- Week 3: Canary testing (10% traffic)
- Week 4: Full rollout + decommission ECS service

---

## Interview Prep Service → Lambda

**Pattern:**
```
"interview_requested" event (SNS)
    ↓
Interview Prep Lambda
├─ Read question templates from DynamoDB
├─ Call OpenAI for custom questions
├─ Cache in DynamoDB (TTL: 30 days)
└─ Publish "questions_generated" event
    ↓
API Gateway serves cached questions to user
```

**Cost:**
- Current: ~$200/month
- Lambda: ~$75/month
- **Savings: $125/month**

---

## AI Integration Service → Lambda

**Pattern:**
```
API Gateway → Lambda
    ├─ Validate request
    ├─ Call OpenAI/Claude
    └─ Return response

Or:

"analysis_needed" event → Lambda → "analysis_complete" event

No database, no persistence, just pass-through.
```

**Cost:**
- Current: ~$100/month
- Lambda: ~$20/month
- **Savings: $80/month**

---

## Independent Deployment: Before vs. After

### BEFORE (Cascading Failure Risk)

```
Developer: "Let's upgrade Spring Boot from 4.0.5 to 4.0.6"
             in Job Analyzer service
    ↓
Git push to main
    ↓
CI/CD triggers
    ├─ Build ALL services (api-gateway, auth, matcher, etc.)
    ├─ If any build fails → ALL deployments blocked
    ├─ Run ALL tests (1000+ tests)
    ├─ Wait 45 minutes for full test suite
    └─ Deploy ALL or NOTHING
    ↓
Problem: Auth Service author says "Wait, I'm not ready to upgrade"
         → Nobody can deploy anything → Blocking all 9 services
```

### AFTER (Independent Deployment)

```
Developer: "Let's upgrade Spring Boot in Job Analyzer service"
    ↓
Git push to services/job-analyzer/**
    ↓
GitHub Actions triggers ONLY Job Analyzer workflow
    ├─ Build Job Analyzer (5 minutes)
    ├─ Run Job Analyzer tests (2 minutes)
    ├─ Deploy to Lambda staging (1 minute)
    ├─ Run integration tests (3 minutes)
    └─ Deploy to production (1 minute) — Total: 12 minutes
    ↓
Meanwhile:

Other developers can push to:
├─ services/auth-service/** → deploys independently
├─ services/api-gateway/** → deploys independently
├─ frontend/admin-dashboard/** → deploys independently
└─ All parallel, no blocking

✅ Job Analyzer upgraded in 12 minutes
✅ Auth Service keeps working
✅ API Gateway keeps working
✅ No coordination needed
```

---

## Quick Implementation Checklist

### Week 1: Refactor Build System
- [ ] Create `services/job-analyzer/build.gradle`
- [ ] Create `services/interview-prep/build.gradle`
- [ ] Create `services/ai-integration/build.gradle`
- [ ] Test: Each service builds independently
- [ ] Verify: JAR sizes < 100MB

### Week 2: Set Up Event Infrastructure
- [ ] Create SNS topics (resume-events, job-events, interview-events)
- [ ] Create SQS queues (dead-letter queues included)
- [ ] Update API Gateway to publish events
- [ ] Update Lambda IAM roles for SNS/SQS

### Week 3: Implement Job Analyzer Lambda
- [ ] Create Lambda function (Java 21 + Quarkus)
- [ ] Implement SNS listener
- [ ] Add DynamoDB cache for results
- [ ] Unit tests + integration tests with LocalStack
- [ ] Deploy to staging

### Week 4: Implement Interview Prep Lambda
- [ ] Create Lambda function
- [ ] Implement SNS listener
- [ ] Add DynamoDB cache
- [ ] Deploy to staging

### Week 5: Implement AI Integration Lambda
- [ ] Create Lambda function
- [ ] Test with OpenAI API
- [ ] Deploy to staging

### Week 6: Canary Testing & Rollout
- [ ] Route 10% traffic to Lambda (10 jobs/day)
- [ ] Monitor error rates, latency, costs
- [ ] Gradually increase to 100%
- [ ] Decommission ECS services

### Week 7-8: Scaling & Monitoring
- [ ] Load test (1000 concurrent requests)
- [ ] Monitor Lambda cold starts
- [ ] Set up CloudWatch alarms
- [ ] Document runbooks

---

## Monitoring & Alarms

### For Lambda Services (Critical Metrics)

```
CREATE ALARM "job-analyzer-error-rate"
WHEN ErrorCount/Invocations > 5% 
THEN PagerDuty alert

CREATE ALARM "job-analyzer-duration"
WHEN p95 Duration > 5 seconds
THEN Warn (might be timeout risk)

CREATE ALARM "job-analyzer-concurrency"
WHEN ConcurrentExecutions > 800 (of 1000)
THEN Auto-scale RDS read replicas

CREATE ALARM "job-analyzer-cold-starts"
WHEN InitDuration > 2 seconds
THEN Track (investigate if > 10% of invocations)
```

### For Event Queue Health

```
CREATE ALARM "sqs-queue-depth"
WHEN ApproximateNumberOfMessagesVisible > 1000
THEN Page on-call (messages backing up)

CREATE ALARM "dlq-messages"
WHEN ApproximateNumberOfMessagesVisible > 0
THEN Alert (failures accumulating)
```

---

## Cost Model (Detailed)

### Lambda Pricing (us-east-1)

| Service | Requests/Day | Avg Duration | Monthly Cost | Notes |
|---------|-------------|--------------|--------------|-------|
| Job Analyzer | 100 | 300ms | $50 | Compute-bound |
| Interview Prep | 50 | 2s | $75 | AI calls, longer |
| AI Integration | 200 | 1s | $30 | Pass-through |
| Notification | 500 | 100ms | $10 | Email delivery |
| **Total Lambda** | - | - | **$165** | - |

### Container Services (ECS - m5.large)

| Service | Requests/Day | Monthly Cost | Notes |
|---------|-------------|--------------|-------|
| API Gateway | - | $200 | Always running |
| Auth Service | - | $150 | Always running |
| Candidate Matcher | - | $200 | Query-heavy |
| **Total ECS** | - | **$550** | - |

### Data Layer

| Service | Storage | Monthly Cost | Notes |
|---------|---------|--------------|-------|
| PostgreSQL (RDS) | 100 GB | $80 | Shared |
| MongoDB (DocumentDB) | 50 GB | $50 | Shared |
| DynamoDB (Lambda cache) | 10 GB | $30 | Auto-scaling |
| Redis (cache) | 5 GB | $20 | Shared |
| **Total Data** | - | **$180** | - |

### Total Monthly Cost: ~$895

**Breakdown:**
- Lambda (5 services): $165 (18%)
- ECS (3 services): $550 (61%)
- Data layer: $180 (20%)

**Savings vs Current:** 
- Current estimate: ~$1,300/month
- After migration: ~$895/month
- **Total savings: 31%**

---

## Next Steps

1. **This Week:**
   - [ ] Review this document with team
   - [ ] Identify pain points in current deployment
   - [ ] Assign owners for Phase 2 services

2. **Next Week:**
   - [ ] Provision DynamoDB tables
   - [ ] Set up SNS/SQS
   - [ ] Create service-level GitHub Actions workflows

3. **Week 3:**
   - [ ] Start Job Analyzer Lambda development
   - [ ] Begin build.gradle refactoring

---

## Related Documents

1. **SERVERLESS_MIGRATION_ROADMAP.md** - Full strategic roadmap
2. **MICROSERVICE_SEPARATION_STRATEGY.md** - Detailed separation implementation
3. **CLEANUP_SYSTEM_SUMMARY.md** - Already created cleanup system

---

## Questions?

**Q: When should we start?**
A: Start Phase 2 immediately (Job Analyzer, Interview Prep, AI Integration). Estimated 4-6 weeks.

**Q: Can we do this without major refactoring?**
A: Minimal refactoring needed. Just wrap existing logic in Lambda handler + add SNS listeners.

**Q: What if we need to rollback?**
A: Keep both Lambda and ECS running in parallel for 2 weeks. Route 10% to Lambda, 90% to ECS. Swap when confident.

**Q: Will Lambda be faster than ECS?**
A: Job Analyzer: Same speed. Interview Prep: Faster (cached questions). Overall: Better user experience (async processing).

**Q: How much engineering effort?**
A: Phase 2: ~4 weeks (2 engineers). Phase 3: ~2 weeks. Phase 4: ~2 weeks.

