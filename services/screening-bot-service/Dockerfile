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

# The Gradle home (wrapper distribution + dependency cache) is a BuildKit cache mount shared by every
# service image, so it is downloaded once and reused instead of fetched from scratch per image.
# sharing=locked: parallel image builds take turns on the cache instead of racing for Gradle's own
# lock (which times out after 60 s while the first build fills a cold cache).
RUN --mount=type=cache,id=gradle-home,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :services:screening-bot-service:bootJar -x test --no-daemon

# Runtime: lightweight Alpine image, running as an unprivileged user
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=builder /app/services/screening-bot-service/build/libs/*.jar app.jar

EXPOSE 8006

# Size the heap from the container's memory limit and exit on OutOfMemoryError so Docker restarts the
# service instead of leaving a half-dead JVM. JAVA_TOOL_OPTIONS can be overridden per environment.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# The jar stays root-owned and read-only to the app user
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
