$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$path = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $path) { Write-Output '.env already exists; preserved.'; exit 0 }
function New-RandomSecret {
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes)
}
$dbSecret = New-RandomSecret
$jwtSecret = New-RandomSecret
$lines = @(
    'DB_USERNAME=education', "DB_PASSWORD=$dbSecret", 'DB_NAME=education', 'DB_PORT=55432',
    'DB_URL=jdbc:postgresql://localhost:55432/education', "APP_JWT_SECRET=$jwtSecret",
    'APP_JWT_EXPIRATION_MS=86400000', 'CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173'
)
[IO.File]::WriteAllLines($path, $lines, (New-Object System.Text.UTF8Encoding $false))
Write-Output 'Created local .env with random secrets. PostgreSQL port: 55432.'
