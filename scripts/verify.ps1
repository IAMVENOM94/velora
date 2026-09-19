param(
    [switch]$SkipContainers,
    [string]$ReleaseApiUrl = 'https://api.velora.invalid/'
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

function Invoke-Checked([scriptblock]$Command) {
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code $LASTEXITCODE" }
}

Push-Location $root
try {
    if (-not (Test-Path -LiteralPath '.env')) { throw 'Missing .env. Run scripts/setup-dev.ps1 first.' }
    if (-not $ReleaseApiUrl.StartsWith('https://', [StringComparison]::OrdinalIgnoreCase)) { throw 'ReleaseApiUrl must use HTTPS.' }
    if (-not (Test-Path -LiteralPath 'backend/node_modules')) {
        Push-Location backend
        try { Invoke-Checked { npm ci } }
        finally { Pop-Location }
    }

    Push-Location backend
    try {
        Invoke-Checked { npm run build }
        Invoke-Checked { npm test }
        Invoke-Checked { npm audit --audit-level=high }
    } finally { Pop-Location }

    if (-not $SkipContainers) {
        Invoke-Checked { docker compose --env-file .env up -d --build }
        $healthy = $false
        foreach ($attempt in 1..30) {
            try {
                if ((Invoke-RestMethod 'http://localhost:8080/health' -TimeoutSec 2).status -eq 'ok') { $healthy = $true; break }
            } catch { }
            Start-Sleep -Seconds 1
        }
        if (-not $healthy) { docker compose logs --tail=100 backend; throw 'Backend health check failed.' }
        Push-Location backend
        try { Invoke-Checked { npm run test:integration } }
        finally { Pop-Location }
    }

    Invoke-Checked { .\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest --no-daemon }
    Invoke-Checked { .\gradlew.bat assembleRelease "-PVELORA_API_BASE_URL=$ReleaseApiUrl" --no-daemon }
    Invoke-Checked { .\scripts\scan-release-apk.ps1 }
    Invoke-Checked { .\gradlew.bat lintDebug --no-daemon --max-workers=1 }
    Write-Host 'Verification passed: backend, integration, Android build, unit tests, test APK, release secret scan, and lint.' -ForegroundColor Green
} finally { Pop-Location }
