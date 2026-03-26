# Multi-stage Docker build for Interview Prep Service
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/interview-prep-service services/interview-prep-service

RUN ./gradlew :services:interview-prep-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/interview-prep-service/build/libs/*.jar app.jar

EXPOSE 8004
ENTRYPOINT ["java", "-jar", "app.jar"]

