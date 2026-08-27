output "project_number" {
  value = data.google_project.current.number
}

output "terraform_state_bucket" {
  value = google_storage_bucket.terraform_state.name
}

output "artifact_registry_repository" {
  value = google_artifact_registry_repository.backend.name
}

output "artifact_registry_host" {
  value = "${var.region}-docker.pkg.dev"
}

output "cloud_sql_instance_name" {
  value = google_sql_database_instance.postgres.name
}

output "cloud_sql_connection_name" {
  value = google_sql_database_instance.postgres.connection_name
}

output "api_service_account_email" {
  value = google_service_account.api.email
}

output "migration_service_account_email" {
  value = google_service_account.migration.email
}

output "deployer_service_account_email" {
  value = google_service_account.deployer.email
}

output "workload_identity_provider" {
  value = google_iam_workload_identity_pool_provider.github.name
}

output "required_secret_ids" {
  value = sort(tolist(local.secret_ids))
}

output "firebase_liff_site_id" {
  value = google_firebase_hosting_site.liff.site_id
}

output "firebase_liff_url" {
  value = google_firebase_hosting_site.liff.default_url
}

output "firebase_admin_site_id" {
  value = google_firebase_hosting_site.admin.site_id
}

output "firebase_admin_url" {
  value = google_firebase_hosting_site.admin.default_url
}
