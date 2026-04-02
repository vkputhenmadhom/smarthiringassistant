# Microservice Separation & Independent Deployment Strategy

## Overview

This guide explains how to separate your monolithic codebase into independently deployable artifacts, enabling each service to be deployed without affecting others. This eliminates cascading failures and allows teams to own specific services end-to-end.

---

## Current Problem: Monolithic Coupling

### Build-Time Coupling
```
Single settings.gradle + build.gradle
        ↓
All services compile together
        ↓
One failing test blocks all deployments ❌
        ↓
One service wants to upgrade Spring Boot,
others not ready yet → Deadlock
```

### Runtime Coupling
```
API Gateway ← Hard-coded URLs to other services
    ├─ http://resume-parser:8002
    ├─ http://auth-service:8001
    └─ http://candidate-matcher:8003

If Resume Parser is down
    ↓
API Gateway can't start (dependency injection fails)
    ↓
Entire platform down ❌
```

### Database Coupling
```
Single PostgreSQL + MongoDB
    ├─ All services write to same DB
    ├─ Schema changes affect everyone
    └─ Hard to scale individual services
```

---

## Solution: Micro-Repository Pattern (Single Repo, Independent CI/CD)

### Structure

```
SmartHiringAssistant/
│
├── services/
│   ├── api-gateway/
│   │   ├── build.gradle              (INDEPENDENT)
│   │   ├── src/
│   │   ├── Dockerfile
│   │   ├── kubernetes/
│   │   │   ├── deployment.yaml
│   │   │   └── service.yaml
│   │   ├── cloudformation/           (IaC for ALB, security groups)
│   │   └── .github/workflows/        (INDEPENDENT CI/CD)
│   │       └── deploy.yml
│   │
│   ├── auth-service/
│   │   ├── build.gradle              (INDEPENDENT)
│   │   ├── src/
│   │   ├── Dockerfile
│   │   └── .github/workflows/deploy.yml
│   │
│   ├── resume-parser/
│   │   ├── build.gradle              (INDEPENDENT)
│   │   ├── src/
│   │   ├── samconfig.toml            (Lambda config)
│   │   └── .github/workflows/        (INDEPENDENT Lambda deploy)
│   │       └── deploy-lambda.yml
│   │
│   ├── job-analyzer/                 (PHASE 2)
│   │   └── (same structure, Lambda-based)
│   │
│   └── [other services...]
│
├── shared/
│   ├── shared-commons/               (Core DTOs, exceptions - LOW CHANGE RATE)
│   │   ├── build.gradle
│   │   └── src/
│   │       └── org/vinod/sha/common/
│   │           ├── dto/
│   │           ├── exception/
│   │           └── config/
│   │
│   ├── grpc-definitions/             (gRPC proto files - VERSIONED)
│   │   ├── build.gradle
│   │   └── src/main/proto/
│   │       ├── resume.proto (v1)
│   │       ├── job.proto (v2)
│   │       └── screening.proto (v1)
│   │
│   ├── shared-events/                (Event schemas - VERSIONED)
│   │   ├── build.gradle
│   │   └── src/
│   │       └── org/vinod/sha/events/
│   │           ├── ResumeSubmittedEvent.java (v1)
│   │           ├── JobAnalyzedEvent.java (v1)
│   │           └── ScreeningCompletedEvent.java (v1)
│   │
│   └── contracts/                    (API contracts - VERSION CONTROL)
│       ├── openapi/
│       │   ├── auth-service.yaml (v1.2)
│       │   ├── job-analyzer.yaml (v1.0)
│       │   └── resume-parser.yaml (v1.0)
│       ├── graphql/
│       │   └── gateway-schema.graphql
│       ├── grpc/
│       │   └── README.md (versioning rules)
│       └── asyncapi/                 (Event schemas)
│           ├── job-analyzer-events.yaml
│           └── resume-events.yaml
│
├── infrastructure/
│   ├── cloudformation/               (AWS IaC)
│   │   ├── core/
│   │   │   ├── vpc.yaml              (Shared VPC)
│   │   │   ├── rds.yaml              (PostgreSQL)
│   │   │   ├── mongodb.yaml          (DocumentDB)
│   │   │   ├── sns-topics.yaml       (Event topics)
│   │   │   └── sqs-queues.yaml       (Queues)
│   │   │
│   │   └── services/
│   │       ├── api-gateway-alb.yaml  (ALB for ECS)
│   │       ├── auth-ecs.yaml         (ECS task def)
│   │       ├── job-analyzer-lambda.yaml
│   │       └── [service stacks]
│   │
│   ├── kubernetes/                   (Optional K8s manifests)
│   │   ├── base/
│   │   │   ├── api-gateway-deployment.yaml
│   │   │   ├── auth-deployment.yaml
│   │   │   └── [other services]
│   │   │
│   │   └── overlays/
│   │       ├── dev/
│   │       ├── staging/
│   │       └── prod/
│   │
│   └── scripts/
│       ├── bootstrap-infrastructure.sh
│       ├── setup-rds-proxy.sh
│       ├── setup-dynamodb.sh
│       └── setup-sns-sqs.sh
│
├── frontend/
│   ├── admin-dashboard/              (INDEPENDENT deploy)
│   │   ├── package.json
│   │   ├── .github/workflows/deploy.yml
│   │   └── src/
│   │
│   └── candidate-portal/             (INDEPENDENT deploy)
│       └── (same structure)
│
├── scripts/
│   ├── deploy/
│   │   ├── deploy-service.sh         (Generic service deploy)
│   │   ├── deploy-lambda.sh          (Lambda-specific)
│   │   ├── deploy-ecs.sh             (ECS-specific)
│   │   └── rollback.sh
│   │
│   ├── test/
│   │   ├── integration-tests.sh
│   │   ├── contract-tests.sh
│   │   └── load-tests.sh
│   │
│   └── ops/
│       ├── health-check.sh
│       ├── cleanup-resources.sh
│       └── cost-analysis.sh
│
├── docs/
│   ├── architecture/
│   │   ├── service-boundaries.md     (Clear ownership)
│   │   ├── communication-patterns.md (Sync vs async)
│   │   └── failure-modes.md          (How to handle failures)
│   │
│   ├── deployment/
│   │   ├── service-deploy-guide.md   (Per-service)
│   │   ├── database-migration.md
│   │   └── rollback-procedure.md
│   │
│   └── development/
│       ├── local-setup.md
│       ├── testing-strategy.md
│       └── adding-new-service.md
│
├── build.gradle                      (MINIMAL - only shared deps)
├── settings.gradle                   (Module definitions)
├── .github/workflows/
│   ├── build-services.yml            (Matrix: build all services)
│   ├── deploy-staging.yml            (Matrix: deploy affected services)
│   └── deploy-production.yml         (Matrix: production deployment)
│
└── README.md                         (High-level overview)
```

