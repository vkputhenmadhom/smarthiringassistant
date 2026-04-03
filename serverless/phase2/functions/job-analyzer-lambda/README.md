# Job Analyzer Lambda

Independently deployable Phase 2 Lambda for lightweight job description analysis.

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

