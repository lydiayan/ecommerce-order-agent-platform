#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SCREEN_PREFIX="ecommerce-order-agent-platform"

for pid_file in run/*.pid; do
  [[ -e "$pid_file" ]] || continue
  pid="$(<"$pid_file")"
  if kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid"
    echo "stopped $(basename "$pid_file" .pid) (pid=$pid)"
  fi
  rm -f "$pid_file"
done

if command -v screen >/dev/null 2>&1; then
  for name in mall-order mall-order-cmp-server mall-order-observability mall-order-agent; do
    screen -S "$SCREEN_PREFIX-$name" -X quit >/dev/null 2>&1 || true
  done
fi
