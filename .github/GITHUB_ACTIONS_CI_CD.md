# GitHub Actions Workflows - Independent Serverless Deployment

## Overview

You now have **independent CI/CD pipelines** for each Lambda service. Each service deploys separately without affecting others.

```
Push to serverless/phase1/... 
    ↓
deploy-phase1-resume-parser.yml triggers
    ├─ Build Phase 1 only
    ├─ Test Phase 1 only
    ├─ Package Phase 1 only
    └─ Deploy Phase 1 stack (doesn't touch Phase 2)

Meanwhile:

Push to serverless/phase2/...
    ↓
deploy-phase2-ai-integration.yml triggers
    ├─ Build Phase 2 only
    ├─ Test Phase 2 only
    ├─ Package Phase 2 only
    └─ Deploy Phase 2 stack (doesn't touch Phase 1)

✅ Completely independent - no blocking, no crosstalk!
```

---

## Workflows Created

### 1. `.github/workflows/deploy-phase1-resume-parser.yml`
**Triggers when**:
- Files under `serverless/phase1/functions/resume-parser-lambda/**` change
- `serverless/phase1/template.yaml` changes
- Workflow file itself changes

**Does**:
1. Build Phase 1 Lambda independently
2. Run unit tests
3. Package as ZIP artifact
4. Validate SAM template
5. Deploy to AWS (only on main branch push)
6. Health check post-deployment

**Does NOT do**:
- ❌ Touch Phase 2 files
- ❌ Affect other services
- ❌ Deploy on pull requests (only test/validate)

### 2. `.github/workflows/deploy-phase2-ai-integration.yml`
**Triggers when**:
- Files under `serverless/phase2/functions/ai-integration-lambda/**` change
- `serverless/phase2/template.yaml` changes
- Workflow file itself changes

**Does**:
1. Build Phase 2 Lambda independently
2. Run unit tests
3. Package as ZIP artifact
4. Validate SAM template
5. Deploy to AWS (only on main branch push)
6. Health check post-deployment
7. Post summary to GitHub

**Does NOT do**:
- ❌ Touch Phase 1 files
- ❌ Affect other services
- ❌ Deploy on pull requests (only test/validate)

---

## Workflow Structure (Both Services)

### Stage 1: Build (2-3 minutes)
```yaml
./gradlew :serverless:phase2:functions:ai-integration-lambda:build -x test
```
✅ Compiles Java source code (skips tests to save time)
✅ Produces JAR artifact

### Stage 2: Test (2-3 minutes)
```yaml
./gradlew :serverless:phase2:functions:ai-integration-lambda:test
```
✅ Runs 6 unit tests
✅ Uploads test report to GitHub Artifacts (7 day retention)
✅ Fails if tests fail (blocks deployment)

### Stage 3: Package (1-2 minutes)
```yaml
./gradlew :serverless:phase2:functions:ai-integration-lambda:packageLambdaZip
```
✅ Creates function.zip (19 MB)
✅ Bundles all dependencies
✅ Uploads to GitHub Artifacts (30 day retention)

### Stage 4: Validate (< 1 minute)
```yaml
sam validate -t serverless/phase2/template.yaml --lint
```
✅ Validates SAM template structure
✅ Checks for CloudFormation syntax errors
✅ Optional linting for best practices

### Stage 5: Deploy (3-5 minutes, only on main push)
```yaml
sam deploy --template-file serverless/phase2/template.yaml \
  --config-file serverless/phase2/samconfig.toml \
  --stack-name smart-hiring-phase2-ai-integration
```
✅ Deploys CloudFormation stack to AWS
✅ Updates Lambda function code
✅ Creates/updates API Gateway endpoints
✅ Creates/updates IAM roles

### Stage 6: Smoke Test (Health Check, ~10 seconds)
```bash
curl https://<api-id>.execute-api.us-east-1.amazonaws.com/Prod/health
```
✅ Verifies deployment succeeded
✅ Checks Lambda is responding
✅ Includes retry logic for cold start

---

## When Workflows Trigger

### Phase 1 Workflow Triggers On:
```
✅ Push to main + serverless/phase1/** changes
✅ Pull request + serverless/phase1/** changes
❌ Push to main + serverless/phase2/** changes (different workflow)
❌ Push to main + services/** changes (no Lambda workflow)
```

### Phase 2 Workflow Triggers On:
```
✅ Push to main + serverless/phase2/** changes
✅ Pull request + serverless/phase2/** changes
❌ Push to main + serverless/phase1/** changes (different workflow)
❌ Push to main + services/** changes (no Lambda workflow)
```

---

## Deployment Rules

### Pull Request (PR)
- ✅ Build Phase 1/2
- ✅ Test Phase 1/2
- ✅ Package Phase 1/2
- ✅ Validate SAM
- ❌ **Does NOT deploy to AWS** (test-only)
- Check results in PR before merging

### Push to main
- ✅ Build
- ✅ Test
- ✅ Package
- ✅ Validate
- ✅ **Deploy to AWS** (if all previous pass)
- ✅ Health check post-deployment

### Push to other branch
- No workflow triggered (configured for main only)

---

## Monitoring Workflows

### In GitHub
1. Go to your repo → **Actions** tab
2. See all workflow runs
3. Click on specific run to see logs
4. Download artifacts (test results, ZIP)

### Example Workflow Run Output
```
✓ Build AI Integration Lambda (2m 30s)
  └─ Gradle build successful

✓ Test AI Integration Lambda (2m 15s)
  └─ 6 tests completed, 1 failed ← Would fail deployment!

✗ Test failed - see artifact "test-results"
  └─ Workflow stops here

✓ (PR only) Deployment blocked until tests pass
```