---

## Key Principles

### 1. Build-Time Independence

**Each service has its own `build.gradle`**:

```groovy
// services/job-analyzer/build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot'
}

group = 'org.vinod.sha.analyzer'
version = '1.0.0'    # ← Individual versioning!

dependencies {
    // Only dependencies needed for THIS service
    implementation project(':shared:shared-commons')     # ✅ Minimal
    implementation 'org.springframework.boot:...'
    implementation 'com.amazonaws:aws-java-sdk-lambda'
    
    // NOT: com.fasterxml.jackson (unless needed)
    // NOT: org.springframework.cloud:spring-cloud-gateway (that's API Gateway's job)
    // NOT: heavy dependencies of other services
}

// Independent build commands:
// gradle :services:job-analyzer:build
// gradle :services:job-analyzer:bootJar
// gradle :services:job-analyzer:packageLambdaZip
```

**Benefits**:
- Smaller JAR files (faster builds, faster deploys)
- Fewer transitive dependencies (fewer security vulnerabilities)
- Independent version upgrades

### 2. Runtime Loose Coupling

**Replace hard-coded URLs with service discovery + circuit breakers**:

**Before (Tightly Coupled)**:
```java
// api-gateway/src/.../GatewayConfig.java
@Configuration
public class GatewayConfig {
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("resume-parser", 
                r -> r.path("/resume/**")
                    .uri("http://resume-parser-service:8002")  // ❌ Hard-coded!
            )
            .build();
    }
}

// Problem: If Resume Parser is down, requests pile up and timeout
```

