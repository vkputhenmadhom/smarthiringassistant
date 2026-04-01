# Phase 1 Serverless Starter (Quarkus + AWS SAM)

This starter lets you run a first Lambda-based slice immediately, without changing existing Spring services.

## Structure

- `serverless/phase1/template.yaml` - SAM template
- `serverless/phase1/events/resume-parse-request.json` - sample local invoke payload
- `serverless/phase1/functions/resume-parser-lambda/` - Quarkus Lambda module

## Prerequisites

- Java 17
- AWS SAM CLI
- Docker (for `sam local invoke`)

## Local Run

```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant
chmod +x scripts/serverless-phase1-smoke.sh
./scripts/serverless-phase1-smoke.sh
```

If Docker is not running yet, you can validate packaging/template only:

```bash
SKIP_LOCAL_INVOKE=true ./scripts/serverless-phase1-smoke.sh
```

Manual equivalent:

```bash
./gradlew --no-daemon :serverless:phase1:functions:resume-parser-lambda:test :serverless:phase1:functions:resume-parser-lambda:packageLambdaZip
sam validate -t serverless/phase1/template.yaml
sam local invoke ResumeParserFunction \
  -t serverless/phase1/template.yaml \
  -e serverless/phase1/events/resume-parse-request.json
```

## Expected Local Result

A JSON response with extracted fields like `email`, `skills`, and `experienceYears`.

## Deploy (guided)

```bash
sam deploy --guided --stack-name sha-phase1-dev
```

## CI Modes

- Default PR/push workflow runs fast mode (`SKIP_LOCAL_INVOKE=true`) using `scripts/serverless-phase1-smoke.sh`.
- Optional deep validation is available from Actions UI:
  - Workflow: `serverless-phase1`
  - Trigger: `Run workflow`
  - Input: set `run_full_local_invoke=true`

## Notes

- Function currently performs lightweight parsing for demo speed.
- This can be wired to S3 triggers/API Gateway in the next phase.
- Keep existing compose stack for the rest of the system while validating this serverless slice.
- After upgrading SAM CLI to a java21-capable version, you can switch runtime/toolchain back to Java 21.
