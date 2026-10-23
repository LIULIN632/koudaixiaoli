@echo off
rem ============================================================
rem  PocketXiaoli - Windows build entry point
rem
rem  This file is ONLY a thin ASCII wrapper around build.ps1.
rem  Do NOT put non-ASCII characters in this file: cmd.exe reads
rem  .bat files byte-wise as ANSI regardless of chcp, so UTF-8
rem  Chinese would be executed as bogus commands.
rem  All Chinese user-facing output lives in build.ps1 instead.
rem
rem  Why the wrapper exists: the old build.bat called
rem  "gradlew renameApk" directly, which does NOT bump the
rem  version. Since renameApk writes to
rem  PocketXiaoli_v{versionName}.apk, running it twice would
rem  silently overwrite the previous build. build.ps1 matches
rem  build.sh semantics (bump -> build -> verify signature).
rem
rem  Pass-through args, e.g.:  build.bat -NoBump
rem ============================================================

chcp 65001 >nul

rem 编码说明见 build.ps1 顶部：5.1 下 [Console]::OutputEncoding 赋值无效，
rem 所以由 build.ps1 自己按 UTF-8 写标准输出流。
rem 5.1 分支额外用 -Command 而不是 -File —— 5.1 对**无 BOM** 的 UTF-8 脚本
rem 会按 ANSI 解析，脚本里的中文会先坏在读取阶段。

where pwsh >nul 2>&1
if %errorlevel%==0 (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "[Console]::OutputEncoding=[Text.Encoding]::UTF8; & '%~dp0build.ps1' %*"
)
set BUILD_EXIT=%errorlevel%

if %BUILD_EXIT% neq 0 (
    echo.
    echo [FAILED] build.ps1 exited with code %BUILD_EXIT%
    pause
    exit /b %BUILD_EXIT%
)

pause
exit /b 0
