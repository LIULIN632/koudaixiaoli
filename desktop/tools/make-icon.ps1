# 生成应用图标（多尺寸 .ico，PNG 压缩条目）。
# 图形是「品牌蓝圆角方块 + 白色鲤鱼」的简化版吉祥物，不依赖任何外部素材。
# 用法: pwsh -File tools/make-icon.ps1
param([string]$OutPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'docs\icon.ico'))

Add-Type -AssemblyName System.Drawing

function New-IconPng([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    $s = $size / 256.0
    $radius = 58 * $s
    $rect = New-Object System.Drawing.RectangleF (8 * $s), (8 * $s), (240 * $s), (240 * $s)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $path.AddArc($rect.X, $rect.Y, $d, $d, 180, 90)
    $path.AddArc($rect.Right - $d, $rect.Y, $d, $d, 270, 90)
    $path.AddArc($rect.Right - $d, $rect.Bottom - $d, $d, $d, 0, 90)
    $path.AddArc($rect.X, $rect.Bottom - $d, $d, $d, 90, 90)
    $path.CloseFigure()

    $brand = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 77, 107, 254))
    $g.FillPath($brand, $path)

    # 鱼身
    $white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $body = New-Object System.Drawing.RectangleF (78 * $s), (96 * $s), (104 * $s), (74 * $s)
    $g.FillEllipse($white, $body)

    # 尾巴（三角）
    $tail = New-Object System.Drawing.Drawing2D.GraphicsPath
    $points = [System.Drawing.PointF[]]@(
        (New-Object System.Drawing.PointF (52 * $s), (108 * $s)),
        (New-Object System.Drawing.PointF (52 * $s), (158 * $s)),
        (New-Object System.Drawing.PointF (86 * $s), (133 * $s))
    )
    $tail.AddPolygon($points)
    $g.FillPath($white, $tail)

    # 眼睛与气泡
    $eye = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 77, 107, 254))
    $g.FillEllipse($eye, (156 * $s), (120 * $s), (16 * $s), (16 * $s))
    $bubble = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(120, 255, 255, 255))
    $g.FillEllipse($bubble, (172 * $s), (104 * $s), (12 * $s), (12 * $s))
    $g.FillEllipse($bubble, (188 * $s), (122 * $s), (8 * $s), (8 * $s))

    $g.Dispose()
    $brand.Dispose(); $white.Dispose(); $eye.Dispose(); $bubble.Dispose()
    $path.Dispose(); $tail.Dispose()

    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    return ,$ms.ToArray()
}

$sizes = 256, 64, 48, 32, 16
$images = @()
foreach ($size in $sizes) { $images += ,(New-IconPng $size) }

$out = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter $out
$bw.Write([UInt16]0)                 # reserved
$bw.Write([UInt16]1)                 # type = icon
$bw.Write([UInt16]$sizes.Count)      # count

$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $size = $sizes[$i]
    $data = $images[$i]
    $bw.Write([byte]($(if ($size -ge 256) { 0 } else { $size })))  # width
    $bw.Write([byte]($(if ($size -ge 256) { 0 } else { $size })))  # height
    $bw.Write([byte]0)               # palette
    $bw.Write([byte]0)               # reserved
    $bw.Write([UInt16]1)             # color planes
    $bw.Write([UInt16]32)            # bpp
    $bw.Write([UInt32]$data.Length)  # bytes
    $bw.Write([UInt32]$offset)       # offset
    $offset += $data.Length
}
foreach ($data in $images) { $bw.Write([byte[]]$data) }
$bw.Flush()

$dir = Split-Path -Parent $OutPath
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
[System.IO.File]::WriteAllBytes($OutPath, $out.ToArray())
$bw.Dispose(); $out.Dispose()
Write-Output "icon -> $OutPath ($((Get-Item $OutPath).Length) bytes, $($sizes.Count) sizes)"
