# 冷启动时间：从进程启动到主窗口出现（标题非空）。
# 用法：pwsh -File tools/measure-startup.ps1                     # 默认测 dist 绿色包
#       pwsh -File tools/measure-startup.ps1 -Runs 5
#       pwsh -File tools/measure-startup.ps1 -Jar                  # 改测 app/build/libs 的 fat jar
param(
    [int]$Runs = 3,
    [switch]$UseJar
)

$root = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $root 'dist\PocketXiaoli\PocketXiaoli.exe'
$jarPath = Join-Path $root 'app\build\libs\pocket-xiaoli-desktop.jar'

if ($UseJar) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'java'
    $psi.Arguments = "-jar `"$jarPath`""
    $psi.UseShellExecute = $false
    $psi.WorkingDirectory = $root
    $label = 'fat jar (系统 JDK)'
} else {
    if (-not (Test-Path $exe)) { throw "找不到 $exe，先跑 tools\package.ps1" }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.UseShellExecute = $false
    $psi.WorkingDirectory = Split-Path $exe
    $label = '打包版 exe (内置运行时)'
}

function Stop-App {
    Get-Process PocketXiaoli, java -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like '*口袋小鲤*' } |
        Stop-Process -Force -ErrorAction SilentlyContinue
}

$times = @()
foreach ($i in 1..$Runs) {
    Stop-App
    Start-Sleep -Seconds 1
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    [System.Diagnostics.Process]::Start($psi) | Out-Null
    $window = $null
    while ($sw.Elapsed.TotalSeconds -lt 25) {
        $window = Get-Process PocketXiaoli, java -ErrorAction SilentlyContinue |
            Where-Object { $_.MainWindowTitle -like '*口袋小鲤*' } |
            Select-Object -First 1
        if ($window) { break }
        Start-Sleep -Milliseconds 100
    }
    $sw.Stop()
    if ($window) {
        $times += $sw.Elapsed.TotalSeconds
        Write-Output ("run{0} 窗口出现 {1:N2}s" -f $i, $sw.Elapsed.TotalSeconds)
    } else {
        Write-Output "run$i 超时（25s 内没等到窗口）"
    }
    Start-Sleep -Seconds 2
}
Stop-App

if ($times.Count -gt 0) {
    $sorted = $times | Sort-Object
    $median = $sorted[[int][math]::Floor($sorted.Count / 2)]
    Write-Output ("`n{0}：中位 {1:N2}s，最快 {2:N2}s，最慢 {3:N2}s（{4} 次）" -f $label, $median, $sorted[0], $sorted[-1], $sorted.Count)
    if ($median -gt 2.0) { Write-Output '⚠️ 超过 2s 线：按事实卡要求查磁盘 IO / JIT' }
} else {
    Write-Output '没有拿到有效样本'
}


