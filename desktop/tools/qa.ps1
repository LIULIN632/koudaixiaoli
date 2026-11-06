# 一步完成「聚焦窗口 → （可选）点击 → 截图」。必须一个进程内做完：
# 分开执行时新起的 pwsh 控制台会把焦点从被测窗口抢走，点击就会落到别处（踩过这个坑）。
#
# 用法:
#   pwsh -File tools/qa.ps1 -ProcessId 27880 -OutPath shots\assistant.png -ClickX 501 -ClickY 541
#   pwsh -File tools/qa.ps1 -ProcessId 27880 -OutPath shots\home.png            # 只截图
param(
    [Parameter(Mandatory = $true)][int]$ProcessId,
    [Parameter(Mandatory = $true)][string]$OutPath,
    [int]$ClickX = -1,
    [int]$ClickY = -1,
    [int]$ScrollY = 0,
    [string]$Paste = '',
    [int]$WaitMs = 900
)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class QaWin {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool attach);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, int data, UIntPtr extra);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

[void][QaWin]::SetProcessDPIAware()
$proc = Get-Process -Id $ProcessId -ErrorAction Stop
$hwnd = $proc.MainWindowHandle
if ($hwnd -eq [IntPtr]::Zero) { throw "进程 $ProcessId 没有主窗口" }

if ([QaWin]::IsIconic($hwnd)) { [void][QaWin]::ShowWindow($hwnd, 9) }

# 1) 聚焦（最多试 3 次）
for ($i = 0; $i -lt 3; $i++) {
    if ([QaWin]::GetForegroundWindow() -eq $hwnd) { break }
    $fg = [QaWin]::GetForegroundWindow()
    $fgPid = 0
    $fgThread = [QaWin]::GetWindowThreadProcessId($fg, [ref]$fgPid)
    $myThread = [QaWin]::GetCurrentThreadId()
    [void][QaWin]::AttachThreadInput($myThread, $fgThread, $true)
    [void][QaWin]::BringWindowToTop($hwnd)
    [void][QaWin]::SetForegroundWindow($hwnd)
    [void][QaWin]::AttachThreadInput($myThread, $fgThread, $false)
    Start-Sleep -Milliseconds 250
}
if ([QaWin]::GetForegroundWindow() -ne $hwnd) {
    Write-Warning "焦点没抢到，点击可能落空（截图仍可用）"
}

# 2) 点击（物理屏幕坐标）
if ($ClickX -ge 0 -and $ClickY -ge 0) {
    # 先晃一下鼠标：合成点击若没有前置 WM_MOUSEMOVE，Compose 的命中测试可能落在旧位置
    [void][QaWin]::SetCursorPos($ClickX - 60, $ClickY - 60)
    Start-Sleep -Milliseconds 150
    [void][QaWin]::SetCursorPos($ClickX, $ClickY)
    Start-Sleep -Milliseconds 250
    [QaWin]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)  # LEFTDOWN
    Start-Sleep -Milliseconds 100
    [QaWin]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)  # LEFTUP
    Start-Sleep -Milliseconds $WaitMs
} else {
    Start-Sleep -Milliseconds 300
}

# 2.5) 滚轮（负值向下滚）
# 2.7) 粘贴（用于模拟 IME「上屏」结果：测试输入框插入中文后是否错位）
if ($Paste) {
    Add-Type -AssemblyName System.Windows.Forms
    Set-Clipboard -Value $Paste
    Start-Sleep -Milliseconds 200
    [System.Windows.Forms.SendKeys]::SendWait('^v')
    Start-Sleep -Milliseconds 400
}

if ($ScrollY -ne 0) {
    if ($ClickX -lt 0) {
        # 没给坐标就滚窗口中心；先晃一下产生 WM_MOUSEMOVE，否则 hover 命中的还是旧位置
        $rectForScroll = New-Object QaWin+RECT
        [void][QaWin]::GetWindowRect($hwnd, [ref]$rectForScroll)
        $cx = [int](($rectForScroll.Left + $rectForScroll.Right) / 2)
        $cy = [int](($rectForScroll.Top + $rectForScroll.Bottom) / 2)
        [void][QaWin]::SetCursorPos($cx - 120, $cy - 80)
        Start-Sleep -Milliseconds 150
        [void][QaWin]::SetCursorPos($cx, $cy)
        Start-Sleep -Milliseconds 200
    }
    # 合成 WM_MOUSEWHEEL 直接投递（mouse_event 的轮事件 Compose Desktop 收不到，踩过）
    $rectWheel = New-Object QaWin+RECT
    [void][QaWin]::GetWindowRect($hwnd, [ref]$rectWheel)
    $wx = [int](($rectWheel.Left + $rectWheel.Right) / 2)   # lParam 要的是屏幕坐标，不是客户区坐标
    $wy = [int](($rectWheel.Top + $rectWheel.Bottom) / 2)
    $steps = [Math]::Max(1, [Math]::Abs($ScrollY) / 120)
    $notch = if ($ScrollY -lt 0) { -120 } else { 120 }
    for ($i = 0; $i -lt $steps; $i++) {
        $lp = [IntPtr](($wy -shl 16) -bor $wx)
        $wp = [IntPtr]($notch -shl 16)
        [void][QaWin]::PostMessage($hwnd, 0x020A, $wp, $lp)
        Start-Sleep -Milliseconds 120
    }
    Start-Sleep -Milliseconds 400
}

# 3) PrintWindow 截图（窗口被遮挡也能拍）
$rect = New-Object QaWin+RECT
[void][QaWin]::GetWindowRect($hwnd, [ref]$rect)
$width = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top
$bitmap = New-Object System.Drawing.Bitmap $width, $height
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$hdc = $graphics.GetHdc()
[void][QaWin]::PrintWindow($hwnd, $hdc, 2)
$graphics.ReleaseHdc($hdc)
$graphics.Dispose()

$dir = Split-Path -Parent $OutPath
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
$bitmap.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
Write-Output "saved $OutPath (${width}x${height})"
