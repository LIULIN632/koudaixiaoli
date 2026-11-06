# 口袋小鲤 · 桌面版 安装脚本（由 iexpress 打出的安装器调用，也可单独运行）
# 安装到 %LOCALAPPDATA%\Programs\PocketXiaoli（无需管理员），并创建开始菜单/桌面快捷方式、
# 注册「设置 → 应用」里的卸载项。
$ErrorActionPreference = 'Stop'

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$payload = Join-Path $here 'payload.zip'
$target = Join-Path $env:LOCALAPPDATA 'Programs\PocketXiaoli'
$appName = '口袋小鲤'

# 版本号跟随仓库根 gradle.properties 的 versionName（与安装器 / 绿色包同一真源）
$propsFile = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'gradle.properties'
$displayVersion = if (Test-Path $propsFile) {
    ((Select-String -Path $propsFile -Pattern '^\s*versionName\s*=' | Select-Object -First 1).Line `
            -replace '^\s*versionName\s*=\s*', '').Trim()
} else { '0.0.0' }

if (-not (Test-Path $payload)) {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show('安装包内的 payload.zip 丢失，无法继续。', $appName, 'OK', 'Error') | Out-Null
    exit 1
}

Write-Host "→ 安装到 $target"
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
New-Item -ItemType Directory -Path $target -Force | Out-Null
Expand-Archive -Path $payload -DestinationPath $target -Force

$exe = Join-Path $target 'PocketXiaoli.exe'
if (-not (Test-Path $exe)) { throw "解压后找不到 PocketXiaoli.exe：$target" }

# 卸载脚本（安装目录里留一份，注册表指向它）
Copy-Item (Join-Path $here 'uninstall.ps1') (Join-Path $target 'uninstall.ps1') -Force

Write-Host '→ 创建快捷方式'
$ws = New-Object -ComObject WScript.Shell
$linkPaths = @(
    (Join-Path ([Environment]::GetFolderPath('Programs')) "$appName.lnk"),
    (Join-Path ([Environment]::GetFolderPath('Desktop')) "$appName.lnk")
)
foreach ($lnkPath in $linkPaths) {
    $lnk = $ws.CreateShortcut($lnkPath)
    $lnk.TargetPath = $exe
    $lnk.WorkingDirectory = $target
    $lnk.IconLocation = "$exe,0"
    $lnk.Description = '口袋小鲤 · 桌面版（烟台理工学院校园助手）'
    $lnk.Save()
}

Write-Host '→ 注册卸载信息'
$uninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketXiaoli'
New-Item -Path $uninstallKey -Force | Out-Null
Set-ItemProperty -Path $uninstallKey -Name DisplayName -Value '口袋小鲤 桌面版'
Set-ItemProperty -Path $uninstallKey -Name DisplayVersion -Value $displayVersion
Set-ItemProperty -Path $uninstallKey -Name Publisher -Value 'Pocket Xiaoli'
Set-ItemProperty -Path $uninstallKey -Name InstallLocation -Value $target
Set-ItemProperty -Path $uninstallKey -Name DisplayIcon -Value "$exe,0"
Set-ItemProperty -Path $uninstallKey -Name NoModify -Value 1 -Type DWord
Set-ItemProperty -Path $uninstallKey -Name NoRepair -Value 1 -Type DWord
Set-ItemProperty -Path $uninstallKey -Name EstimatedSize -Value ([int]((Get-ChildItem $target -Recurse -File | Measure-Object Length -Sum).Sum / 1KB)) -Type DWord
Set-ItemProperty -Path $uninstallKey -Name UninstallString -Value "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$target\uninstall.ps1`""

Write-Host '→ 启动应用'
Start-Process -FilePath $exe -WorkingDirectory $target

Write-Host '✓ 安装完成：开始菜单 / 桌面快捷方式已就绪'
Start-Sleep -Seconds 2
