terraform {
  required_version = ">= 1.10.0, < 2.0.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.45"
    }
  }

  # The production backend is deliberately configured only at terraform init.
  # See backend.hcl.example; production state must never use local state.
  backend "gcs" {}
}

provider "google" {
  project = var.runtime_project_id
  region  = var.region
}

locals {
  common_labels = merge({
    application = "pickleball-booking"
    environment = "production"
    managed_by  = "terraform"
  }, var.resource_labels)

  runtime_secrets = {
    database_runtime_password  = "pickleball-prod-db-runtime-password"
    database_migrator_password = "pickleball-prod-db-migrator-password"
    jwt_signing_material       = "pickleball-prod-jwt-signing-material"
    line_login_channel_secret  = "pickleball-prod-line-login-channel-secret"
    line_messaging_credential  = "pickleball-prod-line-messaging-credential"
  }

  private_database_url = "jdbc:postgresql://${google_sql_database_instance.postgres.private_ip_address}:5432/${var.database_name}?sslmode=require"
}

resource "google_project_service" "required" {
  for_each = toset([
    "artifactregistry.googleapis.com",
    "iamcredentials.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "servicenetworking.googleapis.com",
    "sqladmin.googleapis.com",
    "sts.googleapis.com",
  ])

  project            = var.runtime_project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_compute_network" "runtime" {
  project                 = var.runtime_project_id
  name                    = "pickleball-prod-runtime"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "cloud_run" {
  project       = var.runtime_project_id
  name          = "pickleball-prod-cloud-run"
  region        = var.region
  network       = google_compute_network.runtime.id
  ip_cidr_range = var.cloud_run_subnet_cidr
}

resource "google_compute_global_address" "private_services" {
  project       = var.runtime_project_id
  name          = "pickleball-prod-private-services"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = var.private_services_prefix_length
  network       = google_compute_network.runtime.id
}

resource "google_service_networking_connection" "private_services" {
  network                 = google_compute_network.runtime.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_services.name]

  depends_on = [google_project_service.required]
}

resource "google_artifact_registry_repository" "backend" {
  project       = var.runtime_project_id
  location      = var.region
  repository_id = var.artifact_repository_id
  description   = "Pickleball Booking Platform production container images"
  format        = "DOCKER"
  labels        = local.common_labels

  depends_on = [google_project_service.required]
}

resource "google_sql_database_instance" "postgres" {
  project             = var.runtime_project_id
  name                = var.cloud_sql_instance_name
  region              = var.region
  database_version    = "POSTGRES_18"
  deletion_protection = var.database_deletion_protection

  settings {
    tier              = var.database_tier
    availability_type = var.database_availability_type
    disk_size         = var.database_disk_size_gb
    disk_autoresize   = var.database_disk_autoresize
    disk_type         = "PD_SSD"
    user_labels       = local.common_labels

    backup_configuration {
      enabled                        = var.database_backups_enabled
      point_in_time_recovery_enabled = var.database_pitr_enabled
      start_time                     = var.database_backup_start_time_utc

      backup_retention_settings {
        retained_backups = var.database_retained_backups
        retention_unit   = "COUNT"
      }
    }

    maintenance_window {
      day          = var.database_maintenance_day
      hour         = var.database_maintenance_hour_utc
      update_track = "stable"
    }

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.runtime.id
      ssl_mode        = "ENCRYPTED_ONLY"
    }
  }

  depends_on = [google_service_networking_connection.private_services]
}

resource "google_sql_database" "application" {
  project  = var.runtime_project_id
  instance = google_sql_database_instance.postgres.name
  name     = var.database_name
}

# These are containers only. Secret versions are intentionally created outside
# Terraform so their values cannot enter Terraform state.
resource "google_secret_manager_secret" "runtime" {
  for_each = local.runtime_secrets

  project   = var.runtime_project_id
  secret_id = each.value
  labels    = local.common_labels

  replication {
    auto {}
  }

  depends_on = [google_project_service.required]
}

resource "google_service_account" "api" {
  project      = var.runtime_project_id
  account_id   = "pickleball-prod-api"
  display_name = "Pickleball production API runtime"
}

resource "google_service_account" "migration" {
  project      = var.runtime_project_id
  account_id   = "pickleball-prod-migration"
  display_name = "Pickleball production migration job"
}

resource "google_service_account" "release" {
  project      = var.runtime_project_id
  account_id   = "pickleball-prod-release"
  display_name = "Pickleball GitHub production release"
}

resource "google_secret_manager_secret_iam_member" "api" {
  for_each = toset([
    "database_runtime_password",
    "jwt_signing_material",
    "line_login_channel_secret",
    "line_messaging_credential",
  ])

  project   = var.runtime_project_id
  secret_id = google_secret_manager_secret.runtime[each.value].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api.email}"
}

resource "google_secret_manager_secret_iam_member" "migration" {
  project   = var.runtime_project_id
  secret_id = google_secret_manager_secret.runtime["database_migrator_password"].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.migration.email}"
}

