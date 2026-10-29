# Kubernetes Deployment Guide

## Overview

This guide covers deploying the Smart Hiring Assistant microservices to Kubernetes using Kustomize overlays for different environments.

## Prerequisites

- Kubernetes cluster v1.24+ (with init container support)
- `kubectl` CLI installed
- `kustomize` v4.0+
- PostgreSQL database configured
- Access to container registry (GHCR)

## Environment Overlays

### Staging Environment
- **Location:** `infrastructure/kubernetes/overlays/staging/`
- **Replicas:** 1 per service (defined in base)
- **Resources:** Standard compute
- **HPA:** Not included
- **Manifests:** 1321 lines
- **Services:** 7 microservices + PostgreSQL + Prometheus monitoring

### Production Environment
- **Location:** `infrastructure/kubernetes/overlays/prod/`
- **Replicas:** 3 per service (higher availability)
- **Resources:** Standard compute with HPA
- **HPA:** 6 services with auto-scaling (3-12 replicas for API gateway, 3-10 for others, 3-8 for notification)
- **Manifests:** 1658 lines
- **Services:** 7 microservices + PostgreSQL + Prometheus monitoring + Network Policies + Ingress

## Pre-Deployment Checklist

### 1. Database Configuration
```bash
# Create PostgreSQL user and database
psql -U postgres <<EOF
CREATE ROLE hiring_user WITH LOGIN PASSWORD 'your-secure-password';
CREATE DATABASE smart_hiring_db OWNER hiring_user;
GRANT ALL PRIVILEGES ON DATABASE smart_hiring_db TO hiring_user;
EOF
```

### 2. Container Registry Secrets
```bash
# Create pull secret for container registry
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<your-username> \
  --docker-password=<your-token> \
  --docker-email=<your-email> \
  -n smart-hiring
```

### 3. Database Credentials Secret
```bash
# Create secret for PostgreSQL credentials
kubectl create secret generic smart-hiring-secrets \
  --from-literal=postgres-password='your-secure-password' \
  --from-literal=postgres-user='hiring_user' \
  --from-literal=postgres-db='smart_hiring_db' \
  -n smart-hiring
```

### 4. External Secrets Operator (Optional)
If using External Secrets to sync from AWS Secrets Manager, update the external-secrets.yaml files with your AWS account details.

## Deployment Steps

### Step 1: Validate Overlays
```bash
# Run validation script
./infrastructure/kubernetes/validate-overlays.sh

# Or manually validate
kubectl kustomize infrastructure/kubernetes/overlays/staging
kubectl kustomize infrastructure/kubernetes/overlays/prod
```

### Step 2: Deploy Staging Environment
```bash
# Apply staging overlay
kubectl apply -k infrastructure/kubernetes/overlays/staging --validate=false

# Wait for all pods to be ready
kubectl wait --for=condition=Ready pods \
  -l app.kubernetes.io/managed-by=kustomize \
  -n smart-hiring \
  --timeout=300s
```

### Step 3: Verify Staging Deployment
```bash
# Check pod status
kubectl get pods -n smart-hiring

# Check init container logs (database migration)
kubectl logs <pod-name> -n smart-hiring -c db-migrate

# Check service logs
kubectl logs <pod-name> -n smart-hiring -c <service-name>

# Verify services
kubectl get svc -n smart-hiring
```

### Step 4: Run Integration Tests
```bash
# Port-forward to API gateway
kubectl port-forward svc/api-gateway 8080:80 -n smart-hiring

# Test endpoints
curl http://localhost:8080/health
curl http://localhost:8080/api/auth/health
```

### Step 5: Deploy Production Environment
```bash
# Apply production overlay
kubectl apply -k infrastructure/kubernetes/overlays/prod --validate=false

# Monitor rollout
kubectl rollout status deployment -n smart-hiring

# Verify HPAs are active
kubectl get hpa -n smart-hiring
```

## Init Containers - Database Migration

The init containers automatically run database migrations before main application containers start.

### How It Works

1. **Pod Creation:** Pod is created by Kubernetes
2. **Init Container Execution:** 
   - Waits for PostgreSQL to be ready
   - Executes `migrate-all-to-hiring.sql` from ConfigMap
   - Creates required schema and tables
3. **Main Container Start:** Only starts after init container succeeds

### Monitoring Init Containers

```bash
# View init container status
kubectl get pods -n smart-hiring -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.initContainerStatuses[*].state}{"\n"}{end}'

# View init container logs
kubectl logs <pod-name> -n smart-hiring -c db-migrate

# Verify tables were created
kubectl exec -it <pod-name> -c db-migrate -n smart-hiring -- \
  psql -h postgres -U hiring_user -d smart_hiring_db -c "\dt hiring.*"
```

### Troubleshooting Init Containers

**Problem:** Init container stuck on "Waiting for PostgreSQL"
```bash
# Check PostgreSQL pod status
kubectl get pods -n smart-hiring | grep postgres

# Check PostgreSQL service DNS
kubectl exec -it <pod-name> -c db-migrate -n smart-hiring -- \
  nslookup postgres.smart-hiring.svc.cluster.local

# Check credentials
kubectl get secret smart-hiring-secrets -n smart-hiring -o jsonpath='{.data.postgres-password}' | base64 -d
```

**Problem:** Migration script fails with SQL errors
```bash
# View migration script from ConfigMap
kubectl get configmap db-migration-script -n smart-hiring -o \
  jsonpath='{.data.migrate-all-to-hiring\.sql}' | head -100

# Test database connectivity manually
kubectl run -it --rm debug --image=postgres:16-alpine --restart=Never -n smart-hiring -- \
  psql -h postgres -U hiring_user -d smart_hiring_db -c "SELECT version();"
```

