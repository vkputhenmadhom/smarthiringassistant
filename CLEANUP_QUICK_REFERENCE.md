# 🧹 Cleanup Quick Reference

## TL;DR - Just Show Me the Commands

```bash
# Preview what will be deleted (safe, no changes)
./scripts/cleanup-all.sh --dry-run

# Actually clean everything (interactive, asks for confirmation)
./scripts/cleanup-all.sh

# Force cleanup without asking
./scripts/cleanup-all.sh --force
```

---

## What Gets Deleted?

| Component | Deleted | Status |
|-----------|---------|--------|
| **AWS CloudFormation Stack** | ✓ | Removes Lambda, API Gateway, IAM roles |
| **Docker Containers** | ✓ | Stops all services |
| **Docker Volumes** | ✓ | Removes persistent data |
| **Build Caches** | ✓ | `.gradle/`, `build/`, `.aws-sam/` |
| **Frontend Dependencies** | ✓ | `node_modules/`, `dist/` |
| **CloudWatch Logs** | ✓ | Cleans up log groups |
| **Source Code** | ✗ | Preserved! (stays untouched) |
| **Git History** | ✗ | Preserved! |
| **Backups** | ✗ | Created in `.cleanup-backup-*` |

---

## Cost After Cleanup

| Item | Cost |
|------|------|
| **Monthly Bill** | **$0** |
| **Remaining Resources** | None |
| **Time to Delete** | ~5 minutes |

---

## Recovery

To redeploy after cleanup:

```bash
# 1. Restore config (if you want)
cp .cleanup-backup-*/samconfig.toml .

# 2. Redeploy Lambda
./scripts/serverless-phase1-deploy.sh --guided

# 3. Restart Docker
docker-compose up -d
```

All source code is preserved, so redeployment is fast!

---

## Common Scenarios

### "I want to free disk space but keep AWS Lambda"
```bash
# Manually clean just local stuff (skip AWS)
rm -rf .gradle build .aws-sam
find . -name "node_modules" -type d -exec rm -rf {} + 2>/dev/null
```

### "I want to stop AWS charges but keep local Docker"
```bash
# Just delete the CloudFormation stack
aws cloudformation delete-stack \
  --stack-name smart-hiring-phase1-deploy \
  --region us-east-1
```

### "I want to completely nuke everything"
```bash
./scripts/cleanup-all.sh --force
```

### "I'm not sure what will happen"
```bash
# Always do dry-run first!
./scripts/cleanup-all.sh --dry-run
```

---

## Environment Variables

```bash
# Use different stack name
STACK_NAME=custom-stack ./scripts/cleanup-all.sh --dry-run

# Use different AWS region
AWS_REGION=eu-west-1 ./scripts/cleanup-all.sh --force

# Both
STACK_NAME=my-stack AWS_REGION=eu-west-1 ./scripts/cleanup-all.sh
```

---

## For Full Documentation

See: [`CLEANUP_GUIDE.md`](./CLEANUP_GUIDE.md)

---

## Questions?

| Question | Answer |
|----------|--------|
| Will I lose my code? | **No**, all source code is safe |
| Can I redo it? | **Yes**, just redeploy |
| Is it reversible? | **Yes**, backups are created |
| Does it cost money? | **No**, cleanup is free |
| How long does it take? | **~5 minutes** (mostly CloudFormation) |
| Will I still pay AWS? | **No**, everything is deleted |
| Is it safe? | **Yes**, we back up important files first |

---

**Pro Tip:** Always run with `--dry-run` first to see what will happen!

