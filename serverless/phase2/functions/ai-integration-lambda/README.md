# Phase 2: Independently Deployable Lambdas

This phase demonstrates **microservice segregation** in action: independently deployable Lambda functions that can build, test, and deploy without touching other services.

## Architecture

```
Phase 1                    Phase 2
├─ Resume Parser Lambda    ├─ AI Integration Lambda
│                            │  - POST /ai/generate
│                            │  - GET /health
│                            │  - Stateless
│                            │  - Independent build/deploy
│                            └─ Job Analyzer Lambda
│                               - POST /job/analyze
│                               - GET /health
│                               - Stateless
│                               - Independent build/deploy
```

## Available artifacts

- `template.yaml` + `samconfig.toml` → AI Integration Lambda
- `job-analyzer-template.yaml` + `job-analyzer-samconfig.toml` → Job Analyzer Lambda
- `functions/ai-integration-lambda/` → AI Integration source, tests, packaging
- `functions/job-analyzer-lambda/` → Job Analyzer source, tests, packaging

## Key Features

- ✅ **Independent Build**: Builds without other services (see `build.gradle`)
- ✅ **API Contract**: Simple JSON-based request/response (no gRPC complexity for this demo)
- ✅ **Health Endpoint**: `GET /health` for monitoring
- ✅ **Unit Tests**: 6 comprehensive test cases
- ✅ **Packaging**: Automated Lambda ZIP creation
- ✅ **SAM Template**: Complete IaC for deployment

## Prerequisites

- Java 17 (or higher)
- Gradle (or `./gradlew`)
- SAM CLI (for local testing and deployment)
- Docker (for `sam local invoke`)
- AWS CLI + credentials (for deployment)

## Build (Independent)

Build this module **without** building other services:

```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant/serverless/phase2

# Option 1: Full build script
chmod +x build.sh
./build.sh

# Option 2: Manual steps
gradle :serverless:phase2:functions:ai-integration-lambda:test
gradle :serverless:phase2:functions:ai-integration-lambda:packageLambdaZip
sam validate -t template.yaml
```

## Local Testing

### Health Endpoint
```bash
sam local invoke AiIntegrationFunction \
  -t template.yaml \
  -e events/health-check.json
```

Expected response:
```json
{
  "statusCode": 200,
  "body": "{\"status\":\"UP\",\"service\":\"ai-integration-lambda\"}"
}
```

### AI Generation Endpoint
```bash
sam local invoke AiIntegrationFunction \
  -t template.yaml \
  -e events/ai-generate-request.json
```

Expected response:
```json
{
  "statusCode": 200,
  "body": "{\"status\":\"SUCCESS\",\"data\":{\"generatedContent\":\"...\",\"model\":\"gpt-4\",\"tokensUsed\":500,\"generatedAt\":\"...\"}}"
}
```

## API Contract

### POST /ai/generate

**Request Body:**
```json
{
  "prompt": "Generate 5 Java interview questions for a senior backend engineer",
  "model": "gpt-4",
  "maxTokens": 500
}
```

**Response (Success):**
```json
{
  "status": "SUCCESS",
  "data": {
    "generatedContent": "Generated content...",
    "model": "gpt-4",
    "tokensUsed": 500,
    "generatedAt": "2026-04-02T12:30:45.123Z"
  }
}
```

**Response (Error):**
```json
{
  "status": "ERROR",
  "error": "Fields 'prompt' and 'model' are required"
}
```

### GET /health

**Response:**
```json
{
  "status": "UP",
  "service": "ai-integration-lambda"
}
```

## Deployment (Guided)

**First deployment (interactive):**
```bash
sam deploy --guided \
  --stack-name smart-hiring-phase2-ai-integration \
  --region us-east-1
```

**Subsequent deployments (non-guided):**
```bash
sam deploy --config-env default
```

**Auto mode (uses samconfig.toml if available):**
```bash
sam deploy
```

## Project Structure

