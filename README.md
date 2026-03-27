# Smart Hiring Assistant - Microservices Architecture

A production-grade, event-driven microservices platform for intelligent recruitment and hiring automation. Built with Java, Spring Boot, and cutting-edge AI/GenAI integration.

## 🎯 Project Overview

Smart Hiring Assistant is a sophisticated hiring platform designed to streamline the recruitment process through:

- **Resume Parsing & Analysis** - AI-powered resume extraction and skill identification
- **Intelligent Candidate Matching** - Advanced algorithms to match candidates with job requirements
- **Interview Preparation** - AI-generated interview questions tailored to the position
- **Job Description Analysis** - Automated JD parsing and requirement extraction
- **Automated Screening Bot** - Multi-stage candidate screening with AI evaluation
- **Event-Driven Architecture** - Asynchronous processing for scalability
- **Multi-Frontend Support** - Angular dashboard for HR + React portal for candidates

## 🏗️ System Architecture

### Microservices (9 Total)

```
┌─────────────────────────────────────────────────────────────┐
│                      API GATEWAY (8000)                     │
│            Spring Cloud Gateway - Request Routing           │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
   ┌────▼────┐        ┌──────▼──────┐      ┌──────▼──────┐
   │  Auth   │        │   Resume    │      │  Candidate  │
   │ Service │        │   Parser    │      │   Matcher   │
   │ (8001)  │        │   (8002)    │      │   (8003)    │
   └────┬────┘        └────┬────────┘      └────┬────────┘
        │                  │                     │
   ┌────▼────┐        ┌──────▼──────┐      ┌──────▼──────┐
   │Interview│        │     Job     │      │ Screening   │
   │  Prep   │        │   Analyzer  │      │    Bot      │
   │ (8004)  │        │   (8005)    │      │   (8006)    │
   └─────────┘        └─────────────┘      └─────────────┘

   ┌─────────────────────────────────────────────────────────┐
   │  AI Integration Service (8008)  │ Notification (8007)   │
   └─────────────────────────────────────────────────────────┘
```

### Technology Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 4.0.5, Spring Cloud 2025.1.1 |
| **Communication** | REST, gRPC, GraphQL |
| **Databases** | PostgreSQL (relational), MongoDB (documents) |
| **Caching** | Redis |
| **Messaging** | RabbitMQ (event-driven) |
| **AI/ML** | OpenAI GPT-4, Claude API (pluggable) |
| **Frontend** | Angular (admin), React (candidate) |
| **Monitoring** | Prometheus, Grafana, Elasticsearch, Kibana |
| **Tracing** | Jaeger |
| **Containerization** | Docker, Docker Compose |

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 21+
- Gradle 8.x+
- Node.js 18+ (for frontends)

### Step 1: Start Infrastructure

```bash
cd SmartHiringAssistant
docker-compose up -d
```

This starts:
- PostgreSQL (5432)
- MongoDB (27017)
- RabbitMQ (5672, Management: 15672)
- Redis (6379)
- Elasticsearch (9200)
- Kibana (5601)
- Prometheus (9090)
- Grafana (3000)
- Jaeger (16686)

### Step 2: Build the Project

```bash
# Build all modules
./gradlew build

# Build specific service
./gradlew :services:api-gateway:build
```

### Step 3: Run Services

Each service can be run individually:

```bash
# Terminal 1: API Gateway
./gradlew :services:api-gateway:bootRun

# Terminal 2: Auth Service
./gradlew :services:auth-service:bootRun

# Terminal 3: Resume Parser Service
./gradlew :services:resume-parser-service:bootRun

# Continue for other services in separate terminals
```

Or use the provided Dockerfile for each service for container deployment.

### Step 4: Access Services

- **API Gateway**: http://localhost:8000
- **Auth Service**: http://localhost:8001
- **Resume Parser**: http://localhost:8002
- **Candidate Matcher**: http://localhost:8003
- **Interview Prep**: http://localhost:8004
- **Job Analyzer**: http://localhost:8005
- **Screening Bot**: http://localhost:8006
- **Notification Service**: http://localhost:8007
- **AI Integration**: http://localhost:8008

