#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "'$1' is required but was not found in PATH."
}

url_ready() {
  curl -fsS --max-time 3 "$1" >/dev/null 2>&1
}

[[ -f .env ]] || die ".env is missing. Create it with: cp .env.example .env"

set -a
source .env
set +a

APP_URL="http://127.0.0.1:${SERVER_PORT:-8087}"
OPEN_BROWSER="${OPEN_BROWSER:-true}"

for command in docker java mvn curl nc; do
  require_command "$command"
done

[[ -n "${DASHSCOPE_API_KEY:-}" && "${DASHSCOPE_API_KEY}" != "replace-with-your-key" ]] \
  || die "Set DASHSCOPE_API_KEY in .env before starting the applications."

docker info >/dev/null 2>&1 || die "Docker is not running. Start Docker Desktop and retry."

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_HOST_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-portfolio}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-portfolio}"

if command -v mysql >/dev/null 2>&1 \
  && MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=tcp \
    --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
    --execute="SELECT 1" >/dev/null 2>&1; then
  echo "MySQL is ready."
else
  mysql_admin_password="${MYSQL_ADMIN_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
  if [[ -n "$mysql_admin_password" ]]; then
    echo "Initializing local MySQL..."
    printf '%s\n' "$mysql_admin_password" | ./scripts/setup-local-mysql.sh
  else
    die "MySQL is not initialized. Run ./scripts/setup-local-mysql.sh once, or set MYSQL_ADMIN_PASSWORD and retry."
  fi
fi

echo "Starting infrastructure..."
./scripts/dev-up.sh

app_names=(mall-order mall-order-cmp-server mall-order-observability mall-order-agent)
app_ports=(8081 8082 8089 8087)
app_health_urls=(
  "http://127.0.0.1:8081/orders/health"
  "http://127.0.0.1:8082"
  "http://127.0.0.1:8089/observability/traces/health"
  "http://127.0.0.1:${SERVER_PORT:-8087}/agent/order/health"
)

all_ready=true
project_apps_running=false
foreign_ports=()

for i in "${!app_names[@]}"; do
  name="${app_names[$i]}"
  port="${app_ports[$i]}"
  health_url="${app_health_urls[$i]}"

  if [[ "$name" == "mall-order-cmp-server" ]]; then
    nc -z 127.0.0.1 "$port" >/dev/null 2>&1 || all_ready=false
  else
    url_ready "$health_url" || all_ready=false
  fi
  nc -z 127.0.0.1 "$port" >/dev/null 2>&1 || continue

  pid_file="run/$name.pid"
  recorded_pid=""
  [[ -f "$pid_file" ]] && recorded_pid="$(<"$pid_file")"
  listening_pid="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -n 1 || true)"

  if [[ -n "$recorded_pid" && "$recorded_pid" == "$listening_pid" ]]; then
    project_apps_running=true
  else
    foreign_ports+=("$port")
  fi
done

if ((${#foreign_ports[@]} > 0)); then
  die "Port(s) ${foreign_ports[*]} are used by another process. Stop them and retry."
fi

if [[ "$all_ready" == true ]]; then
  echo "Applications are already running."
else
  if [[ "$project_apps_running" == true ]]; then
    echo "Restarting project applications..."
    ./scripts/stop-apps.sh
  fi
  ./scripts/start-apps.sh
fi

echo
echo "Frontend and Agent API: $APP_URL"
echo "Order API:              http://127.0.0.1:8081"
echo "MCP server:             http://127.0.0.1:8082"
echo "Trace service:          http://127.0.0.1:8089"

if [[ "$OPEN_BROWSER" == true && "$(uname -s)" == "Darwin" ]]; then
  open "$APP_URL"
fi
