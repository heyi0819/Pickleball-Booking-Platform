terraform {
  required_version = ">= 1.10.0, < 2.0.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.45"
    }
  }

  backend "gcs" {}
}

provider "google" {
  project = var.project_id
  region  = var.region
}

data "google_sql_database_instance" "postgres" {
  project = var.project_id
  name    = var.cloud_sql_instance_name
}

locals {
  database_url = "jdbc:postgresql:///${var.database_name}?socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=${data.google_sql_database_instance.postgres.connection_name}&cloudSqlRefreshStrategy=lazy"
}

resource "google_cloud_run_v2_service" "api" {
  project             = var.project_id
  name                = "pickleball-stg-api"
  location            = var.region
  deletion_protection = false
  ingress             = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = var.api_service_account_email
    timeout         = "60s"

    scaling {
      min_instance_count = var.api_min_instances
      max_instance_count = var.api_max_instances
    }

    containers {
      image = var.runtime_image

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
      }

      env {
        name  = "SPRING_FLYWAY_ENABLED"
        value = "false"
      }
      env {
        name  = "DATABASE_URL"
        value = local.database_url
      }
      env {
        name  = "DATABASE_USERNAME"
        value = var.database_username
      }
      env {
        name = "DATABASE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = var.database_password_secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "JWT_SIGNING_SECRET"
        value_source {
          secret_key_ref {
            secret  = var.jwt_signing_secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "LINE_LOGIN_CHANNEL_ID"
        value = var.line_login_channel_id
      }
      env {
        name  = "DEFAULT_ORGANIZATION_CODE"
        value = var.default_organization_code
      }
      env {
        name  = "DEFAULT_ORGANIZATION_NAME"
        value = var.default_organization_name
      }
    }
  }
}

resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  project  = var.project_id
  location = google_cloud_run_v2_service.api.location
  name     = google_cloud_run_v2_service.api.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_job" "migration" {
  project             = var.project_id
  name                = "pickleball-stg-db-migrate"
  location            = var.region
  deletion_protection = false

  template {
    template {
      service_account = var.migration_service_account_email
      timeout         = "600s"
      max_retries     = 0

      containers {
        image = var.runtime_image
        args  = ["--spring.profiles.active=migration"]

        resources {
          limits = {
            cpu    = "1"
            memory = "512Mi"
          }
        }

        env {
          name  = "SPRING_FLYWAY_ENABLED"
          value = "true"
        }
        env {
          name  = "DATABASE_URL"
          value = local.database_url
        }
        env {
          name  = "DATABASE_USERNAME"
          value = var.database_username
        }
        env {
          name = "DATABASE_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = var.database_password_secret_id
              version = "latest"
            }
          }
        }
        env {
          name = "JWT_SIGNING_SECRET"
          value_source {
            secret_key_ref {
              secret  = var.jwt_signing_secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }
}
