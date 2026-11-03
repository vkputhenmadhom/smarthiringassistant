# Cleanup Quick Commands

## Instant Access Reference

```bash
# ALWAYS START HERE - Preview what will be deleted
./scripts/cleanup-all.sh --dry-run

# Interactive cleanup (asks for confirmation)
./scripts/cleanup-all.sh

# Force cleanup without prompts
./scripts/cleanup-all.sh --force

# Show help
./scripts/cleanup-all.sh --help
```

## What You're Deleting

- ✅ AWS CloudFormation stack (Lambda, API Gateway, IAM)
- ✅ Docker containers & volumes
- ✅ Build caches (`.gradle/`, `build/`)
- ✅ Frontend dependencies (`node_modules/`)
- ✅ CloudWatch logs
- ❌ Source code (SAFE!)
- ❌ Git history (SAFE!)

## Cost

| Before | After |
|--------|-------|
| $0/mo  | $0/mo |

## Recovery

Everything is reversible:
```bash
cp .cleanup-backup-*/samconfig.toml .
./scripts/serverless-phase1-deploy.sh --guided
docker-compose up -d
```

## Advanced

```bash
# Custom stack name
STACK_NAME=my-stack ./scripts/cleanup-all.sh --dry-run

# Custom region
AWS_REGION=eu-west-1 ./scripts/cleanup-all.sh --force

# Both
STACK_NAME=prod AWS_REGION=us-west-2 ./scripts/cleanup-all.sh
```

## Files Created

- `scripts/cleanup-all.sh` - Main script
- `CLEANUP_GUIDE.md` - Full docs
- `CLEANUP_QUICK_REFERENCE.md` - TL;DR
- `CLEANUP_SYSTEM_SUMMARY.md` - Overview
- `CLEANUP_README_SNIPPET.md` - For README

---

**Always preview first with `--dry-run` before running cleanup!**

