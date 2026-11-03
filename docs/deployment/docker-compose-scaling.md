# Docker Compose App Stack (Health Checks + Initial Scaling)

Use `docker-compose.yml` for infrastructure and `docker-compose.apps.yml` for backend app services.

## Start infrastructure + app services

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d --build
```

Optional infrastructure (Prometheus/Grafana, Kafka, ...) is behind Compose profiles; see the
[Startup Guide](../../STARTUP_GUIDE.md#2-what-runs-where).

## Verify service health

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml ps
```

Each service has a Docker health check that calls its actuator health endpoint from inside the
container (for example `http://localhost:8002/api/resumes/actuator/health`; auth-service uses its
management port 9001). `docker compose ps` shows the result.

From your machine, only the gateway is reachable: `curl http://localhost:8000/actuator/health`.
Internal services publish no ports; add `-f docker-compose.debug-ports.yml` to reach them directly.

## Scaling (initial, Docker Compose)

`deploy.replicas` is included as baseline policy metadata in `docker-compose.apps.yml`.
For local Compose scaling, use `--scale` at runtime:

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d \
  --scale resume-parser-service=3 \
  --scale screening-bot-service=2
```

Internal services scale freely because they publish no host ports; Docker's DNS spreads requests across
the replicas. Services that publish a fixed host port (api-gateway on 8000, auth-service on 8001) can't
run more than one replica this way. Put a load balancer in front, or use Kubernetes.

RabbitMQ consumers scale too: replicas of one service share that service's queue, so each event is
handled once per service.

## Stop stack

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml down
```

## Notes

- This is an initial scaling setup for local/docker environments.
- For production autoscaling (CPU/memory based), add Kubernetes `HPA` manifests next.
