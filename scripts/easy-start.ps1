param(
    [int]$Port = 8787,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function New-Token {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    return [Convert]::ToBase64String($bytes).Replace('+', '-').Replace('/', '_').TrimEnd('=')
}

function Get-ConfiguredToken {
    if ($env:APP_TOKEN -and $env:APP_TOKEN.Trim().Length -ge 16) {
        return $env:APP_TOKEN.Trim()
    }
    if (Test-Path ".env") {
        $line = Get-Content ".env" |
            Where-Object { $_ -match '^\s*APP_TOKEN\s*=\s*(.*)$' } |
            Select-Object -First 1
        if ($line -and $line -match '^\s*APP_TOKEN\s*=\s*(.*)$') {
            $value = $Matches[1].Trim().Trim('"').Trim("'")
            if ($value.Length -ge 16) { return $value }
        }
    }
    $tokenFile = Join-Path $Root "backend\data\token.txt"
    if (Test-Path $tokenFile) {
        $value = (Get-Content $tokenFile -Raw).Trim()
        if ($value.Length -ge 16) { return $value }
    }
    $value = New-Token
    New-Item -ItemType Directory -Force -Path (Split-Path $tokenFile) | Out-Null
    Set-Content -Path $tokenFile -Value $value -NoNewline -Encoding ASCII
    return $value
}

function Get-LanIp {
    try {
        $ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
            Where-Object {
                $_.IPAddress -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' -and
                $_.InterfaceAlias -notmatch 'Loopback|vEthernet|WSL|Tailscale'
            } |
            Select-Object -ExpandProperty IPAddress -Unique
        if ($ips.Count -gt 0) { return $ips[0] }
    } catch {
        # Fall through to ipconfig.
    }
    try {
        $text = (ipconfig | Out-String)
        $match = [regex]::Match(
            $text,
            'IPv4[^\r\n]*?:\s*(192\.168\.[0-9.]+|10\.[0-9.]+|172\.(?:1[6-9]|2[0-9]|3[01])\.[0-9.]+)'
        )
        if ($match.Success) { return $match.Groups[1].Value }
    } catch {
        # ignore
    }
    return $null
}

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    Write-Host "首次运行，先安装依赖（可能需要几分钟）..."
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root "scripts\bootstrap.ps1")
    if ($LASTEXITCODE -ne 0) { throw "依赖安装失败" }
}
if (-not (Test-Path "web\dist\index.html")) {
    Write-Host "正在构建页面..."
    pnpm --dir web build
    if ($LASTEXITCODE -ne 0) { throw "页面构建失败" }
}
if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
}

$python = Join-Path $Root ".venv\Scripts\python.exe"
$token = Get-ConfiguredToken
$env:APP_TOKEN = $token

$ip = Get-LanIp
if (-not $ip) {
    throw "没有找到局域网 IPv4。请确认电脑已连 Wi-Fi/网线，然后重试。"
}
$baseUrl = "http://${ip}:$Port"

# If an instance is already healthy, do not start a second one.
$alreadyRunning = $false
try {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/health" -TimeoutSec 2
    if ($health.status -eq "ok") { $alreadyRunning = $true }
} catch {
    # not running
}

$process = $null
if (-not $alreadyRunning) {
    $outLog = Join-Path $Root ".tmp\easy-start.out.log"
    $errLog = Join-Path $Root ".tmp\easy-start.err.log"
    New-Item -ItemType Directory -Force -Path (Split-Path $outLog) | Out-Null
    $arguments = @(
        "-m", "uvicorn", "app.main:app",
        "--app-dir", "backend",
        "--host", "0.0.0.0",
        "--port", "$Port"
    )
    $process = Start-Process -FilePath $python -ArgumentList $arguments `
        -WorkingDirectory $Root -WindowStyle Hidden `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
        -PassThru
}

try {
    $ready = $alreadyRunning
    for ($i = 0; $i -lt 40 -and -not $ready; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/health" -TimeoutSec 2
            if ($health.status -eq "ok") { $ready = $true }
        } catch {
            # wait
        }
    }
    if (-not $ready) {
        $err = Join-Path $Root ".tmp\easy-start.err.log"
        if (Test-Path $err) { Get-Content $err -Tail 30 }
        throw "后端启动失败，请查看上面的错误。"
    }

    if (-not $NoBrowser) {
        Start-Process "http://127.0.0.1:$Port"
    }

    Write-Host ""
    Write-Host "=============================================="
    Write-Host " 统一收件箱后端已启动（可选电脑同步）"
    Write-Host " 手机地址: $baseUrl"
    Write-Host " Token 文件: $Root\backend\data\token.txt"
    Write-Host "=============================================="
    Write-Host ""
    Write-Host "手机操作（仅在需要电脑同步时）："
    Write-Host "  1) 手机和电脑连同一个 Wi-Fi"
    Write-Host "  2) 打开 App → 采集与同步设置 → 手动填上面的地址和 Token"
    Write-Host "  3) 点测试连接"
    Write-Host ""
    Write-Host "只用手机本地收件箱时，不需要运行这个脚本。"
    Write-Host "这个窗口保持打开；按 Ctrl+C 停止后端。"
    Write-Host ""

    if ($alreadyRunning) { return }
    Read-Host "按 Enter 停止后端（直接关闭窗口也可以）" | Out-Null
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}
