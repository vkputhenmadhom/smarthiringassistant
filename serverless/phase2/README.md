# Phase 2 Serverless Artifacts

Phase 2 contains independently deployable Lambda artifacts that extend the serverless migration beyond the Phase 1 resume parser.

## Available Lambdas

### 1. AI Integration Lambda
- Module: `serverless/phase2/functions/ai-integration-lambda/`
- SAM template: `serverless/phase2/template.yaml`
- Workflow: `.github/workflows/deploy-phase2-ai-integration.yml`
- Endpoints:
  - `POST /ai/generate`
  - `GET /health`

### 2. Job Analyzer Lambda
- Module: `serverless/phase2/functions/job-analyzer-lambda/`
- SAM template: `serverless/phase2/job-analyzer-template.yaml`
- Workflow: `.github/workflows/deploy-phase2-job-analyzer.yml`
- Endpoints:
  - `POST /job/analyze`
  - `GET /health`

## Independent Deployment Paths

Each Phase 2 Lambda has its own:
- source directory
- Gradle packaging task
- SAM template
- deploy stack name
- GitHub Actions workflow

That means you can change and deploy one artifact without affecting the other.

## Quick Build Commands

```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant

# AI Integration Lambda
./gradlew :serverless:phase2:functions:ai-integration-lambda:test
./gradlew :serverless:phase2:functions:ai-integration-lambda:packageLambdaZip
sam validate -t serverless/phase2/template.yaml --region us-east-1

# Job Analyzer Lambda
./gradlew :serverless:phase2:functions:job-analyzer-lambda:test
./gradlew :serverless:phase2:functions:job-analyzer-lambda:packageLambdaZip
sam validate -t serverless/phase2/job-analyzer-template.yaml --region us-east-1
```

## GitHub Actions Workflows

### AI Integration
Triggered by changes to:
- `serverless/phase2/functions/ai-integration-lambda/**`
- `serverless/phase2/template.yaml`
- `serverless/phase2/samconfig.toml`
- `.github/workflows/deploy-phase2-ai-integration.yml`

### Job Analyzer
Triggered by changes to:
- `serverless/phase2/functions/job-analyzer-lambda/**`
- `serverless/phase2/job-analyzer-template.yaml`
- `serverless/phase2/job-analyzer-samconfig.toml`
- `serverless/phase2/events/job-analyze-request.json`
- `serverless/phase2/events/job-analyzer-health-check.json`
- `.github/workflows/deploy-phase2-job-analyzer.yml`

## Manual Deployment Paths

### AI Integration
```bash
sam deploy \
  --template-file serverless/phase2/template.yaml \
  --stack-name smart-hiring-phase2-ai-integration \
  --region us-east-1 \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --s3-prefix smart-hiring-phase2-ai-integration \
  --no-confirm-changeset
```

### Job Analyzer
```bash
sam deploy \
  --template-file serverless/phase2/job-analyzer-template.yaml \
  --stack-name smart-hiring-phase2-job-analyzer \
  --region us-east-1 \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --s3-prefix smart-hiring-phase2-job-analyzer \
  --no-confirm-changeset
```

## Local Invoke

### AI Integration
```bash
sam local invoke AiIntegrationFunction \
  -t serverless/phase2/template.yaml \
  -e serverless/phase2/events/ai-generate-request.json
```

### Job Analyzer
```bash
sam local invoke JobAnalyzerFunction \
  -t serverless/phase2/job-analyzer-template.yaml \
  -e serverless/phase2/events/job-analyze-request.json
```

## Why this matters for your portfolio

Phase 2 now demonstrates a repeatable pattern:
- multiple serverless components
- independent workflows
- isolated deployment paths
- shared architectural approach, separate operational ownership

This is the exact progression from “single Lambda proof of concept” to “repeatable serverless platform pattern.”

