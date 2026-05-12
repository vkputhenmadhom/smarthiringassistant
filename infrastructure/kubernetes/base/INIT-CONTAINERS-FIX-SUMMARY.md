# Init Containers Fix Summary

## ✅ Issues Fixed

### 1. Deprecation Warning
**Before:**
```yaml
patchesStrategicMerge:
  - db-migration-volume-patch.yaml
```

**After:**
```yaml
patches:
  - path: db-migration-auth-service-patch.yaml
    target:
      group: apps
      version: v1
      kind: Deployment
      name: auth-service
  - path: db-migration-resume-parser-patch.yaml
    target:
      group: apps
      version: v1
      kind: Deployment
      name: resume-parser-service
  - path: db-migration-candidate-matcher-patch.yaml
    target:
      group: apps
      version: v1
      kind: Deployment
      name: candidate-matcher-service
```

**Why:** Kustomize v5+ deprecated `patchesStrategicMerge` in favor of the modern `patches` syntax with explicit target selectors.

---

### 2. Patch Target Matching Error
**Before:**
```
error: no matches for Id Deployment.v1.apps/auth-service.[noNs]; 
       failed to find unique target for patch
```

**Why:** Using a single patch file with multiple deployments caused target ambiguity.

**After:** Split into 3 separate patch files:
- `db-migration-auth-service-patch.yaml`
- `db-migration-resume-parser-patch.yaml`
- `db-migration-candidate-matcher-patch.yaml`

Each file has explicit target metadata matching the deployment name.

---

## ✅ Verification

Kustomize build successful:
```bash
$ kubectl kustomize infrastructure/kubernetes/base
✓ Generated 1193 lines of manifests
✓ No errors or warnings
```

**Init container in candidate-matcher-service:**
```yaml
initContainers:
  - name: db-migrate
    image: postgres:16-alpine
    command: ["sh", "-c"]
    args:
      - |
        until pg_isready -h postgres -p 5432 -U hiring_user; do
          echo "Waiting for PostgreSQL to be ready..."
          sleep 5
        done
        echo "PostgreSQL is ready!"
        psql -h postgres -p 5432 -U hiring_user -d smart_hiring_db -f /migrations/migrate-all-to-hiring.sql
    env:
      - name: PGPASSWORD
        valueFrom:
          secretKeyRef:
            name: smart-hiring-secrets
            key: postgres-password
    volumeMounts:
      - name: migration-script
        mountPath: /migrations
    resources:
      requests:
        cpu: 100m
        memory: 128Mi
      limits:
        cpu: 250m
        memory: 256Mi
    securityContext:
      runAsNonRoot: false
      allowPrivilegeEscalation: false
      readOnlyRootFilesystem: true

volumes:
  - name: migration-script
    configMap:
      name: db-migration-script
      items:
        - key: migrate-all-to-hiring.sql
          path: migrate-all-to-hiring.sql
```

---

## Files Created/Modified

✅ Created:
- `db-migration-auth-service-patch.yaml`
- `db-migration-resume-parser-patch.yaml`
- `db-migration-candidate-matcher-patch.yaml`

✅ Modified:
- `kustomization.yaml` (updated to modern `patches` syntax)

✅ Existing:
- `db-migration-configmap.yaml` (migration SQL script)
- `INIT-CONTAINERS-README.md` (documentation)

---

## Deploy Command

```bash
# Now works without deprecation warnings or errors
kubectl apply -k infrastructure/kubernetes/overlays/staging
kubectl apply -k infrastructure/kubernetes/overlays/prod
```

## Next Steps

- Deploy to test cluster to verify init containers execute successfully
- Monitor logs: `kubectl logs <pod> -n smart-hiring -c db-migrate`
- Verify tables created: `kubectl exec <pod> -c db-migrate -- psql -h postgres -U hiring_user -d smart_hiring_db -c "\dt hiring.*;"`

