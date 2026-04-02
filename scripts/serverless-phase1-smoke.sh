#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_PATH="${TEMPLATE_PATH:-$ROOT_DIR/serverless/phase1/template.yaml}"
EVENT_PATH="${EVENT_PATH:-$ROOT_DIR/serverless/phase1/events/resume-parse-request.json}"
FUNCTION_NAME="${FUNCTION_NAME:-ResumeParserFunction}"
SKIP_LOCAL_INVOKE="${SKIP_LOCAL_INVOKE:-false}"

status_gradle="FAIL"
status_validate="FAIL"
status_invoke="SKIPPED"

run_step() {
  local label="$1"
  shift
  echo "==> $label"
  "$@"
}

print_summary() {
  echo
  echo "================ SERVERLESS PHASE-1 SMOKE SUMMARY ================"
  echo "gradle_package_zip : $status_gradle"
  echo "sam_validate       : $status_validate"
  echo "sam_local_invoke   : $status_invoke"
  echo "=================================================================="
}

cleanup() {
  local exit_code=$?
  if [[ $exit_code -ne 0 ]]; then
    print_summary
  fi
}
trap cleanup EXIT

cd "$ROOT_DIR"

run_step "Packaging Lambda zip via Gradle" \
  ./gradlew --no-daemon :serverless:phase1:functions:resume-parser-lambda:packageLambdaZip
status_gradle="PASS"

run_step "Validating SAM template" sam validate -t "$TEMPLATE_PATH"
status_validate="PASS"

if [[ "$SKIP_LOCAL_INVOKE" == "true" ]]; then
  status_invoke="SKIPPED"
  print_summary
  exit 0
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not running. Start Docker Desktop or set SKIP_LOCAL_INVOKE=true."
  status_invoke="FAIL"
  exit 1
fi

status_invoke="FAIL"
run_step "Invoking Lambda locally with SAM" \
  sam local invoke "$FUNCTION_NAME" -t "$TEMPLATE_PATH" -e "$EVENT_PATH"
status_invoke="PASS"

print_summary
