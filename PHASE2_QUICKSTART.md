# Smart Hiring Assistant - Phase 2 Quick Start Guide

## 🚀 Quick Start (5 minutes)

### Step 1: Start Infrastructure
```bash
# Navigate to project directory
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant

# Start all services with Docker Compose
docker-compose up -d

# Verify services are running
docker-compose ps
```

### Step 2: Build the Project
```bash
# Build without tests (faster)
./gradlew clean build -x test

# Full build with tests
./gradlew clean build
```

### Step 3: Run Services

**Terminal 1 - Auth Service:**
```bash
./gradlew :services:auth-service:bootRun
# Runs on http://localhost:8001
```

**Terminal 2 - Resume Parser Service:**
```bash
./gradlew :services:resume-parser-service:bootRun
# Runs on http://localhost:8002
# gRPC on localhost:9091
```

**Terminal 3 - Candidate Matcher Service:**
```bash
./gradlew :services:candidate-matcher-service:bootRun
# Runs on http://localhost:8003
```

### Step 4: Test the Services

#### 1. Register User
```bash
curl -X POST http://localhost:8001/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com",
    "password": "SecurePass123!",
    "confirmPassword": "SecurePass123!",
    "firstName": "John",
    "lastName": "Doe",
    "role": "JOB_SEEKER"
  }'
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "username": "john_doe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "role": "JOB_SEEKER"
  }
}
```

#### 2. Login
```bash
curl -X POST http://localhost:8001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "password": "SecurePass123!"
  }'
```

#### 3. Create Sample Job (for testing matching)
```bash
# You can use the database directly or create an endpoint
# For now, data can be inserted via database client
```

#### 4. Get Current User
```bash
TOKEN="eyJhbGciOiJIUzUxMiJ9..."

curl http://localhost:8001/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

#### 5. Validate Token
```bash
curl http://localhost:8001/api/auth/validate \
  -H "Authorization: Bearer $TOKEN"
```

#### 6. Match Candidate with Jobs
```bash
curl -X POST http://localhost:8003/api/matches/match \
  -H "Content-Type: application/json" \
  -d '{
    "candidateId": 1,
    "skills": ["Java", "Spring Boot", "PostgreSQL", "Docker"],
    "experienceYears": 5,
    "location": "New York"
  }'
```

Response:
```json
[
  {
    "matchId": "123e4567-e89b-12d3-a456-426614174000",
    "candidateId": 1,
    "jobId": "job-001",
    "overallScore": 85.5,
    "skillMatchPercentage": 90.0,
    "experienceMatchPercentage": 100.0,
    "locationMatchPercentage": 100.0,
    "status": "NEW",
    "createdAt": "2026-03-26T10:30:00"
  }
]
```

---

## 📊 Monitoring & Administration

### RabbitMQ Management
- **URL**: http://localhost:15672
- **Username**: hiring_user
- **Password**: hiring_password
- **Check**: User Registration & Authentication Events, Resume Parsing Events, Candidate Matching Events

### Prometheus Metrics
- **URL**: http://localhost:9090
- **Metrics**: JVM, HTTP requests, custom application metrics

### Grafana Dashboards
- **URL**: http://localhost:3000
- **Username**: admin
- **Password**: hiring_password
- **Dashboards**: Service metrics, request latency, error rates

### Kibana Logs
- **URL**: http://localhost:5601
- **View**: Application logs, error tracking, audit trails

### Jaeger Tracing
- **URL**: http://localhost:16686
- **View**: Distributed traces, service dependencies, latency analysis

---

## 📁 Project Structure

```
SmartHiringAssistant/
├── services/
│   ├── auth-service/                    # Authentication & Authorization
│   ├── resume-parser-service/           # Resume Parsing (REST + gRPC)
│   ├── candidate-matcher-service/       # Job Matching Engine
│   ├── api-gateway/                     # API Gateway (Phase 3)
│   └── [other-services]/
├── shared/
│   ├── shared-commons/                  # Common utilities & RabbitMQ config
│   ├── shared-events/                   # Event models
│   └── grpc-definitions/                # gRPC Proto definitions
├── infrastructure/
│   ├── db/                              # Database init scripts
│   ├── messaging/                       # RabbitMQ config
│   └── monitoring/                      # Prometheus config
├── docker-compose.yml                   # Infrastructure setup
├── build.gradle                         # Root Gradle config
├── settings.gradle                      # Module configuration
└── PHASE2_IMPLEMENTATION.md             # Detailed documentation
```

---

## 🔧 Configuration

### Environment Variables (Optional)
```bash
export OPENAI_API_KEY="sk-your-api-key"
export POSTGRES_USER="hiring_user"
export POSTGRES_PASSWORD="hiring_password"
export JWT_SECRET="your-secret-key-change-in-production"
```

### PostgreSQL Database Connection
```yaml
Database1: PostgreSQL
Host: localhost
Port: 5432
Database2: smart_hiring_db
Username: hiring_user
Password: hiring_password
```

### Redis Cache
```yaml
Host: localhost
Port: 6379
DB: 0
```

### RabbitMQ Messaging
```yaml
Host: localhost
Port: 5672
Username: hiring_user
Password: hiring_password
Virtual Host: /
```

---

## 🐛 Troubleshooting

### Service won't start
```bash
# Check logs
./gradlew :services:auth-service:bootRun

