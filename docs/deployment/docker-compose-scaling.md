# Docker Compose App Stack (Health Checks + Initial Scaling)

Use `docker-compose.yml` for infrastructure and `docker-compose.apps.yml` for backend app services.

## Start infrastructure + app services

```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d --build
```

## Verify service health

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml ps
```

Health checks call each service health endpoint:

- `http://localhost:8000/actuator/health` (`api-gateway`)
- `http://localhost:8002/actuator/health` (`resume-parser-service`)
- `http://localhost:8004/actuator/health` (`interview-prep-service`)
- `http://localhost:8006/actuator/health` (`screening-bot-service`)
- `http://localhost:8007/actuator/health` (`notification-service`)

## Scaling (initial, Docker Compose)

`deploy.replicas` is included as baseline policy metadata in `docker-compose.apps.yml`.
For local Compose scaling, use `--scale` at runtime:

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d \
  --scale api-gateway=3 \
  --scale resume-parser-service=3 \
  --scale screening-bot-service=2
```

## Stop stack

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml down
```

## Notes

- This is an initial scaling setup for local/docker environments.
- For production autoscaling (CPU/memory based), add Kubernetes `HPA` manifests next.
