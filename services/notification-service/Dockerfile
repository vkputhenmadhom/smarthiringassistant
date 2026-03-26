# Multi-stage Docker build for Notification Service
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/notification-service services/notification-service

RUN ./gradlew :services:notification-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/notification-service/build/libs/*.jar app.jar

EXPOSE 8007
ENTRYPOINT ["java", "-jar", "app.jar"]

