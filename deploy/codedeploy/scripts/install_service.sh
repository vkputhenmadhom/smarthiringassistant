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

INSTALL_DIR="/opt/smart-hiring/${SERVICE_NAME}"
mkdir -p "${INSTALL_DIR}"

# CodeDeploy unpacks the archive under /opt/smart-hiring/incoming.
ARTIFACT_DIR="/opt/smart-hiring/incoming"
cp "${ARTIFACT_DIR}/service.jar" "${INSTALL_DIR}/service.jar"
chown -R root:root "${INSTALL_DIR}"
chmod 0644 "${INSTALL_DIR}/service.jar"

