#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-}"
if [[ -z "${SERVICE_NAME}" && -n "${DEPLOYMENT_GROUP_NAME:-}" ]]; then
  SERVICE_NAME="${DEPLOYMENT_GROUP_NAME#sha-}"
  SERVICE_NAME="${SERVICE_NAME%-staging}"
  SERVICE_NAME="${SERVICE_NAME%-production}"
fi

if [[ -z "${SERVICE_NAME}" ]]; then
  echo "SERVICE_NAME is not set and could not be derived from DEPLOYMENT_GROUP_NAME" >&2
  exit 1
fi

UNIT_NAME="smart-hiring-${SERVICE_NAME}.service"

if systemctl list-unit-files | grep -q "^${UNIT_NAME}"; then
  systemctl stop "${UNIT_NAME}"
else
  echo "No systemd unit ${UNIT_NAME} found. Skipping stop."
fi

