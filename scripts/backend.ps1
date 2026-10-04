param([ValidateSet('run','test','build')][string]$Action = 'run')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $configPath) {
    Get-Content -LiteralPath $configPath | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object {
        $pair = $_.Split('=', 2)
        [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
    }
}
Push-Location (Join-Path $projectRoot 'backend')
try {
    switch ($Action) {
        'run' { & .\mvnw.cmd spring-boot:run }
        'test' { & .\mvnw.cmd verify }
        'build' { & .\mvnw.cmd -DskipTests package }
    }
    exit $LASTEXITCODE
} finally { Pop-Location }
