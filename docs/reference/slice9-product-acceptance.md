# Slice 9 — MVP Product Acceptance and Release-Candidate Readiness

## Status and intent

Slice 9 is the explicitly approved acceptance Slice after Slices 1–8 were sealed. It does not add a new business capability. It turns the existing P0 product into one auditable release-candidate gate that proves the sealed capabilities still work together and that every protected capability remains deny-by-default in a production-like runtime.

## Scope

- Preserve the sealed Slice 1–8 domain, API, schema, security, and UI contracts.
- Make representative backend acceptance evidence for every Slice mandatory in CI.
- Make representative browser evidence for every existing P0 UI journey mandatory in CI.
- Add the missing browser acceptance journey for Slice 3 matching, pricing, and formal-course confirmation.
- Expand the ephemeral production-like smoke from one protected endpoint to a Slice 1–8 security-boundary matrix.
- Record the clean migration version, readiness, public OpenAPI availability, and protected-slice checks in release-candidate metadata.
- Normalize the copied Maven Wrapper inside the Linux Docker build stage so a Windows CRLF checkout cannot make the production image unbuildable.

## Non-goals

- No Coupon, Event, Analytics / BI, Court Inventory, online payment provider, or other Future Extension.
- No API endpoint, OpenAPI schema, database migration, authorization role, business rule, or product UI redesign.
- No staging or production deployment and no persistent cloud resource.
- No rewrite of a sealed Slice. A failing acceptance test must first be handled as evidence; any product forward-fix requires a separately explained minimal change.

## Acceptance matrix

| Slice | P0 capability | Required backend evidence | Required browser / runtime evidence |
| --- | --- | --- | --- |
| S1 | Identity, RBAC, organization scope | `AuthorizationMatrixIT`, `IdentitySecurityIT` | `slice1.spec.ts`, `admin.spec.ts`, unauthenticated `/me` = 401 |
| S2 | Coach supply and lesson demand | `LessonRequestSubmissionConcurrencyIT` plus the full backend suite | `slice2.spec.ts`, `slice2-coach.spec.ts`, lesson-request boundary = 401 |
| S3 | Matching, immutable pricing, course confirmation | `Slice3HttpEndToEndIT` | `slice3-matching.spec.ts`, course-match boundary = 401 |
| S4 | Open enrollment | `Slice4HttpEndToEndIT` | `slice4-open-enrollment.spec.ts`, offering boundary = 401 |
| S5 | Course operations | `Slice5HttpEndToEndIT` | `slice5-course-operations.spec.ts`, course boundary = 401 |
| S6 | Payment and refund | `Slice6FinanceHttpEndToEndIT`, `RefundConcurrencyIT` | `slice6-finance.spec.ts`, payment command boundary = 401 |
| S7 | Settlement and payout | `SettlementApplicationServiceIT`, `PayoutApplicationServiceIT` | No dedicated S7 UI exists in the sealed contract; coach-settlement boundary = 401 |
| S8 | Notification, outbox, admin recovery | `AdminOperationsHttpIT`, `NotificationProjectionRepositoryIT` | `s8-4-admin-operations.spec.ts`, admin-outbox boundary = 401 |

The evidence scripts reject missing, zero-test, failed, errored, skipped, unexpected, or flaky required suites. This prevents a renamed, excluded, or silently skipped critical suite from being mistaken for Slice 9 acceptance.

## CI and production-like Gate

1. Backend full test suite passes.
2. `scripts/verify-slice9-backend-evidence.sh` validates the required Surefire XML reports.
3. Frontend typecheck, lint, unit tests, build, and generated-client contract check pass.
4. Playwright executes all configured journeys and writes `frontend/test-results/slice9-playwright.json`.
5. `frontend/scripts/verify-slice9-playwright-evidence.mjs` validates every required UI evidence file and rejects unexpected or flaky results.
6. Clean PostgreSQL 18 forward migration and container / Terraform reference validation pass.
7. `scripts/zero-cost-production-like.sh` starts an isolated PostgreSQL 18 and production backend image, verifies readiness, public OpenAPI, and unauthenticated 401 boundaries for S1–S8, then destroys all ephemeral resources.
8. The PR final HEAD and the eventual merge commit on `main` must both have the complete CI workflow green before Slice 9 can be marked SEALED.

## Compatibility, rollout, and recovery

- API / schema / security / UI compatibility: unchanged. The Docker-only wrapper normalization changes no source checkout or build tool.
- Migration: no new migration; the smoke asserts the latest existing Flyway version is applied.
- Rollout: CI-only acceptance expansion. Deployment remains a separate, explicitly authorized operation.
- Monitoring: CI logs identify the missing or non-passing Slice evidence by Slice and suite.
- Rollback: revert the Slice 9 test / CI commit if the gate itself is defective. Product data is unaffected.
- Forward-fix: if the gate exposes a genuine sealed-product defect, preserve the failing evidence and submit the smallest compatible product fix with its own root-cause and risk analysis.

## Closure definition

Slice 9 is `READY TO MERGE` only when the final PR HEAD is stable, mergeable, free of blocking comments, and all CI jobs are green. It becomes `SEALED` only after explicit merge authorization, merge completion, and the full `main` CI including the production-like smoke is green.
