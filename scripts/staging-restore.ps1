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
$ageExecutable = if ($env:AGE_EXE) { $env:AGE_EXE } else { (Get-Command age -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $ageExecutable -PathType Leaf)) { throw 'AGE_EXE must point to an existing age executable.' }

$expectedConfirmation = if ($EnvironmentName -eq 'staging') { 'RESTORE_ISOLATED_STAGING' } else { 'RESTORE_ISOLATED_REHEARSAL' }
if ($Confirmation -ne $expectedConfirmation) { throw 'Explicit isolated restore confirmation is required.' }
if (-not $env:PGDATABASE -or -not $env:PGHOST -or -not $env:PGUSER -or -not $env:PGPASSWORD) { throw 'PGHOST, PGDATABASE, PGUSER, and PGPASSWORD must be supplied as environment variables.' }
if ($env:PGDATABASE -eq $ActiveDatabaseName) { throw 'Restore target must not be the active staging database.' }
if ($EnvironmentName -eq 'staging' -and $env:PGSSLMODE -ne 'require') { throw 'Staging restore requires PGSSLMODE=require.' }
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf) -or -not (Test-Path -LiteralPath $IdentityFile -PathType Leaf)) { throw 'BackupFile and IdentityFile must exist.' }

$plain = [System.IO.Path]::GetTempFileName()
try {
    & $ageExecutable -d -i $IdentityFile -o $plain $BackupFile
    if ($LASTEXITCODE -ne 0) { throw 'age decryption failed.' }
    & pg_restore --clean --if-exists --no-owner --no-privileges --dbname=$env:PGDATABASE $plain
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed.' }
    Write-Output "Isolated restore completed for database: $($env:PGDATABASE)"
} finally {
    Remove-Item -LiteralPath $plain -Force -ErrorAction SilentlyContinue
}
