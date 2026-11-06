# 在屏幕物理坐标点击/移动鼠标（DPI 感知）。配合 tools/capture-window.ps1 做 UI 验收。
# 用法: pwsh -File tools/click.ps1 -X 501 -Y 459            # 左键单击
#       pwsh -File tools/click.ps1 -X 501 -Y 459 -MoveOnly  # 只移动
param(
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y,
    [switch]$MoveOnly,
    [switch]$RightClick
)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class MouseH {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
}
"@

[void][MouseH]::SetProcessDPIAware()
[void][MouseH]::SetCursorPos($X, $Y)
Start-Sleep -Milliseconds 120
if (-not $MoveOnly) {
    if ($RightClick) {
        [MouseH]::mouse_event(0x0008, 0, 0, 0, [UIntPtr]::Zero)  # RIGHTDOWN
        Start-Sleep -Milliseconds 60
        [MouseH]::mouse_event(0x0010, 0, 0, 0, [UIntPtr]::Zero)  # RIGHTUP
    } else {
        [MouseH]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)  # LEFTDOWN
        Start-Sleep -Milliseconds 60
        [MouseH]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)  # LEFTUP
    }
}
Write-Output "mouse $X,$Y done"
