#!/usr/bin/env bash
set -euo pipefail

: "${STAGING_API_URL:?STAGING_API_URL is required}"
: "${STAGING_LIFF_URL:?STAGING_LIFF_URL is required}"
: "${STAGING_ADMIN_URL:?STAGING_ADMIN_URL is required}"

assert_http_code() {
  local url="$1"
  local expected="$2"
  local actual
  actual="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "$url")"
  if [[ "$actual" != "$expected" ]]; then
    echo "Expected HTTP ${expected} from ${url}, got ${actual}" >&2
    exit 1
  fi
}

echo "Checking Cloud Run readiness..."
health="$(curl --fail --silent --show-error "${STAGING_API_URL%/}/actuator/health/readiness")"
if [[ "$health" != *'"status":"UP"'* ]]; then
  echo "Readiness response is not UP: ${health}" >&2
  exit 1
fi

echo "Checking Firebase Hosting frontends..."
curl --fail --silent --show-error "${STAGING_LIFF_URL%/}/" >/dev/null
curl --fail --silent --show-error "${STAGING_ADMIN_URL%/}/" >/dev/null

echo "Checking Hosting -> Cloud Run rewrite and Spring Security boundary..."
assert_http_code "${STAGING_LIFF_URL%/}/api/v1/me" "401"
assert_http_code "${STAGING_ADMIN_URL%/}/api/v1/me" "401"

echo "Staging smoke PASS"
