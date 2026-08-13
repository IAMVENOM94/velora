param(
    [switch]$Force,
    [switch]$StartServices
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envPath = Join-Path $root '.env'

function New-RandomHex([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($buffer) }
    finally { $generator.Dispose() }
    return -join ($buffer | ForEach-Object { $_.ToString('x2') })
}

if ((Test-Path -LiteralPath $envPath) -and -not $Force) {
    Write-Host '.env already exists; keeping it unchanged.'
} else {
    $dbPassword = New-RandomHex 24
    $minioUser = "velora-$(New-RandomHex 8)"
    $minioPassword = New-RandomHex 24
    $accessSecret = New-RandomHex 48
    $refreshSecret = New-RandomHex 48
    $demoPassword = New-RandomHex 16
    $content = @"
POSTGRES_DB=velora
POSTGRES_USER=velora
POSTGRES_PASSWORD=$dbPassword
DATABASE_URL=postgres://velora:$dbPassword@localhost:5432/velora
MINIO_ROOT_USER=$minioUser
MINIO_ROOT_PASSWORD=$minioPassword
MINIO_ACCESS_KEY=$minioUser
MINIO_SECRET_KEY=$minioPassword
MINIO_ENDPOINT=http://localhost:9000
MINIO_PUBLIC_ENDPOINT=http://localhost:9000
MINIO_BUCKET=velora-photos
JWT_ACCESS_SECRET=$accessSecret
JWT_REFRESH_SECRET=$refreshSecret
ACCESS_TTL=15m
REFRESH_TTL_DAYS=30
CORS_ORIGINS=http://localhost:8080
PORT=8080
DEMO_EMAIL=demo@velora.app
DEMO_PASSWORD=$demoPassword
"@
    [IO.File]::WriteAllText($envPath, $content, [Text.UTF8Encoding]::new($false))
    Write-Host 'Created .env with random development credentials. The demo login is stored in DEMO_EMAIL and DEMO_PASSWORD.'
}

$localProperties = Join-Path $root 'local.properties'
if (-not (Test-Path -LiteralPath $localProperties)) {
    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
    if ($sdkCandidates.Count -gt 0) {
        $sdk = [IO.Path]::GetFullPath($sdkCandidates[0]).Replace('\', '\\').Replace(':', '\:')
        [IO.File]::WriteAllText($localProperties, "sdk.dir=$sdk`n", [Text.UTF8Encoding]::new($false))
        Write-Host "Created local.properties for $($sdkCandidates[0])."
    } else {
        Write-Warning 'Android SDK was not found automatically. Open the project in Android Studio or create local.properties with sdk.dir.'
    }
}

if ($StartServices) {
    Push-Location $root
    try { docker compose --env-file .env up -d --build }
    finally { Pop-Location }
}

Write-Host 'Velora development configuration is ready.'
