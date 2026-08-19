#!/usr/bin/env bash
set -euo pipefail

command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 1; }

API_URL="${AGENT_API_URL:-http://127.0.0.1:8087}"
RUN_ID="$(date +%s)"
USERNAME="${SMOKE_USERNAME:-customer.zhangwei}"
PASSWORD="${SMOKE_PASSWORD:-${DEMO_INITIAL_PASSWORD:-DemoLogin@2026!}}"
ORDER_SERVICE_TOKEN="${MALL_ORDER_SERVICE_TOKEN:-local-order-service-token-change-me}"
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

csrf_json="$(curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" "$API_URL/auth/csrf")"
csrf_header="$(jq -r '.data.headerName' <<<"$csrf_json")"
csrf_token="$(jq -r '.data.token' <<<"$csrf_json")"
curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" -X POST "$API_URL/auth/login" \
  -H "$csrf_header: $csrf_token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "username=$USERNAME" --data-urlencode "password=$PASSWORD" >/dev/null

refresh_csrf() {
  csrf_json="$(curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" "$API_URL/auth/csrf")"
  csrf_header="$(jq -r '.data.headerName' <<<"$csrf_json")"
  csrf_token="$(jq -r '.data.token' <<<"$csrf_json")"
}

ask() {
  local query="$1"
  local conversation_id="$2"
  refresh_csrf
  curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" -X POST "$API_URL/agent/order/ask" \
    -H 'Content-Type: application/json' -H "$csrf_header: $csrf_token" \
    -d "$(jq -nc --arg query "$query" --arg conversationId "$conversation_id" \
      '{query: $query, conversationId: $conversationId, topK: 5}')"
}

echo "[1/3] Order query"
order_response="$(ask '查询订单 ORD20260810001 的状态' "smoke-order-$RUN_ID")"
jq -e '.code == 200 and .data.planStrategy == "ORDER_QUERY" and .data.grounded == true' \
  <<<"$order_response" >/dev/null
echo "  PASS: $(jq -r '.data.answer' <<<"$order_response" | head -n 1)"

echo "[2/3] RAG question"
rag_response="$(ask '退款规则是什么' "smoke-rag-$RUN_ID")"
jq -e '.code == 200 and .data.planStrategy == "RAG_QA" and .data.grounded == true' \
  <<<"$rag_response" >/dev/null
echo "  PASS: traceId=$(jq -r '.data.traceId' <<<"$rag_response")"

echo "[3/3] Refund interruption"
sensitive_response="$(ask '帮我退款 ORD20260810001' "smoke-refund-$RUN_ID")"
jq -e '.code == 200 and .data.planStrategy == "DANGEROUS_ORDER_OP" and
       .data.interrupted == true and .data.awaitingUserConfirm == true' \
  <<<"$sensitive_response" >/dev/null
thread_id="$(jq -r '.data.threadId' <<<"$sensitive_response")"
echo "  PASS: waiting for confirmation, threadId=$thread_id"

if [[ "${CONFIRM_SENSITIVE:-false}" == "true" ]]; then
  echo "[optional] Confirming the refund and verifying database-backed ticket creation"
  refresh_csrf
  resume_response="$(curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" -X POST "$API_URL/agent/order/resume" \
    -H 'Content-Type: application/json' -H "$csrf_header: $csrf_token" \
    -d "$(jq -nc --arg threadId "$thread_id" '{threadId: $threadId, approved: true}')")"
  jq -e '.code == 200 and .data.grounded == true and (.data.answer | contains("工单号"))' \
    <<<"$resume_response" >/dev/null
  tickets="$(curl -fsS -H "Authorization: Bearer $ORDER_SERVICE_TOKEN" \
    http://127.0.0.1:8081/orders/after-sales/user/USER1001)"
  jq -e 'any(.[]; .orderId == "ORD20260810001" and .operationType == "退款")' \
    <<<"$tickets" >/dev/null
  echo "  PASS: persisted refund ticket verified"
fi

echo "All smoke scenarios passed."
