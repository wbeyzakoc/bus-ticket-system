param(
    [Parameter(Position = 0)]
    [string]$Command = ""
)

$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $RootDir ".run"
$BackendDir = Join-Path $RootDir "backend-app"
$LocalEnvFile = Join-Path $RootDir ".env.local"

$BackendPidFile = Join-Path $RunDir "backend.pid"
$FrontendPidFile = Join-Path $RunDir "frontend.pid"
$BackendLogFile = Join-Path $RunDir "backend.log"
$FrontendLogFile = Join-Path $RunDir "frontend.log"

$BackendHealthUrl = "http://127.0.0.1:8080/api/cities"
$FrontendUrl = "http://127.0.0.1:8000/index.html"
$DockerComposeFile = Join-Path $RootDir "docker-compose.yml"
$DbServiceName = "db"

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

function Load-LocalEnv {
    if (-not (Test-Path $LocalEnvFile)) {
        return
    }

    Get-Content $LocalEnvFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $parts = $line -split "=", 2
        if ($parts.Count -ne 2) {
            return
        }

        [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
    }
}

function Get-DbHost {
    if ($env:BUSGO_DB_HOST) { return $env:BUSGO_DB_HOST }
    return "127.0.0.1"
}

function Get-DbPort {
    if ($env:BUSGO_DB_PORT) { return [int]$env:BUSGO_DB_PORT }
    return 3306
}

function Get-PidFromFile([string]$Path) {
    if (-not (Test-Path $Path)) {
        return $null
    }

    $raw = (Get-Content $Path -Raw).Trim()
    if (-not $raw) {
        return $null
    }

    return [int]$raw
}

function Test-PidRunning([int]$Pid) {
    try {
        Get-Process -Id $Pid -ErrorAction Stop | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Test-UrlReady([string]$Url) {
    try {
        Invoke-WebRequest -Uri $Url -TimeoutSec 2 -UseBasicParsing | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Test-PortOpen([string]$HostName, [int]$Port, [int]$TimeoutMs = 1000) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-ForUrl([string]$Url, [string]$Label, [int]$TimeoutSeconds = 60) {
    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        if (Test-UrlReady $Url) {
            return
        }
        Start-Sleep -Seconds 1
    }

    throw "$Label did not become ready within ${TimeoutSeconds}s."
}

function Wait-ForPort([string]$HostName, [int]$Port, [string]$Label, [int]$TimeoutSeconds = 60) {
    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        if (Test-PortOpen $HostName $Port) {
            return
        }
        Start-Sleep -Seconds 1
    }

    throw "$Label did not become ready on ${HostName}:$Port within ${TimeoutSeconds}s."
}

function Get-DockerComposeCommand {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        try {
            & docker compose version | Out-Null
            return @("docker", "compose")
        } catch {
        }
    }

    $dockerCompose = Get-Command docker-compose -ErrorAction SilentlyContinue
    if ($dockerCompose) {
        return @("docker-compose")
    }

    return $null
}

function Invoke-DockerCompose {
    param([string[]]$Args)

    $command = Get-DockerComposeCommand
    if (-not $command) {
        throw "Database is not reachable and Docker Compose is not available."
    }

    if ($command.Count -eq 2) {
        & $command[0] $command[1] @Args
    } else {
        & $command[0] @Args
    }
}

function Get-MavenCommand {
    $mvnCmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mvnCmd) { return $mvnCmd.Source }

    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvn) { return $mvn.Source }

    return $null
}

function Get-PythonCommand {
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) { return @($py.Source, "-3") }

    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) { return @($python.Source) }

    $python3 = Get-Command python3 -ErrorAction SilentlyContinue
    if ($python3) { return @($python3.Source) }

    return $null
}

function Start-Database {
    $dbHost = Get-DbHost
    $dbPort = Get-DbPort

    if (Test-PortOpen $dbHost $dbPort) {
        Write-Host "Database already reachable at ${dbHost}:$dbPort"
        return
    }

    if (-not (Test-Path $DockerComposeFile)) {
        throw "Database is not reachable at ${dbHost}:$dbPort and docker-compose.yml was not found."
    }

    Push-Location $RootDir
    try {
        Invoke-DockerCompose -Args @("up", "-d", $DbServiceName) | Out-Null
    } finally {
        Pop-Location
    }

    Wait-ForPort $dbHost $dbPort "Database" 60
    Write-Host "Database started: ${dbHost}:$dbPort"
}

function Start-Backend {
    $backendPid = Get-PidFromFile $BackendPidFile
    if ($backendPid -and (Test-PidRunning $backendPid)) {
        Write-Host "Backend already running with PID $backendPid"
        return
    }

    if (Test-UrlReady $BackendHealthUrl) {
        Write-Host "Backend already reachable at $BackendHealthUrl"
        return
    }

    $mavenCommand = Get-MavenCommand
    if (-not $mavenCommand) {
        throw "Maven is required to start the backend."
    }

    $process = Start-Process -FilePath $mavenCommand `
        -ArgumentList @("spring-boot:run") `
        -WorkingDirectory $BackendDir `
        -RedirectStandardOutput $BackendLogFile `
        -RedirectStandardError $BackendLogFile `
        -PassThru

    Set-Content -Path $BackendPidFile -Value $process.Id

    try {
        Wait-ForUrl $BackendHealthUrl "Backend" 60
    } catch {
        throw "$($_.Exception.Message)`nBackend log: $BackendLogFile"
    }

    Write-Host "Backend started: http://127.0.0.1:8080/api"
}

