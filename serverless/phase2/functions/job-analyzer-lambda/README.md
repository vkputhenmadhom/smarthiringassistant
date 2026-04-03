# Job Analyzer Lambda

Independently deployable Phase 2 Lambda for lightweight job description analysis.

## Independent workflow

- Workflow file: `.github/workflows/deploy-phase2-job-analyzer.yml`
- Stack name: `smart-hiring-phase2-job-analyzer`
- SAM template: `serverless/phase2/job-analyzer-template.yaml`
- SAM config: `serverless/phase2/job-analyzer-samconfig.toml`

### Workflow trigger scope

The workflow runs only when one of these changes:

- `serverless/phase2/functions/job-analyzer-lambda/**`
- `serverless/phase2/job-analyzer-template.yaml`
- `serverless/phase2/job-analyzer-samconfig.toml`
- `serverless/phase2/events/job-analyze-request.json`
- `serverless/phase2/events/job-analyzer-health-check.json`
- `.github/workflows/deploy-phase2-job-analyzer.yml`

## Endpoints

- `POST /job/analyze`
- `GET /health`

## Build

```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant
./gradlew :serverless:phase2:functions:job-analyzer-lambda:test
./gradlew :serverless:phase2:functions:job-analyzer-lambda:packageLambdaZip
```

## Validate SAM

```bash
sam validate -t serverless/phase2/job-analyzer-template.yaml --region us-east-1
```

## Local invoke

```bash
sam local invoke JobAnalyzerFunction \
  -t serverless/phase2/job-analyzer-template.yaml \
  -e serverless/phase2/events/job-analyze-request.json
```

## Deploy

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

## Deployment path

### GitHub Actions path

1. Push changes under the job-analyzer paths listed above
2. GitHub Actions runs `.github/workflows/deploy-phase2-job-analyzer.yml`
3. The workflow builds, tests, packages, validates, and deploys the Lambda
4. The workflow performs a health check against the deployed endpoint

### Manual path

Use the `sam deploy` command above when you want a manual deploy outside GitHub Actions.

## Artifacts

- Packaged ZIP: `serverless/phase2/functions/job-analyzer-lambda/build/function.zip`
- Local event: `serverless/phase2/events/job-analyze-request.json`
- Health event: `serverless/phase2/events/job-analyzer-health-check.json`

