#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi

if [[ -z "${DASHSCOPE_API_KEY:-}" || "${DASHSCOPE_API_KEY}" == "replace-with-your-key" ]]; then
  echo "Set DASHSCOPE_API_KEY in .env before starting the applications." >&2
  exit 1
fi

mkdir -p logs run

SCREEN_PREFIX="ecommerce-order-agent-platform"

for port in 8081 8082 8089 8087; do
  if nc -z 127.0.0.1 "$port" >/dev/null 2>&1; then
    echo "Port $port is already in use. Stop the existing application before running this script." >&2
    exit 1
  fi
done

cleanup_on_error() {
  status=$?
  echo "Application startup failed; stopping processes started by this project." >&2
  "$ROOT_DIR/scripts/stop-apps.sh" || true
  exit "$status"
}

trap cleanup_on_error ERR INT TERM

for pid_file in run/*.pid; do
  [[ -e "$pid_file" ]] || continue
  pid="$(<"$pid_file")"
  if kill -0 "$pid" >/dev/null 2>&1; then
    echo "An application recorded in $pid_file is already running (pid=$pid)." >&2
    exit 1
  fi
  rm -f "$pid_file"
done

echo "Building executable application jars..."
if command -v mvn >/dev/null 2>&1; then
  mvn -q -DskipTests package
else
  ./mvnw -q -DskipTests package
fi

start_jar() {
  local jar="$1"
  local name="$2"
  local pid

  : >"logs/$name.log"
  if [[ "${USE_SCREEN:-auto}" != "false" ]] && command -v screen >/dev/null 2>&1; then
    screen -dmS "$SCREEN_PREFIX-$name" \
      bash "$ROOT_DIR/scripts/run-app-process.sh" "$jar" "$name"
    for _ in $(seq 1 50); do
      [[ -s "run/$name.pid" ]] && break
      sleep 0.1
    done
    [[ -s "run/$name.pid" ]] || {
      echo "Failed to record PID for $name" >&2
      return 1
    }
    pid="$(<"run/$name.pid")"
  else
    nohup java -jar "$jar" \
      "--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-demo}" >"logs/$name.log" 2>&1 &
    pid=$!
    echo "$pid" >"run/$name.pid"
  fi

  kill -0 "$pid" >/dev/null 2>&1 || {
    echo "$name exited before readiness checks; inspect logs/$name.log" >&2
    return 1
  }
  echo "  started $name (pid=$pid)"
}

wait_http() {
  local name="$1"
  local url="$2"
  local pid

  pid="$(<"run/$name.pid")"
  for _ in $(seq 1 90); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      echo "$name exited before becoming ready; inspect logs/$name.log" >&2
      return 1
    fi
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "  ready: $name"
      return 0
    fi
    sleep 2
  done
  echo "$name did not become ready; inspect logs/$name.log" >&2
  return 1
}

start_jar mall-order/target/mall-order-0.0.1-SNAPSHOT.jar mall-order
wait_http mall-order http://127.0.0.1:8081/orders/health

start_jar mall-order-cmp-server/target/mall-order-cmp-server-0.0.1.jar mall-order-cmp-server
cmp_pid="$(<run/mall-order-cmp-server.pid)"
for _ in $(seq 1 90); do
  if ! kill -0 "$cmp_pid" >/dev/null 2>&1; then
    echo "mall-order-cmp-server exited before becoming ready; inspect logs/mall-order-cmp-server.log" >&2
    exit 1
  fi
  nc -z 127.0.0.1 8082 >/dev/null 2>&1 && break
  sleep 2
done
nc -z 127.0.0.1 8082 >/dev/null 2>&1 || { echo "mall-order-cmp-server did not become ready" >&2; exit 1; }
echo "  ready: mall-order-cmp-server"

start_jar mall-order-observability/target/mall-order-observability-0.0.1-SNAPSHOT-exec.jar mall-order-observability
wait_http mall-order-observability http://127.0.0.1:8089/observability/traces/health

start_jar mall-order-agent/target/mall-order-agent-0.0.1-SNAPSHOT.jar mall-order-agent
wait_http mall-order-agent http://127.0.0.1:8087/agent/order/health

trap - ERR INT TERM
echo "Applications are ready: http://127.0.0.1:8087"
