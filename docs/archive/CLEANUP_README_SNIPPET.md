<!-- Add this section to your main README.md -->

## 🧹 Cleanup & Cost Management

### Tearing Down Resources

If you're done with the deployment or want to clean up to save disk space:

```bash
# Preview what will be deleted (always do this first!)
./scripts/cleanup-all.sh --dry-run

# Interactive cleanup (asks for confirmation)
./scripts/cleanup-all.sh

# Force cleanup without confirmation
./scripts/cleanup-all.sh --force
```

**What gets cleaned up:**
- ✅ AWS CloudFormation stack (Lambda, API Gateway, IAM roles)
- ✅ Docker containers and networks
- ✅ Docker volumes
- ✅ Local build caches and artifacts
- ✅ Frontend dependencies
- ✅ CloudWatch logs
- ❌ Source code (always preserved!)
- ❌ Git history (always preserved!)

### Cost Analysis

**While Deployed:**
| Resource | Our Usage | Monthly Cost |
|----------|-----------|--------------|
| Lambda Invocations | 1M free tier | **$0** |
| API Gateway | Minimal | **$0** |
| Docker (local) | N/A | **$0** |
| **Total** | | **$0** |

**Key Point:** You **DO NOT** incur costs when Lambda is deployed but not running. You only pay for:
- Actual invocations (requests)
- Compute duration (execution time)

Free tier covers 1M invocations/month + 400,000 GB-seconds of compute.

### After Cleanup

Once you run the cleanup script, **all costs stop immediately** - there are no idle/standing costs.

### Recovery

Everything is reversible! All source code is preserved:
```bash
# Restore from backup
cp .cleanup-backup-*/samconfig.toml .

# Redeploy
./scripts/serverless-phase1-deploy.sh --guided
docker-compose up -d
```

### Documentation

- **Quick Reference**: `CLEANUP_QUICK_REFERENCE.md`
- **Full Guide**: `CLEANUP_GUIDE.md`
- **Script Help**: `./scripts/cleanup-all.sh --help`

---

**Bottom Line:** No charges while idle. Cost is $0 after cleanup. Everything is recoverable.

