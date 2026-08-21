#!/bin/bash

# Read from environment variables injected via Downward API
cpu="${CPU_LIMIT:-2}"
memory="${MEMORY_LIMIT:-4}"  # default fallback to 4 GiB

# Use 80% of memory limit for JVM heap
heap=$(echo "$memory * 0.8" | bc | awk '{printf "%d", $1}')

echo "Detected CPU: ${cpu}, Memory Limit: ${memory}Gi, Allocating Heap: ${heap}G"

# Conditionally add javaagent if OTEL is enabled
if [ "${OTEL_JAVAAGENT_ENABLED:-true}" = "true" ]; then
  JAVAAGENT_OPT="-javaagent:/otel/opentelemetry-javaagent.jar"
  echo "OTEL JavaAgent enabled"
else
  JAVAAGENT_OPT=""
  echo "OTEL JavaAgent disabled"
fi

exec java \
  ${JAVAAGENT_OPT} \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  -Dotel.service.name=${OTEL_SERVICE_NAME:-recommendation-engine} \
  -Dotel.exporter.otlp.endpoint=http://${OTEL_COLLECTOR_HOST:-otel-opentelemetry-collector.monitoring.svc.cluster.local}:4317 \
  -Dotel.exporter.otlp.protocol=grpc \
  -Dotel.metrics.exporter=otlp \
  -Dotel.traces.exporter=otlp \
  -Xms${heap}G \
  -Xmx${heap}G \
  -XX:ParallelGCThreads=${cpu%.*} \
  -XX:+UseG1GC \
  $JAVA_OPTS \
  -jar /app/app.jar