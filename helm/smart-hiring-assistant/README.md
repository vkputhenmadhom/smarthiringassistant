# Smart Hiring Assistant Helm Chart
Complete Helm chart for deploying the Smart Hiring Assistant microservices to Kubernetes.
## Chart Overview
This Helm chart manages 7 microservices:
- **api-gateway** - API Gateway (port 8000)
- **auth-service** - Authentication Service (port 8001)
- **resume-parser-service** - Resume Parser (port 8002)
- **candidate-matcher-service** - Candidate Matching (port 8003)
- **interview-prep-service** - Interview Preparation (port 8004)
- **job-analyzer-service** - Job Analysis (port 8005)
- **screening-bot-service** - Screening Bot (port 8006)
- **notification-service** - Notification Service (port 8007)
## Prerequisites
- Kubernetes 1.24+
- Helm 3.0+
- PostgreSQL 14+ (external or managed)
- Redis (for API Gateway)
- RabbitMQ (for Notifications)
- Metrics Server (for HPA)
## Quick Start
### Install
```bash
# Staging
helm install smart-hiring ./smart-hiring-assistant \
  -f values-staging.yaml \
  --namespace smart-hiring \
  --create-namespace
# Production
helm install smart-hiring ./smart-hiring-assistant \
  -f values-prod.yaml \
  --namespace smart-hiring \
  --create-namespace
```
### Upgrade
```bash
helm upgrade smart-hiring ./smart-hiring-assistant \
  -f values-prod.yaml \
  --namespace smart-hiring
```
### Uninstall
```bash
helm uninstall smart-hiring --namespace smart-hiring
```
## Configuration
### Values Files
- **values.yaml** - Default values (staging-like default- *- **values-staging.yaml** - Staging environment overrides
- **- **- **- **- **- **- **- **- **-nvironment overrides
### Key Configuration Options
#### Global Settings
```yaml
global:
  namespace: smart-hiring              # K8s namespace
  environment: staging                 # Environment (staging/prod)
  domain: staging-hr.example.com       # Domain name
  imagePullPolicy: IfNotPresent        # Image pull policy
```
#### Database
```yaml
database:
  host: postgres                       # PostgreSQL host
  port: 5432                          # PostgreSQL port
  name: smart_hiring_db               # Database name
  username: hiring_user               # Database user
  schema: hiring                       # Database schema
  # password provided via secret
```
#### Service Configuration
Each service has:
- **replicas** - Number of replicas
- **image** - Image name (without tag)
- **port** - Container port
- **resources** - CPU/memory requests and limits
- **hpa** - Horizontal Pod Autoscaler settings
- **probes** - Readiness/liveness probe paths
- **initContainer** - Database migration settings (DB-dependent services)
Example:
```yaml
services:
  authService:
    enabled: true
    replicas: 1
    image: auth-service
    port: 8001
    resources:
      requests:
        cpu: 200m
        memory: 384Mi
      limits:
        cpu: "1"
        memory: 1Gi
    hpa:
      enabled: true
      minReplicas: 2
      maxReplicas: 8
      targetCPU: 70
      targetMemory: 80
    initContainer:
      enabled: true
```
## Features
### Init Containers
Database-dependent services run init containers that:
- Wait for PostgreSQL readiness
- Execute database migration scripts
- Create required schemas and tables
- Grant necessary permissions
Services with init containers:
- auth-service
- resume-parser-service
- candidate-matcher-service
### Auto-Scaling (HPA)
Horizontal Pod Autoscaler configured for all services with:
- CPU utilization target: 70%
- Memory utilization target: 80%
- Environment-specific min/max replicas
Staging: 1-3 replicas per service
Production: 3-12 replicas per service
### Monitoring
ServiceMonitor resources enabled for Prometheus:
- Scrape interval: 30 seconds
- Metrics endpoint: `/actuator/prometheus`
- Port: 8080
### Networking
Ingress support for:
- Staging: staging-hr.smarthiringassistant.com, staging-candidate.smarthiringassistant.com
- Production: hr.smarthiringassistant.com, candidate.smarthiringassistant.com
### Security
- Network Policies (Production)
- Pod Disruption Budgets (Production)
- RBAC support
- ServiceAccount per deployment
- Secrets for sensitive data
## Deployment Examples
### Deploy Staging Environment
```bash
# Create namespace and secrets
kubectl create namespace smart-hiring
kubectl create secret generic smart-hiring-secrets \
  --from-literal=postgres-password='your-password' \
  --from-literal=rabbitmq-password='rabbitmq-pass' \
  --from-literal=mail-username='email@gmail.com' \
  --from-literal=mail-password='app-password' \
  --from-literal=twilio-account-sid='your-sid' \
  --from-literal=twilio-auth-token='your-token' \
  --from-literal=twilio-from-number='+1234567890' \
  -n smart-hiring
# Deploy
helm install smart-hiring ./smart-hiring-assistant \
  -f values-staging.yaml \
  --set image.tag=staging-$(git rev-parse --short HEAD) \
  --namespace smart-hiring
```
### Deploy Production Environment
```bash
helm install smart-hiring ./smart-hiring-assistant \
  -f values-prod.yaml \
  --set image.tag=prod-$(git rev-parse --short HEAD) \
  --set image.registry=ghcr.io \
  --set image.organization=your-org \
  --namespace smart-hiring
```
### Scale Services
```bash
# Increase replicas
helm upgrade smart-hiring ./smart-hiring-assistant \
  -f values-prod.yaml \
  --set services.apiGateway.replicas=5 \
  --set services.apiGateway.hpa.maxReplicas=15 \
  --namespace smart-hiring
```
## Verification
### Check Deployment Status
```bash
# Get all pods
kubectl get pods -n smart-hiring
# Get services
kubectl get svc -n smart-hiring
# Get HPAs
kubectl get hpa -n smart-hiring
# Check deployment
kubectl get deployment -n smart-hiring
```
### View Logs
```bash
# Init container logs (database migration)
kubectl logs <pod-name> -n smart-hiring -c db-migrate
# Application logs
kubectl logs <pod-name> -n smart-hiring -c <service-name>
# View logs from specific service
kubectl logs -l app=auth-service -n smart-hiring --tail=100
```
### Monitor Metrics
```bash
# Check HPA scaling
kubectl describe hpa <hpa-name> -n smart-hiring
# View metrics
kubectl get metrics pods -n smart-hiring
```
## Customization
### Create Custom Values File
```bash
# Create environment-specific values
cp values-staging.yaml values-custom.yaml
# Edit values-custom.yaml
helm install smart-hiring ./smart-hiring-assistant \
  -f values-custom.yaml \
  --namespace smart-hiring
```
### Override Values on Command Line
```bash
helm install smart-hiring ./smart-hiring-assistant \
  -f values-staging.yaml \
  --set image.organization=my-org \
  --set services.apiGateway.replicas=2 \
  --set database.host=postgres.example.com \
  --namespace smart-hiring
```
## Troubleshooting
### Init Container Issues
```bash
# Check init container status
kubectl describe pod <pod-name> -n smart-hiring
# View init container logs
kubectl logs <pod-name> -n smart-hiring -c db-migrate
# Check database connectivity
kubectl run -it --rm debug --image=postgres:16-alpine --restart=Never \
  -n smart-hiring -- psql -h postgres -U hiring_user -d smart_hiring_db
```
### HPA Not Scaling
```bash
# Check HPA status
kubectl describe hpa <hpa-name> -n smart-hiring
# Check metrics available
kubectl get --raw /apis/custom.metrics.k8s.io/v1beta1
```
### Image Pull Issues
```bash
# Create image pull secret
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<username> \
  --docker-password=<token> \
  --docker-email=<email> \
  -n smart-hiring
# Update values to reference secret
image:
  pullSecrets:
    - name: ghcr-secret
```
## Best Practices
1. **Always use values files** - Never rely on defaults in production
2. **Separate staging and prod** - Use distinct values files for each environment
3. **Version your images** - Use explicit image tags (not 'latest')
4. **Monitor deployments** - Watch HPA scaling and resource utilization
5. **Test upgrades** - Always test in staging first
6. **Backup database** - Before major upgrades
7. **Review changes** - Use `helm diff` before upgrades
8. **Automate deployments** - Use CI/CD for consistency
## Migration from Kustomize
Converting from Kustomize to Helm:
1. **Extract configuration** - Move Kustomize overlays to values files
2. **Templatize manifests** - Convert static YAML to Helm templates
3. **Test thoroughly** - Compare generated manifests
4. **Validate output** - Use `helm template` to review
5. **Deploy incrementally** - Test on staging first
6. **Keep documentation** - Document custom values
## Support & Maintenance
- Review values files regularly
- Monitor metrics and logs
- Keep Kubernetes and Helm updated
- Update container images
- Test major version upgrades of dependencies
## License
Same as main project
## Contributing
For improvements or bug reports:
1. Test changes locally
2. Validate with `helm lint`
3. Template output with `helm template`
4. Submit pull request with details
