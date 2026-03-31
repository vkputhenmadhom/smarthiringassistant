# Multi-stage Docker build for Auth Service
# Builder: Debian-based JDK for reliable TLS handshake with Maven Central
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/auth-service services/auth-service

RUN chmod +x gradlew && ./gradlew :services:auth-service:bootJar -x test --no-daemon

# Runtime: lightweight Alpine image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/auth-service/build/libs/*.jar app.jar

EXPOSE 8001
ENTRYPOINT ["java", "-jar", "app.jar"]

