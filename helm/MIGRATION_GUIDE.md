# Kustomize to Helm Migration Guide
Complete guide for migrating from Kustomize to Helm charts for the Smart Hiring Assistant project.
## Overview
The Smart Hiring Assistant project has been fully converted from Kustomize to Helm Charts. This guide provides step-by-step instructions for migration.
### Key Benefits of Helm Charts
- ✅ **Package Management** - Versioning and artifact distribution
- ✅ **Advanced Templating** - Full Go template engine for flexibility
- ✅ **Dependency Management** - Charts can depend on other charts
- ✅ **Rollbacks** - Easy version rollback with `helm rollback`
- ✅ **Release Management** - `helm history`, `helm list` for tracking
- ✅ **Community** - Published charts in Helm Hub
- ✅ **Enterprise Support** - Better for enterprise deployments
## Migration Path
### Phase 1: Preparation (Now)
- ✅ Helm chart created: `helm/smart-hiring-assistant/`
- ✅ Values extracted to: `values.yaml`, `values-staging.yaml`, `values-prod.yaml`
- ✅ All templates created and validated
- ✅ Documentation ready
### Phase 2: Testing (Next Steps)
1. Test Helm chart locally with `helm template`
2. Compare output with current Kustomize output
3. Deploy to test environment
4. Verify all pods and services work correctly
### Phase 3: Migration (Then)
1. Deploy to staging using Helm
2. Monitor logs and metrics
3. Deploy to production using Helm
4. Keep Kustomize configuration as fallback temporarily
5. Decommission Kustomize after confirmation
### Phase 4: Cleanup (Finally)
1. Archive old Kustomize files
2. Delete `infrastructure/kubernetes/overlays/` (if no longer needed)
3. Document the migration
4. Update CI/CD pipelines
## Chart Structure
```
helm/smart-hiring-assistant/
├── Chart.yaml                          # Chart metadata
├── README.md                           # Chart documentation
├── values.yaml                         # Default values
├── values-staging.yaml                 # Staging overrides
├── values-prod.yaml                    # Production overrides
└── templates/
    ├── _helpers.tpl                    # Template helpers
    ├── namespace.yaml                  # Namespace creation
    ├── deployment.yaml                 # All 7 microservice deployments
    ├── service.yaml                    # All 7 services
    ├── hpa.yaml                        # Horizontal Pod Autoscalers
    └── db-migration-configmap.yaml     # Database migration script
```
## Comparing Kustomize vs Helm
### Kustomize (Before)
```bash
# Deploy
kubectl apply -k infrastructure/kubernetes/overlays/staging
kubectl apply -k infrastructure/kubernkubectl apply -k in# Version management: Manual via git tags
# Rollback: kubec# Rollback: kubec# Rollback: kubec# Rollb### Helm (After)
```ba```ba```ba``helm install smart-hiring ./helm/smart-hiring-assistant -f values-staging.yaml
helm install smart-hiring ./helm/smart-hiring-assistant -f values-prod.yaml
# Version management: helm --version, semantic versioning
# Rollback: helm rollback smart-hiring 1
# History: helm history smart-hiring
# List: helm list
```
## Testing the Helm Chart
### 1. Validate Chart
```bash
# Check chart syntax
helm lint helm/smart-hiring-assistant
# Expected output: 0 chart(s) linted, all clean
```
### 2. Generate Manifests Locally
```bash
# Staging
helm template smart-hiring ./helm/smart-hiring-assistant \
  -f helm/smart-hiring-assistant/values-staging.yaml \
  --namespace smart-hiring
# Production
helm template smart-hiring ./helm/smart-hiring-assistant \
  -f helm/smart-hiring-assistant/values-prod.yaml \
  --namespace smart-hiring
```
### 3. Compare with Kustomize
```bash
# Generate current Kustomize output
kubectl kustomize infrastructure/kubernetes/overlays/staging > kustomize-staging.yaml
# Generate Helm output
helm template smart-hiring ./helm/smart-hiring-assistant \
  -f helm/smart-hiring-assistant/values-staging.yaml \
  --namespace smart-hiring > helm-staging.yaml
# Compare (differences are expected due to formatting)
diff -u kustomize-staging.yaml helm-staging.yaml | less
```
### 4. Dry-Run Deployment
```bash
# Test installation without applying
helm install smart-hiring ./helm/smart-hiring-assistant \
  -f helm/smart-hiring-assistant/values-staging.yaml \
  --namespace smart-hiring \
  --dry-run \
  --debug
```
## Migration Commands
### Staging Environment
```bash
# Step 1: Create namespace and secrets (if not exist)
kubectl create namespace smart-hiring || true
kubectl create secret generic smart-hiring-secrets \
  --from-literal=postgres-password='your-password' \
  --from-literal=rabbitmq-password='your-password' \
  --from-literal=mail-username='your-email' \
  --from-literal=mail-password='your-app-password' \
  --from-literal=twilio-account-sid='your-sid' \
  --from-literal=twilio-auth-token='your-token' \
  --from-literal=twilio-from-number='+1234567890' \
  -n smart-hiring 2>/dev/null || echo "Secret already exists"
# Step 2: Install Helm chart
helm install smart-hiring ./helm/smart-hiring-assistant \
  -f helm/smart-hiring-assistant/values-staging.yaml \
  --set image.ta  --set image.ta  --serse --short HEAD) \
  --namespace smart-hiring
# Step 3: Wait for deployment
kubectl wait --for=condition=Ready pods \
  -l app.kubernetes.io/managed-by=Helm \
                      --timeout=300s
