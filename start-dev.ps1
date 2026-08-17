[CmdletBinding()]
param(
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'

$RepositoryRoot = $PSScriptRoot
$StateFile = Join-Path $env:TEMP 'pickleball-booking-platform-dev-processes.json'
$HealthUrl = 'http://localhost:8080/actuator/health'

function Stop-WithError {
    param([Parameter(Mandatory)][string]$Message)

    Write-Error $Message
    exit 1
}

function Require-Command {
    param([Parameter(Mandatory)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Stop-WithError "Required command '$Name' was not found on PATH. Install it and restart PowerShell."
    }
}

function Import-DotEnv {
    param([Parameter(Mandatory)][string]$Path)

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*(?:#|$)') {
            continue
        }

        if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*)$') {
            $name = $matches[1]
            $value = $matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            Set-Item -Path "Env:$name" -Value $value
            continue
        }

        Stop-WithError "Invalid .env entry: '$line'. Use KEY=VALUE syntax."
    }
}

function Test-DockerDaemon {
    # Run through cmd.exe so Docker CLI stderr is redirected before Windows PowerShell
    # can turn it into a NativeCommandError when $ErrorActionPreference is 'Stop'.
    & cmd.exe /d /c 'docker info >nul 2>&1'
    return $LASTEXITCODE -eq 0
}

function Wait-ForDocker {
    if (Test-DockerDaemon) {
        return
    }

    $dockerDesktopCandidates = @(
        if ($env:ProgramFiles) {
            Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
        }
        if ($env:LOCALAPPDATA) {
            Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'
        }
        if ($env:LOCALAPPDATA) {
            Join-Path $env:LOCALAPPDATA 'Programs\Docker\Desktop\Docker Desktop.exe'
        }
    )
    $dockerDesktop = $dockerDesktopCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $dockerDesktop) {
        Stop-WithError "Docker daemon is unavailable and Docker Desktop was not found in the standard install locations: $($dockerDesktopCandidates -join '; '). Open Docker Desktop manually, then rerun this script."
    }

    Write-Host 'Docker daemon is unavailable. Starting Docker Desktop and waiting up to 120 seconds...'
    Start-Process -FilePath $dockerDesktop | Out-Null
    $deadline = (Get-Date).AddSeconds(120)
    do {
        Start-Sleep -Seconds 2
        if (Test-DockerDaemon) {
            return
        }
    } while ((Get-Date) -lt $deadline)

    Stop-WithError 'Docker Desktop did not become ready within 120 seconds. Check Docker Desktop, then rerun this script.'
}

