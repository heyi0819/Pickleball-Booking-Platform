#!/usr/bin/env bash
set -euo pipefail

REPORT_DIR="${1:-backend/target/surefire-reports}"

required_suites=(
  "S1 Identity and organization authorization|TEST-com.pickleball.booking.identity.application.AuthorizationMatrixIT.xml"
  "S1 HTTP security boundary|TEST-com.pickleball.booking.identity.api.IdentitySecurityIT.xml"
  "S2 lesson demand concurrency|TEST-com.pickleball.booking.lessonrequest.application.LessonRequestSubmissionConcurrencyIT.xml"
  "S3 matching HTTP acceptance|TEST-com.pickleball.booking.coursematch.api.Slice3HttpEndToEndIT.xml"
  "S4 open enrollment HTTP acceptance|TEST-com.pickleball.booking.offering.api.Slice4HttpEndToEndIT.xml"
  "S5 course operations HTTP acceptance|TEST-com.pickleball.booking.course.api.Slice5HttpEndToEndIT.xml"
  "S6 finance HTTP acceptance|TEST-com.pickleball.booking.receivable.api.Slice6FinanceHttpEndToEndIT.xml"
  "S6 refund concurrency|TEST-com.pickleball.booking.RefundConcurrencyIT.xml"
  "S7 settlement integration|TEST-com.pickleball.booking.SettlementApplicationServiceIT.xml"
  "S7 payout integration|TEST-com.pickleball.booking.PayoutApplicationServiceIT.xml"
  "S8 admin recovery HTTP acceptance|TEST-com.pickleball.booking.notification.api.AdminOperationsHttpIT.xml"
  "S8 notification projection PostgreSQL evidence|TEST-com.pickleball.booking.notification.infrastructure.NotificationProjectionRepositoryIT.xml"
)

for entry in "${required_suites[@]}"; do
  label="${entry%%|*}"
  report="${REPORT_DIR}/${entry#*|}"
  if [[ ! -f "${report}" ]]; then
    echo "Missing Slice 9 acceptance evidence: ${label} (${report})" >&2
    exit 1
  fi

  suite_line="$(grep -m 1 '<testsuite ' "${report}")"
  tests="$(sed -nE 's/.* tests="([0-9]+)".*/\1/p' <<<"${suite_line}")"
  failures="$(sed -nE 's/.* failures="([0-9]+)".*/\1/p' <<<"${suite_line}")"
  errors="$(sed -nE 's/.* errors="([0-9]+)".*/\1/p' <<<"${suite_line}")"
  skipped="$(sed -nE 's/.* skipped="([0-9]+)".*/\1/p' <<<"${suite_line}")"

  if [[ -z "${tests}" || "${tests}" == "0" || "${failures}" != "0" || "${errors}" != "0" || "${skipped}" != "0" ]]; then
    echo "Invalid Slice 9 acceptance evidence for ${label}: ${suite_line}" >&2
    exit 1
  fi
  echo "PASS ${label}: ${tests} test(s)"
done

echo "Slice 9 backend acceptance evidence PASS"
