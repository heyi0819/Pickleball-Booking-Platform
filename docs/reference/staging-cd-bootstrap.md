# Optional Paid GCP Staging Reference

> Status after the 2026-08-27 Zero-Cost backfill: **optional paid-cloud reference only**.
>
> The current required Slice closure gate is documented in `docs/reference/zero-cost-cloud-baseline.md`. Real GCP Staging is no longer required for Slice 6 closure or initial production.

This document preserves the already implemented GCP repository baseline so it can be reused later if a paid GCP environment is explicitly approved.

## Cost-safety rule

GCP resources such as Cloud SQL can create charges. Therefore:

- `.github/workflows/deploy-staging.yml` is manual-only;
- a run must explicitly select `ENABLE_PAID_GCP`;
- there is no `workflow_run` / main auto-deployment trigger for GCP;
- do not provision a billing-enabled GCP environment merely to satisfy a current Slice Gate;
- Terraform validation remains in normal CI because validating reference configuration does not provision resources.

## Preserved GCP architecture

The optional reference contains:

- GCP region `asia-east1`;
- Cloud SQL for PostgreSQL 18;
- Artifact Registry Docker repository;
- Cloud Run API Service;
- Cloud Run Migration Job using the same backend image digest;
- Firebase Hosting sites for LIFF and Admin;
- Secret Manager for runtime secrets;
- GitHub Actions + Google Workload Identity Federation;
- Terraform with GCS remote state.

No service-account JSON key or application secret value is committed to Git or stored as plaintext Terraform configuration.

## Optional deployment sequence

```mermaid
flowchart LR
    A[Manual ENABLE_PAID_GCP] --> B[Checkout fixed main commit]
    B --> C[WIF authentication]
    C --> D[Build + push backend image]
    D --> E[Resolve immutable digest]
    E --> F[Update Migration Job]
    F --> G[Flyway forward migration]
    G --> H[Deploy Cloud Run API same digest]
    H --> I[Build LIFF / Admin]
    I --> J[Deploy Firebase Hosting]
    J --> K[GCP staging smoke]
    K --> L[Optional paid-GCP RC metadata]
```

The API runtime sets `SPRING_FLYWAY_ENABLED=false`; migration is executed by the dedicated migration job before the API is updated.

## One-time external inputs if GCP is ever enabled

A real run still requires account-owned setup that repository code cannot provide:

- billing-enabled GCP project;
- globally unique Terraform state bucket;
- globally unique Firebase LIFF/Admin site IDs;
- Secret Manager values for `pickleball-stg-db-password` and `pickleball-stg-jwt-signing-secret`;
- Cloud SQL application user `pickleball_app`;
- LINE staging LIFF ID and Login channel ID;
- GitHub `staging` Environment variables from Terraform outputs.

Required GitHub environment variables remain:

| Variable | Source |
|---|---|
| `GCP_STAGING_PROJECT_ID` | GCP project ID |
| `GCP_STAGING_WIF_PROVIDER` | Terraform `workload_identity_provider` output |
| `GCP_STAGING_DEPLOYER_SA` | Terraform `deployer_service_account_email` output |
| `GCP_STAGING_TF_STATE_BUCKET` | Terraform `terraform_state_bucket` output |
| `STAGING_LIFF_SITE_ID` | Terraform `firebase_liff_site_id` output |
| `STAGING_ADMIN_SITE_ID` | Terraform `firebase_admin_site_id` output |
| `STAGING_LIFF_ID` | LINE Developers staging LIFF ID |
| `STAGING_LINE_LOGIN_CHANNEL_ID` | LINE Login staging channel ID |

Secret payloads stay outside GitHub variables and Terraform state.

## Terraform bootstrap reference

The foundation root remains:

```text
infra/terraform/bootstrap/staging-foundation
```

The runtime root remains:

```text
infra/terraform/environments/staging
```

If a paid GCP environment is approved, initialize the foundation without a remote backend first, apply it with a privileged bootstrap identity, then migrate Terraform state to the created GCS bucket. Runtime state uses a separate `runtime/staging` prefix.

Terraform continues to create secret **resources**, not secret payload values. Cloud SQL application-user passwords are deliberately managed outside Terraform to keep plaintext values out of Terraform state.

## GCP smoke acceptance

`scripts/staging-smoke.sh` remains the optional GCP-specific smoke and verifies:

- Cloud Run readiness is `UP`;
- LIFF and Admin Firebase Hosting roots are reachable;
- both Hosting `/api/**` rewrites reach the backend;
- unauthenticated `/api/v1/me` returns `401`.

This smoke is useful only after an explicitly approved GCP deployment. It is **not** the current Slice closure gate.

## Current required gate

Use:

```text
.github/workflows/ci.yml
scripts/zero-cost-production-like.sh
docs/reference/zero-cost-cloud-baseline.md
```

The required production-like validation creates only ephemeral GitHub-hosted Docker resources and destroys them when the job finishes.
