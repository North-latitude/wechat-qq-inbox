param(
    [switch]$SkipAndroid
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "== Backend tests =="
$env:TEMP = Join-Path $Root ".tmp"
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
& ".venv\Scripts\python.exe" -m pytest backend\tests -q
if ($LASTEXITCODE -ne 0) { throw "Backend tests failed" }

Write-Host "== Security static checks =="
& ".venv\Scripts\python.exe" scripts\security_check.py
if ($LASTEXITCODE -ne 0) { throw "Security checks failed" }

Write-Host "== Web build =="
pnpm --dir web build
if ($LASTEXITCODE -ne 0) { throw "Web build failed" }

if (-not $SkipAndroid -and (Test-Path "android\gradlew.bat")) {
    Write-Host "== Android unit tests + debug build =="
    Push-Location android
    if (Test-Path (Join-Path $Root ".tools\jdk17")) {
        $env:JAVA_HOME = Join-Path $Root ".tools\jdk17"
    }
    if (Test-Path (Join-Path $Root ".tools\android-sdk")) {
        $env:ANDROID_HOME = Join-Path $Root ".tools\android-sdk"
    }
    .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Android build failed" }
    Pop-Location
}

Write-Host "All checks passed."
