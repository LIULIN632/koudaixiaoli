# 打包 Windows 桌面版：fat jar → jpackage app-image → zip
#
# 为什么不用 MSI/EXE 安装包：jpackage 的 msi/exe 都依赖 WiX Toolset（本机没有），
# 而 app-image 是「免安装绿色版」：解开就能双击 PocketXiaoli.exe 跑，用户想要"安装感"
# 可以自己建快捷方式。以后要 MSI 再装 WiX，脚本结构不用改。
#
# 用法:
#   pwsh tools/package.ps1              # 打 build\libs 的 jar + 出 dist\PocketXiaoli\ + zip
#   pwsh tools/package.ps1 -SkipBuild   # 复用已有 jar，只重做 app-image
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot   # desktop\
$dist = Join-Path $root 'dist'
$jar = Join-Path $root 'app\build\libs\pocket-xiaoli-desktop.jar'
$appName = 'PocketXiaoli'
# 版本号唯一真源：仓库根 gradle.properties 的 versionName（与 Android 同一口径）。
$propsFile = Join-Path (Split-Path $root -Parent) 'gradle.properties'
if (-not (Test-Path $propsFile)) { throw "找不到版本真源：$propsFile" }
$appVersion = (Select-String -Path $propsFile -Pattern '^\s*versionName\s*=' |
        Select-Object -First 1).Line -replace '^\s*versionName\s*=\s*', ''
$appVersion = $appVersion.Trim()
if ($appVersion -notmatch '^\d+\.\d+\.\d+$') {
    throw "gradle.properties 的 versionName 必须是 x.y.z（jpackage 限制），实际为「$appVersion」"
}
$imageDir = Join-Path $dist $appName

if (-not $SkipBuild) {
    Write-Host '→ gradlew fatJar' -ForegroundColor Cyan
    Push-Location $root
    & .\gradlew.bat :app:fatJar --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "fatJar 失败（exit $LASTEXITCODE）" }
    Pop-Location
}
if (-not (Test-Path $jar)) { throw "找不到 $jar，先跑 gradlew fatJar" }

# 图标：没有就现场生成（tools\make-icon.ps1 用 GDI+ 画，不依赖外部素材）
$icon = Join-Path $root 'docs\icon.ico'
if (-not (Test-Path $icon)) {
    Write-Host '→ 生成应用图标' -ForegroundColor Cyan
    & pwsh -NoProfile -File (Join-Path $root 'tools\make-icon.ps1') | Out-Null
    if (-not (Test-Path $icon)) { throw "图标生成失败：$icon" }
}

if (Test-Path $imageDir) { Remove-Item $imageDir -Recurse -Force }
New-Item -ItemType Directory -Path $dist -Force | Out-Null

# ── jlink 裁剪口径 ──────────────────────────────────────────────────
# 默认（非模块化 app）jpackage 会把整套 JDK 都塞进 runtime：实测 64 个模块、
# runtime\lib\modules 114.9 MB，压缩后仍占 36 MB，是包里最大的一块。
# 这里显式只带真正用到的模块：
#   java.base      —— 核心
#   java.desktop   —— AWT / Swing / Compose Desktop
#   java.logging   —— OkHttp 的 java.util.logging
#   jdk.crypto.ec  —— TLS 的 ECDHE（访问 api.deepseek.com 必须要）
#   java.management —— ManagementFactory（配置体检卡量 JVM→首帧；缺它首帧计时会抛 NoClassDefFoundError）
#   jdk.unsupported—— sun.misc.Unsafe（若干库用）
#   jdk.charsets   —— GBK/GB18030 等扩展字符集，避免中文环境踩坑
# 附加：--compress=zip-6（JDK 21 用 zip-6，别再写已废弃的 compress=2）、
#       --strip-debug / --no-header-files / --no-man-pages 都是纯删减，无运行期影响。
# 注意：--add-modules 不能塞进 --jlink-options（jpackage 会报「无效的 jlink 选项」），
# 它是 jpackage 自己的顶层参数；--jlink-options 只放纯 jlink 开关。
$runtimeModules = 'java.base,java.desktop,java.logging,java.management,jdk.crypto.ec,jdk.unsupported,jdk.charsets'
$jlinkOptions = '--compress=zip-6 --strip-debug --no-header-files --no-man-pages'

Write-Host "→ jpackage app-image ($appName $appVersion)" -ForegroundColor Cyan
Push-Location $root
try {
    & jpackage `
        --type app-image `
        --name $appName `
        --app-version $appVersion `
        --vendor 'Pocket Xiaoli' `
        --description '口袋小鲤 · 桌面版（烟台理工学院校园助手）' `
        --input 'app\build\libs' `
        --main-jar 'pocket-xiaoli-desktop.jar' `
        --main-class 'com.pocket.xiaoli.desktop.MainKt' `
        --icon $icon `
        --java-options '-Xmx1g' `
        --java-options '-Dfile.encoding=UTF-8' `
        --jlink-options $jlinkOptions `
        --add-modules $runtimeModules `
        --dest $dist
    if ($LASTEXITCODE -ne 0) { throw "jpackage 失败（exit $LASTEXITCODE）" }
} finally {
    Pop-Location
}

