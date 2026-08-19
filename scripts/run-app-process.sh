#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="$1"
APP_NAME="$2"

cd "$ROOT_DIR"
echo "$$" >"run/$APP_NAME.pid"
exec java -jar "$JAR_PATH" \
  "--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-demo}" >>"logs/$APP_NAME.log" 2>&1
