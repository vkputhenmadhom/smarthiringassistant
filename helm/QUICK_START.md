# Helm Chart Quick Start
Fast reference for deploying Smart Hiring Assistant with Helm Charts.
## Install Helm (if needed)
```bash
# macOS
brew install helm
# Linux
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
# Windows (using Chocolatey)
choco install kubernetes-helm
```
## Quick Deploy
### 1. Create Namespace & Secrets
```bash
kubectl create namespace smart-hiring
kubectl create secret generic smart-hiring-secrets \
  --from-literal=postgres-password='hiring123' \
  --from-literal=rabbitmq-password='rabbitmq123' \
  --from-literal=mail-username='notifications@example.com' \
  --from-literal=mail-password='app-password' \
  --from-literal=twilio-account-sid='AC...' \
  --from-literal=twilio-auth-token='...' \
  --from-literal=twilio-from-number='+1234567890' \
  -n smart-hiring
```
### 2. Install to Staging
```bash
cd helm
helm install smart-hiring ./smart-hiring-assistant \
  -f smart-hiring-assistant/values-staging.yaml \
  --namespace smart-hiring
```
### 3. Install to Production
```bash
helm install smart-hiring ./smart-hiring-assistant \
  -f smart-hiring-assistant/values-prod.yaml \
  --namespace smart-hiring
```
### 4. Verify
```bash
# Check pods
kubectl get pods -n smart-hiring
# Check services
kubectl get svc -n smart-hiring
# Check HPAs
kubectl get hpa -n smart-hiring
# View logs
kubectl logs -l app=api-gateway -n smart-hiring
```
## Common Commands
### Check Status
```bash
# Helm release status
helm status smart-hiring -n smart-hiring
# List releases
helm list -n smart-hiring
# Release history
helm history smart-hiring -n smart-hiring
```
### Update
```bash
# Update image tag
helm upgrade smart-hiring ./smart-hiring-assistant \
  -f smart-hiring-assistant/values-prod.yaml \
  --set image.tag=prod-abc123def456 \
  --namespace smart-hiring
# Scale service
helm upgrade smart-hiring ./smart-hiring-assistant \
  -f smart-hiring-assistant/values-prod.yaml \
  --set services.apiGateway.replicas=5 \
  --namespace smart-hiring
```
### Rollback
```bash
# View history
helm history smart-hiring -n smart-hiring
# Rollback to previous version
helm rollback smart-hiring -n smart-hiring
# Rollback to specific revision
helm rollback smart-hiring 2 -n smart-hiring
```
### Uninstall
```bash
helm uninstall smart-hiring -n smart-hiring
```
## Validate Chart
```bash
# Check syntax
helm lint smart-hiring-assistant
# Generate manifests
helm template smart-hiring ./smart-hiring-assistant \
  -f smart-hiring-assistant/values-staging.yaml \
  --namespace smart-hiring
# Dry-run
helm install smart-hiring ./smart-hiring-assistant \
  -f smart-hiring-assistant/values-staging.yaml \
  --namespace smart-hiring \
  --dry-run --debug
```
## Customize Values
### Create Custom Values
```bash
# Copy and modify
cp smart-hiring-assistant/values-staging.yaml values-custom.yaml
# Edit with your settings
vim values-custom.yaml
# Deploy with custom values
helm install smart-hiring ./smart-hiring-assistant \
  -f values-custom.yaml \
  --namespace smart-hiring
```
### Override on Command Line
```bash
helm install smart-hiring ./smart-hiring-assistant \
  -f smart-hiring-assistant/values-staging.yaml \
  --set services.apiGateway.replicas=2 \
  --set image.organization=my-org \
  --set database.host=postgres.example.com \
  --namespace smart-hiring
```
## Chart Structure
```
helm/
├── MIGRATION_GUIDE.md              # Detailed migration instructions
├── QUICK_START.md                  # This file
└── smart-hiring-assistant/
    ├── Chart.yaml                  # Chart metadata
    ├── README.md                   # Full documentation
    ├── values.yaml                 # Default values
    ├── values-staging.yaml         # Staging overrides
    ├── values-prod.yaml            # Production overrides
    └── templates/
        ├── _helpers.tpl            # Template helpers
        ├── namespace.yaml          # Namespace
        ├── deployment.yaml         # All deployments
        ├── service.yaml            # All services
        ├── hpa.yaml                # Autoscaling
        └── db-migration-configmap.yaml  # DB migrations
```
## Services Deployed
1. **api-gateway** (port 8000) - API entry point
2. **auth-service** (port 8001) - Authentication
3. **resume-parser-service** (port 8002) - Resume parsing
4. **candidate-matcher-service** (port 8003) - Matching
5. **interview-prep-service** (port 8004) - Interview prep
6. **job-analyzer-service** (port 8005) - Job analysis
7. **screening-bot-service** (port 8006) - Scree7. **8. **notification-service** (port 8007) - Notifications
## Troubleshooting
### Pods Not Starting
```bash
# Check pod status
kubectl describe pod <pod-name> -n smart-hiring
# View logs
kubectl logs <pod-name> -n smart-hiring
# Check init container logs
kubectl logs <podkubectl logs <podkubectl logs <podkub```
### HPA Not Scaling
```bash
# Check HPA status
kubectl describe hpa <hpa-name> -n smart-hiring
# Check metrics server
kubectl get deployment metrics-server -n kube-system
```
### Secret Issues
```bash
# Verify secret exists
kubectl get secret smart-hiring-secrets -n smart-hiring
# Check secret content
kubectl get secret smart-hiring-secrets -n smart-hiring -o yaml
```
## Links
- **Chart Documentation**: `smart-hiring-assistant/RE- **Chart- **Migration Guide**: `MIGRATION_GUIDE.md`
- **Values File**: `smart-hiring-assistant/values.yaml`
## Next Steps
1. Read full documentation: `smart-hiring-assistan1. Read full documentation: `smart-hiring-assmization options
3. Test with `helm template` before deployment
4. Deploy to staging first
5. Monitor pods and logs
6. Deploy to productio6. Deploy ty