**After (Loose Coupling with Circuit Breaker)**:
```yaml
# api-gateway/src/main/resources/application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: resume-parser
          uri: lb://resume-parser-service  # ✅ Service discovery
          predicates:
            - Path=/resume/**
          filters:
            - name: CircuitBreaker         # ✅ Circuit breaker
              args:
                name: resumeParserCB
                fallback: /fallback/resume # ✅ Fallback response
            - name: Retry
              args:
                retries: 2
                status: 503

resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 5000
        failureRateThreshold: 50
        eventConsumerBufferSize: 10
```

**Benefits**:
- ✅ Resume Parser down → 503 response with fallback, NOT cascading failure
- ✅ API Gateway stays up
- ✅ Other services unaffected

### 3. Event-Driven Communication (Preferred)

**Async messaging instead of synchronous service calls**:

```
BEFORE (Synchronous - Tightly Coupled):
┌──────────────┐
│ API Gateway  │
│  (ECS)       │
└──────┬───────┘
       │ 1. Resume uploaded
       ↓
┌──────────────────────┐
│ Resume Parser        │ 2. Parse & analyze
│ (Lambda)             │
└──────┬───────────────┘
       │ 3. Sync call to Job Analyzer
       ↓
┌──────────────────────┐
│ Job Analyzer         │ 4. Analyze job
│ (Lambda)             │
└──────┬───────────────┘
       │ 5. Sync call to Candidate Matcher
       ↓
┌──────────────────────┐
│ Candidate Matcher    │ 6. Match candidates
│ (ECS)                │
└──────────────────────┘

Problem: If any service is slow or down, entire chain blocks


AFTER (Asynchronous - Loosely Coupled):
┌──────────────┐
│ API Gateway  │  1. Resume uploaded
│  (ECS)       │  2. Publish "resume_submitted" to SNS
└──────┬───────┘
       ↓
   SNS Topic: "resume-events"
   ├─ Resume Parser Lambda subscribes
   ├─ Job Analyzer Lambda subscribes
   └─ Interview Prep Lambda subscribes
   
   Each processes independently:
   
   ┌──────────────────────────────────────────────────────┐
   │ Resume Parser Lambda                                 │
   │ 1. Parse resume                                     │
   │ 2. Publish "resume_parsed" event with data          │
   └──────────────┬───────────────────────────────────────┘
                  ↓
              SNS Topic: "resume-events"
   
   ┌──────────────────────────────────────────────────────┐
   │ Job Analyzer Lambda                                  │
   │ 1. Listen for "resume_parsed"                       │
   │ 2. Get resume data from SQS                         │
   │ 3. Analyze job-resume fit                           │
   │ 4. Publish "job_analyzed" event                     │
   └──────────────┬───────────────────────────────────────┘
                  ↓
   
   ┌──────────────────────────────────────────────────────┐
   │ Candidate Matcher Lambda                             │
   │ 1. Listen for "job_analyzed"                        │
   │ 2. Get job data from SQS                            │
   │ 3. Match candidates                                 │
   │ 4. Publish "candidates_matched" event               │
   └──────────────┬───────────────────────────────────────┘

Benefits:
- ✅ Resume Parser down? Job Analyzer still processes old resumes
- ✅ Job Analyzer slow? Other services not blocked
- ✅ Messages queued in SQS if consumer down (no loss)
- ✅ Natural retry mechanism with DLQ
- ✅ Easy to add new consumers (Interview Prep listening to same event)
```

### 4. Shared Code Management

**Use shared modules strategically (low coupling)**:

```java
// ✅ GOOD: Shared core objects (low change rate)
shared-commons/src/main/java/org/vinod/sha/common/
├── dto/
│   ├── ResumeDto.java         (Simple DTO)
│   ├── CandidateDto.java      (Simple DTO)
│   └── ErrorResponse.java     (Standard error format)
├── exception/
│   ├── ServiceException.java  (Base exception)
│   └── ValidationException.java
├── constants/
│   ├── ServiceNames.java
│   └── EventTopics.java
└── config/
    └── BaseConfig.java        (Logging, metrics)

// ❌ AVOID: Shared business logic (high change rate)
// Don't put in shared-commons:
// - JobAnalyzerService
// - ResumeParserService
// - Database repositories
// - Service-specific configurations
```

