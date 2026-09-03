output "api_service_name" { value = google_cloud_run_v2_service.api.name }
output "api_url" { value = google_cloud_run_v2_service.api.uri }
output "migration_job_name" { value = google_cloud_run_v2_job.migration.name }
output "cloud_sql_connection_name" { value = google_sql_database_instance.postgres.connection_name }
output "cloud_sql_private_ip" { value = google_sql_database_instance.postgres.private_ip_address }
output "runtime_service_account" { value = google_service_account.api.email }
output "migration_service_account" { value = google_service_account.migration.email }
output "release_service_account" { value = google_service_account.release.email }
output "github_workload_identity_provider" { value = google_iam_workload_identity_pool_provider.github.name }
output "backup_project_interface" {
  value = {
    project_id      = var.backup_project_id
    bucket_location = var.backup_bucket_location
    writer_identity = var.backup_writer_identity
    retention_days  = var.backup_retention_days
  }
}
