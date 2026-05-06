# Kubernetes Database Migration Init Containers

This directory contains Kubernetes manifests with init container support for database schema initialization using the consolidated migration script.

## Overview

Init containers run before the main application containers start, ensuring the PostgreSQL schema is ready before the application attempts to connect.

## Files

- **`db-migration-configmap.yaml`** - ConfigMap containing the complete migration SQL script
  - Uses `migrate-all-to-hiring.sql` logic
  - Handles auth-service and resume-parser tables
  - Idempotent (safe to run multiple times)

- **`db-migration-volume-patch.yaml`** - Strategic merge patch for volume mounts
  - Injects the ConfigMap volume into auth, resume-parser, and candidate-matcher deployments
  - Mount path: `/migrations/migrate-all-to-hiring.sql`

## How It Works

### Init Container Execution

```yaml
initContainers:
  - name: db-migrate
    image: postgres:16-alpine
    command: ["sh", "-c"]
    args:
      - |
        until pg_isready -h postgres -p 5432 -U hiring_user; do
          echo "Waiting for PostgreSQL..."
          sleep 5
        done
        psql -h postgres -p 5432 -U hiring_user -d smart_hiring_db -f /migrations/migrate-all-to-hiring.sql
```

**Sequence:**
1. Pod is created
2. **Init container runs first:**
   - Waits for PostgreSQL to be ready (`pg_isready`)
   - Executes migration script via `psql`
   - Exits when complete
3. **Main application container starts:**
   - Database schema is already initialized
   - Ready to connect and use tables

## Services Using Init Containers

| Service | Port | Init Container |
|---------|------|-----------------|
| `auth-service` | 8001 | ✅ Yes - Creates `hiring.auth_users` |
| `resume-parser-service` | 8002 | ✅ Yes - Creates `hiring.resume_parser_*` |
| `candidate-matcher-service` | 8003 | ✅ Yes - Depends on schema |
| `interview-prep-service` | 8004 | ❌ No - Uses MongoDB |
| `screening-bot-service` | 8006 | ❌ No - Uses MongoDB |
| `notification-service` | 8007 | ✅ Yes - May need logging tables |

## Deployment

### Deploy with Kustomize

```bash
# Staging environment
kubectl apply -k infrastructure/kubernetes/overlays/staging

# Production environment
kubectl apply -k infrastructure/kubernetes/overlays/prod
```

### Verify Init Containers

```bash
# Check if init containers completed successfully
kubectl get pods -n smart-hiring --field-selector=status.phase=Running -o json | \
  jq '.items[].status.initContainerStatuses'

# View init container logs
kubectl logs <pod-name> -n smart-hiring -c db-migrate

# Check main container logs (after init completes)
kubectl logs <pod-name> -n smart-hiring -c <service-name>
```

### Monitor Migration Progress

```bash
# Watch init container startup
kubectl describe pod <pod-name> -n smart-hiring

# Expected output in Events section:
# - "db-migrate" container initializing
# - "db-migrate" container terminated successfully
# - Main container starting
```

## Resource Limits

Init containers have modest resource requests/limits to avoid cluster overhead:

```yaml
resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 250m
    memory: 256Mi
```

## Security Context

Init containers run with minimal privileges:

```yaml
securityContext:
  runAsNonRoot: false  # Required for psql client
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
```

## Troubleshooting

### Init Container Stuck at "Waiting for PostgreSQL"

**Symptoms:** Pod pending, init container logs show connection timeouts

**Solutions:**
1. Verify PostgreSQL pod is running:
   ```bash
   kubectl get pods -n smart-hiring | grep postgres
   ```
2. Check PostgreSQL service DNS:
   ```bash
   kubectl exec <pod-name> -c db-migrate -- nslookup postgres.smart-hiring.svc.cluster.local
   ```
3. Verify credentials in secret:
   ```bash
   kubectl get secret smart-hiring-secrets -n smart-hiring -o jsonpath='{.data.postgres-password}' | base64 -d
   ```

### Migration Script Fails

**Symptoms:** Init container logs show SQL errors

**Solutions:**
1. Verify migration script syntax:
   ```bash
   kubectl get configmap db-migration-script -n smart-hiring -o jsonpath='{.data.migrate-all-to-hiring\.sql}' | head -50
   ```
2. Check database connectivity:
   ```bash
   kubectl exec <pod-name> -c db-migrate -- psql -h postgres -U hiring_user -d smart_hiring_db -c "SELECT version();"
   ```
3. Review PostgreSQL logs:
   ```bash
   kubectl logs <postgres-pod> -n smart-hiring
   ```

### Main Container Won't Start

**Symptoms:** Pod shows init container succeeded, but main container fails

**Solutions:**
1. Check main container readiness probe:
   ```bash
   kubectl describe pod <pod-name> -n smart-hiring
   ```
2. Review main container logs:
   ```bash
   kubectl logs <pod-name> -c <service-name> --previous
   ```
3. Verify tables were created:
   ```bash
   kubectl exec <pod-name> -c db-migrate -- psql -h postgres -U hiring_user -d smart_hiring_db -c "\dt hiring.*;"
   ```

## Migration Script Changes

If you update `migrate-all-to-hiring.sql`, update the ConfigMap:

```bash
kubectl set env configmap/db-migration-script -n smart-hiring \
  --from-file=infrastructure/kubernetes/base/db-migration-configmap.yaml
```

Or redeploy:

```bash
kubectl apply -k infrastructure/kubernetes/overlays/staging
```

## Performance Considerations

- **Init container overhead:** ~5-10 seconds for migration
- **PostgreSQL connection pool:** Shared across all pods
- **Idempotency:** Migration is safe to run multiple times (uses `IF NOT EXISTS`, `ON CONFLICT`)
- **Rollout strategy:** Sequential pod updates ensure only one migration at a time

## Future Enhancements

- [ ] Add init container logs to Elasticsearch for better observability
- [ ] Implement schema versioning with migrations table
- [ ] Create separate init containers for different migration phases
- [ ] Add metrics for migration execution time
- [ ] Support for blue/green deployments with schema evolution

