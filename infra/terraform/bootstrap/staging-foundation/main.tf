terraform {
  required_version = ">= 1.10.0, < 2.0.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.45"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 7.45"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project               = var.project_id
  region                = var.region
  user_project_override = true
}

data "google_project" "current" {
  project_id = var.project_id
}

locals {
  required_services = toset([
    "artifactregistry.googleapis.com",
    "firebase.googleapis.com",
    "firebasehosting.googleapis.com",
    "iamcredentials.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "serviceusage.googleapis.com",
    "sqladmin.googleapis.com",
    "sts.googleapis.com",
    "storage.googleapis.com",
  ])

  secret_ids = toset([
    "pickleball-stg-db-password",
    "pickleball-stg-jwt-signing-secret",
  ])

  runtime_project_roles = {
    api_cloud_sql       = [google_service_account.api.email, "roles/cloudsql.client"]
    api_secret_accessor = [google_service_account.api.email, "roles/secretmanager.secretAccessor"]
    migration_cloud_sql = [google_service_account.migration.email, "roles/cloudsql.client"]
    migration_secret    = [google_service_account.migration.email, "roles/secretmanager.secretAccessor"]
  }

  deployer_project_roles = toset([
    "roles/artifactregistry.writer",
    "roles/cloudsql.viewer",
    "roles/firebasehosting.admin",
    "roles/run.admin",
    "roles/secretmanager.viewer",
    "roles/serviceusage.apiKeysViewer",
    "roles/serviceusage.serviceUsageConsumer",
  ])
}

resource "google_project_service" "required" {
  for_each = local.required_services

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_storage_bucket" "terraform_state" {
  name                        = var.terraform_state_bucket_name
  project                     = var.project_id
  location                    = var.region
  uniform_bucket_level_access = true
  force_destroy               = false

  versioning {
    enabled = true
  }

  depends_on = [google_project_service.required]
}

resource "google_artifact_registry_repository" "backend" {
  project       = var.project_id
  location      = var.region
  repository_id = var.artifact_repository_id
  description   = "Pickleball Booking Platform staging container images"
  format        = "DOCKER"

  depends_on = [google_project_service.required]
}

resource "google_sql_database_instance" "postgres" {
  project             = var.project_id
  name                = var.cloud_sql_instance_name
  region              = var.region
  database_version    = "POSTGRES_18"
  deletion_protection = var.database_deletion_protection

  settings {
    tier              = var.database_tier
    availability_type = "ZONAL"
    disk_autoresize   = true
    disk_type         = "PD_SSD"

    backup_configuration {
      enabled = true
    }

    ip_configuration {
      ipv4_enabled = true
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_sql_database" "application" {
  project  = var.project_id
  instance = google_sql_database_instance.postgres.name
  name     = var.database_name
}

resource "google_secret_manager_secret" "runtime" {
  for_each = local.secret_ids

  project   = var.project_id
  secret_id = each.value

  replication {
    auto {}
  }

  depends_on = [google_project_service.required]
}

resource "google_firebase_project" "staging" {
  provider = google-beta
  project  = var.project_id

  depends_on = [google_project_service.required]
}

resource "google_firebase_hosting_site" "liff" {
  provider        = google-beta
  project         = var.project_id
  site_id         = var.firebase_liff_site_id
  deletion_policy = "DELETE"

  depends_on = [google_firebase_project.staging]
}

resource "google_firebase_hosting_site" "admin" {
  provider        = google-beta
  project         = var.project_id
  site_id         = var.firebase_admin_site_id
  deletion_policy = "DELETE"

  depends_on = [google_firebase_project.staging]
}

resource "google_service_account" "api" {
  project      = var.project_id
  account_id   = "pickleball-stg-api"
  display_name = "Pickleball staging API runtime"
}

resource "google_service_account" "migration" {
  project      = var.project_id
  account_id   = "pickleball-stg-migration"
  display_name = "Pickleball staging migration runtime"
}

resource "google_service_account" "deployer" {
  project      = var.project_id
  account_id   = "pickleball-stg-deployer"
  display_name = "Pickleball GitHub staging deployer"
}

resource "google_project_iam_member" "runtime" {
  for_each = local.runtime_project_roles

  project = var.project_id
  role    = each.value[1]
  member  = "serviceAccount:${each.value[0]}"
}

resource "google_project_iam_member" "deployer" {
  for_each = local.deployer_project_roles

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_service_account_iam_member" "deployer_uses_api" {
  service_account_id = google_service_account.api.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_service_account_iam_member" "deployer_uses_migration" {
  service_account_id = google_service_account.migration.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_storage_bucket_iam_member" "deployer_state" {
  bucket = google_storage_bucket.terraform_state.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_iam_workload_identity_pool" "github" {
  project                   = var.project_id
  workload_identity_pool_id = "github-actions"
  display_name              = "GitHub Actions"
  description               = "OIDC identities from the approved Pickleball Booking Platform repository"

  depends_on = [google_project_service.required]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "pickleball-staging"
  display_name                       = "Pickleball staging GitHub provider"

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  attribute_condition = "assertion.repository == '${var.github_repository}' && assertion.ref == 'refs/heads/${var.github_branch}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account_iam_member" "github_deployer" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repository}"
}
