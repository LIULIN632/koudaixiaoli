#Requires -Version 5.1
<#
.SYNOPSIS
    口袋小鲤 — 一键打包（Windows / PowerShell 版）

.DESCRIPTION
    与 build.sh 语义一致，但可在原生 Windows 上运行（build.sh 依赖 bash 的
    sed -i / grep -oP，Windows 下无法直接执行）。

    流程：
      1. 读取 gradle.properties 的 versionCode / versionName
      2. 版本号递增（三段式，每段上限 99，补丁满 99 进位次版本，次版本满 99 进位主版本）
      3. 写回 gradle.properties
      4. 调用 gradlew renameApk —— 由它统一负责 assembleRelease、复制到根目录、
         按「保留最近 3 个」归档旧包（这部分复用项目既有实现，不另起一套）
      5. 校验产物存在 + 签名指纹，打印汇总

    ⚠️ 版本号住在 gradle.properties，**不是** app/build.gradle.kts。
    原因见 gradle.properties 里的注释：写进构建脚本会让"发一次版"触发全量重编
    （实测改 kts 72.5 秒 vs 改 properties 53.3 秒，空转只要 1.9 秒）。

.PARAMETER NoBump
    不递增版本号，用当前版本号直接打包。用于重新出同版本包（例如同版本修 bug 重打）。

.PARAMETER SkipSignCheck
    跳过签名校验（签名工具不存在时自动跳过，此参数用于显式跳过）。

.PARAMETER Rollout
    写入 announce\feed.json 的 app.rollout（灰度百分比 0..100）。
    没传时：**升了版本**就复位成 100（feed 里原来的数字属于上一版，留着会让新版悄悄
    沿用上一次的档位）；**-NoBump 同版本重打**则完全不动这个字段 —— 因为「10% → 50% →
    100%」这三档本来就只改 feed，不该重新打包。

.EXAMPLE
    .\build.ps1
    递增版本号并打包。

.EXAMPLE
    .\build.ps1 -NoBump
    不递增版本号，直接打包当前版本。

.EXAMPLE
    .\build.ps1 -Rollout 10
    打包并把这一版只放给 10% 的设备（其余人看不到更新提示，公告照常可见）。
