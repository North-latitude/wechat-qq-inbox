param(
    [switch]$SkipWeb,
    [switch]$SkipPython
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "== Unified Inbox bootstrap =="
Write-Host "Root: $Root"

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created .env from .env.example (APP_TOKEN blank = generated on first start)."
}

if (-not $SkipPython) {
    if (-not (Test-Path ".venv\Scripts\python.exe")) {
        python -m venv .venv
    }
    & ".venv\Scripts\python.exe" -m pip install --upgrade pip
    & ".venv\Scripts\python.exe" -m pip install -r "backend\requirements-dev.txt"
    if ($LASTEXITCODE -ne 0) { throw "Python dependency install failed" }
}

if (-not $SkipWeb) {
    if (-not (Get-Command pnpm -ErrorAction SilentlyContinue)) {
        throw "pnpm is required. Install Node.js 20+ and pnpm, then rerun."
    }
    pnpm --dir web install
    if ($LASTEXITCODE -ne 0) { throw "Web dependency install failed" }
    pnpm --dir web build
    if ($LASTEXITCODE -ne 0) { throw "Web build failed" }
}

Write-Host ""
Write-Host "Bootstrap complete."
Write-Host "Start with:  .\scripts\start.ps1"
Write-Host "Token file:  $Root\backend\data\token.txt (created on first start)"
