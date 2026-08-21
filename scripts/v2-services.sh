#!/usr/bin/env bash
# Local infrastructure for the V2 backend stack: MongoDB, Redis, MinIO.
# (The shipped configs point at localhost for Redis/MinIO; Mongo runs locally
# because outbound port 27017 to the staging Atlas cluster is blocked here.)
set -euo pipefail

# Persist under the workspace so plan data survives container recycles
# (/tmp is wiped whenever the repl container restarts). .data/ is git-ignored.
DATA="$(cd "$(dirname "$0")/.." && pwd)/.data/v2infra"
mkdir -p "$DATA/mongo" "$DATA/redis" "$DATA/minio"

redis-server --port 6379 --dir "$DATA/redis" --daemonize no &
REDIS_PID=$!

MINIO_ROOT_USER=admin MINIO_ROOT_PASSWORD=admin123 \
  minio server "$DATA/minio" --address 127.0.0.1:9000 --console-address 127.0.0.1:9001 &
MINIO_PID=$!

# Create the buckets the backend expects, once MinIO is up.
(
  for i in $(seq 1 30); do
    if curl -sf http://127.0.0.1:9000/minio/health/live > /dev/null; then break; fi
    sleep 1
  done
  export MC_HOST_local=http://admin:admin123@127.0.0.1:9000
  mc mb --ignore-existing local/mw-planner-local local/brand-logo-bucket 2>/dev/null || true
) &

# RabbitMQ (apps expect admin/admin123 on localhost:5672)
export RABBITMQ_MNESIA_BASE="$DATA/rabbitmq/mnesia"
export RABBITMQ_LOG_BASE="$DATA/rabbitmq/log"
export RABBITMQ_PID_FILE="$DATA/rabbitmq/rabbit.pid"
export RABBITMQ_NODENAME="rabbit@localhost"
mkdir -p "$RABBITMQ_MNESIA_BASE" "$RABBITMQ_LOG_BASE"
rabbitmq-server &
RABBIT_PID=$!
(
  rabbitmqctl wait "$RABBITMQ_PID_FILE" --timeout 90 || exit 0
  rabbitmqctl add_user admin admin123 2>/dev/null || true
  rabbitmqctl set_user_tags admin administrator 2>/dev/null || true
  rabbitmqctl set_permissions -p / admin '.*' '.*' '.*' 2>/dev/null || true
) &

trap 'kill $REDIS_PID $MINIO_PID $RABBIT_PID 2>/dev/null || true' EXIT

exec mongod --dbpath "$DATA/mongo" --port 27017 --bind_ip 127.0.0.1
