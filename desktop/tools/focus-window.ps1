# 把目标进程的主窗口拉到前台（绕过 SetForegroundWindow 的焦点窃取限制），并校验成功。
# 用法: pwsh -File tools/focus-window.ps1 -ProcessId 27880
param([Parameter(Mandatory = $true)][int]$ProcessId)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class FocusH {
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool attach);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@

[void][FocusH]::SetProcessDPIAware()
$proc = Get-Process -Id $ProcessId -ErrorAction Stop
$hwnd = $proc.MainWindowHandle
if ($hwnd -eq [IntPtr]::Zero) { throw "进程 $ProcessId 没有主窗口" }

if ([FocusH]::IsIconic($hwnd)) { [void][FocusH]::ShowWindow($hwnd, 9) }

$fg = [FocusH]::GetForegroundWindow()
$fgPid = 0
[void][FocusH]::GetWindowThreadProcessId($fg, [ref]$fgPid)
$targetPid = 0
[void][FocusH]::GetWindowThreadProcessId($hwnd, [ref]$targetPid)

$myThread = [FocusH]::GetCurrentThreadId()
$fgThread = [FocusH]::GetWindowThreadProcessId($fg, [ref]$targetPid)
[void][FocusH]::AttachThreadInput($myThread, $fgThread, $true)
[void][FocusH]::BringWindowToTop($hwnd)
[void][FocusH]::SetForegroundWindow($hwnd)
[void][FocusH]::AttachThreadInput($myThread, $fgThread, $false)

Start-Sleep -Milliseconds 350
$now = [FocusH]::GetForegroundWindow()
if ($now -eq $hwnd) { Write-Output "focused $hwnd" } else { Write-Output "focus-failed hwnd=$hwnd foreground=$now" }
