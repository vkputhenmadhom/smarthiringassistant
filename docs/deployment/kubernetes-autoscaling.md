# Kubernetes Baseline (Health Probes + HPA)

This baseline adds Kubernetes manifests for core stateless services:

- `api-gateway`
- `resume-parser-service`
- `screening-bot-service`
- `candidate-matcher-service`
- `interview-prep-service`
- `notification-service`

Resources are in `infrastructure/kubernetes/base`, with overlays in `infrastructure/kubernetes/overlays`.

## Included resources

- `Namespace` (`smart-hiring`)
- `Deployment` + `Service` per service
- `HorizontalPodAutoscaler` per service (`autoscaling/v2`)
- Fallback `Secret` template (`secret-template.yaml`)
- Per-service `ConfigMap` overlays via Kustomize
- Production hardening resources: `PodDisruptionBudget`, AWS ALB `Ingress`, `NetworkPolicy`
- AWS Secrets Manager integration via External Secrets (`ClusterSecretStore` + `ExternalSecret`)
- Prometheus scrape resources via `ServiceMonitor`

## EKS assumptions

Production overlay assumes EKS with:

- AWS Load Balancer Controller installed
- External Secrets Operator installed
- Prometheus Operator (or kube-prometheus-stack) installed
- IRSA configured for service accounts that access AWS APIs

If your cluster differs, adapt `ingressClassName`, External Secrets provider auth, and monitoring resources accordingly.

## Secret management

Preferred production/staging path: deploy the External Secrets Operator and let the overlay create `smart-hiring-secrets` from AWS Secrets Manager.

Expected remote secret names:

- `smart-hiring/staging/application`
- `smart-hiring/prod/application`

These AWS Secrets Manager values should be JSON objects whose keys match the Kubernetes secret keys below.

Fallback/manual path: populate `infrastructure/kubernetes/base/secret-template.yaml` and apply it directly.

## Required secret keys

Populate `infrastructure/kubernetes/base/secret-template.yaml` with environment-specific values for:

- `postgres-password`
- `rabbitmq-password`
- `redis-password`
- `mongodb-uri`
- `openai-api-key`
- `mail-username`
- `mail-password`
- `twilio-account-sid`
- `twilio-auth-token`
- `twilio-from-number`

## External Secrets prerequisites

- External Secrets Operator installed in the cluster
- EKS IAM role for service accounts (IRSA)
- Replace placeholder IAM role ARNs in:
  - `overlays/staging/external-secrets.yaml`
  - `overlays/prod/external-secrets.yaml`
- Replace AWS region if not `us-east-1`
- `ClusterSecretStore` names are environment-scoped:
  - `aws-secretsmanager-staging`
  - `aws-secretsmanager-prod`

## Health checks

Deployments use HTTP probes:

- `api-gateway`: `/actuator/health/readiness`, `/actuator/health/liveness`
- `resume-parser-service`: `/api/resumes/actuator/health/readiness`, `/api/resumes/actuator/health/liveness`
- `screening-bot-service`: `/api/screening/actuator/health/readiness`, `/api/screening/actuator/health/liveness`
- `candidate-matcher-service`: `/api/matches/actuator/health/readiness`, `/api/matches/actuator/health/liveness`
- `interview-prep-service`: `/api/interviews/actuator/health/readiness`, `/api/interviews/actuator/health/liveness`
- `notification-service`: `/api/notifications/actuator/health/readiness`, `/api/notifications/actuator/health/liveness`

## Apply manifests

```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant

# 1) Create/update secrets from template values first
kubectl apply -f infrastructure/kubernetes/base/secret-template.yaml

# 2) Apply environment overlays
kubectl apply -k infrastructure/kubernetes/overlays/staging
# or
kubectl apply -k infrastructure/kubernetes/overlays/prod
```

## GitHub Actions deploy wiring

The backend workflow (`.github/workflows/backend-services.yml`) now supports `workflow_dispatch` deploy mode `kubernetes`.

- It maps environment input to overlay (`staging` -> `overlays/staging`, `prod|production` -> `overlays/prod`)
- It replaces `__GIT_SHA__` in the selected overlay `kustomization.yaml` with `${{ github.sha }}`
- It applies the overlay to EKS with `kubectl apply -k ...`

Required CI configuration:

- Secret: `AWS_GITHUB_ACTIONS_ROLE_ARN`
- Variable: `AWS_REGION` (optional if default is acceptable)
- Variable: `EKS_CLUSTER_NAME`

## Verify rollout and autoscaling

```bash
kubectl -n smart-hiring get deploy,svc,hpa,pdb,ingress,networkpolicy
kubectl -n smart-hiring describe hpa api-gateway-hpa
```

## Overlay intent

- `overlays/staging`: environment config + per-service config maps + CI image tag placeholders + External Secrets
- `overlays/prod`: environment config + per-service config maps + higher replica/HPA floors + `PDB` + ALB ingress + ingress/egress policies + External Secrets + ServiceMonitors

## Production hardening notes

- Ingress is configured for AWS Load Balancer Controller (`ingressClassName: alb`)
- Replace placeholder ACM certificate ARN in `overlays/prod/ingress.yaml`
- Production overlay enforces default-deny ingress and egress
- Explicit egress rules allow only DNS, backing services, and required service-to-service traffic

## Prometheus ServiceMonitor notes

- ServiceMonitors live in `base/servicemonitors.yaml`
- They are labeled `release: kube-prometheus-stack`
- If your Prometheus Operator uses different selectors, adjust this label to match your deployment

## CI image tag convention

Overlays use immutable CI tag placeholders:

- staging: `staging-__GIT_SHA__`
- prod: `prod-__GIT_SHA__`

In CI/CD, replace `__GIT_SHA__` with your commit SHA (or set image tags with `kustomize edit set image`) before apply.
Also replace image repository placeholders (`ghcr.io/your-org/...`) with your real registry paths.

