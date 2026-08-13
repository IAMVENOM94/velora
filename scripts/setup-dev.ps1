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
    $existing = [IO.File]::ReadAllText($envPath)
    $requiredSecrets = @('OTP_SECRET', 'OAUTH_STATE_SECRET', 'DEV_PROVIDER_SECRET')
    $append = New-Object Collections.Generic.List[string]
    foreach ($name in $requiredSecrets) {
        if ($existing -notmatch "(?m)^$name=") { $append.Add("$name=$(New-RandomHex 48)") }
    }
    foreach ($name in @('GOOGLE_CLIENT_ID', 'GOOGLE_WEB_CLIENT_ID', 'X_CLIENT_ID', 'X_REDIRECT_URI', 'VK_CLIENT_ID', 'VK_CLIENT_SECRET', 'VK_ANDROID_CLIENT_SECRET', 'VK_REDIRECT_URI')) {
        if ($existing -notmatch "(?m)^$name=") {
            $default = switch ($name) { 'X_REDIRECT_URI' { 'velora://auth/x' } 'VK_REDIRECT_URI' { 'velora://auth/vk' } default { '' } }
            $append.Add("$name=$default")
        }
    }
    if ($existing -notmatch '(?m)^SMS_PROVIDER_CONFIGURED=') { $append.Add('SMS_PROVIDER_CONFIGURED=false') }
    if ($append.Count -gt 0) {
        [IO.File]::AppendAllText($envPath, "`n" + ($append -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
        Write-Host 'Added missing Stage 9 development secrets without replacing existing configuration.'
    }
} else {
    $dbPassword = New-RandomHex 24
    $minioUser = "velora-$(New-RandomHex 8)"
    $minioPassword = New-RandomHex 24
    $accessSecret = New-RandomHex 48
    $refreshSecret = New-RandomHex 48
    $otpSecret = New-RandomHex 48
    $oauthStateSecret = New-RandomHex 48
    $devProviderSecret = New-RandomHex 48
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
OTP_SECRET=$otpSecret
OAUTH_STATE_SECRET=$oauthStateSecret
DEV_PROVIDER_SECRET=$devProviderSecret
ACCESS_TTL=15m
REFRESH_TTL_DAYS=30
CORS_ORIGINS=http://localhost:8080
PORT=8080
DEMO_EMAIL=demo@velora.app
DEMO_PASSWORD=$demoPassword
GOOGLE_CLIENT_ID=
GOOGLE_WEB_CLIENT_ID=
X_CLIENT_ID=
X_REDIRECT_URI=velora://auth/x
VK_CLIENT_ID=
VK_CLIENT_SECRET=
VK_ANDROID_CLIENT_SECRET=
VK_REDIRECT_URI=velora://auth/vk
SMS_PROVIDER_CONFIGURED=false
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
