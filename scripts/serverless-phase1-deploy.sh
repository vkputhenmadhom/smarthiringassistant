#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_PATH="${TEMPLATE_PATH:-$ROOT_DIR/serverless/phase1/template.yaml}"
STACK_NAME="${STACK_NAME:-smart-hiring-phase1-deploy}"
CONFIG_ENV="${CONFIG_ENV:-default}"
AWS_REGION="${AWS_REGION:-}"
AWS_PROFILE="${AWS_PROFILE:-}"
SAMCONFIG_PATH="$ROOT_DIR/samconfig.toml"

MODE="auto"

usage() {
  cat <<'EOF'
Usage: scripts/serverless-phase1-deploy.sh [--guided/--non-guided] [--stack-name NAME] [--region REGION] [--profile PROFILE] [--config-env ENV]

Modes:
  --guided       Force interactive first-time deployment
  --non-guided   Force non-interactive deploy using samconfig.toml
  (default)      Auto: guided if samconfig.toml is missing, otherwise non-guided

Examples:
  ./scripts/serverless-phase1-deploy.sh
  ./scripts/serverless-phase1-deploy.sh --guided --stack-name smart-hiring-phase1-deploy --region us-east-1
  ./scripts/serverless-phase1-deploy.sh --non-guided --config-env default
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --guided)
      MODE="guided"
      ;;
    --non-guided)
      MODE="non-guided"
      ;;
    --stack-name)
      STACK_NAME="$2"
      shift
      ;;
    --region)
      AWS_REGION="$2"
      shift
      ;;
    --profile)
      AWS_PROFILE="$2"
      shift
      ;;
    --config-env)
      CONFIG_ENV="$2"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1"
      usage
      exit 1
      ;;
  esac
  shift
done

if [[ -n "$AWS_REGION" ]]; then
  export AWS_REGION
fi
if [[ -n "$AWS_PROFILE" ]]; then
  export AWS_PROFILE
fi

cd "$ROOT_DIR"

echo "==> Packaging Lambda zip"
./gradlew --no-daemon :serverless:phase1:functions:resume-parser-lambda:packageLambdaZip

if [[ "$MODE" == "auto" ]]; then
  if [[ -f "$SAMCONFIG_PATH" ]]; then
    MODE="non-guided"
  else
    MODE="guided"
  fi
fi

if [[ "$MODE" == "guided" ]]; then
  echo "==> Running guided deploy"
  sam deploy --guided --template-file "$TEMPLATE_PATH" --stack-name "$STACK_NAME"
  exit 0
fi

if [[ ! -f "$SAMCONFIG_PATH" ]]; then
  echo "samconfig.toml not found; run guided deploy first:"
  echo "  ./scripts/serverless-phase1-deploy.sh --guided --stack-name $STACK_NAME"
  exit 1
fi

echo "==> Running non-guided deploy (config-env=$CONFIG_ENV)"
sam deploy \
  --template-file "$TEMPLATE_PATH" \
  --stack-name "$STACK_NAME" \
  --config-env "$CONFIG_ENV" \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --no-confirm-changeset \
  --no-fail-on-empty-changeset
