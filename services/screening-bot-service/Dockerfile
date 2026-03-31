# Multi-stage Docker build for Screening Bot Service
# Builder: Debian-based JDK for reliable TLS handshake with Maven Central
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/screening-bot-service services/screening-bot-service

RUN chmod +x gradlew && ./gradlew :services:screening-bot-service:bootJar -x test --no-daemon

# Runtime: lightweight Alpine image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/screening-bot-service/build/libs/*.jar app.jar

EXPOSE 8006
ENTRYPOINT ["java", "-jar", "app.jar"]