**Benefits**:
- ✅ DTOs shared (no conversion overhead)
- ✅ Service logic isolated (can evolve independently)
- ✅ Each service can have different Spring Boot versions eventually

### 5. Contract-Based Development

**Define contracts upfront, implement independently**:

```yaml
# contracts/openapi/job-analyzer.yaml
openapi: 3.0.0
info:
  title: Job Analyzer Service
  version: 1.0.0

paths:
  /analyze:
    post:
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AnalysisRequest'
      responses:
        '200':
          description: Analysis completed
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AnalysisResponse'

components:
  schemas:
    AnalysisRequest:
      type: object
      required: [jobId, resumeId]
      properties:
        jobId:
          type: string
        resumeId:
          type: string
    
    AnalysisResponse:
      type: object
      properties:
        matchScore:
          type: number
          minimum: 0
          maximum: 100
        skills:
          type: array
          items: { type: string }
```

**Development**:
1. API Gateway team creates OpenAPI spec (contract)
2. Job Analyzer team implements service to spec
3. Resume Parser team implements independently
4. All teams test against contract (contract testing with Pact)
5. Deployment doesn't break contract

---

## Implementation: From Monolithic to Independent

### Step 1: Refactor Gradle (Week 1)

**Goal**: Each service can build independently

```bash
# Move to service-specific build.gradle
services/
├── api-gateway/build.gradle      ← Created (was: imported from root)
├── auth-service/build.gradle     ← Created
├── resume-parser/build.gradle    ← Created
├── job-analyzer/build.gradle     ← Created (NEW SERVICE)
└── [...other services]

# Root build.gradle becomes minimal
root/build.gradle                 ← Only shared module definitions
```

**Testing**:
```bash
# Should work independently now:
gradle :services:job-analyzer:build          # ✅ Works
gradle :services:api-gateway:build           # ✅ Works
gradle :services:auth-service:test           # ✅ Works

# No interdependencies at build time
```

### Step 2: Create Service-Level CI/CD (Week 2)

**Goal**: Each service deploys independently

```yaml
# .github/workflows/deploy-job-analyzer.yml
name: Deploy Job Analyzer

on:
  push:
    paths:
      - 'services/job-analyzer/**'          # ← Only triggers for this service
      - 'shared/shared-commons/**'          # ← or its dependencies
      - '.github/workflows/deploy-job-analyzer.yml'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Lambda package
        run: |
          cd services/job-analyzer
          gradle packageLambdaZip
      
      - name: Deploy to AWS Lambda
        run: |
          ./scripts/deploy-lambda.sh \
            --service job-analyzer \
            --stage staging \
            --region us-east-1
      
      - name: Run integration tests
        run: |
          gradle :services:job-analyzer:integrationTest
      
      - name: Approve for production
        uses: actions/github-script@v6
        with:
          script: |
            # Manual approval required before prod deploy
            
      - name: Deploy to production
        if: github.ref == 'refs/heads/main'
        run: |
          ./scripts/deploy-lambda.sh \
            --service job-analyzer \
            --stage production \
            --region us-east-1
```

**Benefits**:
- Resume Parser deployment doesn't trigger Job Analyzer tests
- API Gateway can deploy without triggering Lambda builds
- Faster feedback loops

### Step 3: Implement Service Discovery (Week 2-3)

**Option 1: Kubernetes/ECS (For API Gateway, Auth, Matcher)**
```yaml
# kubernetes/api-gateway-deployment.yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
spec:
  selector:
    app: api-gateway
  ports:
    - port: 80
      targetPort: 8000

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
      - name: api-gateway
        image: smart-hiring/api-gateway:1.2.3
        ports:
        - containerPort: 8000
        env:
        - name: RESUME_PARSER_SERVICE_URL
          value: lb://resume-parser-service  # ← Service discovery
        - name: AUTH_SERVICE_URL
          value: lb://auth-service
```

