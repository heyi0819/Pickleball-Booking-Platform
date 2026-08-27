variable "project_id" {
  description = "Dedicated GCP staging project ID."
  type        = string
}

variable "region" {
  description = "Staging region."
  type        = string
  default     = "asia-east1"
}

variable "runtime_image" {
  description = "Immutable backend container image reference, preferably by sha256 digest."
  type        = string
}

variable "cloud_sql_instance_name" {
  description = "Existing staging Cloud SQL instance created by the foundation bootstrap."
  type        = string
  default     = "pickleball-stg-pg18"
}

variable "database_name" {
  description = "Application database."
  type        = string
  default     = "pickleball_booking"
}

variable "database_username" {
  description = "Application database user. The password is read from Secret Manager."
  type        = string
  default     = "pickleball_app"
}

variable "database_password_secret_id" {
  description = "Secret Manager secret containing the database password."
  type        = string
  default     = "pickleball-stg-db-password"
}

variable "jwt_signing_secret_id" {
  description = "Secret Manager secret containing the platform JWT signing secret."
  type        = string
  default     = "pickleball-stg-jwt-signing-secret"
}

variable "api_service_account_email" {
  description = "Existing runtime service account for the Cloud Run API."
  type        = string
}

variable "migration_service_account_email" {
  description = "Existing runtime service account for the Cloud Run migration job."
  type        = string
}

variable "line_login_channel_id" {
  description = "Staging LINE Login channel ID. This identifier is not treated as a secret."
  type        = string
  default     = ""
}

variable "default_organization_code" {
  type    = string
  default = "MVP-DEFAULT"
}

variable "default_organization_name" {
  type    = string
  default = "Pickleball MVP Staging"
}

variable "api_min_instances" {
  type    = number
  default = 0
}

variable "api_max_instances" {
  type    = number
  default = 3
}
