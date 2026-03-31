# Multi-stage Docker build for Candidate Matcher Service
# Builder: Debian-based JDK for reliable TLS handshake with Maven Central
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/candidate-matcher-service services/candidate-matcher-service

RUN chmod +x gradlew && ./gradlew :services:candidate-matcher-service:bootJar -x test --no-daemon

# Runtime: lightweight Alpine image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/candidate-matcher-service/build/libs/*.jar app.jar

EXPOSE 8003
ENTRYPOINT ["java", "-jar", "app.jar"]

