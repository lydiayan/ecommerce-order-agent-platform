#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 1; }

curl -fsS http://127.0.0.1:8087/agent/order/health >/dev/null || {
  echo "mall-order-agent is not available on port 8087" >&2
  exit 1
}

# Document and chunk IDs are stable, so rerunning this import is an idempotent upsert.
"$ROOT_DIR/scripts/import-knowledge.sh"
echo "Demo knowledge setup completed."
