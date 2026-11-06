# 桌面端截图回归门禁：固定数据目录 → 逐场景启动 → PrintWindow 抓图 → 与基线逐像素比对。
#
# 为什么只覆盖「不显示时钟」的界面：首页/课表/我的 会显示今天、第几周、天气、课程高亮，
# 每天都会自然变化，进像素门禁只会天天红。门禁只负责抓**渲染与布局回归**，
# 文案改动由别的检查（单测/评审）负责。
#
# 用法：
#   pwsh -File tools/verify-shots.ps1              # 比对，任何一张超阈值即 exit 1
#   pwsh -File tools/verify-shots.ps1 -Record      # 重录基线（有意改了外观之后跑）
#   pwsh -File tools/verify-shots.ps1 -Only login  # 只跑一个场景
param(
    [switch]$Record,
    [string]$Only = "",
    [double]$ThresholdPct = 0.1,
    [string]$Jar = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot          # desktop/
$repo = Split-Path -Parent $root                  # 仓库根
if ([string]::IsNullOrWhiteSpace($Jar)) { $Jar = Join-Path $root "app/build/libs/pocket-xiaoli-desktop.jar" }
$baselineDir = Join-Path $root "docs/shots-baseline"
$actualDir = Join-Path $root "shots/actual"
$fixture = Join-Path $root "tools/fixtures/appdata"   # 里面是 PocketXiaoli/（固定数据 + onboarded.flag）

if (-not (Test-Path $Jar)) {
    throw "找不到 $Jar —— 先跑: cd desktop; .\gradlew.bat :app:fatJar"
}
if (-not (Test-Path (Join-Path $fixture "PocketXiaoli/pocket-desktop.json")) -and $Record) {
    Write-Output "⚠️ 固定数据目录里还没有 pocket-desktop.json，本次会由应用自己 seed 后再写盘（首次录基线正常）。"
}

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;

public class ShotGate {
    public delegate bool EnumProc(IntPtr h, IntPtr p);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr p);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }

    public static IntPtr Find() {
        IntPtr found = IntPtr.Zero;
        EnumWindows((h, p) => {
            if (!IsWindowVisible(h)) return true;
            var sb = new StringBuilder(260);
            GetWindowText(h, sb, 260);
            if (!sb.ToString().Contains("口袋小鲤")) return true;
            RECT r; GetWindowRect(h, out r);
            if (r.Right - r.Left >= 600 && r.Bottom - r.Top >= 400) { found = h; return false; }
            return true;
        }, IntPtr.Zero);
        return found;
    }
}
"@

# 抓一帧到 path（PowerShell 侧用 System.Drawing，别塞进内联 C#）。
# **只留客户区**：标题栏是系统画的，窗口有没有焦点会让它一会儿蓝一会儿灰，
# 同一张图两次就能差 1000 多个像素 —— 那与我们的界面无关，不该进门禁。
function Save-Shot([IntPtr]$hwnd, [string]$path) {
    $wr = New-Object ShotGate+RECT
    [void][ShotGate]::GetWindowRect($hwnd, [ref]$wr)
    $w = $wr.Right - $wr.Left
    $h = $wr.Bottom - $wr.Top

    $cr = New-Object ShotGate+RECT
    [void][ShotGate]::GetClientRect($hwnd, [ref]$cr)
    $pt = New-Object ShotGate+POINT
    $pt.X = 0; $pt.Y = 0
    [void][ShotGate]::ClientToScreen($hwnd, [ref]$pt)
    $chrome = $pt.Y - $wr.Top           # 标题栏高度（物理像素）

    $full = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($full)
    $hdc = $g.GetHdc()
    [void][ShotGate]::PrintWindow($hwnd, $hdc, 2)
    $g.ReleaseHdc($hdc)
    $g.Dispose()

    $cw = $cr.Right - $cr.Left
    $ch = $cr.Bottom - $cr.Top
    if ($chrome -lt 0 -or $chrome -ge $h) { $chrome = 0; $cw = $w; $ch = $h }
    $crop = $full.Clone((New-Object System.Drawing.Rectangle 0, $chrome, $cw, $ch), $full.PixelFormat)
    $crop.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $crop.Dispose()
    $full.Dispose()
    return "${cw}x${ch}"
}

[void][ShotGate]::SetProcessDPIAware()

