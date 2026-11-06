# 冷启动测量：起一个 jpackage 产物，量「到窗口出现」的耗时（默认 3 次，取中位数）。
# 说明：
#   - 不用 Process.MainWindowHandle 轮询（它在 .NET 里刷新不及时，实测会一直拿到 0）；
#     改成 EnumWindows 按 pid 找可见窗口，拿到就停表。
#   - 每次跑完强制结束进程，避免下一次被单实例/残留影响。
# 用法: pwsh -File tools\measure-coldstart.ps1 -Exe dist\PocketXiaoli\PocketXiaoli.exe -Runs 3
param(
    [Parameter(Mandatory = $true)][string]$Exe,
    [int]$Runs = 3,
    [int]$TimeoutSeconds = 60
)

Add-Type @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public class ColdWin {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc cb, IntPtr extra);
    delegate bool EnumProc(IntPtr h, IntPtr extra);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowTextLength(IntPtr h);

    public static int CountVisibleWindows(uint targetPid) {
        int n = 0;
        EnumWindows((h, e) => {
            if (!IsWindowVisible(h)) return true;
            uint pid; GetWindowThreadProcessId(h, out pid);
            if (pid == targetPid && GetWindowTextLength(h) > 0) n++;
            return true;
        }, IntPtr.Zero);
        return n;
    }
}
"@
[void][ColdWin]::SetProcessDPIAware()

$exeFull = (Resolve-Path $Exe).Path
$times = @()

for ($i = 1; $i -le $Runs; $i++) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exeFull
    $psi.WorkingDirectory = (Split-Path $exeFull)
    $psi.UseShellExecute = $false
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = [System.Diagnostics.Process]::Start($psi)
    # jpackage 的 exe 会再起一个同名子进程，窗口在子进程上 —— 所以要按「进程名 + 有标题的窗口」找
    $procName = [System.IO.Path]::GetFileNameWithoutExtension($exeFull)
    $elapsed = -1
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $win = Get-Process -Name $procName -ErrorAction SilentlyContinue |
            Where-Object { $_.MainWindowTitle } | Select-Object -First 1
        if ($win) { $elapsed = $sw.Elapsed.TotalSeconds; break }
        Start-Sleep -Milliseconds 50
    }
    $sw.Stop()
    if ($elapsed -lt 0) {
        Write-Warning "第 $i 次：$TimeoutSeconds s 内没出现窗口（pid=$($proc.Id)）"
    } else {
        $times += $elapsed
        Write-Output ("第 {0} 次：窗口出现 {1:N2} s（pid={2}）" -f $i, $elapsed, $proc.Id)
    }
    Get-Process -Name $procName -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
}

if ($times.Count -gt 0) {
    $sorted = $times | Sort-Object
    $median = $sorted[[int]([math]::Floor($sorted.Count / 2))]
    Write-Output ("中位数 {0:N2} s（min {1:N2} / max {2:N2}）" -f $median, $sorted[0], $sorted[-1])
}
