param(
    [Parameter(Position = 0)]
    [string]$DumpFile = ""
)

$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$LocalEnvFile = Join-Path $RootDir ".env.local"
if (-not $DumpFile) {
    $DumpFile = Join-Path $RootDir "database\busgo.sql"
}

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

Load-LocalEnv

$DbHost = if ($env:BUSGO_DB_HOST) { $env:BUSGO_DB_HOST } else { "127.0.0.1" }
$DbPort = if ($env:BUSGO_DB_PORT) { $env:BUSGO_DB_PORT } else { "3306" }
$DbName = if ($env:BUSGO_DB_NAME) { $env:BUSGO_DB_NAME } else { "busgo" }
$DbUser = if ($env:BUSGO_DB_USER) { $env:BUSGO_DB_USER } else { "busgo" }
$DbPass = if ($env:BUSGO_DB_PASS) { $env:BUSGO_DB_PASS } else { "busgo123" }
$DbRootUser = if ($env:BUSGO_DB_ROOT_USER) { $env:BUSGO_DB_ROOT_USER } else { "root" }
$DbRootPass = if ($env:BUSGO_DB_ROOT_PASS) { $env:BUSGO_DB_ROOT_PASS } else { "root123" }

$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysql) {
    throw "mysql client is required."
}

if (-not (Test-Path $DumpFile)) {
    throw "Dump file not found: $DumpFile"
}

$env:MYSQL_PWD = $DbRootPass
& $mysql.Source `
    "--host=$DbHost" `
    "--port=$DbPort" `
    "--user=$DbRootUser" `
    "--execute=CREATE DATABASE IF NOT EXISTS ``$DbName``; CREATE USER IF NOT EXISTS '$DbUser'@'%' IDENTIFIED BY '$DbPass'; GRANT ALL PRIVILEGES ON ``$DbName``.* TO '$DbUser'@'%'; FLUSH PRIVILEGES;"

$env:MYSQL_PWD = $DbPass
Get-Content -Path $DumpFile -Raw | & $mysql.Source `
    "--host=$DbHost" `
    "--port=$DbPort" `
    "--user=$DbUser" `
    $DbName

Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

Write-Host "Dump imported into $DbName on ${DbHost}:$DbPort"
