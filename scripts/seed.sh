#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIRECTORY
PROJECT_DIRECTORY="$(cd -- "$SCRIPT_DIRECTORY/.." && pwd)"
readonly PROJECT_DIRECTORY
readonly COMPOSE_FILE="$PROJECT_DIRECTORY/compose.production.yml"
readonly ENV_FILE="${DEPLOY_ENV_FILE:-$PROJECT_DIRECTORY/.env.production}"
readonly NODE_IMAGE="${SEED_NODE_IMAGE:-node:24-alpine}"

readonly USDA_FOUNDATION_RELEASE="${USDA_FOUNDATION_RELEASE:-2026-04}"
readonly USDA_FOUNDATION_URL="${USDA_FOUNDATION_URL:-https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_json_2026-04-30.zip}"
readonly USDA_SR_LEGACY_RELEASE="${USDA_SR_LEGACY_RELEASE:-2018-04}"
readonly USDA_SR_LEGACY_URL="${USDA_SR_LEGACY_URL:-https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip}"
readonly MATVARETABELLEN_RELEASE="${MATVARETABELLEN_RELEASE:-2026}"

usage() {
    cat <<'EOF'
Usage: MACROSAURUS_TOKEN='your-admin-access-token' ./scripts/seed.sh

Download, normalize, and import the pinned USDA Foundation Foods, USDA SR
Legacy, and Matvaretabellen releases into the running production deployment.
The imports are idempotent when the source releases have not changed.

Environment:
  MACROSAURUS_TOKEN             Supabase access token for a user in ADMIN_USER_IDS (required)
  DEPLOY_ENV_FILE               Production env file (default: .env.production)
  SEED_NODE_IMAGE               Temporary runner image (default: node:24-alpine)
  USDA_FOUNDATION_RELEASE       Foundation release key (default: 2026-04)
  USDA_FOUNDATION_URL           Foundation JSON ZIP download URL
  USDA_SR_LEGACY_RELEASE        SR Legacy release key (default: 2018-04)
  USDA_SR_LEGACY_URL            SR Legacy JSON ZIP download URL
  MATVARETABELLEN_RELEASE       Matvaretabellen release key (default: 2026)
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
fi

if [[ $# -ne 0 ]]; then
    usage >&2
    exit 2
fi

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Production environment file not found: $ENV_FILE" >&2
    echo "Copy .env.production.example to .env.production and fill in every placeholder." >&2
    exit 1
fi

if [[ -z "${MACROSAURUS_TOKEN:-}" ]]; then
    echo "MACROSAURUS_TOKEN is required and must belong to a user listed in ADMIN_USER_IDS." >&2
    exit 1
fi

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

docker info >/dev/null
"${compose[@]}" config --quiet

backend_container="$("${compose[@]}" ps --status running -q backend)"
if [[ -z "$backend_container" ]]; then
    echo "The production backend is not running. Run ./scripts/deploy.sh first." >&2
    exit 1
fi
readonly backend_container

backend_network="$(
    docker inspect --format '{{range $network, $_ := .NetworkSettings.Networks}}{{println $network}}{{end}}' "$backend_container" |
        sed -n '1p'
)"
if [[ -z "$backend_network" ]]; then
    echo "Could not determine the production backend's Docker network." >&2
    exit 1
fi
readonly backend_network

echo "Seeding the production catalog from pinned public datasets..."
docker run --rm --pull missing \
    --network "$backend_network" \
    --mount "type=bind,source=$SCRIPT_DIRECTORY,target=/macrosaurus-scripts,readonly" \
    --env MACROSAURUS_TOKEN \
    --env MACROSAURUS_API_URL=http://backend:8080/api/v1 \
    --env USDA_FOUNDATION_RELEASE="$USDA_FOUNDATION_RELEASE" \
    --env USDA_FOUNDATION_URL="$USDA_FOUNDATION_URL" \
    --env USDA_SR_LEGACY_RELEASE="$USDA_SR_LEGACY_RELEASE" \
    --env USDA_SR_LEGACY_URL="$USDA_SR_LEGACY_URL" \
    --env MATVARETABELLEN_RELEASE="$MATVARETABELLEN_RELEASE" \
    --workdir /tmp/catalog-seed \
    "$NODE_IMAGE" \
    sh -eu -c '
        download_and_extract() {
            archive_name="$1"
            download_url="$2"
            json_name="${download_url##*/}"
            json_name="${json_name%.zip}.json"

            echo "Downloading ${download_url}..."
            wget -q --show-progress -O "${archive_name}.zip" "$download_url"
            if ! unzip -p "${archive_name}.zip" "$json_name" > "${archive_name}.json"; then
                echo "Expected ${json_name} was not found in ${download_url}." >&2
                exit 1
            fi
            test -s "${archive_name}.json"
            rm "${archive_name}.zip"
        }

        prepare_and_import_usda() {
            kind="$1"
            release="$2"
            input="$3"
            output="$4"

            node /macrosaurus-scripts/prepare-catalog-release.mjs "$kind" \
                --release "$release" --input "$input" --output "$output"
            node /macrosaurus-scripts/import-catalog-release.mjs "$output"
            rm "$input" "$output"
        }

        download_and_extract foundation "$USDA_FOUNDATION_URL"
        prepare_and_import_usda usda-foundation "$USDA_FOUNDATION_RELEASE" foundation.json foundation-release.json

        download_and_extract sr-legacy "$USDA_SR_LEGACY_URL"
        prepare_and_import_usda usda-sr-legacy "$USDA_SR_LEGACY_RELEASE" sr-legacy.json sr-legacy-release.json

        node /macrosaurus-scripts/prepare-catalog-release.mjs matvaretabellen \
            --release "$MATVARETABELLEN_RELEASE" --output matvaretabellen-release.json
        node /macrosaurus-scripts/import-catalog-release.mjs matvaretabellen-release.json
    '

echo "Catalog seeding completed successfully."
