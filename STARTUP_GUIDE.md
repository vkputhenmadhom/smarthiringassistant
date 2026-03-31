# Smart Hiring Assistant - Full Stack Startup Guide

## ✅ Issue Fixed
The Docker Compose error `service "candidate-portal" depends on undefined service api-gateway` has been resolved by reorganizing the frontend services into `docker-compose.apps.yml`.

## 🚀 Starting the Full Stack

### Step 1: Ensure Docker is Running
```bash
# On macOS, Docker Desktop should be running
# If not started, launch it from Applications or use:
open -a Docker
```

### Step 2: Start the Complete Stack
From the project root directory:

```bash
# Full stack with infrastructure and applications
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d --build

# Alternative: With staging monitoring overrides
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.monitoring-staging.yml up -d --build
```

### Step 3: Monitor the Build Progress
```bash
# Watch services starting up
docker compose -f docker-compose.yml -f docker-compose.apps.yml logs -f

# Check specific service logs
docker compose -f docker-compose.yml -f docker-compose.apps.yml logs -f api-gateway
docker compose -f docker-compose.yml -f docker-compose.apps.yml logs -f candidate-portal

# View running containers
docker compose -f docker-compose.yml -f docker-compose.apps.yml ps
```

## 📋 Services & Ports

### Infrastructure Services
| Service | Port | URL |
|---------|------|-----|
| PostgreSQL | 5432 | `localhost:5432` |
| MongoDB | 27017 | `localhost:27017` |
| RabbitMQ Management | 15672 | `http://localhost:15672` |
| Redis | 6379 | `localhost:6379` |
| Prometheus | 9090 | `http://localhost:9090` |
| Grafana | 3000 | `http://localhost:3000` |
| Elasticsearch | 9200 | `http://localhost:9200` |
| Kibana | 5601 | `http://localhost:5601` |
| Jaeger | 16686 | `http://localhost:16686` |

### Application Services
| Service | Port | URL |
|---------|------|-----|
| API Gateway | 8000 | `http://localhost:8000` |
| Resume Parser | 8002 | `http://localhost:8002` |
| Candidate Matcher | 8003 | `http://localhost:8003` |
| Interview Prep | 8004 | `http://localhost:8004` |
| Screening Bot | 8006 | `http://localhost:8006` |
| Notification Service | 8007 | `http://localhost:8007` |

### Frontend Portals
| Application | Port | URL |
|---------|------|-----|
| **HR Admin Dashboard** | 4200 | `http://localhost:4200` |
| **Candidate Portal** | 5173 | `http://localhost:5173` |

## 🌐 Accessing the Application

### After Everything is Running:

1. **HR Admin Dashboard** (Angular)
   - Open: `http://localhost:4200`
   - Default credentials: (Check documentation)

2. **Candidate Portal** (React)
   - Open: `http://localhost:5173`
   - For candidates to register and apply

3. **API Gateway**
   - Swagger UI: `http://localhost:8000/swagger-ui.html`
   - Health Check: `http://localhost:8000/actuator/health`

4. **Monitoring & Logs**
   - Grafana: `http://localhost:3000` (Dashboards & Alerts)
   - Prometheus: `http://localhost:9090` (Metrics)
   - Kibana: `http://localhost:5601` (Logs)
   - Jaeger: `http://localhost:16686` (Distributed Tracing)
   - RabbitMQ: `http://localhost:15672` (Message Broker UI)

## 🔧 Useful Commands

### Stop All Services
```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml down
```

### Remove All Data (Reset Everything)
```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml down -v
```

### View Service Status
```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml ps
```

### Restart a Specific Service
```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml restart <service-name>
```

### Start a Specific Service

For application services declared in `docker-compose.apps.yml`, use both compose files:

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d <service-name>
```

Examples:

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d auth-service
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d api-gateway
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d interview-prep-service
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d candidate-portal
```

For infrastructure-only services declared in `docker-compose.yml`, use the base compose file:

```bash
docker compose -f docker-compose.yml up -d postgres
docker compose -f docker-compose.yml up -d rabbitmq
docker compose -f docker-compose.yml up -d redis
```

If you want Docker to rebuild the service image before starting it:

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d --build <service-name>
```

### View Logs for All Services
```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml logs --follow
```

### View Logs for a Specific Service (Last 100 lines)
```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml logs --tail=100 <service-name>
```

## 💾 Data Persistence Matrix

| Command | Containers | Named Volumes (e.g., `postgres_data`) | DB Data | `init.sql` / `mongo-init.js` Re-run? |
|---------|------------|-----------------------------------------|---------|--------------------------------------|
| `docker compose stop` | Stopped (kept) | Kept | Kept | No |
| `docker compose down` | Removed | Kept | Kept | No |
| `docker compose down -v` | Removed | Removed | Deleted | Yes, on next `up` |
| `docker compose restart <service>` | Recreated/Restarted service container | Kept | Kept | No |
| `docker compose up -d` (existing volumes) | Started/Recreated as needed | Kept | Kept | No |

> Postgres and Mongo init scripts under `/docker-entrypoint-initdb.d` run only when the data directory is initialized for the first time (or after volume deletion).

## ✅ Health Checks

Once everything is running, verify services are healthy:

```bash
# Check all service health
docker compose -f docker-compose.yml -f docker-compose.apps.yml ps

# API Gateway health
curl http://localhost:8000/actuator/health

# Database connectivity
curl http://localhost:9200/_cluster/health  # Elasticsearch
```

## 🛑 Troubleshooting

### If Services Won't Start
1. **Check Docker Daemon**: Ensure Docker Desktop is running
2. **Check Logs**: `docker compose logs <service-name>`
3. **Check Disk Space**: Ensure sufficient disk space for images
4. **Check Ports**: Ensure ports are not in use

### Common Issues
- **Port Already in Use**: Kill process using `lsof -i :<port>` or change Docker Desktop port settings
- **Out of Memory**: Increase Docker memory in Settings → Resources
- **Build Failures**: Check internet connection or image registry availability

## 📝 Database Credentials

```
PostgreSQL:
  User: hiring_user
  Password: hiring_password
  Database: smart_hiring_db

MongoDB:
  User: hiring_user
  Password: hiring_password
  Database: smart_hiring_db

RabbitMQ:
  User: hiring_user
  Password: hiring_password

Redis:
  Password: hiring_password
```

## 🎯 Expected Startup Time
- **Full Build**: 10-20 minutes (first time)
- **Subsequent Starts**: 2-5 minutes
- **All Services Ready**: 5 additional minutes (waiting for health checks)

---

**Last Updated**: March 29, 2026
**Build Status**: ✅ Docker Compose v2 Commands Updated

