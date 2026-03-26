# Smart Hiring Assistant - Developer Quick Start Guide

## Prerequisites Installation

### 1. Install Required Tools

```bash
# macOS (using Homebrew)
brew install java@21
brew install gradle
brew install docker

# Verify installations
java -version
gradle --version
docker --version
```

### 2. Set up Docker Desktop
- Download and install [Docker Desktop](https://www.docker.com/products/docker-desktop)
- Start Docker Desktop
- Verify: `docker --version`

### 3. OpenAI API Key
1. Sign up at [OpenAI](https://platform.openai.com)
2. Generate API key from account settings
3. Store in environment variable:
   ```bash
   export OPENAI_API_KEY=sk-your-key-here
   ```

## Development Setup

### Initial Setup (First Time Only)

```bash
# Clone the repository
git clone <repo-url>
cd SmartHiringAssistant

# Start all infrastructure services
docker-compose up -d

# Verify all containers are running
docker-compose ps

# Build all modules
./gradlew clean build -x test
```

### Starting Development Environment

**Option 1: Run all services locally (recommended for development)**

```bash
# Terminal 1: API Gateway
./gradlew :services:api-gateway:bootRun

# Terminal 2: Auth Service
./gradlew :services:auth-service:bootRun

# Terminal 3: Resume Parser Service
./gradlew :services:resume-parser-service:bootRun

# Terminal 4: Candidate Matcher Service
./gradlew :services:candidate-matcher-service:bootRun

# Terminal 5: Interview Prep Service
./gradlew :services:interview-prep-service:bootRun

# Terminal 6: Job Analyzer Service
./gradlew :services:job-analyzer-service:bootRun

# Terminal 7: Screening Bot Service
./gradlew :services:screening-bot-service:bootRun

# Terminal 8: AI Integration Service
./gradlew :services:ai-integration-service:bootRun

# Terminal 9: Notification Service
./gradlew :services:notification-service:bootRun
```

**Option 2: Run all services using Docker Compose (easier for testing)**

```bash
# Update Dockerfile paths in docker-compose.yml and run
docker-compose -f docker-compose.prod.yml up -d
```

### Checking Service Health

```bash
# Check all services are running
curl http://localhost:8000/actuator/health

# Check individual services
curl http://localhost:8001/actuator/health  # Auth
curl http://localhost:8002/actuator/health  # Resume Parser
# ... etc
```

## Common Development Tasks

### Building

```bash
# Build all modules
./gradlew clean build

# Build specific module
./gradlew :services:resume-parser-service:build

# Build without running tests (faster)
./gradlew build -x test
```

### Testing

```bash
# Run all tests
./gradlew test

# Run specific service tests
./gradlew :services:resume-parser-service:test

# Run specific test class
./gradlew :services:resume-parser-service:test --tests ResumeParsing*

# Run with coverage report
./gradlew test jacocoTestReport
```

### Database Management

```bash
# Access PostgreSQL
docker exec -it smart-hiring-postgres psql -U hiring_user -d smart_hiring_db

# Access MongoDB
docker exec -it smart-hiring-mongodb mongosh --username hiring_user --password hiring_password --authenticationDatabase admin

# View logs
docker logs smart-hiring-postgres
docker logs smart-hiring-mongodb
```

### Messaging/Queue Management

```bash
# RabbitMQ Management Console
# Navigate to: http://localhost:15672
# Username: hiring_user
# Password: hiring_password

# View RabbitMQ logs
docker logs smart-hiring-rabbitmq
```

### Debugging

```bash
# Enable debug logging for a service
export DEBUG=true
./gradlew :services:resume-parser-service:bootRun

# View service logs
docker logs -f smart-hiring-<service-name>

# Access service logs via Kibana
# Navigate to: http://localhost:5601
```

### Code Quality & Linting

```bash
# Format code using Gradle Spotless (once configured)
./gradlew spotlessApply

# Run static analysis (once configured)
./gradlew sonarqube
```

## Frontend Development

### Angular Admin Dashboard

```bash
cd frontend/admin-dashboard

# Install dependencies
npm install

# Start development server
npm start

# Build for production
npm run build

# Run tests
npm test
```

### React Candidate Portal

```bash
cd frontend/candidate-portal

# Install dependencies
npm install

# Start development server
npm start

# Build for production
npm run build

# Run tests
npm test
```

## Useful URLs During Development

| Service | URL | Purpose |
|---------|-----|---------|
| API Gateway | http://localhost:8000 | Main API entry point |
| Swagger UI | http://localhost:8000/swagger-ui.html | API documentation |
| GraphQL Playground | http://localhost:8000/graphql | GraphQL testing |
| PostgreSQL | localhost:5432 | Database |
| MongoDB | localhost:27017 | Document database |
| RabbitMQ | http://localhost:15672 | Message broker |
| Redis | localhost:6379 | Cache |
| Kibana | http://localhost:5601 | Log visualization |
| Grafana | http://localhost:3000 | Metrics dashboard |
| Prometheus | http://localhost:9090 | Metrics storage |
| Jaeger | http://localhost:16686 | Distributed tracing |

## Troubleshooting

### Port Already in Use

```bash
# Find process using port 8000
lsof -i :8000

# Kill process (macOS)
kill -9 <PID>

# Or use different port by setting environment variable
export SERVER_PORT=8001
./gradlew :services:api-gateway:bootRun
```

### Docker Issues

```bash
# Stop all containers
docker-compose down

# Remove all containers and volumes
docker-compose down -v

# View container logs
docker-compose logs -f <service-name>

# Restart specific service
docker-compose restart smart-hiring-postgres
```

### Gradle Issues

```bash
# Clean Gradle cache
rm -rf ~/.gradle/caches

# Rebuild dependencies
./gradlew clean --refresh-dependencies build
```

### Memory Issues

```bash
# Increase Java heap size
export GRADLE_OPTS="-Xmx4g -Xms2g"

# Or update gradle.properties
echo "org.gradle.jvmargs=-Xmx4g" >> gradle.properties
```

## Project Structure for New Developers

When adding new features:

1. **DTOs** - Add to `shared:shared-commons/src/main/java/org/vinod/smarthiringassistant/commons/dto/`
2. **Events** - Add to `shared:shared-events/src/main/java/org/vinod/smarthiringassistant/events/`
3. **Service Logic** - Add to respective service under `services:/`
4. **Tests** - Mirror structure under `src/test/`
5. **Config** - Update `application.yaml` in service's resources

## Best Practices

✅ **DO:**
- Use feature branches for development
- Write unit tests for all business logic
- Document complex algorithms with comments
- Use dependency injection (Spring @Autowired)
- Follow REST conventions for APIs
- Use proper exception handling
- Keep DTOs separate from entities

❌ **DON'T:**
- Commit directly to main branch
- Skip writing tests
- Use System.out.println (use Logging)
- Hard-code configuration values
- Mix business logic with REST controllers
- Ignore deprecation warnings

## Git Workflow

```bash
# Create feature branch
git checkout -b feature/new-feature-name

# Make changes and commit
git add .
git commit -m "feat: add new feature"

# Push to remote
git push origin feature/new-feature-name

# Create Pull Request on GitHub

# Once merged, delete branch
git branch -d feature/new-feature-name
```

## Performance Tips

1. **Use gRPC for internal service communication** - Faster than REST
2. **Cache frequently accessed data** - Use Redis
3. **Use async processing** - Leverage RabbitMQ for non-blocking operations
4. **Paginate large queries** - Avoid loading entire datasets
5. **Use database indexes** - On frequently queried fields
6. **Monitor with Prometheus** - Track metrics over time

## Getting Help

1. Check the main README.md
2. Review service-specific documentation
3. Check logs in Kibana
4. Use Jaeger for distributed tracing
5. Ask in team Slack/Discord channel
6. Create GitHub issue for bugs

## Next Steps

1. ✅ Complete this setup
2. ✅ Run services successfully
3. 📖 Read through architecture documentation
4. 🧪 Run existing tests
5. 👨‍💻 Start implementing your first feature
6. 📝 Document your changes

---

Happy coding! 🚀

