[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateSet('staging', 'rehearsal')] [string] $EnvironmentName,
    [Parameter(Mandatory)] [string] $BackupFile,
    [Parameter(Mandatory)] [string] $IdentityFile,
    [Parameter(Mandatory)] [string] $ActiveDatabaseName,
    [Parameter(Mandatory)] [string] $Confirmation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Command([string] $Name) {
    $command = Get-Command $Name -ErrorAction Stop
    return $command.Source
}

function Resolve-AgeExecutable {
    if ($env:AGE_EXE) {
        if (-not (Test-Path -LiteralPath $env:AGE_EXE -PathType Leaf)) { throw 'AGE_EXE must point to an existing age executable.' }
        return $env:AGE_EXE
    }
    return Assert-Command 'age'
}

$expectedConfirmation = if ($EnvironmentName -eq 'staging') { 'RESTORE_ISOLATED_STAGING' } else { 'RESTORE_ISOLATED_REHEARSAL' }
if ($Confirmation -ne $expectedConfirmation) { throw 'Explicit isolated restore confirmation is required.' }
if (-not $env:PGDATABASE -or -not $env:PGHOST -or -not $env:PGUSER -or -not $env:PGPASSWORD) { throw 'PGHOST, PGDATABASE, PGUSER, and PGPASSWORD must be supplied as environment variables.' }
if ($env:PGDATABASE -eq $ActiveDatabaseName) { throw 'Restore target must not be the active database.' }
if ($env:PGDATABASE -notmatch '^pickleball_restore_[a-z0-9_]+$') { throw 'Restore target database name must start with pickleball_restore_.' }
if ($env:PGHOST -notin @('localhost', '127.0.0.1', '::1')) { throw 'Restore target must use a local PostgreSQL host.' }
if ($EnvironmentName -eq 'staging' -and $env:PGSSLMODE -ne 'require') { throw 'Staging source handling requires PGSSLMODE=require.' }
if ($BackupFile -notmatch '^.*pickleball-(staging|rehearsal)-\d{8}T\d{6}Z\.dump\.age$') { throw 'BackupFile does not match the controlled archive naming convention.' }
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf) -or -not (Test-Path -LiteralPath $IdentityFile -PathType Leaf)) { throw 'BackupFile and IdentityFile must exist.' }

$ageExecutable = Resolve-AgeExecutable
$null = Assert-Command 'pg_restore'
$null = Assert-Command 'psql'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$expectedFlywayVersion = (Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'backend/src/main/resources/db/migration') -Filter 'V*__*.sql' | ForEach-Object {
    if ($_.Name -match '^V(\d+)__') { [int]$Matches[1] }
} | Measure-Object -Maximum).Maximum
$plain = Join-Path ([System.IO.Path]::GetTempPath()) ("pickleball-restore-$([guid]::NewGuid().ToString('N')).dump")

try {
    & $ageExecutable -d -i $IdentityFile -o $plain $BackupFile
    if ($LASTEXITCODE -ne 0) { throw 'age decryption failed.' }
    & pg_restore --list $plain *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Decrypted archive is not a readable PostgreSQL custom-format dump.' }
    & pg_restore --clean --if-exists --no-owner --no-privileges --dbname=$env:PGDATABASE $plain
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed.' }

    $restoredFlywayVersion = (& psql -X -At -v ON_ERROR_STOP=1 -c "select max(version)::int from flyway_schema_history where success = true;").Trim()
    if ($LASTEXITCODE -ne 0 -or $restoredFlywayVersion -ne [string]$expectedFlywayVersion) { throw 'Restored Flyway history does not match the repository migration level.' }

    $criticalTables = @('users', 'organizations', 'user_role_assignments', 'coach_availability_proposals', 'lesson_requests', 'course_offerings', 'course_sessions', 'course_offering_registrations', 'receivables', 'payments', 'refunds', 'audit_logs', 'outbox_events')
    $tableValues = ($criticalTables | ForEach-Object { "('$($_)')" }) -join ','
    $missing = (& psql -X -At -v ON_ERROR_STOP=1 -c "with expected(table_name) as (values $tableValues) select expected.table_name from expected left join information_schema.tables actual on actual.table_schema = 'public' and actual.table_name = expected.table_name where actual.table_name is null order by expected.table_name;").Trim()
    if ($LASTEXITCODE -ne 0 -or $missing) { throw "Critical table validation failed: $missing" }

    $shapeCount = (& psql -X -At -v ON_ERROR_STOP=1 -c "select count(*) from information_schema.columns where table_schema = 'public' and ((table_name = 'users' and column_name = 'id' and data_type = 'uuid') or (table_name = 'course_sessions' and column_name = 'scheduled_start_at' and data_type = 'timestamp with time zone') or (table_name = 'receivables' and column_name = 'total_amount' and data_type = 'numeric'));").Trim()
    if ($LASTEXITCODE -ne 0 -or $shapeCount -ne '3') { throw 'UUID, TIMESTAMPTZ, or NUMERIC schema validation failed.' }

    $extension = (& psql -X -At -v ON_ERROR_STOP=1 -c "select extname from pg_extension where extname = 'btree_gist';").Trim()
    if ($LASTEXITCODE -ne 0 -or $extension -ne 'btree_gist') { throw 'Required btree_gist extension is missing from the isolated restore target.' }

    $counts = (& psql -X -At -v ON_ERROR_STOP=1 -c "select count(*) from users; select count(*) from organizations; select count(*) from audit_logs; select count(*) from outbox_events;")
    if ($LASTEXITCODE -ne 0) { throw 'Non-sensitive row-count validation failed.' }
    Write-Output "Isolated restore completed and validated: Flyway V$restoredFlywayVersion; users/organizations/audit/outbox counts recorded without row data."
} finally {
    Remove-Item -LiteralPath $plain -Force -ErrorAction SilentlyContinue
}