#>
[CmdletBinding()]
param(
    [switch]$NoBump,
    [switch]$SkipSignCheck,
    [ValidateRange(0, 100)][int]$Rollout
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── 控制台输出一律走 UTF-8 ──
#
# 为什么要绕这一圈：Windows PowerShell 5.1 下 `[Console]::OutputEncoding = UTF8` 并**不生效** ——
# 实测把「结果」显示成「缁撴灉」（按 UTF-8 字节解释成了 ANSI 字符）。
# 而 build.bat 在没有 pwsh 的机器上正是回退到 5.1，所以必须处理。
#
# 解法：不依赖控制台的编码设置，自己拿标准输出流按 UTF-8 写字节。
# 7.x 下与编码设置冲突也不要紧 —— 那条分支已经输出过内容了。
$script:Utf8Out = New-Object System.IO.StreamWriter(
    [Console]::OpenStandardOutput(), (New-Object System.Text.UTF8Encoding($false))
)
$script:Utf8Out.AutoFlush = $true
function Write-Utf8([string]$text) { $script:Utf8Out.Write($text + [Environment]::NewLine) }

if ([Console]::OutputEncoding -ne [System.Text.Encoding]::UTF8) {
    try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
}

# 项目根 = 本脚本所在目录；用 $PSScriptRoot 而非当前目录，所以在任何地方调用都成立
$Root        = $PSScriptRoot
$PropsFile   = Join-Path $Root 'gradle.properties'
$GradleBat   = Join-Path $Root 'gradlew.bat'
$KtsFile     = Join-Path $Root 'app\build.gradle.kts'

function Write-Head($text) {
    Write-Utf8 ''
    Write-Utf8 '========================================'
    Write-Utf8 "  $text"
    Write-Utf8 '========================================'
}

function Write-Step($text) { Write-Utf8 "`n>>> $text" }
function Write-Ok($text)   { Write-Utf8 "  [OK] $text" }
function Write-Warn($text) { Write-Utf8 "  [警告] $text" }
function Write-Info($text) { Write-Utf8 "  $text" }
function Fail($text) {
    Write-Utf8 "`n[错误] $text"
    exit 1
}

# ── 前置检查 ──
Write-Head '口袋小鲤 — 一键打包'

if (-not (Test-Path $PropsFile)) { Fail "找不到 $PropsFile" }
if (-not (Test-Path $GradleBat)) { Fail "找不到 $GradleBat" }

# 迁移护栏：版本号若又出现在 build.gradle.kts 里，说明有人把它改回去了 ——
# 那会让每次发版重新付出全量重编的代价，而这里读的 properties 会变成**无效值**
#（APK 里仍是 kts 那个号），是最难查的一类不一致。直接在打包前拦住。
if (Test-Path $KtsFile) {
    $ktsRaw = Get-Content $KtsFile -Raw -Encoding UTF8
    if ($ktsRaw -match '(?m)^\s*versionCode\s*=\s*\d') {
        Fail @"
版本号又写回 app/build.gradle.kts 了。
请把它改回 gradle.properties 的 versionCode=数字（打包脚本只递增那份）。
这么改的理由见 gradle.properties 的注释：写进构建脚本会让每次发版全量重编。
"@
    }
}

# ── 灰度档位要不要动 ──
#
# 只认「显式传了 -Rollout」，不能看 `$Rollout` 的值：不传时它是 [int] 的默认 0，
# 而 0 在灰度里是「刹车」（谁都不给看），当成用户的意思会把整个发版按停。
$hasRolloutParam = $PSBoundParameters.ContainsKey('Rollout')

# ── 1. 读取当前版本 ──
$content = Get-Content $PropsFile -Raw -Encoding UTF8

# 只认行首的 key=value（不加 (?m)^ 会匹配到注释里出现的同名文本）
$vcMatch = [regex]::Match($content, '(?m)^\s*versionCode\s*=\s*(\d+)\s*$')
$vnMatch = [regex]::Match($content, '(?m)^\s*versionName\s*=\s*([0-9][0-9.]*)\s*$')
if (-not $vcMatch.Success -or -not $vnMatch.Success) {
    Fail "无法解析版本号，请检查 $PropsFile 里的 versionCode / versionName（格式：versionCode=102）"
}

[int]$oldVc    = $vcMatch.Groups[1].Value
[string]$oldVn = $vnMatch.Groups[1].Value
Write-Info "`n当前版本: v$oldVn ($oldVc)"

# ── 2. 版本号递增 ──
# 保持项目既有规律：versionCode 随每次打包 +1，versionName 按三段式补丁位 +1。
# 不自行发明 versionCode 编码规则。
$newVc = $oldVc
$newVn = $oldVn

if (-not $NoBump) {
    $newVc = $oldVc + 1

    $parts = $oldVn.Split('.')
    if ($parts.Count -lt 3) {
        # 容忍 "1.0" 这种两段式写法，补 0 成三段
        $parts = @($parts + @('0', '0'))[0..2]
    }
    [int]$major = $parts[0]
    [int]$minor = $parts[1]
    [int]$patch = $parts[2]

    $patch++
    if ($patch -gt 99) { $patch = 0; $minor++ }
    if ($minor -gt 99) { $minor = 0; $major++ }

    $newVn = "$major.$minor.$patch"
    Write-Info "新版本:   v$newVn ($newVc)"
} else {
    Write-Info '已指定 -NoBump，沿用当前版本号'
}

# ── 3. 写回版本号 ──
if (-not $NoBump) {
    Write-Step '写入新版本号'
    # 用 MatchEvaluator 逐行替换，而不是 -replace 裸串：
    # 后者会把注释里出现的 "versionCode=102" 一起改掉。
    $updated = [regex]::Replace($content, '(?m)^(\s*versionCode\s*=\s*)\d+\s*$', "`${1}$newVc")
    $updated = [regex]::Replace($updated, '(?m)^(\s*versionName\s*=\s*)[0-9][0-9.]*\s*$', "`${1}$newVn")

    if ($updated -eq $content) {
        Fail '版本号替换未生效（正则未匹配）。请检查 gradle.properties 的写法。'
    }
    # UTF-8 无 BOM：Gradle 能读带 BOM 的文件，但无 BOM 更干净，且避免生成噪声 diff
    Set-Content -Path $PropsFile -Value $updated -Encoding UTF8 -NoNewline
    Write-Ok "versionCode = $oldVc -> $newVc"
    Write-Ok "versionName = `"$oldVn`" -> `"$newVn`""
}

# ── 4. 构建（renameApk 负责 assembleRelease + 复制到根目录 + 归档旧包）──
Write-Step '构建 Release APK（R8 混淆 + 资源压缩 + 签名）'
Write-Info '  首次或改过构建脚本约 1-3 分钟；只递增版本号约 1 分钟...'

Push-Location $Root
try {
    & $GradleBat renameApk --console=plain
    $gradleExit = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($gradleExit -ne 0) {
    Fail "Gradle 构建失败（exit=$gradleExit）。版本号已递增，如需回退请手工改回 v$oldVn ($oldVc)。"
}
Write-Ok '构建完成'

# ── 5. 校验产物 ──
Write-Step '校验产物'
$apkPath = Join-Path $Root "口袋小鲤_v$newVn.apk"
if (-not (Test-Path $apkPath)) {
    Fail "未找到预期产物：$apkPath`n（renameApk 应生成该文件；若版本号写回失败会导致文件名不符）"
}

$apk    = Get-Item $apkPath
$sizeMb = [math]::Round($apk.Length / 1MB, 1)

# 清单里的实际版本 —— 产物文件名对不上压缩包内容的坑就这样被堵住：
# 以前只看文件名，而文件名是脚本自己算的，等于自己证明自己。
$aapt = $null
$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $sdkRoot) {
    $lp = Join-Path $Root 'local.properties'
    if (Test-Path $lp) {
        $line = (Get-Content $lp -Encoding UTF8 | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1)
        if ($line) {
            $sdkRoot = ($line -replace '^\s*sdk\.dir\s*=\s*', '').Trim() -replace '\\\\', '\' -replace '\\:', ':'
        }
    }
}
if ($sdkRoot -and (Test-Path $sdkRoot)) {
    $aapt = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Recurse -Filter 'aapt2.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
}

$manifestVc = $null
if ($aapt) {
    $badging = & $aapt.FullName dump badging $apkPath 2>&1
    if ($LASTEXITCODE -eq 0) {
        $pkgLine = ($badging | Select-String "^package:" | Select-Object -First 1)
        if ($pkgLine) {
            $vcInApk = [regex]::Match($pkgLine.Line, "versionCode='(\d+)'").Groups[1].Value
            $vnInApk = [regex]::Match($pkgLine.Line, "versionName='([^']*)'").Groups[1].Value
            $manifestVc = $vcInApk
            if ($vcInApk -ne "$newVc" -or $vnInApk -ne $newVn) {
                Fail "APK 内的版本号与预期不一致：包内 v$vnInApk ($vcInApk)，预期 v$newVn ($newVc)"
            }
            Write-Ok "包内版本: v$vnInApk ($vcInApk)  ← 与文件名一致"
        }
    }
}
if ($null -eq $manifestVc) {
    Write-Warn '未找到 aapt2，跳过「包内版本号」校验'
}

Write-Ok "文件: $($apk.Name)"
Write-Ok "大小: $sizeMb MB"

# 签名校验：优先用 apksigner，找不到就跳过（不让打包因为工具缺失而失败）
$signInfo = '（未校验）'
if (-not $SkipSignCheck) {
    $apksigner = $null
    if ($sdkRoot -and (Test-Path $sdkRoot)) {
        $apksigner = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Recurse -Filter 'apksigner.bat' -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
    }

    if ($apksigner) {
        $out = & $apksigner.FullName verify --print-certs $apkPath 2>&1
        if ($LASTEXITCODE -eq 0) {
            $digest = ($out | Select-String 'SHA-256 digest' | Select-Object -First 1)
            $signInfo = if ($digest) { ($digest.Line -replace '^.*SHA-256 digest:\s*', '').Trim() } else { '已签名' }
            Write-Ok "签名 SHA-256: $signInfo"
        } else {
            $msg = 'apksigner 校验未通过。'
            # keystore 配好了却验签失败 = 这个包**装不上**老用户的机器（签名指纹不一致），
            # 属于发版事故，不能只"警告一下"继续往下发。
            if (Test-Path (Join-Path $Root 'keystore.properties')) {
                Fail "$msg keystore.properties 存在，说明本该签好 —— 请先解决再发。"
            }
            Write-Warn "$msg （未配置 keystore.properties，产出的是未签名包，只能本地调试用）"
            $signInfo = '（未签名）'
        }
    } else {
        Write-Info '  [跳过] 未找到 apksigner（可设 ANDROID_HOME 或忽略）'
    }
}

# ── 5.5 同步 announce\feed.json ──
#
# 为什么必须由脚本写，而不是"发版时记得手改"：
# 客户端判定新版用的是 feed 里的 versionCode，**它一旦比真实包大，就永久显示「已是最新」**，
# Release 发出去也没人收到。这个数字和 gradle.properties 必须同源，
# 而"记得手改"在一个人维护的项目里就是一定会忘的那一步。
Write-Step '同步 announce\feed.json'
$FeedFile = Join-Path $Root 'announce\feed.json'
if (-not (Test-Path $FeedFile)) {
    Write-Warn "找不到 $FeedFile，跳过（客户端将退回读 Release 的老路）"
} else {
    $feedRaw = Get-Content $FeedFile -Raw -Encoding UTF8
    $apkSha  = (Get-FileHash $apkPath -Algorithm SHA256).Hash.ToLower()
    # Gitee 的附件名是脚本上传时用的 ASCII 名（见 tools\deploy\publish-gitee.ps1），
    # 不是根目录那个中文文件名 —— 直链必须拼前者，否则 404。
    $apkUrl  = "https://gitee.com/liulin62633/pocket-carp/releases/download/v$newVn/koudaixiaoli_v$newVn.apk"

    $before = $feedRaw

    # ⚠️ 只改 **app 块那一段**。feed 里可以有顶层 `beta` 块（内测通道，见 docs/更新与公告.md §3），
    # 它对客户端是同一个形状（versionCode / versionName / apkUrl / sha256 / rollout）。
    # 整份文件全局替换会把内测包的版本号和指纹一并覆盖成稳定版的 ——
    # 那内测用户永远装不上内测包，而且 feed 看上去完全正常，是最难发现的一种错。
    # 做法：在 `beta` 键处把文件切成两半，前半（含 app）随便改，后半原样拼回去
    # （切在键名上，所以拼回去与原文逐字节一致 —— 缩进、换行都不受影响）。
    # 代价是约定 app 必须写在 beta 之前；写反了当场 Fail，而不是静默改错块。
    # 这里**不加行首锚点**：feed.json 目前是"一键一行"的排版，锚点更严，但排版一变
    # 就检测不到 beta 块，于是静默覆盖内测包 —— 漏检比误检危险得多。
    $tail = ''
    $betaMatch = [regex]::Match($feedRaw, '"beta"\s*:\s*\{')
    if ($betaMatch.Success) {
        $appMatch = [regex]::Match($feedRaw, '"app"\s*:\s*\{')
        if (-not $appMatch.Success -or $appMatch.Index -gt $betaMatch.Index) {
            Fail "announce\feed.json 里 app 块要写在 beta 块之前 —— 脚本只改 app 那一段，写反了会改错通道。"
        }
        $tail = $feedRaw.Substring($betaMatch.Index)
        $feedRaw = $feedRaw.Substring(0, $betaMatch.Index)
    }

    $feedRaw = [regex]::Replace($feedRaw, '("versionCode"\s*:\s*)\d+', "`${1}$newVc")
    $feedRaw = [regex]::Replace($feedRaw, '("versionName"\s*:\s*)"[^"]*"', "`${1}`"$newVn`"")
    $feedRaw = [regex]::Replace($feedRaw, '("apkUrl"\s*:\s*)"[^"]*"', "`${1}`"$apkUrl`"")
    $feedRaw = [regex]::Replace($feedRaw, '("sha256"\s*:\s*)"[^"]*"', "`${1}`"$apkSha`"")

    # 灰度档位（app.rollout）—— 客户端拿它决定「这台设备此刻看不看得到这一版」，
    # 判定见 AppFeedView.build + ReleaseGate.isRolloutHit。
    #   · 显式传 -Rollout：写它。
    #   · 没传但本次真的升了版本：复位成 100 —— feed 里原有那个数字是**上一版**的档位，
    #     不复位就会让新版一出去只有 10% 的人看得见，而发版人以为全量了。
    #   · 没传且没升版（-NoBump，同版本重打）：完全不动。10% → 50% → 100% 这三档
    #     本就该只改 feed、不重打包（包是同一个，sha256 都没变）。
    $nextRollout = if ($hasRolloutParam) { $Rollout }
                   elseif ($newVc -ne $oldVc) { 100 }
                   else { $null }
    if ($null -ne $nextRollout) {
        $rxRollout = [regex]'("rollout"\s*:\s*)\d+'
        if ($rxRollout.IsMatch($feedRaw)) {
            # 只换第一处：将来 beta / 其它通道也有 rollout 字段时，
            # 一次全换会把别的通道按同一个档位关掉。
            $feedRaw = $rxRollout.Replace($feedRaw, "`${1}$nextRollout", 1)
        } else {
            # 老 feed 没这个键时客户端按全量处理，所以补上键才能真的放量
            $rxVn = [regex]'("versionName"\s*:\s*"[^"]*")'
            if (-not $rxVn.IsMatch($feedRaw)) {
                Fail "$FeedFile 里找不到 app.versionName，无法写入 rollout —— 请先手工把 app 块补全。"
            }
            $feedRaw = $rxVn.Replace($feedRaw, "`${1},`n    `"rollout`": $nextRollout", 1)
        }
    }

    # 后半段（beta 块及其之后）原样拼回 —— 这一段脚本一行都不该动
    $feedRaw = $feedRaw + $tail

    # 改完必须还能解析，且这一步要在写盘之前。
    # 脚本是拿正则动一份运维手写的 JSON：切错一刀、或本来就有个多余的逗号，
    # 都会把整份「公告 + 更新通道」写废。而客户端解析失败时是**静默退回老路**，
    # 手机上看不出任何异常 —— 所以宁可让发版脚本当场停下，也不能把坏文件推出去。
    try {
        $null = ConvertFrom-Json $feedRaw
    } catch {
        Fail "announce\feed.json 改完解析不过：$($_.Exception.Message) —— 原文件未改动，请先修 feed 再重跑。"
    }

    if ($feedRaw -eq $before) {
        # -NoBump 重打同版本时走到这里属正常，不算错
        Write-Info '  feed.json 无需变更（版本号与摘要均已一致）'
    } else {
        Set-Content -Path $FeedFile -Value $feedRaw -Encoding UTF8 -NoNewline
        Write-Ok "versionCode=$newVc versionName=$newVn sha256=$($apkSha.Substring(0,12))…"
        if ($null -ne $nextRollout) { Write-Ok "app.rollout=$nextRollout（${nextRollout}% 的设备看得到这一版）" }
        else { Write-Info '  app.rollout 保持不变（同版本重打，档位由 feed 自己管）' }
        if ($tail) { Write-Info '  beta 块原样保留（内测包要手工改，脚本只发稳定版）' }
        Write-Info "  ⚠️ 记得把这份 feed.json 一起提交并推到两个仓，否则客户端读不到这一版"
    }
}

# ── 6. 汇总 ──
Write-Head '打包完成！'
Write-Utf8 "  版本:  v$newVn ($newVc)"
Write-Utf8 "  文件:  $($apk.Name)"
Write-Utf8 "  大小:  $sizeMb MB"
Write-Utf8 "  签名:  $signInfo"
Write-Utf8 "  路径:  $apkPath"

# 顺带列出根目录现有的包，方便确认 renameApk 的「保留最近 3 个」是否生效
#
# ⚠️ 必须用 `@(...)` 强制成数组：PowerShell 7 下**单个 FileInfo 没有 `.Count`**，
# 于是"只有 1 个包"时 `$allApks.Count` 会抛「找不到属性 Count」。
# 这个坑在应用改名时暴露过一次 —— 改名后 glob 只匹配到新包，从 3 个变成 1 个。
$allApks = @(Get-ChildItem (Join-Path $Root '口袋小鲤_v*.apk') -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending)
if ($allApks.Count -gt 0) {
    Write-Utf8 "`n  根目录现有包（$($allApks.Count) 个）："
    foreach ($a in $allApks) {
        # 注意：不要用 '<-' 这类含 '<' 的字面量 —— '<' 在 PowerShell 里是保留的重定向符号
        $mark = if ($a.FullName -eq $apkPath) { '  <<< 本次产出' } else { '' }
        Write-Utf8 ("    {0,-26} {1,6:N1} MB{2}" -f $a.Name, ($a.Length / 1MB), $mark)
    }
}
