param([string]$ApkPath = 'app\build\outputs\apk\release\app-release-unsigned.apk')

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$apk = [IO.Path]::GetFullPath((Join-Path $root $ApkPath))
if (-not (Test-Path -LiteralPath $apk)) { throw "Release APK not found: $apk" }

$secretNames = @(
    'POSTGRES_PASSWORD', 'MINIO_ROOT_PASSWORD', 'MINIO_SECRET_KEY',
    'JWT_ACCESS_SECRET', 'JWT_REFRESH_SECRET', 'OTP_SECRET', 'OAUTH_STATE_SECRET',
    'DEV_PROVIDER_SECRET', 'DEMO_PASSWORD', 'VK_CLIENT_SECRET'
)
$credentials = New-Object Collections.Generic.List[object]
$envFile = Join-Path $root '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#') -or -not $trimmed.Contains('=')) { continue }
        $pair = $trimmed.Split('=', 2)
        if ($secretNames -contains $pair[0].Trim() -and $pair[1].Trim().Length -ge 8) {
            $credentials.Add([pscustomobject]@{ Name = $pair[0].Trim(); Value = $pair[1].Trim() })
        }
    }
}
foreach ($name in $secretNames) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ($value -and $value.Length -ge 8) { $credentials.Add([pscustomobject]@{ Name = $name; Value = $value }) }
}

$signatures = @(
    '-----BEGIN PRIVATE KEY-----', '-----BEGIN RSA PRIVATE KEY-----',
    '-----BEGIN EC PRIVATE KEY-----', 'sk_live_', 'xoxb-', 'xoxp-'
)
$findings = [Collections.Generic.HashSet[string]]::new()
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    foreach ($entry in $archive.Entries) {
        if (-not $entry.Name) { continue }
        if ($entry.FullName -match '(^|/)(\.env($|\.)|[^/]+\.(pem|p12|pfx|jks|keystore|key)$)') {
            [void]$findings.Add("sensitive file packaged: $($entry.FullName)")
        }
        $stream = $entry.Open()
        $memory = [IO.MemoryStream]::new()
        try {
            $stream.CopyTo($memory)
            $bytes = $memory.ToArray()
        } finally {
            $stream.Dispose()
            $memory.Dispose()
        }
        $latin = [Text.Encoding]::GetEncoding(28591).GetString($bytes)
        $utf16 = [Text.Encoding]::Unicode.GetString($bytes)
        foreach ($credential in $credentials) {
            $value = [string]$credential.Value
            if ($latin.IndexOf($value, [StringComparison]::Ordinal) -ge 0 -or $utf16.IndexOf($value, [StringComparison]::Ordinal) -ge 0) {
                [void]$findings.Add("environment credential embedded: $($credential.Name)")
            }
        }
        foreach ($signature in $signatures) {
            if ($latin.IndexOf($signature, [StringComparison]::Ordinal) -ge 0 -or $utf16.IndexOf($signature, [StringComparison]::Ordinal) -ge 0) {
                [void]$findings.Add("credential signature embedded: $signature")
            }
        }
    }
} finally {
    $archive.Dispose()
}

$sdkRoot = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) |
    Where-Object { $_ -and (Test-Path -LiteralPath $_) } |
    Select-Object -First 1
if ($sdkRoot) {
    $buildTools = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
    $aapt = Join-Path $buildTools.FullName 'aapt2.exe'
    if (Test-Path -LiteralPath $aapt) {
        $manifest = & $aapt dump xmltree $apk --file AndroidManifest.xml 2>&1 | Out-String
        if ($manifest.IndexOf('android.permission.ACCESS_BACKGROUND_LOCATION', [StringComparison]::Ordinal) -ge 0) {
            [void]$findings.Add('background location permission packaged')
        }
    }
}

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash
if ($findings.Count -gt 0) {
    Write-Host "Release APK scan failed with $($findings.Count) finding(s):" -ForegroundColor Red
    $findings | Sort-Object | ForEach-Object { Write-Host "- $_" }
    exit 1
}
Write-Host 'Release APK scan passed: no configured secrets, private keys, credential files, or background location permission.' -ForegroundColor Green
Write-Host "SHA-256: $hash"
exit 0
