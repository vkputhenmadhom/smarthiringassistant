# Multi-stage Docker build for Job Analyzer Service
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/job-analyzer-service services/job-analyzer-service

RUN chmod +x gradlew && ./gradlew :services:job-analyzer-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/job-analyzer-service/build/libs/*.jar app.jar

EXPOSE 8005
ENTRYPOINT ["java", "-jar", "app.jar"]

