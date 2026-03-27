# Multi-stage Docker build for Candidate Matcher Service
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/candidate-matcher-service services/candidate-matcher-service

RUN ./gradlew :services:candidate-matcher-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/candidate-matcher-service/build/libs/*.jar app.jar

EXPOSE 8003
ENTRYPOINT ["java", "-jar", "app.jar"]