resource "google_cloud_run_v2_service" "api" {
  project             = var.runtime_project_id
  name                = "pickleball-prod-api"
  location            = var.region
  ingress             = var.api_ingress
  deletion_protection = var.api_deletion_protection
  labels              = local.common_labels

  template {
    service_account                  = google_service_account.api.email
    timeout                          = "${var.api_request_timeout_seconds}s"
    max_instance_request_concurrency = var.api_concurrency

    scaling {
      min_instance_count = var.api_min_instances
      max_instance_count = var.api_max_instances
    }

    vpc_access {
      egress = "PRIVATE_RANGES_ONLY"
      network_interfaces {
        network    = google_compute_network.runtime.id
        subnetwork = google_compute_subnetwork.cloud_run.id
        tags       = ["pickleball-prod-cloud-run"]
      }
    }

    containers {
      # Release input must be an immutable digest, validated by variables.tf.
      image = var.runtime_image_digest

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = var.api_cpu
          memory = var.api_memory
        }
      }

      env {
        name  = "SPRING_FLYWAY_ENABLED"
        value = "false"
      }
      env {
        name  = "DATABASE_URL"
        value = local.private_database_url
      }
      env {
        name  = "DATABASE_USERNAME"
        value = var.database_runtime_username
      }
      env {
        name  = "APP_CORS_ALLOWED_ORIGINS"
        value = var.api_cors_allowed_origins
      }
      env {
        name  = "WORKERS_ENABLED"
        value = tostring(var.workers_enabled)
      }
      env {
        name  = "LINE_LOGIN_CHANNEL_ID"
        value = var.line_login_channel_id
      }
      env {
        name  = "LINE_LOGIN_ADMIN_REDIRECT_URI"
        value = var.line_login_admin_redirect_uri
      }
      env {
        name  = "DEFAULT_ORGANIZATION_CODE"
        value = var.default_organization_code
      }
      env {
        name  = "DEFAULT_ORGANIZATION_NAME"
        value = var.default_organization_name
      }

      env {
        name = "DATABASE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.runtime["database_runtime_password"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "JWT_SIGNING_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.runtime["jwt_signing_material"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "LINE_LOGIN_CHANNEL_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.runtime["line_login_channel_secret"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "LINE_MESSAGING_CHANNEL_ACCESS_TOKEN"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.runtime["line_messaging_credential"].secret_id
            version = "latest"
          }
        }
      }
    }
  }
}

resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  count = var.api_public_invoker ? 1 : 0

  project  = var.runtime_project_id
  location = google_cloud_run_v2_service.api.location
  name     = google_cloud_run_v2_service.api.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_job" "migration" {
  project             = var.runtime_project_id
  name                = "pickleball-prod-db-migrate"
  location            = var.region
  deletion_protection = var.migration_deletion_protection
  labels              = local.common_labels

  template {
    template {
      service_account = google_service_account.migration.email
      timeout         = "${var.migration_timeout_seconds}s"
      max_retries     = 0

      vpc_access {
        egress = "PRIVATE_RANGES_ONLY"
        network_interfaces {
          network    = google_compute_network.runtime.id
          subnetwork = google_compute_subnetwork.cloud_run.id
          tags       = ["pickleball-prod-migration"]
        }
      }

      containers {
        image = var.runtime_image_digest
        args  = ["--spring.profiles.active=migration"]

        resources {
          limits = {
            cpu    = var.migration_cpu
            memory = var.migration_memory
          }
        }

        # The migration profile is non-web; Spring/Flyway exits non-zero on a
        # failed validation or migration. It receives no application secrets.
        env {
          name  = "SPRING_FLYWAY_ENABLED"
          value = "true"
        }
        env {
          name  = "SPRING_FLYWAY_VALIDATE_ON_MIGRATE"
          value = "true"
        }
        env {
          name  = "DATABASE_URL"
          value = local.private_database_url
        }
        env {
          name  = "DATABASE_USERNAME"
          value = var.database_migrator_username
        }
        env {
          name = "DATABASE_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.runtime["database_migrator_password"].secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }
}

resource "google_iam_workload_identity_pool" "github" {
  project                   = var.runtime_project_id
  workload_identity_pool_id = "github-actions"
  display_name              = "GitHub Actions production"
  description               = "OIDC identities from the approved production release repository"

  depends_on = [google_project_service.required]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = var.runtime_project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "pickleball-production"
  display_name                       = "Pickleball production GitHub provider"

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  attribute_condition = "assertion.repository == '${var.github_repository}' && assertion.ref == 'refs/heads/${var.github_release_branch}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account_iam_member" "github_release" {
  service_account_id = google_service_account.release.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repository}"
}

# GitHub receives a release identity, not project Owner/Editor. Service-account
# attachment is deliberately limited to the two runtime identities.
resource "google_project_iam_member" "release" {
  for_each = toset([
    "roles/artifactregistry.writer",
    "roles/cloudsql.viewer",
    "roles/run.admin",
  ])

  project = var.runtime_project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.release.email}"
}

resource "google_service_account_iam_member" "release_uses_api" {
  service_account_id = google_service_account.api.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.release.email}"
}

resource "google_service_account_iam_member" "release_uses_migration" {
  service_account_id = google_service_account.migration.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.release.email}"
}
