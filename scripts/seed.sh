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
readonly MATVARETABELLEN_EN_URL="${MATVARETABELLEN_EN_URL:-https://www.matvaretabellen.no/api/en/foods.json}"
readonly MATVARETABELLEN_NB_URL="${MATVARETABELLEN_NB_URL:-https://www.matvaretabellen.no/api/nb/foods.json}"

usage() {
    cat <<'EOF'
Usage: MACROSAURUS_TOKEN='complete-admin-access-token' ./scripts/seed.sh

Download, normalize, and import the pinned USDA Foundation Foods, USDA SR
Legacy, and Matvaretabellen releases into the running production deployment.
The imports are idempotent when the source releases have not changed.

The Ubuntu host uses curl and unzip for downloads and Docker only to run the
existing JavaScript normalizer. Imports go directly to the backend container.

Environment:
  MACROSAURUS_TOKEN             Complete Supabase JWT for a user in ADMIN_USER_IDS (required in production)
  DEPLOY_ENV_FILE               Production env file (default: .env.production)
  SEED_NODE_IMAGE               Normalizer image (default: node:24-alpine)
  SEED_TMPDIR                   Parent directory for temporary files (default: $TMPDIR or /tmp)
  USDA_FOUNDATION_RELEASE       Foundation release key (default: 2026-04)
  USDA_FOUNDATION_URL           Foundation JSON ZIP download URL
  USDA_SR_LEGACY_RELEASE        SR Legacy release key (default: 2018-04)
  USDA_SR_LEGACY_URL            SR Legacy JSON ZIP download URL
  MATVARETABELLEN_RELEASE       Matvaretabellen release key (default: 2026)
  MATVARETABELLEN_EN_URL        English Matvaretabellen JSON URL
  MATVARETABELLEN_NB_URL        Norwegian Bokmal Matvaretabellen JSON URL

Testing/local development:
  MACROSAURUS_API_URL           Import through this API URL instead of the Compose backend
  MACROSAURUS_USER_ID           Development user ID; only accepted with MACROSAURUS_API_URL
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

for required_command in docker curl unzip; do
    if ! command -v "$required_command" >/dev/null 2>&1; then
        echo "Required command not found: $required_command" >&2
        echo "On Ubuntu, install host dependencies with: sudo apt install curl unzip" >&2
        exit 1
    fi
done

if [[ -n "${MACROSAURUS_TOKEN:-}" ]]; then
    if [[ ! "$MACROSAURUS_TOKEN" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]]; then
        echo "MACROSAURUS_TOKEN must be the complete three-part ASCII JWT." >&2
        echo "Do not use a shortened token containing an ellipsis (... or …), a publishable key, or the 'Bearer ' prefix." >&2
        exit 1
    fi
    auth_header="Authorization: Bearer $MACROSAURUS_TOKEN"
elif [[ -n "${MACROSAURUS_API_URL:-}" && -n "${MACROSAURUS_USER_ID:-}" ]]; then
    if [[ ! "$MACROSAURUS_USER_ID" =~ ^[A-Za-z0-9._-]+$ ]]; then
        echo "MACROSAURUS_USER_ID contains unsupported characters." >&2
        exit 1
    fi
    auth_header="X-User-Id: $MACROSAURUS_USER_ID"
else
    echo "MACROSAURUS_TOKEN is required and must belong to a user listed in ADMIN_USER_IDS." >&2
    exit 1
fi
readonly auth_header

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
backend_container=""
api_url="${MACROSAURUS_API_URL:-}"

docker info >/dev/null
if [[ -z "$api_url" ]]; then
    if [[ ! -f "$ENV_FILE" ]]; then
        echo "Production environment file not found: $ENV_FILE" >&2
        echo "Copy .env.production.example to .env.production and fill in every placeholder." >&2
        exit 1
    fi
    "${compose[@]}" config --quiet
    backend_container="$("${compose[@]}" ps --status running -q backend)"
    if [[ -z "$backend_container" ]]; then
        echo "The production backend is not running. Run ./scripts/deploy.sh first." >&2
        exit 1
    fi
else
    api_url="${api_url%/}"
fi
readonly backend_container api_url

seed_temp_parent="${SEED_TMPDIR:-${TMPDIR:-/tmp}}"
seed_work_directory="$(mktemp -d "$seed_temp_parent/macrosaurus-seed.XXXXXX")"
readonly seed_work_directory
chmod 700 "$seed_work_directory"

cleanup() {
    find "$seed_work_directory" -type f -delete 2>/dev/null || true
    rmdir "$seed_work_directory" 2>/dev/null || true
}
trap cleanup EXIT

auth_header_file="$seed_work_directory/auth-header"
readonly auth_header_file
printf '%s\n' "$auth_header" > "$auth_header_file"
chmod 600 "$auth_header_file"

download_file() {
    local url="$1"
    local output="$2"

    echo "Downloading $url..."
    curl --fail --location --retry 3 --retry-delay 2 --show-error \
        --user-agent "MacroSaurus catalog importer" --output "$output" "$url"
}

download_json_archive() {
    local url="$1"
    local archive_name="$2"
    local json_name="$3"
    local output="$4"
    local archive="$seed_work_directory/$archive_name"

    download_file "$url" "$archive"
    if ! unzip -p "$archive" "$json_name" > "$output"; then
        echo "Expected $json_name was not found in $url." >&2
        exit 1
    fi
    if [[ ! -s "$output" ]]; then
        echo "The extracted USDA JSON file is empty: $json_name" >&2
        exit 1
    fi
    rm "$archive"
}

prepare_release() {
    docker run --rm --pull missing \
        --mount "type=bind,source=$SCRIPT_DIRECTORY,target=/macrosaurus-scripts,readonly" \
        --mount "type=bind,source=$seed_work_directory,target=/work" \
        --workdir /work \
        "$NODE_IMAGE" \
        node /macrosaurus-scripts/prepare-catalog-release.mjs "$@"
}

import_release() {
    local release_file="$1"

    echo "Importing $(basename "$release_file")..."
    if [[ -n "$api_url" ]]; then
        curl --fail-with-body --silent --show-error \
            --header "@$auth_header_file" \
            --header "Content-Type: application/json" \
            --data-binary "@$release_file" \
            "$api_url/admin/catalog-imports"
    else
        {
            printf '%s\n' "$auth_header"
            cat "$release_file"
        } | docker exec -i "$backend_container" sh -eu -c '
            IFS= read -r authorization_header
            curl --fail-with-body --silent --show-error \
                --header "$authorization_header" \
                --header "Content-Type: application/json" \
                --data-binary @- \
                http://127.0.0.1:8080/api/v1/admin/catalog-imports
        '
    fi
    printf '\n'
}

echo "Seeding the catalog from pinned public datasets..."

foundation_json="$seed_work_directory/foundation.json"
foundation_release="$seed_work_directory/foundation-release.json"
download_json_archive \
    "$USDA_FOUNDATION_URL" \
    foundation.zip \
    FoodData_Central_foundation_food_json_2026-04-30.json \
    "$foundation_json"
prepare_release usda-foundation \
    --release "$USDA_FOUNDATION_RELEASE" --input /work/foundation.json --output /work/foundation-release.json
import_release "$foundation_release"
rm "$foundation_json" "$foundation_release"

sr_legacy_json="$seed_work_directory/sr-legacy.json"
sr_legacy_release="$seed_work_directory/sr-legacy-release.json"
download_json_archive \
    "$USDA_SR_LEGACY_URL" \
    sr-legacy.zip \
    FoodData_Central_sr_legacy_food_json_2018-04.json \
    "$sr_legacy_json"
prepare_release usda-sr-legacy \
    --release "$USDA_SR_LEGACY_RELEASE" --input /work/sr-legacy.json --output /work/sr-legacy-release.json
import_release "$sr_legacy_release"
rm "$sr_legacy_json" "$sr_legacy_release"

matvaretabellen_en="$seed_work_directory/matvaretabellen-en.json"
matvaretabellen_nb="$seed_work_directory/matvaretabellen-nb.json"
matvaretabellen_release="$seed_work_directory/matvaretabellen-release.json"
download_file "$MATVARETABELLEN_EN_URL" "$matvaretabellen_en"
download_file "$MATVARETABELLEN_NB_URL" "$matvaretabellen_nb"
prepare_release matvaretabellen \
    --release "$MATVARETABELLEN_RELEASE" \
    --input-en /work/matvaretabellen-en.json \
    --input-nb /work/matvaretabellen-nb.json \
    --output /work/matvaretabellen-release.json
import_release "$matvaretabellen_release"

echo "Catalog seeding completed successfully."