**Admin UIs:**
- **RabbitMQ Management**: http://localhost:15672 (hiring_user/hiring_password)
- **Kibana**: http://localhost:5601
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/hiring_password)
- **Jaeger**: http://localhost:16686

## 📦 Project Structure

```
SmartHiringAssistant/
├── shared/
│   ├── shared-commons/          # Shared DTOs, utilities, exceptions
│   ├── shared-events/           # Domain events for event-driven architecture
│   └── grpc-definitions/        # Protocol Buffer definitions for gRPC
├── services/
│   ├── api-gateway/             # Spring Cloud Gateway (request routing)
│   ├── auth-service/            # User authentication & authorization
│   ├── resume-parser-service/   # Resume parsing & skill extraction
│   ├── candidate-matcher-service/ # Candidate-job matching algorithms
│   ├── interview-prep-service/  # Interview question generation
│   ├── job-analyzer-service/    # JD analysis & salary prediction
│   ├── screening-bot-service/   # Automated multi-stage screening
│   ├── ai-integration-service/  # OpenAI/Claude API wrapper
│   └── notification-service/    # Email & SMS notifications
├── frontend/
│   ├── admin-dashboard/         # Angular HR dashboard
│   └── candidate-portal/        # React candidate portal
├── infrastructure/
│   ├── db/                      # Database init scripts
│   ├── messaging/               # RabbitMQ configuration
│   ├── monitoring/              # Prometheus configuration
│   └── kubernetes/              # K8s manifests (optional)
├── docker-compose.yml           # Local development environment
└── README.md
```

## 🔄 Event-Driven Architecture

The system uses RabbitMQ for asynchronous event processing:

### Key Events

1. **ResumeParsedEvent** - Triggered when resume is successfully parsed
   - Triggers: Candidate Matching

2. **CandidateMatchedEvent** - Triggered when candidate matches a job
   - Triggers: Notification Service

3. **InterviewScheduledEvent** - Triggered when interview is scheduled
   - Triggers: Notification Service, Interview Prep Service

### Event Flow Example

```
Resume Upload
    ↓
Resume Parser Service (parses resume)
    ↓
ResumeParsedEvent (published to RabbitMQ)
    ↓
Candidate Matcher Service (receives event)
    ↓
Finds matching jobs
    ↓
CandidateMatchedEvent (published)
    ↓
Notification Service (receives event)
    ↓
Sends email notification to candidate
```

## 🤖 AI/GenAI Features

### Resume Analysis
- Skill extraction using NLP
- Experience calculation
- Resume scoring
- Keyword matching

### Interview Question Generation
- Difficulty-based question generation
- Role-specific questions
- Behavioral & technical questions
- Answer evaluation using AI

### Job Analysis
- Salary range prediction
- Required skills extraction
- Experience level categorization
- Market competitiveness analysis

### Automated Screening
- Multi-stage screening pipeline
- Response evaluation
- Candidate scoring
- Recommendation generation

## 🔐 Security Features

- **JWT Authentication** - Token-based security
- **OAuth2 Integration** - Third-party provider support
- **Role-Based Access Control** - RBAC implementation
- **Data Encryption** - Sensitive data encryption at rest
- **API Rate Limiting** - Protection against abuse
- **CORS Configuration** - Cross-origin request handling

## 📊 Monitoring & Observability

### Logging
- **Elasticsearch + Kibana** - Centralized log aggregation
- **Structured Logging** - JSON format with correlation IDs

### Metrics
- **Prometheus** - Metrics collection
- **Grafana** - Metrics visualization
- **Custom Dashboards** - Service-specific metrics
- **Prometheus Alerts + Alertmanager** - Service health and SLO-like alerting

See `docs/deployment/monitoring-alerting.md` for setup, scrape targets, alert rules, and dashboard provisioning.
Use `docker-compose.monitoring-staging.yml` when you want staging-specific alert thresholds locally.

### Tracing
- **Jaeger** - Distributed tracing
- **Request Correlation** - End-to-end request tracking

## 📝 API Documentation

### OpenAPI/Swagger
- Available at: http://localhost:8000/swagger-ui.html
- OpenAPI spec: http://localhost:8000/api-docs

