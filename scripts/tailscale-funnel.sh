#!/usr/bin/env bash
set -euo pipefail

# The macOS app binary otherwise may interpret a non-interactive invocation as
# a request to launch the GUI instead of running the CLI.
export TAILSCALE_BE_CLI=1

AGENT_LOCAL_PORT="${TAILSCALE_AGENT_LOCAL_PORT:-8087}"
INSIGHT_LOCAL_PORT="${TAILSCALE_INSIGHT_LOCAL_PORT:-3000}"
PUBLIC_PORT="${TAILSCALE_PUBLIC_PORT:-${TAILSCALE_AGENT_PUBLIC_PORT:-443}}"
INSIGHT_PUBLIC_PATH="${TAILSCALE_INSIGHT_PUBLIC_PATH:-/insight}"
INSIGHT_API_PUBLIC_PATH="${TAILSCALE_INSIGHT_API_PUBLIC_PATH:-/api}"
LEGACY_INSIGHT_PUBLIC_PORT=8443

usage() {
  cat <<'EOF'
Usage: ./scripts/tailscale-funnel.sh <start|status|stop>

  start   Publish mall-order-agent and AgentInsight through Tailscale Funnel
  status  Show local service state, Funnel configuration, and public URLs
  stop    Remove only the 443 path mappings managed by this script

Optional environment variables:
  TAILSCALE_BIN
  TAILSCALE_AGENT_LOCAL_PORT       (default: 8087)
  TAILSCALE_INSIGHT_LOCAL_PORT     (default: 3000)
  TAILSCALE_PUBLIC_PORT            (default: 443)
  TAILSCALE_INSIGHT_PUBLIC_PATH    (default: /insight)
  TAILSCALE_INSIGHT_API_PUBLIC_PATH (default: /api)
EOF
}

