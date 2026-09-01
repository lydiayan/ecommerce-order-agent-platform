#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MYSQL_ADMIN_USER="${MYSQL_ADMIN_USER:-root}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_HOST_PORT:-3306}"

command -v mysql >/dev/null 2>&1 || {
  echo "mysql client is required" >&2
  exit 1
}

read -r -s -p "MySQL ${MYSQL_ADMIN_USER} password: " mysql_admin_password
echo
trap 'unset mysql_admin_password MYSQL_PWD' EXIT

export MYSQL_PWD="$mysql_admin_password"
mysql --protocol=tcp --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_ADMIN_USER" \
  --execute="
    CREATE DATABASE IF NOT EXISTS products CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE DATABASE IF NOT EXISTS agent_memory CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE USER IF NOT EXISTS 'portfolio'@'localhost' IDENTIFIED BY 'portfolio';
    ALTER USER 'portfolio'@'localhost' IDENTIFIED BY 'portfolio';
    CREATE USER IF NOT EXISTS 'portfolio'@'%' IDENTIFIED BY 'portfolio';
    ALTER USER 'portfolio'@'%' IDENTIFIED BY 'portfolio';
    GRANT ALL PRIVILEGES ON products.* TO 'portfolio'@'localhost';
    GRANT ALL PRIVILEGES ON agent_memory.* TO 'portfolio'@'localhost';
    GRANT ALL PRIVILEGES ON products.* TO 'portfolio'@'%';
    GRANT ALL PRIVILEGES ON agent_memory.* TO 'portfolio'@'%';
  "

mysql --protocol=tcp --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_ADMIN_USER" \
  < "$ROOT_DIR/infra/mysql/init/01-schema.sql"

unset MYSQL_PWD mysql_admin_password
MYSQL_PWD=portfolio mysql --protocol=tcp --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  --user=portfolio --execute="SELECT 1" >/dev/null

echo "Local MySQL is ready: products, agent_memory, user portfolio."
