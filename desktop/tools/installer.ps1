# 打 Windows 安装程序（单文件 EXE）。
#
# 链路：gradlew fatJar → jpackage app-image → zip → 作为嵌入资源编进 .NET 安装器 → 单文件 EXE。
# 为什么不用 jpackage --type exe/msi：那条路依赖 WiX（本机没装；winget 安装还要管理员 + NetFx3）。
# 安装器自己实现：解压到 %LOCALAPPDATA%\Programs\PocketXiaoli、建开始菜单/桌面快捷方式、
# 写「设置 → 应用」卸载项，装完直接启动。不需要管理员权限，用户机器不需要装 Java。
#
# 用法:
#   pwsh tools/installer.ps1                 # 用 dist 里已有的 zip 当 payload
#   pwsh tools/installer.ps1 -RebuildZip     # 先重打 app-image 与 zip
param(
    [switch]$RebuildZip
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot          # desktop\
$dist = Join-Path $root 'dist'
$appName = 'PocketXiaoli'
# 版本号唯一真源：仓库根 gradle.properties 的 versionName（与 Android / 绿色包同一口径）。
$propsFile = Join-Path (Split-Path $root -Parent) 'gradle.properties'
if (-not (Test-Path $propsFile)) { throw "找不到版本真源：$propsFile" }
$version = (Select-String -Path $propsFile -Pattern '^\s*versionName\s*=' |
        Select-Object -First 1).Line -replace '^\s*versionName\s*=\s*', ''
$version = $version.Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') {
    throw "gradle.properties 的 versionName 必须是 x.y.z（jpackage 限制），实际为「$version」"
}
$imageZip = Join-Path $dist "$appName-$version-win-x64.zip"
$projectDir = Join-Path $root 'installer\app'
$publishDir = Join-Path $dist 'installer-publish'
$outExe = Join-Path $dist "口袋小鲤-$version-安装程序.exe"

if ($RebuildZip -or -not (Test-Path $imageZip)) {
    Write-Host '→ 先打 app-image 与 zip' -ForegroundColor Cyan
    & pwsh -NoProfile -File (Join-Path $root 'tools\package.ps1') -SkipBuild
    if (-not (Test-Path $imageZip)) { throw "打包失败：$imageZip 不存在" }
}

Write-Host '→ 准备 payload' -ForegroundColor Cyan
Copy-Item $imageZip (Join-Path $projectDir 'payload.zip') -Force

Write-Host '→ dotnet publish 安装器（单文件，自带 .NET 运行时）' -ForegroundColor Cyan
Push-Location $projectDir
try {
    & dotnet publish .\PocketXiaoliSetup.csproj -c Release -r win-x64 -o $publishDir --nologo -v minimal -p:Version=$version
    if ($LASTEXITCODE -ne 0) { throw "dotnet publish 失败（exit $LASTEXITCODE）" }
} finally {
    Pop-Location
    Remove-Item (Join-Path $projectDir 'payload.zip') -Force -ErrorAction SilentlyContinue
}

$built = Join-Path $publishDir 'PocketXiaoliSetup.exe'
if (-not (Test-Path $built)) { throw "publish 后找不到 $built" }

if (Test-Path $outExe) { Remove-Item $outExe -Force }
Copy-Item $built $outExe -Force
$size = [math]::Round((Get-Item $outExe).Length / 1MB, 1)
Write-Host "✓ 安装程序：$outExe（${size} MB）" -ForegroundColor Green
