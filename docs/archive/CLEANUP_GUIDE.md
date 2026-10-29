# Cleanup Guide

Complete guide for cleaning up all Smart Hiring Assistant resources.

## Quick Start

### Dry Run (Recommended First)
See what will be deleted without actually deleting:

```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant
./scripts/cleanup-all.sh --dry-run
```

### Force Cleanup (No Confirmation)
Skip interactive prompts:

```bash
./scripts/cleanup-all.sh --force
```

### Interactive Cleanup (Default)
Asks for confirmation before deleting:

```bash
./scripts/cleanup-all.sh
```

---

## What Gets Cleaned Up

### 1. **AWS CloudFormation Stack** 🔥
- Deletes the SAM-deployed Lambda stack
- Removes API Gateway endpoint
- Removes IAM roles and permissions
- **Stack name**: `smart-hiring-phase1-deploy` (configurable via `STACK_NAME` env var)

```bash
# Custom stack name
STACK_NAME=my-custom-stack ./scripts/cleanup-all.sh
```

### 2. **Docker Resources** 🐳
- Stops all running containers
- Removes Docker networks
- Removes Docker volumes (data persistence)
- Cleans up project images

Targets these compose files:
- `docker-compose.yml`
- `docker-compose.apps.yml`
- `docker-compose.monitoring-staging.yml`

### 3. **Local Build Artifacts** 🗑️
- Gradle build caches (`.gradle/`)
- Module build directories (`*/build/`)
- SAM build cache (`.aws-sam/`)
- Frontend node_modules and dist directories

### 4. **AWS Resources** ☁️
- CloudWatch log groups
- Orphaned Lambda function logs
- API Gateway logs

### 5. **Environment Files** ⚙️
- `.env.local` (local overrides)
- Temporary SAM config backups

---

## What Gets Preserved ✅

The cleanup script is careful to keep important things:
- ✅ All source code (`src/`, `services/`, `frontend/`)
- ✅ Git history and commits
- ✅ Configuration backups in `.cleanup-backup-*` directory
- ✅ Original `samconfig.toml` (backed up before deletion)
- ✅ `.env` file (backed up before deletion)
- ✅ Documentation and README files

---

## Configuration

### Environment Variables

```bash
# Override the CloudFormation stack name
export STACK_NAME=my-stack-name
./scripts/cleanup-all.sh

# Override AWS region
export AWS_REGION=eu-west-1
./scripts/cleanup-all.sh

# Both together
STACK_NAME=my-stack AWS_REGION=eu-west-1 ./scripts/cleanup-all.sh --force
```

### Command-Line Options

| Option | Effect |
|--------|--------|
| `--dry-run` | Preview what would be deleted (no changes made) |
| `--force` | Skip interactive confirmation prompt |
| `--help` | Show help message |

---

## Step-By-Step Cleanup Workflow

### 1️⃣ Safety Check (Always Do This First)

```bash
./scripts/cleanup-all.sh --dry-run
```

Review the output. The script will show:
- What CloudFormation stack will be deleted
- What Docker resources will be removed
- What build artifacts will be cleaned
- What AWS resources will be cleaned

### 2️⃣ Optional: Review Backups

The script automatically backs up important configs:

```bash
# Find the latest backup directory
ls -la .cleanup-backup-*

# Contents will include:
# - samconfig.toml (SAM deployment config)
# - .env.backup (environment variables)
```

### 3️⃣ Run Cleanup

**Interactive (recommended for first time):**
```bash
./scripts/cleanup-all.sh
# Script will ask for confirmation before deleting
```

**Force (for automation/scripts):**
```bash
./scripts/cleanup-all.sh --force
```

### 4️⃣ Monitor CloudFormation Deletion

CloudFormation deletion happens asynchronously. Monitor it:

```bash
# Check deletion status
aws cloudformation describe-stacks \
  --stack-name smart-hiring-phase1-deploy \
  --region us-east-1 \
  --query 'Stacks[0].StackStatus'

# Expected: DELETE_IN_PROGRESS, then stack disappears after ~3 minutes
```

---

## Scenarios

### Scenario 1: Clean Everything for Fresh Start
```bash
./scripts/cleanup-all.sh --dry-run    # Preview
./scripts/cleanup-all.sh --force      # Actually clean
```