$zip = Join-Path $dist "$appName-$appVersion-win-x64.zip"

# ── skiko 原生库随包走 ──────────────────────────────────────────────
# 为什么要这一步：skiko 默认把 skiko-windows-x64.dll 解压到 %USERPROFILE%\.skiko，
# 在这台机器上 jpackage 出来的 JVM 写用户目录会被拦（java.nio.file.AccessDeniedException:
# C:\Users\<user>\.skiko），应用直接起不来。用 -Dskiko.library.path 指定包内目录后
# skiko 直接从那里加载，不再碰用户目录，也不依赖任何解压缓存。
$appDir = Join-Path $imageDir 'app'
$nativeDir = Join-Path $appDir 'native'
New-Item -ItemType Directory -Path $nativeDir -Force | Out-Null
Push-Location $nativeDir
try {
    & jar xf $jar 'skiko-windows-x64.dll' 'skiko-windows-x64.dll.sha256' 'icudtl.dat'
    if ($LASTEXITCODE -ne 0) { throw "从 jar 提取 skiko 原生库失败（exit $LASTEXITCODE）" }
} finally {
    Pop-Location
}
if (-not (Test-Path (Join-Path $nativeDir 'icudtl.dat'))) { throw 'native\icudtl.dat 缺失：skiko 会报 SkLoadICU 并崩溃' }

$cfg = Join-Path $appDir "$appName.cfg"
$cfgText = Get-Content $cfg -Raw
if ($cfgText -notmatch 'skiko\.library\.path') {
    $cfgText = $cfgText.Replace('[JavaOptions]', "[JavaOptions]`r`njava-options=-Dskiko.library.path=`$APPDIR\native")
    Set-Content -Path $cfg -Value $cfgText -NoNewline
}
Write-Host '→ 已内置 skiko 原生库（-Dskiko.library.path）' -ForegroundColor Cyan

# ── 去掉三类纯冗余（都在运行时验证过用不到）────────────────────────
# 1) app 里的 jar 不再需要自带 skiko 原生库：运行期走 $APPDIR\native（上面那一步）。
#    同一份 skiko-windows-x64.dll(13.5 MB) + icudtl.dat(10 MB) 原本在 jar 和 native 各存一份。
# 2) runtime\lib\ct.sym 是 javac 的编译期签名表（10 MB），运行期没有 javac，纯死重。
# 3) runtime\lib\src.zip 若存在同理（本机没有，留着判空防回归）。
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarInImage = Join-Path $appDir 'pocket-xiaoli-desktop.jar'
$zipFile = [System.IO.Compression.ZipFile]::Open($jarInImage, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $saved = 0L
    foreach ($name in @('skiko-windows-x64.dll', 'skiko-windows-x64.dll.sha256', 'icudtl.dat')) {
        $entry = $zipFile.GetEntry($name)
        if ($entry) {
            $saved += $entry.Length
            $entry.Delete()
        }
    }
    Write-Host ("→ 从 app jar 移除 skiko 原生库副本（省 {0:N1} MB）" -f ($saved / 1MB)) -ForegroundColor Cyan
} finally {
    $zipFile.Dispose()
}

foreach ($dead in @('runtime\lib\ct.sym', 'runtime\lib\src.zip')) {
    $path = Join-Path $imageDir $dead
    if (Test-Path $path) {
        $mb = (Get-Item $path).Length / 1MB
        Remove-Item $path -Force
        Write-Host ("→ 删除 {0}（省 {1:N1} MB）" -f $dead, $mb) -ForegroundColor Cyan
    }
}

# ── 模块断言：jlink 裁掉的模块只在打包产物里暴露，跑 fat jar 的 CI 抓不到 ──
# 真实回归：首帧计时用了 ManagementFactory（java.management），裁剪后的 runtime 没带它，
# 装好的应用首帧抛 NoClassDefFoundError —— 窗口照样出现，所以肉眼看是"正常"的。
# 断言放在压缩之前：缺模块时不要产出一个能装但坏掉的 zip。
$releaseFile = Join-Path (Join-Path $imageDir 'runtime') 'release'
if (-not (Test-Path $releaseFile)) { throw "找不到 runtime 的 release 文件，无法校验裁剪结果：$releaseFile" }
$release = Get-Content $releaseFile -Raw
$missing = $runtimeModules.Split(',') | Where-Object { $release -notlike "*$_*" }
if ($missing) { throw "裁剪后的 runtime 缺模块：$($missing -join ', ')（jpackage 没把它们打进去）" }
Write-Host "✓ runtime 模块齐全：$runtimeModules" -ForegroundColor Green

if (Test-Path $zip) { Remove-Item $zip -Force }
Write-Host '→ 压缩绿色版' -ForegroundColor Cyan
# 注意：条目**不带顶层目录**（PocketXiaoli.exe/app/runtime 直接在根上）。
# 这是安装器的解压约定（Program.cs 把条目原样落到目标目录），改它会连带改安装器。
Compress-Archive -Path (Join-Path $imageDir '*') -DestinationPath $zip -CompressionLevel Optimal

$size = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "✓ 完成：$imageDir" -ForegroundColor Green
Write-Host "✓ 压缩包：$zip（${size} MB）" -ForegroundColor Green
