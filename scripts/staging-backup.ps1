[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateSet('staging', 'rehearsal')] [string] $EnvironmentName,
    [Parameter(Mandatory)] [string] $BackupDirectory,
    [Parameter(Mandatory)] [string] $AgeRecipient,
    [Parameter(Mandatory)] [string] $Confirmation,
    [int] $RetentionCount = 7
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ageExecutable = if ($env:AGE_EXE) { $env:AGE_EXE } else { (Get-Command age -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $ageExecutable -PathType Leaf)) { throw 'AGE_EXE must point to an existing age executable.' }

$expectedConfirmation = if ($EnvironmentName -eq 'staging') { 'BACKUP_STAGING' } else { 'BACKUP_REHEARSAL' }
if ($Confirmation -ne $expectedConfirmation) { throw 'Explicit backup confirmation is required.' }
if ($AgeRecipient -notmatch '^age1[0-9ac-hj-np-z]{58}$') { throw 'Age recipient format is invalid.' }
if (-not $env:PGHOST -or -not $env:PGDATABASE -or -not $env:PGUSER -or -not $env:PGPASSWORD) { throw 'PGHOST, PGDATABASE, PGUSER, and PGPASSWORD must be supplied as environment variables.' }
if ($EnvironmentName -eq 'staging' -and $env:PGSSLMODE -ne 'require') { throw 'Staging backup requires PGSSLMODE=require.' }
if ($RetentionCount -lt 1) { throw 'RetentionCount must be positive.' }

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backupRoot = [System.IO.Path]::GetFullPath($BackupDirectory)
if ($backupRoot.StartsWith($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'BackupDirectory must be outside the repository.' }
[System.IO.Directory]::CreateDirectory($backupRoot) | Out-Null
$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$gitSha = (git -C $repositoryRoot rev-parse HEAD).Trim()
$prefix = "pickleball-$EnvironmentName-$timestamp"
$plain = Join-Path ([System.IO.Path]::GetTempPath()) ("$prefix-$([guid]::NewGuid().ToString('N')).dump")
$encrypted = Join-Path $backupRoot "$prefix.dump.age"
$metadata = Join-Path $backupRoot "$prefix.metadata.json"

try {
    $flywayVersion = & psql -X -At -v ON_ERROR_STOP=1 -c "select coalesce(max(version), 'baseline') from flyway_schema_history where success = true;"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to query Flyway history.' }
    $flywayVersion = $flywayVersion.Trim()
    & pg_dump --format=custom --no-owner --no-privileges --schema=public --file=$plain
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed.' }
    & $ageExecutable -r $AgeRecipient -o $encrypted $plain
    if ($LASTEXITCODE -ne 0) { throw 'age encryption failed.' }
    $record = [ordered]@{ timestampUtc = $timestamp; environment = $EnvironmentName; gitSha = $gitSha; flywayVersion = $flywayVersion; databaseIdentifier = $env:PGDATABASE; archiveSha256 = (Get-FileHash -LiteralPath $encrypted -Algorithm SHA256).Hash }
    [System.IO.File]::WriteAllText($metadata, ($record | ConvertTo-Json -Compress), [System.Text.UTF8Encoding]::new($false))
    Get-ChildItem -LiteralPath $backupRoot -Filter "pickleball-$EnvironmentName-*.dump.age" | Sort-Object LastWriteTimeUtc -Descending | Select-Object -Skip $RetentionCount | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force; Remove-Item -LiteralPath ($_.FullName -replace '\.dump\.age$', '.metadata.json') -Force -ErrorAction SilentlyContinue }
    Write-Output "Encrypted backup created: $encrypted"
} finally {
    Remove-Item -LiteralPath $plain -Force -ErrorAction SilentlyContinue
}
