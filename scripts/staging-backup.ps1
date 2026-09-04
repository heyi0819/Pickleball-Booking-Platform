[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateSet('staging', 'rehearsal')] [string] $EnvironmentName,
    [Parameter(Mandatory)] [string] $BackupDirectory,
    [Parameter(Mandatory)] [string] $AgeRecipient,
    [Parameter(Mandatory)] [string] $Confirmation,
    [ValidateRange(1, 3650)] [int] $RetentionDays = 35,
    [switch] $DryRun
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

function Test-ChildPath([string] $Child, [string] $Parent) {
    $relative = [System.IO.Path]::GetRelativePath($Parent, $Child)
    return $relative -ne '' -and $relative -ne '..' -and -not $relative.StartsWith("..$([System.IO.Path]::DirectorySeparatorChar)") -and -not [System.IO.Path]::IsPathRooted($relative)
}

$expectedConfirmation = if ($EnvironmentName -eq 'staging') { 'BACKUP_STAGING' } else { 'BACKUP_REHEARSAL' }
if ($Confirmation -ne $expectedConfirmation) { throw 'Explicit backup confirmation is required.' }
if ($AgeRecipient -notmatch '^age1[0-9ac-hj-np-z]{58}$') { throw 'Age recipient format is invalid.' }
if ($RetentionDays -lt 1) { throw 'RetentionDays must be positive.' }

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backupRoot = [System.IO.Path]::GetFullPath($BackupDirectory)
if (Test-ChildPath $backupRoot $repositoryRoot) { throw 'BackupDirectory must be outside the repository.' }

$ageExecutable = Resolve-AgeExecutable
$null = Assert-Command 'pg_dump'
$null = Assert-Command 'psql'

if ($DryRun) {
    Write-Output "Backup dry run passed for $EnvironmentName; no database connection, dump, encryption, or deletion was performed."
    return
}

if (-not $env:PGHOST -or -not $env:PGDATABASE -or -not $env:PGUSER -or -not $env:PGPASSWORD) { throw 'PGHOST, PGDATABASE, PGUSER, and PGPASSWORD must be supplied as environment variables.' }
if ($EnvironmentName -eq 'staging' -and $env:PGSSLMODE -ne 'require') { throw 'Staging backup requires PGSSLMODE=require.' }

[System.IO.Directory]::CreateDirectory($backupRoot) | Out-Null
$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$gitSha = (git -C $repositoryRoot rev-parse HEAD).Trim()
$prefix = "pickleball-$EnvironmentName-$timestamp"
$plain = Join-Path ([System.IO.Path]::GetTempPath()) ("$prefix-$([guid]::NewGuid().ToString('N')).dump")
$partialEncrypted = Join-Path $backupRoot (".$prefix-$([guid]::NewGuid().ToString('N')).partial")
$encrypted = Join-Path $backupRoot "$prefix.dump.age"
$metadata = Join-Path $backupRoot "$prefix.metadata.json"

try {
    $flywayVersion = & psql -X -At -v ON_ERROR_STOP=1 -c "select coalesce(max(version), 'baseline') from flyway_schema_history where success = true;"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to query Flyway history.' }
    $flywayVersion = $flywayVersion.Trim()

    & pg_dump --format=custom --no-owner --no-privileges --schema=public --file=$plain
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $plain -PathType Leaf)) { throw 'pg_dump failed.' }

    & $ageExecutable -r $AgeRecipient -o $partialEncrypted $plain
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $partialEncrypted -PathType Leaf) -or (Get-Item -LiteralPath $partialEncrypted).Length -eq 0) { throw 'age encryption failed.' }
    if (Test-Path -LiteralPath $encrypted -PathType Leaf) { throw 'Refusing to overwrite an existing encrypted archive.' }
    Move-Item -LiteralPath $partialEncrypted -Destination $encrypted

    $record = [ordered]@{ timestampUtc = $timestamp; environment = $EnvironmentName; gitSha = $gitSha; flywayVersion = $flywayVersion; archiveSha256 = (Get-FileHash -LiteralPath $encrypted -Algorithm SHA256).Hash }
    [System.IO.File]::WriteAllText($metadata, ($record | ConvertTo-Json -Compress), [System.Text.UTF8Encoding]::new($false))

    $cutoff = [DateTime]::UtcNow.AddDays(-$RetentionDays)
    Get-ChildItem -LiteralPath $backupRoot -File | Where-Object {
        $_.Name -match "^pickleball-$EnvironmentName-\d{8}T\d{6}Z\.dump\.age$" -and $_.LastWriteTimeUtc -lt $cutoff
    } | ForEach-Object {
        $archive = $_.FullName
        $sidecar = [System.IO.Path]::ChangeExtension($archive, $null) -replace '\.dump$', '.metadata.json'
        Remove-Item -LiteralPath $archive -Force
        if (Test-Path -LiteralPath $sidecar -PathType Leaf) { Remove-Item -LiteralPath $sidecar -Force }
    }
    Write-Output "Encrypted backup created: $encrypted"
} finally {
    Remove-Item -LiteralPath $plain -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $partialEncrypted -Force -ErrorAction SilentlyContinue
}
