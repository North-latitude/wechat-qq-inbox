param(
    [int]$Port = 8787,
    [switch]$Lan,
    [switch]$Reload
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    throw "Missing .venv. Run .\scripts\bootstrap.ps1 first."
}
if (-not (Test-Path "web\dist\index.html")) {
    Write-Host "web/dist missing; building the PWA..."
    pnpm --dir web build
    if ($LASTEXITCODE -ne 0) { throw "Web build failed" }
}
if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
}

$bindHost = if ($Lan) { "0.0.0.0" } else { "127.0.0.1" }
$uvicornArgs = @(
    "-m", "uvicorn", "app.main:app",
    "--app-dir", "backend",
    "--host", $bindHost,
    "--port", "$Port"
)
if ($Reload) { $uvicornArgs += "--reload" }

Write-Host "Unified Inbox: http://127.0.0.1:$Port"
Write-Host "Token file:   $Root\backend\data\token.txt"

if ($Lan) {
    Write-Host ""
    Write-Host "LAN mode is ON (0.0.0.0). Phone URLs (same Wi-Fi):"
    $ips = @()
    try {
        $ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
            Where-Object {
                $_.IPAddress -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' -and
                $_.InterfaceAlias -notmatch 'Loopback|vEthernet|WSL|Tailscale'
            } |
            Select-Object -ExpandProperty IPAddress -Unique
    } catch {
        # Fall back to ipconfig below.
    }
    if ($ips.Count -eq 0) {
        try {
            $ipconfig = (ipconfig | Out-String)
            $ips = [regex]::Matches($ipconfig, 'IPv4[^\r\n]*?:\s*([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)') |
                ForEach-Object { $_.Groups[1].Value } |
                Where-Object { $_ -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' } |
                Select-Object -Unique
        } catch {
            $ips = @()
        }
    }
    if ($ips.Count -eq 0) {
        Write-Host "  Could not auto-detect. Run: ipconfig"
        Write-Host "  Look for IPv4 Address (192.168.x.x / 10.x.x.x / 172.16-31.x.x)."
    } else {
        foreach ($ip in $ips) { Write-Host "  http://${ip}:$Port" }
    }
    Write-Host ""
    Write-Host "Only use LAN mode on a trusted home/private network."
    Write-Host "Allow TCP $Port through Windows Firewall when prompted."
} else {
    Write-Host "Phone access: run .\scripts\start.ps1 -Lan (same Wi-Fi),"
    Write-Host "or use Tailscale Serve / Caddy (see docs/deployment.md)."
}
Write-Host ""

& ".venv\Scripts\python.exe" @uvicornArgs
