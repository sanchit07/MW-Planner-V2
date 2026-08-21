#!/usr/bin/env bash
# Production run command for the Reserved VM deployment. Starts the local
# infra daemons (mongo/redis/rabbitmq/minio), both Spring services, the
# standalone mock-IAM service, then v2-gateway (the single-port ingress that
# serves the static V2 frontend and proxies /v2api, /v2rec, /v2iam to the
# local backend/recommendation/IAM services).
#
# v2-gateway comes up in seconds so the deployment health check on "/" passes
# while the JVMs finish warming up in the background (~2-3 min); /v2api
# returns 502s until then.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"

JAVA="$ROOT/.jdk24/bin/java"
if [ ! -x "$JAVA" ]; then
  echo "FATAL: vendored JDK not found at $JAVA" >&2
  exit 1
fi
echo "==> Using java: $JAVA"

echo "==> Starting infra daemons (mongo/redis/rabbitmq/minio)"
bash scripts/v2-services.sh &
INFRA_PID=$!

# Wait for Mongo before launching the Spring apps (they fail fast without it).
MONGO_UP=0
for i in $(seq 1 90); do
  if (exec 3<>/dev/tcp/127.0.0.1/27017) 2>/dev/null; then exec 3>&- 3<&-; MONGO_UP=1; break; fi
  sleep 1
done
if [ "$MONGO_UP" != "1" ]; then
  echo "FATAL: Mongo did not come up within 90s" >&2
  exit 1
fi
echo "==> Mongo is up"

BACKEND_JAR="$(ls v2-backend/build/libs/*.jar | head -1)"
REC_JAR="$(ls v2-recommendation/build/libs/*.jar | head -1)"

echo "==> Starting V2 backend ($BACKEND_JAR)"
SPRING_PROFILES_ACTIVE=replit "$JAVA" -jar "$BACKEND_JAR" &
BACKEND_PID=$!

echo "==> Starting V2 recommendation ($REC_JAR)"
SPRING_PROFILES_ACTIVE=replit "$JAVA" -jar "$REC_JAR" &
REC_PID=$!

echo "==> Starting mock IAM (v2-iam-companion)"
(cd v2-iam-companion && PORT=10001 node dist/index.js) &
IAM_PID=$!

echo "==> Starting v2-gateway"
GATEWAY_PORT=5000 NODE_ENV=production node v2-gateway/dist/index.js &
GATEWAY_PID=$!

# Supervise: keep the shell as PID-holder so the trap actually fires (an exec
# would replace it and orphan the daemons), and exit as soon as any critical
# process dies so the VM supervisor restarts the whole stack cleanly.
trap 'kill $INFRA_PID $BACKEND_PID $REC_PID $IAM_PID $GATEWAY_PID 2>/dev/null || true' EXIT INT TERM
wait -n $INFRA_PID $BACKEND_PID $REC_PID $IAM_PID $GATEWAY_PID
echo "FATAL: a critical process exited — shutting down the stack" >&2
exit 1
