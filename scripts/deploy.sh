#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIRECTORY
PROJECT_DIRECTORY="$(cd -- "$SCRIPT_DIRECTORY/.." && pwd)"
readonly PROJECT_DIRECTORY
readonly COMPOSE_FILE="$PROJECT_DIRECTORY/compose.production.yml"
readonly ENV_FILE="${DEPLOY_ENV_FILE:-$PROJECT_DIRECTORY/.env.production}"
readonly WAIT_TIMEOUT="${DEPLOY_TIMEOUT_SECONDS:-180}"

usage() {
    cat <<'EOF'
Usage: ./scripts/deploy.sh [IMAGE_TAG]

Pull and deploy the production backend and web images from the registry.
IMAGE_TAG overrides APP_VERSION from .env.production for this deployment.

Examples:
  ./scripts/deploy.sh main
  ./scripts/deploy.sh v0.2.0
  ./scripts/deploy.sh sha-a1b2c3d

Environment:
  DEPLOY_ENV_FILE         Production env file (default: .env.production)
  DEPLOY_TIMEOUT_SECONDS  Health-check timeout (default: 180)
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
fi

if [[ $# -gt 1 ]]; then
    usage >&2
    exit 2
fi

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Production environment file not found: $ENV_FILE" >&2
    echo "Copy .env.production.example to .env.production and fill in every placeholder." >&2
    exit 1
fi

if [[ -n "${1:-}" ]]; then
    if [[ ! "$1" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
        echo "Invalid container image tag: $1" >&2
        exit 2
    fi
    export APP_VERSION="$1"
fi

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

deployment_failed() {
    local exit_code=$?
    trap - ERR
    echo "Deployment failed. Current service state and recent logs:" >&2
    "${compose[@]}" ps >&2 || true
    "${compose[@]}" logs --tail=100 backend web >&2 || true
    exit "$exit_code"
}
trap deployment_failed ERR

docker info >/dev/null
"${compose[@]}" config --quiet

echo "Pulling Macrosaurus images${APP_VERSION:+ tagged $APP_VERSION}..."
"${compose[@]}" pull

echo "Starting Macrosaurus and waiting for health checks..."
"${compose[@]}" up -d --no-build --remove-orphans --wait --wait-timeout "$WAIT_TIMEOUT"
"${compose[@]}" ps

echo "Deployment completed successfully."
