# Multi-stage Docker build for Screening Bot Service
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/screening-bot-service services/screening-bot-service

RUN ./gradlew :services:screening-bot-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/screening-bot-service/build/libs/*.jar app.jar

EXPOSE 8006
ENTRYPOINT ["java", "-jar", "app.jar"]