### Scenario 2: Keep Source Code, Remove Deployments
The cleanup script is designed for this! It only removes:
- Built artifacts
- Deployed services
- Docker containers

But keeps all your code intact.

### Scenario 3: Stop Incurring AWS Costs
If your only concern is AWS charges:
```bash
# Just delete the CloudFormation stack (cheapest option)
aws cloudformation delete-stack \
  --stack-name smart-hiring-phase1-deploy \
  --region us-east-1
```

This removes Lambda (the only AWS charge). Docker and build artifacts are free to leave running.

### Scenario 4: Clean Up Locally But Keep AWS Deployment
If you only want to free up disk space:
```bash
# Modify and run just the local cleanup parts manually
rm -rf .gradle build
docker-compose down -v
find . -name "node_modules" -type d -exec rm -rf {} + 2>/dev/null
```

---

## Recovery

### If You Need to Redeploy

Everything is reversible! Source code is never deleted.

**Redeploy Lambda:**
```bash
# Restore samconfig.toml if you kept the backup
cp .cleanup-backup-*/samconfig.toml .

# Redeploy
./scripts/serverless-phase1-deploy.sh --guided
```

**Restart Docker Services:**
```bash
# Pull fresh images and start
docker-compose up -d
```

**Rebuild Gradle Caches:**
```bash
# Happens automatically on next build
./gradlew build
```

---

## Cost Impact

### Current Costs While Running
| Resource | Monthly Cost (Our Usage) |
|----------|--------------------------|
| Lambda (free tier: 1M invocations) | **$0** |
| API Gateway | **$0** (negligible) |
| Docker (local) | **$0** |
| Gradle cache | **$0** |
| **Total** | **$0** |

### After Cleanup
| Resource | Monthly Cost |
|----------|--------------|
| Everything | **$0** |

**No ongoing costs once deleted!**

---

## Troubleshooting

### Script Hangs on Docker Cleanup
If Docker is slow:
```bash
# Stop manually
docker-compose -f docker-compose.yml down -v
docker-compose -f docker-compose.apps.yml down -v
docker-compose -f docker-compose.monitoring-staging.yml down -v

# Then try cleanup again
./scripts/cleanup-all.sh
```

### CloudFormation Stack Won't Delete
```bash
# Check what's blocking it
aws cloudformation describe-stack-resources \
  --stack-name smart-hiring-phase1-deploy \
  --region us-east-1

# Force delete (last resort)
aws cloudformation delete-stack \
  --stack-name smart-hiring-phase1-deploy \
  --force-delete-stack \
  --region us-east-1
```

### AWS CLI Not Found
```bash
# Install AWS CLI
brew install awscli   # macOS

# Verify
aws --version
```

---

## Integration with CI/CD

### GitHub Actions Example

```yaml
# .github/workflows/cleanup.yml
name: Cleanup Resources

on:
  workflow_dispatch:  # Manual trigger only

jobs:
  cleanup:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Run cleanup script
        env:
          AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          AWS_REGION: us-east-1
        run: |
          chmod +x scripts/cleanup-all.sh
          ./scripts/cleanup-all.sh --force
```

---

## Advanced Options

### Clean Only CloudFormation (Keep Docker/Local)
```bash
# Extract and run just this part from the script
aws cloudformation delete-stack \
  --stack-name smart-hiring-phase1-deploy \
  --region us-east-1
```

### Clean Only Docker (Keep Lambda/Local)
```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant
docker-compose down -v
docker-compose -f docker-compose.apps.yml down -v
docker-compose -f docker-compose.monitoring-staging.yml down -v
```

### Clean Only Local Artifacts (Keep AWS/Docker)
```bash
rm -rf .gradle .aws-sam build
find . -name "build" -type d -exec rm -rf {} + 2>/dev/null
```

---

## Questions?

### Should I clean up?
- ✅ Yes, if you're done experimenting
- ✅ Yes, if you want to save disk space
- ✅ Yes, if you're worried about surprise charges
- ❌ No, if you want to keep the demo live for LinkedIn

### Will I lose my code?
**No!** Only builds and deployed services are removed. All source code is preserved.

### Can I redo it after cleaning?
**Yes!** Everything is reversible. Just redeploy using the deployment script.

### What if I only delete the Lambda stack?
Then Docker, build artifacts, and local code remain. That's fine! Lambda stack is the only AWS charge.


