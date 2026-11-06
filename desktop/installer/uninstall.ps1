# 口袋小鲤 · 桌面版 卸载脚本（「设置 → 应用」里的卸载入口指向这里）
# 交互式运行时问一句「是否删本地数据」；无人值守用 -KeepData / -RemoveData。
param(
    [switch]$KeepData,
    [switch]$RemoveData
)
$ErrorActionPreference = 'SilentlyContinue'

$target = Split-Path -Parent $MyInvocation.MyCommand.Path
$appName = '口袋小鲤'

# 快捷方式
foreach ($lnk in @(
        (Join-Path ([Environment]::GetFolderPath('Programs')) "$appName.lnk"),
        (Join-Path ([Environment]::GetFolderPath('Desktop')) "$appName.lnk"))) {
    Remove-Item $lnk -Force
}

# 卸载项
Remove-Item 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketXiaoli' -Recurse -Force

# 用户数据单独问一句（默认保留，避免误删课表）
$data = Join-Path $env:APPDATA 'PocketXiaoli'
if (Test-Path $data) {
    if ($RemoveData) {
        Remove-Item $data -Recurse -Force
    } elseif (-not $KeepData) {
        Add-Type -AssemblyName System.Windows.Forms
        $answer = [System.Windows.Forms.MessageBox]::Show(
            "是否同时删除本地数据？`n`n$data`n`n（课表 / 待办 / 会话都在这里，选「否」以后重装还能接着用）",
            "$appName 卸载", 'YesNo', 'Question')
        if ($answer -eq 'Yes') { Remove-Item $data -Recurse -Force }
    }
}

# 自己这个文件夹最后删：拷一份 cmd 到临时目录，等进程退出后再删目录
$cmd = Join-Path $env:TEMP 'pocketxiaoli-uninstall.cmd'
@"
@echo off
timeout /t 2 /nobreak >nul
rmdir /s /q "$target"
del "%~f0"
"@ | Set-Content -Path $cmd -Encoding ASCII
Start-Process -FilePath 'cmd.exe' -ArgumentList "/c `"$cmd`"" -WindowStyle Hidden