## Horizontal Pod Autoscaling (Production Only)

Production environment includes HPAs for automatic scaling based on CPU/memory utilization.

### HPA Configuration

| Service | Min Replicas | Max Replicas | CPU Target | Memory Target |
|---------|--------------|--------------|------------|---------------|
| api-gateway | 3 | 12 | 70% | 80% |
| resume-parser-service | 3 | 10 | 70% | 80% |
| screening-bot-service | 3 | 10 | 70% | 80% |
| candidate-matcher-service | 3 | 10 | 70% | 80% |
| interview-prep-service | 3 | 10 | 70% | 80% |
| notification-service | 3 | 8 | 70% | 80% |

### Monitor HPA Activity

```bash
# View HPA status
kubectl get hpa -n smart-hiring

# Watch HPA scaling in real-time
kubectl get hpa -n smart-hiring -w

# Get detailed HPA information
kubectl describe hpa <hpa-name> -n smart-hiring

# View HPA events
kubectl get events -n smart-hiring --field-selector involvedObject.kind=HorizontalPodAutoscaler
```

## Monitoring and Observability

### Prometheus Metrics

```bash
# Port-forward to Prometheus
kubectl port-forward svc/prometheus 9090:9090 -n smart-hiring

# Access Prometheus UI at http://localhost:9090
```

### Service Monitor Discovery

All services expose metrics through ServiceMonitor resources:
- Metrics endpoint: `/actuator/prometheus`
- Scrape interval: 30s
- Port: 8080

### View Metrics

```bash
# Query metrics from a pod
kubectl exec -it <pod-name> -n smart-hiring -- \
  curl http://localhost:8080/actuator/prometheus | grep jvm_memory

# Check metric scraping
kubectl logs -l app=prometheus -n smart-hiring | grep -i scrape
```

## Troubleshooting

### Common Issues

#### Pods not starting
```bash
# Check pod status
kubectl describe pod <pod-name> -n smart-hiring

# Check events
kubectl get events -n smart-hiring --sort-by='.lastTimestamp'

# Check container logs
kubectl logs <pod-name> -n smart-hiring -c <container-name>
```

#### Service connection errors
```bash
# Test DNS resolution
kubectl run -it --rm debug --image=busybox --restart=Never -n smart-hiring -- \
  nslookup api-gateway.smart-hiring.svc.cluster.local

# Test service connectivity
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -n smart-hiring -- \
  curl http://api-gateway:8080/health
```

#### Database migration failures
```bash
# Check migration script in ConfigMap
kubectl get configmap db-migration-script -n smart-hiring -o yaml

# Test migration manually
kubectl run -it --rm debug --image=postgres:16-alpine --restart=Never -n smart-hiring -- \
  psql -h postgres -U hiring_user -d smart_hiring_db -f /migration.sql
```

## Updating Deployments

### Update Image Tags

```bash
# Edit kustomization.yaml images section
# OR use kubectl set image

kubectl set image deployment/api-gateway \
  api-gateway=ghcr.io/your-org/smart-hiring/api-gateway:staging-<new-sha> \
  -n smart-hiring
```

### Update Configuration

```bash
# Edit ConfigMap
kubectl edit configmap api-gateway-config -n smart-hiring

# Or recreate ConfigMap
kubectl delete configmap api-gateway-config -n smart-hiring
kubectl apply -k infrastructure/kubernetes/overlays/staging
```

### Rolling Updates

```bash
# Watch rollout progress
kubectl rollout status deployment/api-gateway -n smart-hiring -w

# Rollback if needed
kubectl rollout undo deployment/api-gateway -n smart-hiring
```

## Cleanup

### Delete All Resources (Staging)
```bash
kubectl delete -k infrastructure/kubernetes/overlays/staging -n smart-hiring
```

### Delete Specific Deployment
```bash
kubectl delete deployment api-gateway -n smart-hiring
```

### Delete Namespace (removes all resources)
```bash
kubectl delete namespace smart-hiring
```

## Performance Tuning

### Resource Requests/Limits

Default values in base deployment:
```yaml
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: 500m
    memory: 1Gi
```

Adjust in deployment files if needed.

### Pod Disruption Budgets (Prod Only)

Ensures service availability during cluster maintenance:
```bash
kubectl get poddisruptionbudgets -n smart-hiring
```

## Security Best Practices

1. **Network Policies:** Production includes NetworkPolicies to restrict traffic
2. **RBAC:** Restrict ServiceAccount permissions
3. **Image Scanning:** Scan container images for vulnerabilities
4. **Secrets Management:** Use External Secrets or sealed secrets
5. **Pod Security:** Enable Pod Security Standards

## Backups and Disaster Recovery

### Database Backups
```bash
# Backup PostgreSQL database
kubectl exec -it postgres-0 -n smart-hiring -- \
  pg_dump -U hiring_user smart_hiring_db > backup.sql

# Restore from backup
kubectl exec -i postgres-0 -n smart-hiring -- \
  psql -U hiring_user smart_hiring_db < backup.sql
```

## Contact and Support

For issues or questions:
- Review logs: `kubectl logs <pod-name> -n smart-hiring`
- Check events: `kubectl get events -n smart-hiring`
- Review documentation: See base/INIT-CONTAINERS-README.md and docs/archive/KUSTOMIZE-FIX-SUMMARY.md

