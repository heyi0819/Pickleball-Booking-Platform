[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$RepositoryRoot = $PSScriptRoot
$StateFile = Join-Path $env:TEMP 'pickleball-booking-platform-dev-processes.json'

function Stop-OwnedProcessTree {
    param(
        [AllowNull()][object]$ProcessId,
        [AllowNull()][string]$ExpectedStartTime,
        [Parameter(Mandatory)][string]$Name
    )

    if (-not $ProcessId) {
        return 'not managed'
    }

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $process) {
        return 'already stopped'
    }

    if (-not $ExpectedStartTime) {
        Write-Warning "$Name PID $ProcessId has no recorded StartTime. Treating it as stale; it was not stopped."
        return 'stale PID not stopped'
    }

    try {
        $expected = [DateTimeOffset]::Parse($ExpectedStartTime).ToUniversalTime()
        $actual = $process.StartTime.ToUniversalTime()
    } catch {
        Write-Warning "$Name PID $ProcessId could not be verified against its recorded StartTime. Treating it as stale; it was not stopped."
        return 'stale PID not stopped'
    }

    if ($actual -ne $expected.UtcDateTime) {
        Write-Warning "$Name PID $ProcessId has a different StartTime than the state file. Treating it as stale; it was not stopped."
        return 'stale PID not stopped'
    }

    Write-Host "Stopping $Name (PID $ProcessId)..."
    & taskkill.exe /PID $ProcessId /T | Out-Null
    Start-Sleep -Seconds 2
    if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
        Write-Warning "$Name did not exit cleanly; force stopping its process tree."
        & taskkill.exe /PID $ProcessId /T /F | Out-Null
        Start-Sleep -Seconds 1
    }

    if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
        Write-Error "Unable to stop $Name (PID $ProcessId)."
        return 'failed to stop'
    }
    return 'stopped'
}

$state = $null
$managedProcesses = $false
if (-not (Test-Path -LiteralPath $StateFile)) {
    Write-Host 'No Backend/LIFF/Admin process record created by start-dev.ps1 was found, so no unknown Windows processes will be stopped.'
} else {
    try {
        $state = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
        $stateRoot = [IO.Path]::GetFullPath([string]$state.RepositoryRoot)
        $expectedRoot = [IO.Path]::GetFullPath($RepositoryRoot)
        if (-not $stateRoot.Equals($expectedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw "The state file belongs to '$stateRoot', not this repository. It was left untouched."
        }
        $managedProcesses = $true
    } catch {
        Write-Error "Cannot safely use the development state file: $($_.Exception.Message)"
        exit 1
    }
}

$succeeded = $true
$backendStatus = 'not managed'
$liffStatus = 'not managed'
$adminStatus = 'not managed'
if ($managedProcesses) {
    $backendStatus = Stop-OwnedProcessTree -ProcessId $state.BackendPid -ExpectedStartTime $state.BackendStartTime -Name 'Backend'
    $liffStatus = Stop-OwnedProcessTree -ProcessId $state.LiffPid -ExpectedStartTime $state.LiffStartTime -Name 'LIFF'
    $adminStatus = Stop-OwnedProcessTree -ProcessId $state.AdminPid -ExpectedStartTime $state.AdminStartTime -Name 'Admin'
    if (@($backendStatus, $liffStatus, $adminStatus) -contains 'failed to stop') {
        $succeeded = $false
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error 'Docker CLI is unavailable, so PostgreSQL was not stopped.'
    $succeeded = $false
} else {
    Push-Location -LiteralPath $RepositoryRoot
    try {
        Write-Host 'Stopping PostgreSQL...'
        & docker compose stop postgres
        if ($LASTEXITCODE -ne 0) {
            Write-Error 'Docker Compose could not stop PostgreSQL.'
            $succeeded = $false
        }
    } catch {
        Write-Error "Could not stop PostgreSQL: $($_.Exception.Message)"
        $succeeded = $false
    } finally {
        Pop-Location
    }
}

if (-not $succeeded) {
    Write-Error "The development session was not fully stopped. The state file remains at '$StateFile' so this script can be retried."
    exit 1
}

if ($managedProcesses) {
    Remove-Item -LiteralPath $StateFile -Force
}

Write-Host ''
Write-Host '========================================'
Write-Host 'Pickleball Booking Platform DEV STOPPED'
Write-Host '========================================'
Write-Host ''
if ($managedProcesses) {
    Write-Host "Backend     : $backendStatus"
    Write-Host "LIFF        : $liffStatus"
    Write-Host "Admin       : $adminStatus"
} else {
    Write-Host 'Backend     : not managed'
    Write-Host 'LIFF        : not managed'
    Write-Host 'Admin       : not managed'
}
Write-Host 'PostgreSQL  : stopped'
Write-Host ''
Write-Host 'Docker Desktop remains running.'
Write-Host '========================================'
