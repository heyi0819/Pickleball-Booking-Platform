output "api_url" {
  value = google_cloud_run_v2_service.api.uri
}

output "api_service_name" {
  value = google_cloud_run_v2_service.api.name
}

output "migration_job_name" {
  value = google_cloud_run_v2_job.migration.name
}

output "runtime_image" {
  value = var.runtime_image
}

output "cloud_sql_connection_name" {
  value = data.google_sql_database_instance.postgres.connection_name
}
