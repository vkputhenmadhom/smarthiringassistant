# Multi-stage Docker build for Notification Service
# Builder: Debian-based JDK for reliable TLS handshake with Maven Central
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/notification-service services/notification-service

RUN chmod +x gradlew && ./gradlew :services:notification-service:bootJar -x test --no-daemon

# Runtime: lightweight Alpine image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/notification-service/build/libs/*.jar app.jar

EXPOSE 8007
ENTRYPOINT ["java", "-jar", "app.jar"]

