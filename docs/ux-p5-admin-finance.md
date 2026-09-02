# UX-P5 PR B — Admin finance work queue and operational recovery

## Scope

This PR consumes the sealed UX-P5F Finance Read Model. It changes only the Admin frontend and tests.

- Readable receivable, payment, and refund queues for the active organization.
- Detail-first payment recording, refund request, refund review, and refund execution.
- Native shared confirmation dialog for every finance command; Escape, backdrop cancel, confirmation focus, and focus restoration use the shared UI primitive.
- The payer, receivable, payment, and refund are selected from server-scoped read data. UUIDs are secondary technical information only.
- Existing operations recovery uses a readable organization-scope selector when the signed-in user has organization role contexts, and has a second confirmation before recovery.
- Loading, empty, error, success, disabled, and ineligible states are rendered in zh-TW for the new finance journey.

## Security and finance invariants

The UI sends the existing command endpoints unchanged. It does not calculate eligibility, update statuses locally, or treat the read-side refundable value as execution authority.

The backend remains authoritative for role scope, resource organization, lifecycle, money, locking, audit, outbox, and idempotency. Each command has a fresh idempotency key. A command is unavailable in the UI when its read status is clearly ineligible, but the backend still validates every request.

A global-only PLATFORM_ADMIN has no readable organization discovery endpoint in the approved API. This PR does not add one or fall back to a UUID text field. The operations queue exposes only organization contexts already returned by `/me`; if none exist, it explains that no readable authorized context is available. This keeps the existing explicit-scope and deny-by-default model intact. Finance queues are shown for the existing organization-scoped committee context.

## Compatibility and rollback

No API, OpenAPI, generated client, dependency, migration, security policy, LIFF routing, or backend behavior changes. Reverting this PR restores the previous Admin-only UI; financial data and commands are unaffected.
