param([int]$Port = 8787)

$connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if (-not $connections) {
    Write-Host "端口 $Port 上没有正在运行的服务。"
    return
}
$pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($processId in $pids) {
    try {
        $process = Get-Process -Id $processId -ErrorAction Stop
        Write-Host "停止 $($process.ProcessName) (PID $processId)"
        Stop-Process -Id $processId -Force
    } catch {
        Write-Host "停止 PID $processId 失败：$($_.Exception.Message)"
    }
}
