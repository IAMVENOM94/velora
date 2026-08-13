param([string]$Device)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

Push-Location $root
try {
    if (-not $Device) {
        $devices = @(adb devices | Select-String "`tdevice$" | ForEach-Object { ($_ -split "`t")[0] })
        if ($devices.Count -ne 1) { throw "Expected one running Android device/emulator; found $($devices.Count). Pass -Device explicitly." }
        $Device = $devices[0]
    }
    .\gradlew.bat assembleDebug assembleDebugAndroidTest --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Android test APK build failed.' }
    adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'App installation failed.' }
    adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
    adb -s $Device shell am instrument -w -r app.velora.test/androidx.test.runner.AndroidJUnitRunner
    if ($LASTEXITCODE -ne 0) { throw 'Instrumented UI tests failed.' }
} finally { Pop-Location }
