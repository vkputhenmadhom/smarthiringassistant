# Smart Hiring Assistant - Environment Setup Guide

## 🔐 Secure Configuration Management

### Option 1: Environment Variables (Recommended for Personal Projects)

1. **Create `.env` file in project root:**
   ```bash
   cp .env.example .env
   ```

2. **Edit `.env` with your actual values:**
   ```bash
   # Generate new JWT secret
   openssl rand -base64 64 | tr -d '\n' | head -c 64

   # Update .env file with generated secret
   JWT_SECRET=your-new-generated-secret-here
   ```

3. **Run with environment variables:**
   ```bash
   # Linux/Mac
   export $(cat .env | xargs) && ./gradlew :services:auth-service:bootRun

   # Or use dotenv
   pip install python-dotenv
   dotenv -f .env run ./gradlew :services:auth-service:bootRun
   ```

### Option 2: Spring Profiles (Recommended for Development)

1. **Create `application-local.yaml`:**
   ```yaml
   jwt:
     secret: ${JWT_SECRET:your-local-secret-here}
   spring:
     datasource:
       password: ${DB_PASSWORD:your-local-db-password}
   ```

2. **Run with profile:**
   ```bash
   ./gradlew :services:auth-service:bootRun --args='--spring.profiles.active=local'
   ```

### Option 3: External Configuration (Recommended for Production)

1. **Use Spring Cloud Config Server** (for microservices)
2. **Use HashiCorp Vault** (enterprise-grade)
3. **Use AWS Secrets Manager** (cloud deployment)
4. **Use Azure Key Vault** (cloud deployment)

### Option 4: Docker Secrets (for Containerized Deployment)

1. **Create secrets:**
   ```bash
   echo "your-jwt-secret-here" | docker secret create jwt_secret -
   echo "your-db-password" | docker secret create db_password -
   ```

2. **Use in docker-compose.yml:**
   ```yaml
   services:
     auth-service:
       secrets:
         - jwt_secret
         - db_password
       environment:
         - JWT_SECRET_FILE=/run/secrets/jwt_secret
         - DB_PASSWORD_FILE=/run/secrets/db_password
   ```

## 🛡️ Security Best Practices

### JWT Secret Management
- **Length**: Minimum 32 characters (256 bits) for HS256, 64+ for HS512
- **Generation**: Use cryptographically secure random generators
- **Rotation**: Change periodically (every 30-90 days)
- **Storage**: Never commit to version control

### Database Credentials
- **Strong Passwords**: Use password managers to generate complex passwords
- **Connection Security**: Use SSL/TLS for production databases
- **Access Control**: Principle of least privilege

### Environment Variable Security
- **`.env` in `.gitignore`**: Never commit sensitive files
- **File Permissions**: `chmod 600 .env`
- **No Logs**: Don't log sensitive values
- **Container Security**: Use Docker secrets for containerized deployments

## 🔧 Quick Setup for Personal Project

### Step 1: Generate Secure Secrets
```bash
# Generate JWT secret
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n' | head -c 64)
echo "JWT_SECRET=$JWT_SECRET" > .env

# Generate database password
DB_PASSWORD=$(openssl rand -base64 32 | tr -d '\n' | head -c 32)
echo "DB_PASSWORD=$DB_PASSWORD" >> .env

# Add other defaults
cat >> .env << EOF
DB_URL=jdbc:postgresql://localhost:5432/smart_hiring_db
DB_USERNAME=hiring_user
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=hiring_user
RABBITMQ_PASSWORD=hiring_password
RABBITMQ_VHOST=/
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=hiring_password
LOGGING_LEVEL=INFO
EOF
```

### Step 2: Update .gitignore
```bash
echo ".env" >> .gitignore
echo "*.log" >> .gitignore
echo "target/" >> .gitignore
```

### Step 3: Run with Environment Variables
```bash
# Load environment variables and run
set -a && source .env && set +a
./gradlew :services:auth-service:bootRun
```

## 🚀 Production Deployment

For production deployment, use:

1. **Environment Variables** in your deployment platform (Heroku, AWS, etc.)
2. **Secrets Management** services (AWS Secrets Manager, HashiCorp Vault)
3. **Kubernetes Secrets** for container orchestration
4. **GitHub Secrets** for CI/CD pipelines

## 📋 Checklist

- [ ] JWT secret is 64+ characters long
- [ ] Database password is strong and unique
- [ ] `.env` file is in `.gitignore`
- [ ] No sensitive values in application.yaml
- [ ] Environment variables have secure fallbacks
- [ ] Secrets are rotated periodically
- [ ] SSL/TLS enabled for production databases
- [ ] Access logs don't contain sensitive data

## 🔍 Verification

Test your setup:
```bash
# Check environment variables are loaded
echo $JWT_SECRET | wc -c  # Should be > 64
echo $DB_PASSWORD | wc -c  # Should be > 20

# Test JWT generation
curl -X POST http://localhost:8001/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"Test123!","confirmPassword":"Test123!","firstName":"Test","lastName":"User","role":"JOB_SEEKER"}'
```