find_tailscale() {
  if [[ -n "${TAILSCALE_BIN:-}" ]]; then
    [[ -x "$TAILSCALE_BIN" ]] || {
      echo "TAILSCALE_BIN is not executable: $TAILSCALE_BIN" >&2
      exit 1
    }
    printf '%s\n' "$TAILSCALE_BIN"
    return
  fi

  if command -v tailscale >/dev/null 2>&1; then
    command -v tailscale
    return
  fi

  local candidate
  for candidate in \
    /Applications/Tailscale.app/Contents/MacOS/Tailscale \
    /usr/local/bin/tailscale \
    /opt/homebrew/bin/tailscale; do
    if [[ -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done

  echo "Tailscale CLI was not found. Install Tailscale and enable its CLI integration." >&2
  echo "See: https://tailscale.com/docs/install/mac" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "$1 is required" >&2
    exit 1
  }
}

tailscale_status_json() {
  "$TAILSCALE" status --json
}

require_connected() {
  local status state
  status="$(tailscale_status_json)" || {
    echo "Unable to read Tailscale status. Open Tailscale and sign in first." >&2
    exit 1
  }
  state="$(jq -r '.BackendState // empty' <<<"$status")"
  [[ "$state" == "Running" ]] || {
    echo "Tailscale is not connected (state=${state:-unknown}). Open Tailscale and sign in first." >&2
    exit 1
  }
}

funnel_status_json() {
  local config
  if ! config="$("$TAILSCALE" funnel status --json 2>/dev/null)"; then
    config='{}'
  fi
  [[ -n "$config" ]] || config='{}'
  jq -e . >/dev/null <<<"$config" || {
    echo "Tailscale returned an invalid Funnel configuration." >&2
    exit 1
  }
  printf '%s\n' "$config"
}

mapping_exists() {
  local config="$1" public_port="$2" public_path="$3" target="$4"
  jq -e --arg port "$public_port" --arg path "$public_path" --arg target "$target" '
    [
      (.Web // {}) | to_entries[]
      | select(.key | endswith(":" + $port))
      | (.value.Handlers // {})[$path].Proxy // empty
    ] | any(. == $target)
  ' >/dev/null <<<"$config"
}

public_path_in_use() {
  local config="$1" public_port="$2" public_path="$3"
  jq -e --arg port "$public_port" --arg path "$public_path" '
    [
      (.Web // {}) | to_entries[]
      | select(.key | endswith(":" + $port))
      | (.value.Handlers // {})[$path] // empty
    ] | length > 0
  ' >/dev/null <<<"$config"
}

ensure_mapping_available() {
  local config="$1" public_port="$2" public_path="$3" target="$4" label="$5"
  if public_path_in_use "$config" "$public_port" "$public_path" \
      && ! mapping_exists "$config" "$public_port" "$public_path" "$target"; then
    echo "Refusing to replace an existing Tailscale mapping on HTTPS port $public_port path $public_path." >&2
    echo "Inspect it with: $TAILSCALE funnel status" >&2
    echo "Expected $label target: $target" >&2
    exit 1
  fi
}

enable_mapping() {
  local public_port="$1" public_path="$2" target="$3"
  if [[ "$public_path" == "/" ]]; then
    "$TAILSCALE" funnel --bg --https="$public_port" "$target"
  else
    "$TAILSCALE" funnel --bg --https="$public_port" --set-path="$public_path" "$target"
  fi
}

disable_mapping() {
  local public_port="$1" public_path="$2"
  "$TAILSCALE" funnel --https="$public_port" --set-path="$public_path" off
}

validate_public_paths() {
  local value
  for value in "$INSIGHT_PUBLIC_PATH" "$INSIGHT_API_PUBLIC_PATH"; do
    [[ "$value" == /* && "$value" != "/" ]] || {
      echo "Public paths must start with / and cannot be the root path: $value" >&2
      exit 1
    }
  done
  [[ "$INSIGHT_PUBLIC_PATH" != "$INSIGHT_API_PUBLIC_PATH" ]] || {
    echo "AgentInsight UI and API public paths must be different." >&2
    exit 1
  }
}

local_service_ready() {
  nc -z 127.0.0.1 "$1" >/dev/null 2>&1
}

require_local_services() {
  local failed=0
  if ! local_service_ready "$AGENT_LOCAL_PORT"; then
    echo "mall-order-agent is not listening on 127.0.0.1:$AGENT_LOCAL_PORT" >&2
    failed=1
  fi
  if ! local_service_ready "$INSIGHT_LOCAL_PORT"; then
    echo "AgentInsight is not listening on 127.0.0.1:$INSIGHT_LOCAL_PORT" >&2
    failed=1
  fi
  [[ "$failed" -eq 0 ]] || exit 1
}

device_dns_name() {
  local status
  status="$(tailscale_status_json)"
  jq -r '.Self.DNSName // empty | rtrimstr(".")' <<<"$status"
}

print_public_urls() {
  local dns_name
  dns_name="$(device_dns_name)"
  if [[ -z "$dns_name" ]]; then
    echo "Public URLs are unavailable because this node has no MagicDNS name." >&2
    return 1
  fi
  echo "mall_order_agent_url=https://$dns_name"
  echo "agent_insight_url=https://$dns_name$INSIGHT_PUBLIC_PATH/"
}

start_funnels() {
  require_connected
  require_local_services

  local config agent_target insight_origin insight_target insight_api_target
  local agent_preexisting=0 insight_preexisting=0 insight_legacy_mapping=0
  config="$(funnel_status_json)"
  agent_target="http://127.0.0.1:$AGENT_LOCAL_PORT"
  insight_origin="http://127.0.0.1:$INSIGHT_LOCAL_PORT"
  insight_target="$insight_origin$INSIGHT_PUBLIC_PATH"
  insight_api_target="$insight_origin/api"

  ensure_mapping_available "$config" "$PUBLIC_PORT" "/" "$agent_target" "mall-order-agent"
  if mapping_exists "$config" "$PUBLIC_PORT" "$INSIGHT_PUBLIC_PATH" "$insight_origin"; then
    insight_legacy_mapping=1
  else
    ensure_mapping_available "$config" "$PUBLIC_PORT" "$INSIGHT_PUBLIC_PATH" "$insight_target" "AgentInsight UI"
  fi
  ensure_mapping_available "$config" "$PUBLIC_PORT" "$INSIGHT_API_PUBLIC_PATH" "$insight_api_target" "AgentInsight API"
  mapping_exists "$config" "$PUBLIC_PORT" "/" "$agent_target" && agent_preexisting=1
  mapping_exists "$config" "$PUBLIC_PORT" "$INSIGHT_PUBLIC_PATH" "$insight_target" && insight_preexisting=1

  enable_mapping "$PUBLIC_PORT" "/" "$agent_target"
  if ! enable_mapping "$PUBLIC_PORT" "$INSIGHT_PUBLIC_PATH" "$insight_target"; then
    if [[ "$agent_preexisting" -eq 0 ]]; then
      disable_mapping "$PUBLIC_PORT" "/" || true
    fi
    echo "AgentInsight UI Funnel failed; newly created mappings were rolled back." >&2
    exit 1
  fi
  if ! enable_mapping "$PUBLIC_PORT" "$INSIGHT_API_PUBLIC_PATH" "$insight_api_target"; then
    if [[ "$insight_legacy_mapping" -eq 1 ]]; then
      enable_mapping "$PUBLIC_PORT" "$INSIGHT_PUBLIC_PATH" "$insight_origin" || true
    elif [[ "$insight_preexisting" -eq 0 ]]; then
      disable_mapping "$PUBLIC_PORT" "$INSIGHT_PUBLIC_PATH" || true
    fi
    if [[ "$agent_preexisting" -eq 0 ]]; then
      disable_mapping "$PUBLIC_PORT" "/" || true
    fi
    echo "AgentInsight API Funnel failed; newly created mappings were rolled back." >&2
    exit 1
  fi

  if mapping_exists "$config" "$LEGACY_INSIGHT_PUBLIC_PORT" "/" "$insight_origin"; then
    disable_mapping "$LEGACY_INSIGHT_PUBLIC_PORT" "/"
    echo "Removed legacy AgentInsight Funnel on HTTPS port $LEGACY_INSIGHT_PUBLIC_PORT."
  fi

  echo "Tailscale Funnel is running."
  print_public_urls
  "$TAILSCALE" funnel status
}

show_status() {
  require_connected
  local_service_ready "$AGENT_LOCAL_PORT" \
    && echo "local_port=$AGENT_LOCAL_PORT service=mall-order-agent state=up" \
    || echo "local_port=$AGENT_LOCAL_PORT service=mall-order-agent state=down"
  local_service_ready "$INSIGHT_LOCAL_PORT" \
    && echo "local_port=$INSIGHT_LOCAL_PORT service=agent-insight state=up" \
    || echo "local_port=$INSIGHT_LOCAL_PORT service=agent-insight state=down"
  print_public_urls || true
  "$TAILSCALE" funnel status
}

stop_funnels() {
  require_connected
  local config agent_target insight_origin insight_target insight_api_target stopped=0
  config="$(funnel_status_json)"
  agent_target="http://127.0.0.1:$AGENT_LOCAL_PORT"
  insight_origin="http://127.0.0.1:$INSIGHT_LOCAL_PORT"
  insight_target="$insight_origin$INSIGHT_PUBLIC_PATH"
  insight_api_target="$insight_origin/api"

  if mapping_exists "$config" "$PUBLIC_PORT" "/" "$agent_target"; then
    disable_mapping "$PUBLIC_PORT" "/"
    echo "stopped mall-order-agent Funnel on HTTPS port $PUBLIC_PORT path /"
    stopped=1
  fi
  if mapping_exists "$config" "$PUBLIC_PORT" "$INSIGHT_PUBLIC_PATH" "$insight_target"; then
    disable_mapping "$PUBLIC_PORT" "$INSIGHT_PUBLIC_PATH"
    echo "stopped AgentInsight UI Funnel on HTTPS port $PUBLIC_PORT path $INSIGHT_PUBLIC_PATH"
    stopped=1
  fi
  if mapping_exists "$config" "$PUBLIC_PORT" "$INSIGHT_API_PUBLIC_PATH" "$insight_api_target"; then
    disable_mapping "$PUBLIC_PORT" "$INSIGHT_API_PUBLIC_PATH"
    echo "stopped AgentInsight API Funnel on HTTPS port $PUBLIC_PORT path $INSIGHT_API_PUBLIC_PATH"
    stopped=1
  fi
  [[ "$stopped" -eq 1 ]] || echo "No managed Funnel mappings were running."
}

case "${1:-}" in
  start|status|stop)
    require_command jq
    require_command nc
    validate_public_paths
    TAILSCALE="$(find_tailscale)"
    case "$1" in
      start) start_funnels ;;
      status) show_status ;;
      stop) stop_funnels ;;
    esac
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