function Wait-ForPostgres {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        $containerId = (& docker compose ps -q postgres).Trim()
        if ($LASTEXITCODE -eq 0 -and $containerId) {
            $health = (& docker inspect --format '{{.State.Health.Status}}' $containerId 2>$null).Trim()
            if ($LASTEXITCODE -eq 0 -and $health -eq 'healthy') {
                return 'healthy'
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    & docker compose ps
    Stop-WithError 'PostgreSQL did not become healthy within 60 seconds. Check the Compose output above before starting the backend.'
}

function Wait-ForBackend {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        try {
            $health = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 3
            if ($health.status -eq 'UP') {
                return 'UP'
            }
        } catch {
            # The backend is still starting. Its dedicated console has the detailed logs.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    Stop-WithError "Backend did not report UP at $HealthUrl within 90 seconds. Check the 'Pickleball - Backend' window."
}

function Test-BackendHealthy {
    try {
        $health = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 3
        return $health.status -eq 'UP'
    } catch {
        return $false
    }
}

function Test-PortInUse {
    param([Parameter(Mandatory)][int]$Port)

    return Test-NetConnection -ComputerName localhost -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue
}

function Wait-ForPort {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$Name
    )

    $deadline = (Get-Date).AddSeconds(45)
    do {
        if (Test-PortInUse -Port $Port) {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    Stop-WithError "$Name did not open localhost:$Port. Check the '$Name' PowerShell window (the port is intentionally strict)."
}

function Assert-PortAvailable {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$ServiceName
    )

    if (Test-PortInUse -Port $Port) {
        Stop-WithError "$ServiceName cannot start because localhost:$Port is already in use by an unknown process. No process was changed and no alternate port was selected."
    }
}

function Start-DeveloperProcess {
    param(
        [Parameter(Mandatory)][string]$Title,
        [Parameter(Mandatory)][string]$Command
    )

    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Command))
    Start-Process -FilePath 'powershell.exe' -WorkingDirectory $RepositoryRoot -ArgumentList @('-NoExit', '-EncodedCommand', $encodedCommand) -PassThru
}

function Write-State {
    param(
        [AllowNull()][System.Diagnostics.Process]$Backend,
        [AllowNull()][System.Diagnostics.Process]$Liff,
        [AllowNull()][System.Diagnostics.Process]$Admin
    )

    [ordered]@{
        RepositoryRoot = $RepositoryRoot
        Timestamp      = (Get-Date).ToUniversalTime().ToString('o')
        BackendPid     = if ($Backend) { $Backend.Id } else { $null }
        BackendStartTime = if ($Backend) { $Backend.StartTime.ToUniversalTime().ToString('o') } else { $null }
        LiffPid        = if ($Liff) { $Liff.Id } else { $null }
        LiffStartTime  = if ($Liff) { $Liff.StartTime.ToUniversalTime().ToString('o') } else { $null }
        AdminPid       = if ($Admin) { $Admin.Id } else { $null }
        AdminStartTime = if ($Admin) { $Admin.StartTime.ToUniversalTime().ToString('o') } else { $null }
    } | ConvertTo-Json | Set-Content -LiteralPath $StateFile -Encoding utf8
}

Push-Location -LiteralPath $RepositoryRoot
try {
    foreach ($command in 'docker', 'npm', 'java') {
        Require-Command $command
    }
    foreach ($path in 'compose.yaml', 'backend\mvnw.cmd', 'frontend\package.json') {
        if (-not (Test-Path -LiteralPath (Join-Path $RepositoryRoot $path))) {
            Stop-WithError "Required project file is missing: $path"
        }
    }

    if (Test-Path -LiteralPath $StateFile) {
        try {
            $existingState = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
            $stateRoot = [IO.Path]::GetFullPath([string]$existingState.RepositoryRoot)
            $expectedRoot = [IO.Path]::GetFullPath($RepositoryRoot)
            if (-not $stateRoot.Equals($expectedRoot, [StringComparison]::OrdinalIgnoreCase)) {
                Stop-WithError "The existing development state file belongs to '$stateRoot'. It was left untouched."
            }
            $existingPids = @($existingState.BackendPid, $existingState.LiffPid, $existingState.AdminPid) | Where-Object { $_ }
            $activePids = @($existingPids | Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })
            if ($activePids.Count -gt 0) {
                Stop-WithError "An existing Pickleball development session is still running (PID(s): $($activePids.Id -join ', ')). Run .\stop-dev.ps1 first."
            }
        } catch {
            Stop-WithError "Cannot safely use the existing development state file: $($_.Exception.Message)"
        }
        Remove-Item -LiteralPath $StateFile -Force
    }

    $envFile = Join-Path $RepositoryRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile)) {
        $exampleFile = Join-Path $RepositoryRoot '.env.example'
        if (-not (Test-Path -LiteralPath $exampleFile)) {
            Stop-WithError '.env is missing and no .env.example is available to create it.'
        }
        Copy-Item -LiteralPath $exampleFile -Destination $envFile
        Write-Warning 'Created .env from .env.example. It contains placeholders only; set any required local credentials before using external integrations.'
    }
    Import-DotEnv -Path $envFile

    Wait-ForDocker
    & docker compose up -d postgres
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError 'Unable to start the PostgreSQL Compose service.'
    }
    $postgresHealth = Wait-ForPostgres

    $backend = $null
    if (Test-BackendHealthy) {
        Write-Host 'Backend already running at http://localhost:8080; it is not managed by this session.'
        $backendHealth = 'UP (already running)'
    } elseif (Test-PortInUse -Port 8080) {
        Stop-WithError 'Backend cannot start because localhost:8080 is already in use and /actuator/health did not report status=UP. No process was changed.'
    } else {
        $escapedBackendDir = (Join-Path $RepositoryRoot 'backend').Replace("'", "''")
        $backend = Start-DeveloperProcess -Title 'Pickleball - Backend' -Command "`$host.UI.RawUI.WindowTitle = 'Pickleball - Backend'; `$env:MAVEN_USER_HOME = Join-Path `$env:USERPROFILE '.m2'; Set-Location -LiteralPath '$escapedBackendDir'; & '.\mvnw.cmd' spring-boot:run"
        Write-State -Backend $backend -Liff $null -Admin $null
        $backendHealth = Wait-ForBackend
    }

    Assert-PortAvailable -Port 5173 -ServiceName 'LIFF'
    $escapedFrontendDir = (Join-Path $RepositoryRoot 'frontend').Replace("'", "''")
    $liff = Start-DeveloperProcess -Title 'Pickleball - LIFF' -Command "`$host.UI.RawUI.WindowTitle = 'Pickleball - LIFF'; Set-Location -LiteralPath '$escapedFrontendDir'; npm run dev -w '@pickleball/liff' -- --port 5173 --strictPort"
    Write-State -Backend $backend -Liff $liff -Admin $null
    Wait-ForPort -Port 5173 -Name 'Pickleball - LIFF'

    Assert-PortAvailable -Port 5174 -ServiceName 'Admin'
    $admin = Start-DeveloperProcess -Title 'Pickleball - Admin' -Command "`$host.UI.RawUI.WindowTitle = 'Pickleball - Admin'; Set-Location -LiteralPath '$escapedFrontendDir'; npm run dev -w '@pickleball/admin' -- --port 5174 --strictPort"
    Write-State -Backend $backend -Liff $liff -Admin $admin
    Wait-ForPort -Port 5174 -Name 'Pickleball - Admin'

    Write-Host ''
    Write-Host '========================================'
    Write-Host 'Pickleball Booking Platform DEV READY'
    Write-Host '========================================'
    Write-Host ''
    Write-Host 'PostgreSQL : localhost:5432'
    Write-Host 'Backend    : http://localhost:8080'
    Write-Host "Health     : $HealthUrl"
    Write-Host 'LIFF       : http://localhost:5173'
    Write-Host 'Admin      : http://localhost:5174'
    Write-Host ''
    Write-Host "Backend Health: $backendHealth"
    Write-Host "PostgreSQL: $postgresHealth"
    Write-Host ''
    Write-Host 'Use .\stop-dev.ps1 to stop this development session.'
    Write-Host '========================================'

    if (-not $NoBrowser) {
        Start-Process 'http://localhost:5173'
        Start-Process 'http://localhost:5174'
    }
} catch {
    Stop-WithError $_.Exception.Message
} finally {
    Pop-Location
}
