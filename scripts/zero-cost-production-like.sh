#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NETWORK_NAME="pickleball-zero-cost-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
DB_CONTAINER="pickleball-zero-cost-db-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
API_CONTAINER="pickleball-zero-cost-api-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
BACKEND_IMAGE="pickleball-zero-cost:${GITHUB_SHA:-local}"
DATABASE_NAME="pickleball_booking"
DATABASE_USERNAME="pickleball"
DATABASE_PASSWORD="zero-cost-ci-password"
JWT_SIGNING_SECRET="zero-cost-prototype-jwt-signing-secret-at-least-32-bytes"
API_PORT="18080"
METADATA_FILE="${REPO_ROOT}/zero-cost-release-metadata.json"

cleanup() {
  docker rm -f "${API_CONTAINER}" >/dev/null 2>&1 || true
  docker rm -f "${DB_CONTAINER}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

cd "${REPO_ROOT}"

echo "Creating isolated Docker network..."
docker network create "${NETWORK_NAME}" >/dev/null

echo "Starting ephemeral PostgreSQL 18..."
docker run --detach \
  --name "${DB_CONTAINER}" \
  --network "${NETWORK_NAME}" \
  --env POSTGRES_DB="${DATABASE_NAME}" \
  --env POSTGRES_USER="${DATABASE_USERNAME}" \
  --env POSTGRES_PASSWORD="${DATABASE_PASSWORD}" \
  postgres:18 >/dev/null

for attempt in $(seq 1 30); do
  if docker exec "${DB_CONTAINER}" pg_isready -U "${DATABASE_USERNAME}" -d "${DATABASE_NAME}" >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" == "30" ]]; then
    echo "PostgreSQL did not become ready." >&2
    docker logs "${DB_CONTAINER}" >&2 || true
    exit 1
  fi
  sleep 2
done

echo "Building the production backend Docker image..."
docker build --file backend/Dockerfile --tag "${BACKEND_IMAGE}" backend

DATABASE_URL="jdbc:postgresql://${DB_CONTAINER}:5432/${DATABASE_NAME}"

echo "Running Flyway in a dedicated migration container..."
docker run --rm \
  --network "${NETWORK_NAME}" \
  --env SPRING_PROFILES_ACTIVE=migration \
  --env DATABASE_URL="${DATABASE_URL}" \
  --env DATABASE_USERNAME="${DATABASE_USERNAME}" \
  --env DATABASE_PASSWORD="${DATABASE_PASSWORD}" \
  --env JWT_SIGNING_SECRET="${JWT_SIGNING_SECRET}" \
  "${BACKEND_IMAGE}"

EXPECTED_MIGRATION_VERSION="$(find backend/src/main/resources/db/migration \
  -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' \
  | sed -E 's/^V([^_]+)__.*/\1/' \
  | sort -V \
  | tail -n 1)"
APPLIED_MIGRATION_VERSION="$(docker exec "${DB_CONTAINER}" psql \
  -U "${DATABASE_USERNAME}" -d "${DATABASE_NAME}" -Atqc \
  'select version from flyway_schema_history where success = true order by installed_rank desc limit 1')"

if [[ -z "${EXPECTED_MIGRATION_VERSION}" || "${APPLIED_MIGRATION_VERSION}" != "${EXPECTED_MIGRATION_VERSION}" ]]; then
  echo "Flyway version mismatch: expected ${EXPECTED_MIGRATION_VERSION:-<none>}, applied ${APPLIED_MIGRATION_VERSION:-<none>}" >&2
  exit 1
fi

echo "Starting API from the same Docker image with startup Flyway disabled..."
docker run --detach \
  --name "${API_CONTAINER}" \
  --network "${NETWORK_NAME}" \
  --publish "127.0.0.1:${API_PORT}:8080" \
  --env DATABASE_URL="${DATABASE_URL}" \
  --env DATABASE_USERNAME="${DATABASE_USERNAME}" \
  --env DATABASE_PASSWORD="${DATABASE_PASSWORD}" \
  --env JWT_SIGNING_SECRET="${JWT_SIGNING_SECRET}" \
  --env SPRING_FLYWAY_ENABLED=false \
  --env LINE_LOGIN_CHANNEL_ID=zero-cost-prototype \
  "${BACKEND_IMAGE}" >/dev/null

READINESS_URL="http://127.0.0.1:${API_PORT}/actuator/health/readiness"
for attempt in $(seq 1 45); do
  if health="$(curl --fail --silent --show-error "${READINESS_URL}" 2>/dev/null)" && [[ "${health}" == *'"status":"UP"'* ]]; then
    break
  fi
  if [[ "${attempt}" == "45" ]]; then
    echo "API did not become ready." >&2
    docker logs "${API_CONTAINER}" >&2 || true
    exit 1
  fi
  sleep 2
done

echo "Verifying Spring Security boundary..."
ME_STATUS="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  "http://127.0.0.1:${API_PORT}/api/v1/me")"
if [[ "${ME_STATUS}" != "401" ]]; then
  echo "Expected unauthenticated /api/v1/me to return 401, got ${ME_STATUS}." >&2
  exit 1
fi

BACKEND_IMAGE_ID="$(docker image inspect "${BACKEND_IMAGE}" --format '{{.Id}}')"
DEPLOY_SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"

cat > "${METADATA_FILE}" <<EOF
{
  "deploymentMode": "zero-cost-ephemeral",
  "gitSha": "${DEPLOY_SHA}",
  "backendImageId": "${BACKEND_IMAGE_ID}",
  "postgresImage": "postgres:18",
  "migrationVersion": "${APPLIED_MIGRATION_VERSION}",
  "readiness": "UP",
  "unauthenticatedMeStatus": 401,
  "persistentCloudResourcesCreated": false,
  "paidCloudRequired": false
}
EOF

echo "Zero-cost production-like smoke PASS"
cat "${METADATA_FILE}"
