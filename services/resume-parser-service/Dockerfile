# Multi-stage Docker build for Resume Parser Service
# Builder: Debian-based JDK for reliable TLS handshake with Maven Central
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared shared
COPY services/resume-parser-service services/resume-parser-service

# The Gradle home (wrapper distribution + dependency cache) is a BuildKit cache mount shared by every
# service image, so it is downloaded once and reused instead of fetched from scratch per image.
RUN --mount=type=cache,id=gradle-home,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :services:resume-parser-service:bootJar -x test --no-daemon

# Runtime: lightweight Alpine image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/services/resume-parser-service/build/libs/*.jar app.jar

EXPOSE 8002
ENTRYPOINT ["java", "-jar", "app.jar"]
