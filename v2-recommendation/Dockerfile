# Stage 1: Build
FROM gradle:8.14.3-jdk24-corretto AS builder

# Build argument for GitLab token
ARG GITLAB_BRAND_TOKEN
ENV GITLAB_BRAND_TOKEN=${GITLAB_BRAND_TOKEN}

WORKDIR /app

# Copy gradle wrapper and build scripts first (for caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .
RUN chmod +x gradlew

# Copy source code
COPY src src
COPY lib lib
COPY .git .git

# Build the project
RUN ./gradlew clean build -x test -x integrationtest --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:24-jre
LABEL version=1.0.0
LABEL maintainer="devops@movingwalls.com"
RUN apt-get update && apt-get install -y bc curl
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh

EXPOSE 10002

RUN chmod +x /app/entrypoint.sh

# Add OpenTelemetry Java agent - create directory and verify download
ENV OTEL_AGENT_VERSION=2.11.0
RUN mkdir -p /otel && \
    curl -L -o /otel/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar && \
    chmod 644 /otel/opentelemetry-javaagent.jar && \
    ls -lh /otel/opentelemetry-javaagent.jar

CMD ["/app/entrypoint.sh"]