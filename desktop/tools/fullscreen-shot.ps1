# 存一张整屏截图（可先聚焦某个窗口、移动鼠标），用于排查「点击到底落在谁身上」。
# 用法: pwsh -File tools/fullscreen-shot.ps1 -OutPath shots\desk.png -FocusProcessId 27880 -CursorX 501 -CursorY 541
param(
    [Parameter(Mandatory = $true)][string]$OutPath,
    [int]$FocusProcessId = 0,
    [int]$CursorX = -1,
    [int]$CursorY = -1
)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class DeskH {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool attach);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
}
"@
[void][DeskH]::SetProcessDPIAware()

if ($FocusProcessId -gt 0) {
    $hwnd = (Get-Process -Id $FocusProcessId).MainWindowHandle
    $fg = [DeskH]::GetForegroundWindow()
    $fgPid = 0
    $fgThread = [DeskH]::GetWindowThreadProcessId($fg, [ref]$fgPid)
    $myThread = [DeskH]::GetCurrentThreadId()
    [void][DeskH]::AttachThreadInput($myThread, $fgThread, $true)
    [void][DeskH]::BringWindowToTop($hwnd)
    [void][DeskH]::SetForegroundWindow($hwnd)
    [void][DeskH]::AttachThreadInput($myThread, $fgThread, $false)
    Start-Sleep -Milliseconds 400
    Write-Output ("foreground=" + [DeskH]::GetForegroundWindow() + " target=" + $hwnd)
}

if ($CursorX -ge 0) { [void][DeskH]::SetCursorPos($CursorX, $CursorY) }

$bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
$bmp = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen(0, 0, 0, 0, (New-Object System.Drawing.Size $bounds.Width, $bounds.Height))
$bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()
Write-Output "saved $OutPath ($($bounds.Width)x$($bounds.Height))"
