# Stage 1: Build
FROM gradle:8.14.3-jdk24-corretto AS builder
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
RUN apt-get update && apt-get install -y curl bc && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 10000 8100

# Add OpenTelemetry Java agent
ENV OTEL_AGENT_VERSION=2.7.0
RUN mkdir -p /otel && \
    curl -fsSL --retry 3 --retry-delay 2 \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar \
    -o /otel/opentelemetry-javaagent.jar && \
    ls -lh /otel/opentelemetry-javaagent.jar

CMD ["/app/entrypoint.sh"]
