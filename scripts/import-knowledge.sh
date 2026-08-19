#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 1; }

echo "Importing the eight bundled PDF documents..."
response="$(curl -fsS -X POST http://127.0.0.1:8087/vector/milvus/documents/import-local)"
[[ "$(jq -r '.code' <<<"$response")" == "200" ]] || {
  echo "$response" | jq .
  exit 1
}

jq -e '.data | length == 8' <<<"$response" >/dev/null || {
  echo "Expected 8 bundled documents" >&2
  echo "$response" | jq .
  exit 1
}

jq '{message, documents: (.data | length), chunks: ([.data[].chunkCount] | add)}' <<<"$response"
