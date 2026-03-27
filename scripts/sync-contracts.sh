#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_SCHEMA="$ROOT_DIR/contracts/graphql/gateway/schema.graphqls"
TARGET_SCHEMA="$ROOT_DIR/services/api-gateway/src/main/resources/graphql/schema.graphqls"

usage() {
  cat <<'EOF'
Usage: bash scripts/sync-contracts.sh [--check]

Options:
  --check   Verify runtime schema matches contracts source (no file writes).
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ ! -f "$SOURCE_SCHEMA" ]]; then
  echo "ERROR: Source contract schema not found: $SOURCE_SCHEMA" >&2
  exit 1
fi

if [[ "${1:-}" == "--check" ]]; then
  if cmp -s "$SOURCE_SCHEMA" "$TARGET_SCHEMA"; then
    echo "OK: GraphQL schema is in sync."
    exit 0
  fi

  echo "ERROR: GraphQL schema is out of sync." >&2
  echo "Run: bash scripts/sync-contracts.sh" >&2
  exit 2
fi

if [[ -f "$TARGET_SCHEMA" ]] && cmp -s "$SOURCE_SCHEMA" "$TARGET_SCHEMA"; then
  echo "No changes. GraphQL schema already in sync."
  exit 0
fi

mkdir -p "$(dirname "$TARGET_SCHEMA")"
cp "$SOURCE_SCHEMA" "$TARGET_SCHEMA"
echo "Synced GraphQL contract schema to API gateway runtime schema."

