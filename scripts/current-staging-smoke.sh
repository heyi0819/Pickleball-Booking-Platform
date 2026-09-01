#!/usr/bin/env bash
set -euo pipefail

# Public, read-only checks for the current canonical staging deployment.
# No token, credential, business data, or provider API access is used.
API_URL="${STAGING_API_URL:-https://pickleball-stg-api.onrender.com}"
LIFF_URL="${STAGING_LIFF_URL:-https://pickleball-stg-liff.pages.dev}"
ADMIN_URL="${STAGING_ADMIN_URL:-https://pickleball-stg-admin.pages.dev}"
UNKNOWN_ORIGIN="${STAGING_UNKNOWN_ORIGIN:-https://unknown.invalid}"

status() {
  curl --silent --show-error --output /dev/null --write-out '%{http_code}' "$@"
}

expect_status() {
  local expected="$1"
  shift
  local actual
  actual="$(status "$@")"
  if [[ "$actual" != "$expected" ]]; then
    echo "Expected HTTP ${expected}, got ${actual}: $*" >&2
    exit 1
  fi
}

expect_exact_cors_origin() {
  local origin="$1"
  local headers
  headers="$(curl --silent --show-error --dump-header - --output /dev/null --request OPTIONS --header "Origin: ${origin%/}" --header 'Access-Control-Request-Method: GET' "${API_URL%/}/api/v1/me")"
  if ! printf '%s\n' "$headers" | tr -d '\r' | grep --ignore-case --fixed-strings --line-regexp "Access-Control-Allow-Origin: ${origin%/}" >/dev/null; then
    echo "Exact CORS origin was not returned for ${origin%/}." >&2
    exit 1
  fi
}

health="$(curl --fail --silent --show-error "${API_URL%/}/actuator/health")"
if [[ "$health" != *'"status":"UP"'* ]]; then
  echo "Health response is not UP." >&2
  exit 1
fi

expect_status 200 "${LIFF_URL%/}/"
expect_status 200 "${ADMIN_URL%/}/"
expect_status 401 "${API_URL%/}/api/v1/me"
expect_status 401 --header 'Authorization: Bearer invalid-platform-jwt' "${API_URL%/}/api/v1/me"

for origin in "$LIFF_URL" "$ADMIN_URL"; do
  expect_status 200 --request OPTIONS --header "Origin: ${origin%/}" --header 'Access-Control-Request-Method: GET' "${API_URL%/}/api/v1/me"
  expect_exact_cors_origin "$origin"
done
expect_status 403 --request OPTIONS --header "Origin: ${UNKNOWN_ORIGIN%/}" --header 'Access-Control-Request-Method: GET' "${API_URL%/}/api/v1/me"

echo 'Current canonical staging smoke PASS'