### GraphQL
- Endpoint: http://localhost:8000/graphql
- GraphQL Playground: http://localhost:8000/graphql/playground

## 🧪 Testing

### Run All Tests
```bash
./gradlew test
```

### Run Specific Service Tests
```bash
./gradlew :services:resume-parser-service:test
```

### Integration Tests
```bash
./gradlew integrationTest
```

## 📦 Dependencies Management

All dependencies are managed at the root `build.gradle` level using the Spring Dependency Management plugin. This ensures version consistency across all services.

### Key Libraries
- Spring Boot 4.0.5
- Spring Cloud 2025.1.1
- gRPC 1.60.0
- Protobuf 3.24.4
- OpenAI Java SDK 0.15.0
- RabbitMQ AMQP Client

## 🌐 Frontend Setup

### Admin Dashboard (Angular)
```bash
cd frontend/admin-dashboard
npm install
npm start
```

### Candidate Portal (React)
```bash
cd frontend/candidate-portal
npm install
npm start
```

## 📈 Performance Tuning

### Database
- Connection pooling (HikariCP)
- Query optimization
- Index creation for frequently accessed fields

### Caching
- Redis caching for AI responses
- Candidate match results caching
- Job requirements caching (LRU policy)

### Asynchronous Processing
- Event-driven architecture
- Background job processing
- Dead letter queue handling

## 🔄 CI/CD Pipeline

Backend CI/CD is configured in `.github/workflows/backend-services.yml` with:
- Changed-service backend build matrix
- Full backend tests + JaCoCo coverage reports
- SonarQube analysis with quality gate enforcement
- Manual AWS CodeDeploy deployment (`workflow_dispatch`)

This is the single authoritative backend workflow.

See `docs/deployment/aws-cicd.md` for setup details, required secrets, and AWS deployment prerequisites.
For initial container orchestration with health checks and scaling, see `docs/deployment/docker-compose-scaling.md`.

## 🚢 Deployment

### Docker Deployment
```bash
# Build images
docker-compose -f docker-compose.prod.yml build

# Deploy
docker-compose -f docker-compose.prod.yml up -d
```

### Kubernetes Deployment
```bash
# Apply staging overlay (or use overlays/prod)
kubectl apply -k infrastructure/kubernetes/overlays/staging

# Check status
kubectl -n smart-hiring get deploy,svc,hpa,pdb,ingress,networkpolicy
```

See `docs/deployment/kubernetes-autoscaling.md` for probe paths, per-service config maps, External Secrets setup, AWS ALB ingress, and scaling details.

### Environment Variables

Create `.env` file:
```env
OPENAI_API_KEY=sk-your-key-here
MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-password
TWILIO_ACCOUNT_SID=your-account-sid
TWILIO_AUTH_TOKEN=your-token
TWILIO_FROM_NUMBER=+1234567890
```

## 📚 Development Phases

| Phase | Duration | Focus |
|-------|----------|-------|
| 1 | Weeks 1-2 | Foundation & Architecture |
| 2 | Weeks 3-4 | Microservices Core Infrastructure |
| 3 | Weeks 5-7 | AI & Core Business Logic |
| 4 | Weeks 8-10 | Event-Driven System & Data Models |
| 5 | Weeks 11-13 | Frontend Development |
| 6 | Weeks 14-16 | Security, Testing & Deployment |

## 🤝 Contributing

1. Create a feature branch
2. Commit changes
3. Push to branch
4. Create Pull Request

### Code Standards
- Java 21+ features
- Lombok for boilerplate reduction
- Clean code principles
- Comprehensive Javadoc
- Unit test coverage > 80%

## 📄 License

MIT License - See LICENSE file for details

## 👥 Authors

- Vinod Puthenmadhom

## 🎓 Learning Resources

- [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)
- [gRPC Java Guide](https://grpc.io/docs/languages/java/)
- [Event-Driven Architecture Patterns](https://www.confluent.io/blog/event-driven-architecture/)
- [OpenAI API Documentation](https://platform.openai.com/docs)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/getstarted.html)

## 📞 Support

For issues, questions, or suggestions, please open an GitHub issue or contact the development team.

---

**Built with ❤️ for the modern hiring industry**