# Step 4# Verify
kubectl get pods -n smart-hiring
kubectl get svc -n smart-hiring
```
### Production Environment
```bash
# Similar steps but with prod values
helm install smart-hiring ./helm/smart-hiring-assistant \
  -f helm/sma  -f helm/sma  -f helm/sma  -f helm/sma  --set image.  -f helm/sma  -f helm/sma  -f helm/sma    --namespace smart-hiring
```
## Upgrading the Chart
### Update Image Tags
```bash
helm upgrade smart-hiring ./helm/smart-hiring-assistant \
  -f helm/smart-hi  -f helm/smart-hi  -f helm/smart-hi  -f helm/s.tag=prod-$(git rev-parse --short HEAD) \
  --namespace  --namespace  --namespace  --namespace  --namespace  --nasmart-hiring ./helm/smart-hiring-assistant \
  -f helm/smart-hiring-assistant/values-prod.yaml \
  --set services.apiGateway.replicas=5 \
  --set services.apiGateway.hpa.maxReplicas=15 \
  --namespace smart-hiring
```
### Review Changes Before Upgrade
```bash
# Use helm diff plugin
helm diff upgrade smart-hiring ./helm/smart-hiring-assistant \
  -f helm/smart-hiring-assistant/values-prod.yaml \
  --namespace smart-hiring
# Or use dry-run
helm upgrade smart-hiring ./helm/smart-hiring-assistant \
  -f helm/smart-hiring-assistant/values-prod.yaml \
  --namespace smart-hiring \
  --dry-run \
  --debug
```
## Rollback
### Rollback to Previous Release
```bash
# View release history
helm history smart-hiring -n smart-hiring
# Rollback to revision 1
helm rollback smart-hiring 1 -n smart-hiring
# Verify rollback
kubectl get pods -n smart-hiring
```
## Monitoring
### Check Helm Releases
```bash
# List all releases
helm list -n smart-hiring
# Get release details
helm get values smart-hiring -n smart-hiring
helm get manifest smart-hiring -n smart-hiring
```
### Monitor Deployment
```bash
# Watch pods starting
kubectl get pods -n smart-hiring -w
# Check init containers
kubectl logs <pod-name> -n smart-hiring -c db-migrate
# Check service logs
kubectl logs -l app=auth-service -n smart-hiring --tail=100
```
## Troubleshooting
### Chart Lint Issues
```bash
# Check for syntax errors
helm lint helm/smart-hiring-assishelm # Fix issues based on output
```
### Template Issues
```bash
# Validate template syntax
helm template smart-hiring ./helm/smart-hiring-assistant -f values.yaml
# Check specific template
helm template smart-hiring ./helm/smart-hiring-assistant \
  -s templates/deployment.yaml -f val  -s temp```
### Installation Issues
```bash
# Check installation s# Chechelm status smart-hiring -n smart-hiring
# Get detailed debugging info
helm install smart-hirihelm instasmart-hiring-assistant \
  -f values-staging.yaml \
  --  --  --  --  --  ring \
  --debug
# Check what was created
kubectl get all -n smart-hiring
```
## CI/CD Pipeline Updates
### GitHub Actions Example
```yaml
- name: Deploy with Helm
  run: |
    helm repo add smart-hiring ${{ env.REGISTRY }}
    helm repo update
    helm upgrade --install smart-hiring smart-hiring/smart-hiring-assistant \
      -f helm/smart-hiring-assistant/values-prod.yaml \
      --set image.      --set image. .sha }} \
      --namespace smart-hiring
```
### GitLab CI Example
```yaml
deploy:
  stage: deploy
  script:
    - helm upgrade --install smart-hiring ./helm/smart-hiring-assistant
      -f helm/smart-hiring-assistant/values-prod.yaml
      --set image.tag=prod-$CI_COMMIT_SHA
      --namespace smart-hiring
```
## Decommissioning Kustomize
After successful Helm migration:
```bash
# Archive Kustomize files (optional)
tar -czf kustomize-backup.tar.gz infrastructure/kubernetes/
# Delete Kustomize (on# Delete Kustomize (on# Delete Kustomize (on# Delete es/
# Update documentation
git commit -m "Remove legacy Kustogit  configuration"
```
## Rollback to Kustomize (if needed)
```bash
# Keep backup until fully confident
# If issues arise, can fall back to Kustomize:
# Uninstall He# Uninstall He# Uninstall He# Uninstall hiring
# Deploy with Kustomize
kubectl apply -k infrastructure/kubernetes/overlays/prod
```
## Success Criteria
✅ Chart lints without errors
✅ Templates render correctly
✅ Dry-run shows expected resources
✅ All pods start successfully
✅ All services are accessible
✅ Init containers run database migration
✅ HPA scales as expected
✅ Monitoring metrics collected
✅ Logs are accessible
## Documentation
- Chart README: `helm/smart-hiring-assistant/README.md`
- Values documentation: Comments in `values.yaml`
- Migration example- Migration e- Troubleshooting: See README.md
## Next Steps
1. **Test locally*1. **Test locally*1. **Test locally*1. 2. **Deploy to staging**: Use staging values file
3. **Monitor**: Watch logs and metrics
4. **Test features**: Ensure all services work
5. **Deploy to production**: When confident
66666666666666666666666666666er migration complete
## Support
For issues during migration:
1. Check helm status: `helm status smart-hiring`
2. Review logs: `kubectl logs <pod>`
3. Validate templates: `helm template`
4. Check differences: `helm diff`
5. Consult README: `helm/smart-hiring-assistant/README.md`
