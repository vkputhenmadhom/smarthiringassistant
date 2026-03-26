# Multi-stage Docker build for Resume Parser Service
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/resume-parser-service services/resume-parser-service

RUN ./gradlew :services:resume-parser-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/resume-parser-service/build/libs/*.jar app.jar

EXPOSE 8002
ENTRYPOINT ["java", "-jar", "app.jar"]

