#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
docker compose up -d

echo "Waiting for infrastructure ports..."
for target in "${MYSQL_HOST_PORT:-3306}:mysql(local)" \
              "${REDIS_HOST_PORT:-16379}:redis" \
              "${MILVUS_HOST_PORT:-29530}:milvus" \
              "${ATTU_HOST_PORT:-18000}:attu" \
              "${ROCKETMQ_NAMESRV_HOST_PORT:-19876}:rocketmq" \
              "${ELASTICSEARCH_HOST_PORT:-19200}:elasticsearch"; do
  port="${target%%:*}"
  name="${target#*:}"
  for _ in $(seq 1 90); do
    if nc -z 127.0.0.1 "$port" >/dev/null 2>&1; then
      echo "  ready: $name ($port)"
      break
    fi
    sleep 2
  done
  nc -z 127.0.0.1 "$port" >/dev/null 2>&1 || {
    echo "timed out waiting for $name on port $port" >&2
    docker compose ps
    exit 1
  }
done

echo "Infrastructure is ready."