**Option 2: Lambda Environment Variables (For Lambda services)**
```yaml
# cloudformation/job-analyzer-lambda.yaml
Resources:
  JobAnalyzerLambda:
    Type: AWS::Lambda::Function
    Properties:
      FunctionName: job-analyzer
      Environment:
        Variables:
          # These are discovered via API Gateway or SNS
          RESUME_PARSER_URL: !Sub 'https://${ApiGateway}.execute-api.${AWS::Region}.amazonaws.com/prod/resume'
          INTERVIEW_PREP_URL: !Sub 'https://${ApiGateway}.execute-api.${AWS::Region}.amazonaws.com/prod/interview'
          SNS_JOB_ANALYSIS_TOPIC: !Ref JobAnalysisTopic
```

### Step 4: Implement Event-Driven Communication (Week 3-4)

**Goal**: Replace synchronous calls with async events

```java
// services/api-gateway/src/.../controller/ResumeUploadController.java
@RestController
@RequestMapping("/api/resume")
public class ResumeUploadController {
    
    private final SnsTemplate snsTemplate;  // Spring Cloud AWS
    
    @PostMapping
    public ResponseEntity<?> uploadResume(@RequestParam MultipartFile file) {
        // 1. Save resume to S3
        String resumeUrl = s3Service.uploadResume(file);
        
        // 2. Publish event (async) instead of calling service
        ResumeSubmittedEvent event = new ResumeSubmittedEvent(
            resumeId: UUID.randomUUID().toString(),
            resumeUrl: resumeUrl,
            uploadedAt: Instant.now()
        );
        snsTemplate.convertAndSend(
            "arn:aws:sns:us-east-1:123456:resume-events",
            event
        );
        
        // 3. Return immediately to user
        return ResponseEntity.accepted()
            .body(Map.of("resumeId", event.getResumeId()));
        
        // ✅ No waiting for Resume Parser
        // ✅ No cascading if parser is down
        // ✅ Parser processes async
    }
}

// services/resume-parser/src/.../listener/ResumeEventListener.java
@Component
public class ResumeEventListener {
    
    @SqsListener("resume-queue")  // Spring Cloud AWS
    public void onResumeSubmitted(ResumeSubmittedEvent event) {
        try {
            // 1. Parse resume
            ResumeData data = parseResume(event.getResumeUrl());
            
            // 2. Publish result
            snsTemplate.convertAndSend(
                "arn:aws:sns:us-east-1:123456:resume-events",
                new ResumeParsedEvent(
                    resumeId: event.getResumeId(),
                    data: data
                )
            );
        } catch (Exception e) {
            // ✅ Exception doesn't affect API Gateway
            // ✅ Message goes to DLQ for retry
            throw new SqsMessageHandlingException("Parse failed", e);
        }
    }
}

// services/job-analyzer/src/.../listener/JobAnalysisListener.java
@Component
public class JobAnalysisListener {
    
    @SqsListener("job-analysis-queue")
    public void onResumeParsed(ResumeParsedEvent event) {
        // 1. Get job context (from cache or DB)
        Job job = getJob(event.getJobId());
        
        // 2. Analyze fit
        MatchScore score = analyzeJobResumefit(job, event.getData());
        
        // 3. Publish result
        snsTemplate.convertAndSend(
            "arn:aws:sns:us-east-1:123456:analysis-events",
            new JobAnalyzedEvent(
                resumeId: event.getResumeId(),
                jobId: job.getId(),
                matchScore: score
            )
        );
    }
}
```

### Step 5: Implement Circuit Breakers (Week 4)

```yaml
# services/api-gateway/src/main/resources/application.yml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/auth/**
          filters:
            - name: CircuitBreaker
              args:
                name: authServiceCB
                fallback: forward:/fallback/auth
                statusCodes:
                  - 503
                  - 504
            - name: Retry
              args:
                retries: 2

resilience4j:
  circuitbreaker:
    instances:
      authServiceCB:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 10s
        failureRateThreshold: 50
      
  timelimiter:
    instances:
      authServiceCB:
        cancelRunningFuture: false
        timeoutDuration: 3s  # Auth service must respond in 3s
```