# 场景：只放**不显示日期/时间/天气**的界面（理由见文件头）
$scenarios = @(
    @{ Name = "tab-assistant"; Args = @("--tab=2") },
    @{ Name = "tab-services";  Args = @("--tab=3") },
    @{ Name = "demo-api";      Args = @("--tab=0", "--demo=api") },
    @{ Name = "demo-import";   Args = @("--tab=3", "--demo=import") },
    @{ Name = "demo-login";    Args = @("--tab=0", "--demo=login") },
    @{ Name = "demo-ime";      Args = @("--tab=0", "--demo=ime") },
    @{ Name = "demo-about";    Args = @("--tab=0", "--demo=about") },
    @{ Name = "demo-term";     Args = @("--tab=4", "--demo=term") },
    @{ Name = "demo-student";  Args = @("--tab=4", "--demo=student") }
)
if ($Only -ne "") { $scenarios = $scenarios | Where-Object { $_.Name -eq $Only } ; if (-not $scenarios) { throw "没有叫 $Only 的场景" } }

New-Item -ItemType Directory -Force -Path $baselineDir, $actualDir | Out-Null

function Stop-Apps {
    Get-Process -Name java -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -and $_.MainWindowTitle -like "*口袋小鲤*" } |
        ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
}

$results = @()
foreach ($s in $scenarios) {
    Stop-Apps
    Start-Sleep -Milliseconds 500
    $env:APPDATA = $fixture      # 固定数据目录：截图内容不随本机真实数据漂移
    Start-Process -FilePath "java" -ArgumentList (@("-jar", $Jar) + $s.Args) | Out-Null

    $hwnd = [IntPtr]::Zero
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 400
        $h = [ShotGate]::Find()
        if ($h -ne [IntPtr]::Zero) { $hwnd = $h; break }
    }
    if ($hwnd -eq [IntPtr]::Zero) {
        $results += [pscustomobject]@{ 场景 = $s.Name; 结果 = "FAIL"; 差异 = "窗口没起来"; 尺寸 = "-" }
        continue
    }
    # 3.4s：首帧 + 字体排版收敛 + 侧栏气泡自动消失（气泡 2.6s 后自己收，等短了会随机差一块）。
    # 抓之前把鼠标挪到屏幕左上角：悬停高亮同样会让同一张图两次不一样。
    [void][ShotGate]::SetCursorPos(0, 0)
    Start-Sleep -Milliseconds 3400
    $actual = Join-Path $actualDir ($s.Name + ".png")
    $size = Save-Shot $hwnd $actual
    Stop-Apps

    $baseline = Join-Path $baselineDir ($s.Name + ".png")
    if ($Record -or -not (Test-Path $baseline)) {
        Copy-Item -Force $actual $baseline
        $results += [pscustomobject]@{ 场景 = $s.Name; 结果 = "RECORD"; 差异 = "已写基线"; 尺寸 = $size }
        continue
    }
    # 比较交给 jar 里的 ShotDiff（javax.imageio，无额外依赖；PowerShell 侧那套
    # System.Drawing 程序集在 pwsh 7 下加载很挑，不值得为它再折腾）
    $line = & java -jar $Jar ("--compare-shots=" + $baseline + "|" + $actual + "|" + $ThresholdPct) 2>$null
    $ok = ($LASTEXITCODE -eq 0)
    $detail = if ($line) { ($line -join " ").Trim() } else { "比较失败（exit " + $LASTEXITCODE + "）" }
    $results += [pscustomobject]@{
        场景 = $s.Name
        结果 = if ($ok) { "PASS" } else { "FAIL" }
        差异 = $detail
        尺寸 = $size
    }
}
$env:APPDATA = $env:APPDATA  # 恢复由调用方环境决定（PowerShell 进程内无需额外处理）

$results | Format-Table -AutoSize | Out-String | Write-Output

$failed = @($results | Where-Object { $_.结果 -eq "FAIL" })
Write-Output ("基线：{0} · 实际图：{1} · 阈值：{2}%" -f $baselineDir, $actualDir, $ThresholdPct)
if ($failed.Count -gt 0) {
    Write-Output ("❌ {0} 张不一致：" -f $failed.Count)
    $failed | ForEach-Object { Write-Output ("   - " + $_.场景) }
    exit 1
}
Write-Output ("✅ 全部通过（{0} 张）" -f $results.Count)
exit 0