```
serverless/phase2/
├── functions/
│   └── ai-integration-lambda/
│       ├── src/
│       │   ├── main/java/org/vinod/sha/serverless/ai/
│       │   │   ├── AiIntegrationLambda.java (main handler)
│       │   │   ├── AiGenerateRequest.java  (DTO)
│       │   │   └── AiGenerateResponse.java (DTO)
│       │   └── test/java/org/vinod/sha/serverless/ai/
│       │       └── AiIntegrationLambdaTest.java (6 unit tests)
│       └── build.gradle (independent build config)
├── events/
│   ├── ai-generate-request.json (sample POST /ai/generate)
│   └── health-check.json (sample GET /health)
├── template.yaml (SAM IaC)
├── samconfig.toml (deployment config)
├── build.sh (convenience script)
└── README.md (this file)
```

## Key Design Decisions

### 1. **Stateless Design**
- No database reads/writes
- No session management
- Can scale to 1000s of concurrent invocations

### 2. **Simple JSON Contracts**
- No gRPC overhead for this service
- Easy to test and understand
- REST-friendly

### 3. **Independent Build**
- `build.gradle` has NO dependencies on other services
- Can deploy without other services running
- Clear segregation boundary

### 4. **Mocked AI for Demo**
- Production: wire to OpenAI, Claude, or internal AI service
- Demo: returns mock response showing contract works
- Easy to swap implementation without changing API

## Testing Strategy

### Unit Tests (6 tests)
- ✅ Health endpoint
- ✅ Successful AI generation
- ✅ Missing prompt validation
- ✅ Missing model validation
- ✅ Missing request body
- ✅ Invalid JSON handling

Run tests:
```bash
gradle :serverless:phase2:functions:ai-integration-lambda:test
```

### Integration Tests (Optional)
```bash
sam local invoke AiIntegrationFunction -t template.yaml -e events/ai-generate-request.json
```

## Monitoring & Alarms

After deployment, set up CloudWatch alarms (example):

```bash
# Monitor error rate
aws cloudwatch put-metric-alarm \
  --alarm-name ai-integration-errors \
  --alarm-description "Alert if AI Integration Lambda errors > 5%" \
  --metric-name Errors \
  --namespace AWS/Lambda \
  --statistic Sum \
  --period 300 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=FunctionName,Value=sha-phase2-ai-integration

# Monitor latency
aws cloudwatch put-metric-alarm \
  --alarm-name ai-integration-duration \
  --alarm-description "Alert if p95 duration > 10 seconds" \
  --metric-name Duration \
  --namespace AWS/Lambda \
  --statistic Average \
  --period 300 \
  --threshold 10000 \
  --comparison-operator GreaterThanThreshold
```

## Cost Estimate

At ~100 AI generation requests per day:
- **Invocations**: 100 calls/day × $0.0000002/call = ~$0.60/month
- **Duration**: 100 calls × 1s × $0.0000166/GB-s (1GB memory) = ~$1.66/month
- **Total**: ~$2.26/month

(Compare to ECS container running 24/7: ~$100/month)

## Next Steps (According to Architecture Roadmap)

1. ✅ **Phase 1 Complete**: Resume Parser Lambda deployed
2. ✅ **Phase 2 (This)**: AI Integration Lambda scaffolded
3. **Phase 2 Continued**: Job Analyzer Lambda, Interview Prep Lambda
4. **Phase 3**: Screening Bot (Step Functions), Notifications Lambda
5. **Phase 4**: API Gateway refactoring, cost optimization

## Segregation Checklist (For Your Portfolio)

Use this to explain segregation in interviews:

- [x] Separate module path (`serverless/phase2/functions/ai-integration-lambda/`)
- [x] Separate build pipeline (`build.gradle` is independent)
- [x] Separate deploy command (`sam deploy --config-env default`)
- [x] API contract unchanged for consumers (POST /ai/generate, GET /health)
- [x] Other services can remain down while this deploys/works
- [x] Clear failure boundary (Lambda error ≠ cascade)

**This is what "independently deployable" means in architecture interviews!**

## Support

- Full roadmap: `SERVERLESS_MIGRATION_ROADMAP.md`
- Separation strategy: `MICROSERVICE_SEPARATION_STRATEGY.md`
- Quick reference: `SERVERLESS_SEPARATION_QUICK_REFERENCE.md`

---

**Created**: April 2, 2026  
**Status**: Ready for hands-on segregation exercise  
**Next**: Build it, test it locally, deploy it independently!