---

## Testing Strategy for Independent Services

### Contract Testing (Pact)

```java
// services/job-analyzer/test/.../JobAnalyzerConsumerTest.java
@ExtendWith(PactConsumerTestExt.class)
public class JobAnalyzerConsumerTest {
    
    @Pact(consumer = "job-analyzer", provider = "resume-parser")
    public V4Pact createPact(PactBuilder builder) {
        return builder
            .expectsToReceive("a resume parsed event")
            .withRequest("POST", "/events/resume-parsed")
            .withBody(Map.of(
                "resumeId", "123",
                "skills", List.of("Java", "Spring", "React")
            ))
            .willRespondWith(200, Map.of("processed", true))
            .toPact(V4Pact.class);
    }
    
    @Test
    void jobAnalyzerConsumesResumeParsedEvent(MockServer mockServer) {
        // Test that Job Analyzer can handle Resume Parser's response
        String response = restTemplate.postForObject(
            mockServer.getUrl() + "/events/resume-parsed",
            Map.of("resumeId", "123", "skills", List.of("Java")),
            String.class
        );
        assertThat(response).contains("processed");
    }
}

// Services can now publish Pact to Pact Broker
// API Gateway validates all consumer contracts before deployment
```

### Integration Tests (Per Service)

```bash
# gradle :services:job-analyzer:integrationTest
# Spins up:
# - LocalStack for DynamoDB/SNS/SQS
# - Job Analyzer Lambda (SAM local)
# - Test fixtures

gradle :services:job-analyzer:integrationTest

# gradle :services:api-gateway:integrationTest
# Spins up:
# - Kubernetes cluster (or mocked)
# - All dependent services (stubbed)
# - Test fixtures
```

---

## Success Criteria

### Build Independence ✅
- [ ] Each service builds in < 2 minutes
- [ ] Service builds don't depend on other services
- [ ] One failing test doesn't block others
- [ ] JAR files < 100MB (to enable fast container starts)

### Runtime Independence ✅
- [ ] Auth Service down → other services unaffected
- [ ] Job Analyzer down → API Gateway responds with graceful fallback
- [ ] Resume Parser down → events queue in SQS, no loss
- [ ] 99% of services recover from peer failure within 30s

### Deployment Independence ✅
- [ ] Each service can deploy independently
- [ ] Deployment doesn't require other services up
- [ ] Rollback is < 2 minutes per service
- [ ] Deployment takes < 10 minutes (including tests)

### Operational Independence ✅
- [ ] Each team owns one service end-to-end
- [ ] No cross-team deployment blockers
- [ ] Clear ownership: Service X owned by Team Y
- [ ] SLA per service (not whole platform)

---

## Migration Timeline

```
Week 1: Refactor Gradle → Each service builds independently
Week 2: Create service-level CI/CD → Each service deploys independently
Week 3-4: Implement event-driven comm → Replace sync calls with async
Week 5: Implement circuit breakers → Graceful degradation
Week 6: Contract testing → Validate service contracts
Week 7-8: Stress testing → Verify isolation under load
Week 9-10: Runbooks + monitoring → Operational readiness
```

---

## Related Documents

- `SERVERLESS_MIGRATION_ROADMAP.md` - Which services to migrate to Lambda
- `DATABASE_MIGRATION_GUIDE.md` - Separate databases per service (Phase 4)
- `EVENT_DRIVEN_ARCHITECTURE.md` - Detailed event schema design
- `DEPLOYMENT_PROCEDURES.md` - Step-by-step deployment guide

---

## Questions?

**Q: What if we need shared data (e.g., candidate list)?**
A: Cache in DynamoDB or use read replicas. Eventually consistent is OK for most use cases.

**Q: How do we handle distributed transactions?**
A: Use event-driven sagas (e.g., Order Service → Payment Service → Notification Service). Each publishes events, others react.

**Q: Doesn't this mean more operational complexity?**
A: Yes, but failures are now isolated. A cascading failure affecting all services is worse.

**Q: Can we do this without Kubernetes?**
A: Yes! Use ECS for container services + Lambda for serverless + RDS Proxy for shared DB access.

