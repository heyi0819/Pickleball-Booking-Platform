variable "runtime_project_id" {
  description = "Dedicated production runtime GCP project ID; it must not equal backup_project_id."
  type        = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.runtime_project_id))
    error_message = "runtime_project_id must be a valid explicit GCP project ID."
  }
}

variable "backup_project_id" {
  description = "Separate production backup project ID interface for S11.4; this root does not create backup resources."
  type        = string
  validation {
    condition     = var.backup_project_id != var.runtime_project_id
    error_message = "backup_project_id must be structurally separate from runtime_project_id."
  }
}

variable "backup_bucket_location" { type = string }
variable "backup_writer_identity" { type = string }
variable "backup_retention_days" { type = number }
variable "region" { type = string }
variable "database_edition" {
  description = "Cloud SQL edition. The approved MVP production profile uses ENTERPRISE."
  type        = string
  validation {
    condition     = contains(["ENTERPRISE", "ENTERPRISE_PLUS"], var.database_edition)
    error_message = "database_edition must be ENTERPRISE or ENTERPRISE_PLUS."
  }
}
variable "database_tier" { type = string }
variable "database_disk_size_gb" { type = number }
variable "api_min_instances" { type = number }
variable "api_max_instances" { type = number }
variable "api_cors_allowed_origins" { type = string }
variable "line_login_channel_id" { type = string }
variable "line_login_admin_redirect_uri" { type = string }
variable "github_repository" { type = string }

variable "artifact_repository_id" {
  type    = string
  default = "pickleball"
}
variable "cloud_sql_instance_name" {
  type    = string
  default = "pickleball-prod-pg18"
}
variable "database_name" {
  type    = string
  default = "pickleball_booking"
}
variable "database_runtime_username" {
  type    = string
  default = "pickleball_app"
}
variable "database_migrator_username" {
  type    = string
  default = "pickleball_migrator"
}
variable "database_disk_autoresize" {
  type    = bool
  default = true
}
variable "database_deletion_protection" {
  type    = bool
  default = true
}
variable "database_backups_enabled" {
  type    = bool
  default = true
}
variable "database_pitr_enabled" {
  type    = bool
  default = true
}
variable "database_retained_backups" {
  type    = number
  default = 7
}
variable "database_retained_transaction_log_days" {
  description = "Cloud SQL PostgreSQL Enterprise PITR transaction-log retention, in days."
  type        = number
  validation {
    condition     = var.database_retained_transaction_log_days >= 1 && var.database_retained_transaction_log_days <= 7
    error_message = "database_retained_transaction_log_days must be between 1 and 7 days for Cloud SQL Enterprise."
  }
}
variable "database_backup_start_time_utc" {
  type    = string
  default = "03:00"
}
variable "database_maintenance_day" {
  type    = number
  default = 7
}
variable "database_maintenance_hour_utc" {
  type    = number
  default = 4
}
variable "cloud_run_subnet_cidr" {
  type    = string
  default = "10.42.0.0/24"
}
variable "private_services_prefix_length" {
  type    = number
  default = 16
}
variable "api_cpu" {
  type    = string
  default = "1"
}
variable "api_memory" {
  type    = string
  default = "512Mi"
}
variable "api_concurrency" {
  type    = number
  default = 40
}
variable "api_request_timeout_seconds" {
  type    = number
  default = 60
}
variable "api_ingress" {
  type    = string
  default = "INGRESS_TRAFFIC_ALL"
}
variable "api_public_invoker" {
  type    = bool
  default = true
}
variable "api_deletion_protection" {
  type    = bool
  default = true
}
variable "workers_enabled" {
  type    = bool
  default = false
}
variable "default_organization_code" {
  type    = string
  default = "MVP-DEFAULT"
}
variable "default_organization_name" {
  type    = string
  default = "Pickleball MVP"
}
variable "migration_cpu" {
  type    = string
  default = "1"
}
variable "migration_memory" {
  type    = string
  default = "512Mi"
}
variable "migration_timeout_seconds" {
  type    = number
  default = 600
}
variable "migration_deletion_protection" {
  type    = bool
  default = true
}
variable "github_release_branch" {
  type    = string
  default = "main"
}
variable "resource_labels" {
  type    = map(string)
  default = {}
}

variable "database_availability_type" {
  description = "Cloud SQL availability type; choose ZONAL or REGIONAL only after cost approval."
  type        = string
  validation {
    condition     = contains(["ZONAL", "REGIONAL"], var.database_availability_type)
    error_message = "database_availability_type must be ZONAL or REGIONAL."
  }
}

variable "runtime_image_digest" {
  description = "Immutable Artifact Registry image reference using a sha256 digest."
  type        = string
  validation {
    condition     = can(regex("@sha256:[0-9a-f]{64}$", var.runtime_image_digest))
    error_message = "runtime_image_digest must end in an immutable @sha256:<64 lowercase hex> digest."
  }
}
