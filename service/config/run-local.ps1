$ErrorActionPreference = 'Stop'
$serviceDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $serviceDir '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw "Missing $envFile" }
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        if ($Matches[1] -in @('DB_USER', 'DB_PASSWORD', 'DHIS2_PASSWORD', 'CBC_API_KEY')) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
if (-not $env:DHIS2_PASSWORD) { throw 'Set DHIS2_PASSWORD in service/.env' }
if (-not $env:CBC_API_KEY) { throw 'Set CBC_API_KEY in service/.env' }
if (-not $env:DB_PASSWORD) { throw 'Set DB_PASSWORD for the analyzers database in service/.env' }
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/analyzers'
if (-not $env:DB_USER) { $env:DB_USER = 'postgres' }
Push-Location $serviceDir
try {
    & '.\mvnw.cmd' spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Spring Boot exited with an error' }
} finally {
    Pop-Location
}