function Start-Frontend {
    $frontendPid = Get-PidFromFile $FrontendPidFile
    if ($frontendPid -and (Test-PidRunning $frontendPid)) {
        Write-Host "Frontend already running with PID $frontendPid"
        return
    }

    if (Test-UrlReady $FrontendUrl) {
        Write-Host "Frontend already reachable at $FrontendUrl"
        return
    }

    $pythonCommand = Get-PythonCommand
    if (-not $pythonCommand) {
        throw "Python 3 is required to start the frontend."
    }

    $pythonArgs = @()
    if ($pythonCommand.Count -gt 1) {
        $pythonArgs += $pythonCommand | Select-Object -Skip 1
    }
    $pythonArgs += @("-m", "http.server", "8000", "--bind", "127.0.0.1")

    $process = Start-Process -FilePath $pythonCommand[0] `
        -ArgumentList $pythonArgs `
        -WorkingDirectory $RootDir `
        -RedirectStandardOutput $FrontendLogFile `
        -RedirectStandardError $FrontendLogFile `
        -PassThru

    Set-Content -Path $FrontendPidFile -Value $process.Id

    try {
        Wait-ForUrl $FrontendUrl "Frontend" 15
    } catch {
        throw "$($_.Exception.Message)`nFrontend log: $FrontendLogFile"
    }

    Write-Host "Frontend started: http://127.0.0.1:8000"
}

function Stop-Database {
    if (-not (Test-Path $DockerComposeFile)) {
        return
    }

    $command = Get-DockerComposeCommand
    if (-not $command) {
        return
    }

    Push-Location $RootDir
    try {
        Invoke-DockerCompose -Args @("stop", $DbServiceName) | Out-Null
    } finally {
        Pop-Location
    }
}

function Stop-ServiceByPidFile([string]$Label, [string]$PidFile) {
    $pidValue = Get-PidFromFile $PidFile
    if (-not $pidValue) {
        Write-Host "$Label is not managed by this script."
        return
    }

    if (-not (Test-PidRunning $pidValue)) {
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
        Write-Host "$Label PID file was stale and has been removed."
        return
    }

    Stop-Process -Id $pidValue -ErrorAction Stop
    for ($i = 0; $i -lt 10; $i++) {
        if (-not (Test-PidRunning $pidValue)) {
            Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
            Write-Host "$Label stopped."
            return
        }
        Start-Sleep -Seconds 1
    }

    throw "$Label did not stop cleanly. PID: $pidValue"
}

function Print-Status {
    $dbHost = Get-DbHost
    $dbPort = Get-DbPort

    if (Test-PortOpen $dbHost $dbPort) {
        Write-Host "Database: up (${dbHost}:$dbPort)"
    } else {
        Write-Host "Database: down"
    }

    if (Test-UrlReady $BackendHealthUrl) {
        Write-Host "Backend: up ($BackendHealthUrl)"
    } else {
        Write-Host "Backend: down"
    }

    if (Test-UrlReady $FrontendUrl) {
        Write-Host "Frontend: up ($FrontendUrl)"
    } else {
        Write-Host "Frontend: down"
    }

    $backendPid = Get-PidFromFile $BackendPidFile
    if ($backendPid) {
        Write-Host "Backend PID file: $BackendPidFile ($backendPid)"
    }

    $frontendPid = Get-PidFromFile $FrontendPidFile
    if ($frontendPid) {
        Write-Host "Frontend PID file: $FrontendPidFile ($frontendPid)"
    }

    Write-Host "Logs: $BackendLogFile $FrontendLogFile"
}

function Show-Usage {
    Write-Host "Usage:"
    Write-Host "  .\scripts\dev.ps1 up"
    Write-Host "  .\scripts\dev.ps1 down"
    Write-Host "  .\scripts\dev.ps1 restart"
    Write-Host "  .\scripts\dev.ps1 status"
}

Load-LocalEnv

switch ($Command) {
    "up" {
        Start-Database
        Start-Backend
        Start-Frontend
        Write-Host "Application ready."
        Write-Host "Open: http://127.0.0.1:8000"
    }
    "down" {
        Stop-ServiceByPidFile "Frontend" $FrontendPidFile
        Stop-ServiceByPidFile "Backend" $BackendPidFile
        Stop-Database
    }
    "restart" {
        Stop-ServiceByPidFile "Frontend" $FrontendPidFile
        Stop-ServiceByPidFile "Backend" $BackendPidFile
        Stop-Database
        Start-Database
        Start-Backend
        Start-Frontend
        Write-Host "Application restarted."
    }
    "status" {
        Print-Status
    }
    default {
        Show-Usage
        exit 1
    }
}
