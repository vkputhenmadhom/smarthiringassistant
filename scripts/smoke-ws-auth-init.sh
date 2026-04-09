#!/usr/bin/env bash
set -euo pipefail

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8000}"
GRAPHQL_WS_URL="${GRAPHQL_WS_URL:-${GATEWAY_BASE_URL/http:\/\//ws://}/graphql-ws}"
AUTH_TOKEN="${AUTH_TOKEN:-}"
USER_ID="${USER_ID:-}"
TIMEOUT_MS="${TIMEOUT_MS:-3500}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "$AUTH_TOKEN" ]]; then
  echo "Missing AUTH_TOKEN environment variable"
  echo "Example: AUTH_TOKEN=<jwt> USER_ID=<id> $0"
  exit 1
fi

if [[ -z "$USER_ID" ]]; then
  echo "Missing USER_ID environment variable"
  echo "Example: AUTH_TOKEN=<jwt> USER_ID=<id> $0"
  exit 1
fi

if [[ "$GRAPHQL_WS_URL" == https://* ]]; then
  GRAPHQL_WS_URL="wss://${GRAPHQL_WS_URL#https://}"
elif [[ "$GRAPHQL_WS_URL" == http://* ]]; then
  GRAPHQL_WS_URL="ws://${GRAPHQL_WS_URL#http://}"
fi

if [[ ! -f "$SCRIPT_DIR/node_modules/ws/package.json" ]]; then
  echo "Installing script dependencies (ws) in $SCRIPT_DIR ..."
  npm --prefix "$SCRIPT_DIR" install --silent
fi

GRAPHQL_WS_URL="$GRAPHQL_WS_URL" \
AUTH_TOKEN="$AUTH_TOKEN" \
USER_ID="$USER_ID" \
TIMEOUT_MS="$TIMEOUT_MS" \
node "$SCRIPT_DIR/smoke-ws-auth-init.js"

