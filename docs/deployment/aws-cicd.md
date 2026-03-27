# CI/CD and SonarQube Setup (GitHub Actions + AWS CodeDeploy)

This project now includes backend CI/CD automation in `.github/workflows/backend-services.yml`.
It is the single authoritative backend workflow for build, SonarQube quality gates, and AWS CodeDeploy.

## What runs in CI

For backend changes (`services/**`, `shared/**`, Gradle files):

1. Detect changed services and build only impacted backend services.
2. Run full backend tests and generate JaCoCo reports.
3. Run SonarQube analysis and enforce quality gates when Sonar secrets are configured.

SonarQube is configured with:

- Root Gradle `org.sonarqube` plugin + `jacoco` in `build.gradle`
- Monorepo scanner config in `sonar-project.properties`

## Required GitHub Secrets

Add these repository secrets before enabling quality gate enforcement/deploy:

- `SONAR_HOST_URL` (for self-hosted SonarQube, e.g., `https://sonarqube.example.com`)
- `SONAR_TOKEN`
- `AWS_GITHUB_ACTIONS_ROLE_ARN` (IAM role assumed through GitHub OIDC)
- `CODEDEPLOY_ARTIFACT_BUCKET` (S3 bucket for deployment bundles)

## Optional GitHub Variables

Add repository variables for deployment defaults:

- `AWS_REGION` (default in workflow: `us-east-1`)
- `CODEDEPLOY_APP_NAME` (default: `smart-hiring-assistant`)
- `CODEDEPLOY_GROUP_PREFIX` (default: `sha`)

Deployment group naming convention expected by workflow/scripts:

- `${CODEDEPLOY_GROUP_PREFIX}-${service}-${environment}`
- Example: `sha-api-gateway-staging`

## Manual Deployment to AWS CodeDeploy

Use the GitHub Actions workflow `backend-services` via **Run workflow**.

Inputs:

- `deploy_service`: service under `services/`
- `deploy_environment`: `staging` or `production`

The workflow will:

1. Build service bootJar.
2. Build CodeDeploy bundle with:
   - `deploy/codedeploy/appspec.yml`
   - `deploy/codedeploy/scripts/*.sh`
   - `service.jar`
3. Upload bundle to S3.
4. Trigger `aws deploy create-deployment`.

## EC2/CodeDeploy Host Prerequisites

On target instances:

1. Install CodeDeploy agent.
2. Ensure systemd unit exists per service:
   - `smart-hiring-<service>.service`
3. Ensure service uses jar path:
   - `/opt/smart-hiring/<service>/service.jar`

The hook scripts stop/start and validate this systemd unit.

## Local verification before pushing

```bash
./gradlew clean test jacocoTestReport --no-daemon --console=plain
```

## Notes

- If Sonar secrets are missing, CI still runs tests but skips Sonar analysis.
- Deploy jobs run only for manual `workflow_dispatch` executions.
- For production rollout, protect the `production` GitHub environment with required reviewers.

