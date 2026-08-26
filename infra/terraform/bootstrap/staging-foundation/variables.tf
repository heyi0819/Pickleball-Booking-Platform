variable "project_id" {
  description = "Dedicated GCP project ID for staging."
  type        = string
}

variable "region" {
  description = "Primary staging region."
  type        = string
  default     = "asia-east1"
}

variable "github_repository" {
  description = "GitHub repository allowed to federate into the staging deployer service account."
  type        = string
  default     = "heyi0819/Pickleball-Booking-Platform"
}

variable "github_branch" {
  description = "Only this branch may use the staging Workload Identity Federation provider."
  type        = string
  default     = "main"
}

variable "terraform_state_bucket_name" {
  description = "Globally unique GCS bucket name used for Terraform state after bootstrap."
  type        = string
}

variable "artifact_repository_id" {
  description = "Artifact Registry Docker repository ID."
  type        = string
  default     = "pickleball"
}

variable "cloud_sql_instance_name" {
  description = "Cloud SQL PostgreSQL instance name."
  type        = string
  default     = "pickleball-stg-pg18"
}

variable "database_name" {
  description = "Application database name."
  type        = string
  default     = "pickleball_booking"
}

variable "database_tier" {
  description = "Cloud SQL machine tier. Keep staging intentionally small and change only after checking current GCP pricing/capacity."
  type        = string
  default     = "db-f1-micro"
}

variable "database_deletion_protection" {
  description = "Protect the staging Cloud SQL instance from accidental Terraform deletion."
  type        = bool
  default     = true
}

variable "firebase_liff_site_id" {
  description = "Globally unique Firebase Hosting site ID for the LIFF/Mobile Web app."
  type        = string
}

variable "firebase_admin_site_id" {
  description = "Globally unique Firebase Hosting site ID for the Admin app."
  type        = string
}