---

## AWS Credentials (Required for Deployment)

For workflows to deploy to AWS, you need GitHub Secrets:

1. Go to **Settings** → **Secrets and variables** → **Actions**
2. Add secrets:
   - `AWS_ACCESS_KEY_ID` - Your AWS access key
   - `AWS_SECRET_ACCESS_KEY` - Your AWS secret key
3. Workflows will use these automatically

⚠️ **Security**: Use an IAM user with minimal permissions (Lambda + CloudFormation + API Gateway + IAM)

---

## Cost & Performance

### Build + Test + Package + Deploy
- **Total time**: ~8-10 minutes per Phase
- **Free tier**: GitHub provides 2,000 free minutes/month (unlimited for public repos)
- **Cost**: $0 for most projects

### AWS Deployment Cost
- **Lambda deploy**: $0 (CloudFormation is free)
- **Running Lambda**: ~$2-5/month per service (see cost breakdown)

---

## Troubleshooting

### Workflow Not Triggering?
1. Check file paths match exactly:
   - `serverless/phase2/functions/ai-integration-lambda/**`
   - `serverless/phase2/template.yaml`
2. Make sure you pushed to `main` branch (not different branch)
3. Check **Actions** tab for any errors

### Build Fails?
1. Check Java 17 is available (workflow specifies this)
2. Check Gradle cache isn't corrupted (should auto-recover)
3. Run locally: `./gradlew :serverless:phase2:functions:ai-integration-lambda:build -x test`

### Tests Fail?
1. Run locally: `./gradlew :serverless:phase2:functions:ai-integration-lambda:test`
2. Download test results from GitHub Artifacts (HTML report)
3. Fix tests before deploying

### SAM Validation Fails?
1. Check SAM template syntax
2. Run locally: `sam validate -t serverless/phase2/template.yaml --lint`
3. Fix and push again

### Deployment Fails?
1. Check AWS credentials are set in Secrets
2. Check IAM user has permissions
3. Check stack name doesn't exist already (or update existing)
4. See CloudFormation logs in AWS console

### Health Check Fails?
1. Lambda might be in cold-start (workflow retries 5 times)
2. Check CloudWatch logs in AWS for errors
3. Manual test: `curl https://<endpoint>/health`

---

## Next Steps

### For Phase 2 Continued
Create similar workflows for:
- Job Analyzer Lambda (`deploy-phase2-job-analyzer.yml`)
- Interview Prep Lambda (`deploy-phase2-interview-prep.yml`)

Just copy Phase 2 workflow and change:
- `paths` (file paths)
- `env` variables (module, stack name)
- `runs-on` if needed

### Testing Workflows
1. Modify a file under `serverless/phase2/functions/ai-integration-lambda/`
2. Commit and push to main
3. Go to **Actions** → see workflow run
4. Watch it build, test, package, deploy
5. Check live endpoint

### Monitoring Deployments
Set up Slack/email notifications (optional):
- GitHub → Settings → Notifications
- Or add Slack action to workflow

---

## Key Benefits of Independent Workflows

✅ **No Blocking**
- Phase 1 deployment doesn't wait for Phase 2
- Phase 2 tests don't block Phase 1 deployment

✅ **Clear Ownership**
- Each service has its own workflow
- Easy to track which service is deploying when

✅ **Fast Feedback**
- Only changed services rebuild/test
- Unchanged services skip pipeline

✅ **Easy to Scale**
- Add Phase 3 service? Copy workflow, change paths
- Same pattern for all serverless services

✅ **Failure Isolation**
- Phase 1 fails? Phase 2 still deploys
- One service's test failure ≠ block all services

---

## Reference: Workflow Files

### Phase 1
- Location: `.github/workflows/deploy-phase1-resume-parser.yml`
- Triggers: `serverless/phase1/**`
- Stack: `smart-hiring-phase1-deploy`

### Phase 2
- Location: `.github/workflows/deploy-phase2-ai-integration.yml`
- Triggers: `serverless/phase2/**`
- Stack: `smart-hiring-phase2-ai-integration`

---

## Example Sequence

```
Time  Event
──────────────────────────────────────────────────
10:00 Developer pushes Phase 1 changes
      └─ Phase 1 workflow starts
      
10:02 Developer pushes Phase 2 changes (parallel)
      └─ Phase 2 workflow starts (parallel, no conflict)
      
10:05 Phase 1 tests fail
      └─ Phase 1 deployment blocked
      └─ Phase 2 continues unaffected
      
10:10 Phase 2 tests pass
      └─ Phase 2 deploys successfully
      
10:15 Phase 2 health check passes
      └─ Phase 2 live on AWS
      
10:15 Developer fixes Phase 1 tests
      └─ Phase 1 workflow reruns
      
10:22 Phase 1 tests pass
      └─ Phase 1 deploys
      
10:27 Phase 1 health check passes
      └─ Phase 1 live on AWS

✅ Both phases now live, independently deployed
```

---

## For Your Portfolio

When you show this code:
- Mention: *"Each service has independent CI/CD"*
- Point to workflows: *"See how Phase 1 and Phase 2 deploy separately"*
- Explain: *"Failure in Phase 1 doesn't block Phase 2 deployment"*
- Show: *"Full pipeline: build → test → package → validate → deploy"*

This demonstrates DevOps skills interviewers care about.

---

**Status**: ✅ Ready to use  
**Next**: Push changes to main and watch workflows run!