# Verify ports are not in use
lsof -i :8001
lsof -i :8002
lsof -i :8003

# Kill process on port
kill -9 <PID>
```

### Database connection issues
```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Test connection
psql -h localhost -U hiring_user -d smart_hiring_db
```

### RabbitMQ issues
```bash
# Check RabbitMQ is running
docker-compose ps rabbitmq

# View management UI
curl http://localhost:15672/api/overview -u hiring_user:hiring_password
```

### Redis connection issues
```bash
# Test Redis connection
redis-cli -h localhost -p 6379 ping
# Should return: PONG
```

---

## 📚 API Documentation

### Auth Service APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Authenticate user |
| POST | `/api/auth/refresh` | Refresh access token |
| GET | `/api/auth/validate` | Validate token |
| GET | `/api/auth/me` | Get current user |

### Resume Parser Service APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/resumes/parse` | Upload and parse resume |
| GET | `/api/resumes/{resumeId}` | Get parse status |
| GET | `/api/resumes/user/resumes` | List user's resumes |

### Candidate Matcher Service APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/matches/match` | Match candidate with all jobs |
| POST | `/api/matches/match-job` | Match candidate with specific job |
| GET | `/api/matches/job/{jobId}/matches` | Get job's candidate matches |
| GET | `/api/matches/candidate/{candidateId}/matches` | Get candidate's job matches |
| GET | `/api/matches/{matchId}` | Get match details |
| PUT | `/api/matches/{matchId}` | Update match status |

---

## 🔐 Security Notes

### JWT Token
- **Access Token Expiration**: 1 hour (3600000 ms)
- **Refresh Token Expiration**: 7 days (604800000 ms)
- **Algorithm**: HS512 (HMAC with SHA-512)
- **Default Secret**: Change in production!

### Best Practices
1. Store JWT tokens securely (HttpOnly cookies, not localStorage)
2. Use HTTPS in production
3. Update JWT_SECRET in production
4. Implement rate limiting on auth endpoints
5. Enable CORS only for trusted domains
6. Use database connection pooling
7. Enable SSL for PostgreSQL connections

---

## 📈 Performance Considerations

### Caching
- Job requirements cached in Redis (1 hour TTL)
- Database connection pooling enabled
- gRPC used for high-throughput internal communication

### Scalability
- Stateless authentication (JWT)
- Message-driven architecture (RabbitMQ)
- Horizontal scaling ready
- Independent service deployments

### Optimization Tips
1. Enable Prometheus metrics for monitoring
2. Use Grafana for real-time dashboards
3. Monitor Elasticsearch logs for errors
4. Track Jaeger traces for bottlenecks
5. Cache frequently matched jobs
6. Batch process large resume uploads

---

## 🚀 Next Phase (Phase 3)

- [ ] API Gateway with routing and rate limiting
- [ ] GraphQL endpoint
- [ ] LLM integration for advanced NLP
- [ ] Frontend applications (Angular/React)
- [ ] AWS deployment (ECS, RDS, ElastiCache)
- [ ] CI/CD pipelines (GitHub Actions)
- [ ] Comprehensive test suite
- [ ] Production monitoring setup

---

## 📞 Support

For issues or questions:
1. Check PHASE2_IMPLEMENTATION.md for detailed documentation
2. Review application logs: `docker-compose logs -f <service-name>`
3. Check RabbitMQ admin panel for event queues
4. Review Kibana for application errors
5. Use Jaeger for tracing issues

---

## ✅ Verification Checklist

- [ ] Docker containers are running (`docker-compose ps`)
- [ ] PostgreSQL accessible (`psql -h localhost -U hiring_user -d smart_hiring_db`)
- [ ] RabbitMQ admin UI accessible (http://localhost:15672)
- [ ] Redis accessible (`redis-cli ping`)
- [ ] Auth Service running (http://localhost:8001/actuator/health)
- [ ] Resume Parser running (http://localhost:8002/actuator/health)
- [ ] Candidate Matcher running (http://localhost:8003/actuator/health)
- [ ] Can register new user
- [ ] Can login with credentials
- [ ] Can validate token
- [ ] RabbitMQ queues receiving events

---

**Great job! Phase 2 is complete and ready to use! 🎉**

