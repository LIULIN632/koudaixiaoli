# 口袋小鲤 · 桌面版

烟台理工学院校园助手「口袋小鲤」的 Windows 桌面移植。用 Kotlin + Compose Multiplatform（Desktop）重写，界面语言沿用 Android 端的视觉体系（品牌蓝 `#4D6BFE`、圆角卡片、鲤鱼吉祥物），课程/待办/学期数据存在本机，不联网也能用。

> 非官方构建：与手机端 `v0.2.4` 同源的桌面版，不替代手机端。版本号跟随仓库根 `gradle.properties` 的 `versionName`。数据可以互相搬（见「数据在哪」）。

## 截图

| 首页 | 课表 |
|---|---|
| ![首页](docs/screenshots/home.png) | ![课表](docs/screenshots/timetable.png) |

| 鲤鱼（AI） | 服务 |
|---|---|
| ![鲤鱼](docs/screenshots/assistant.png) | ![服务](docs/screenshots/services.png) |

| 我的 | 深色模式 |
|---|---|
| ![我的](docs/screenshots/profile.png) | ![深色](docs/screenshots/dark_home.png) |

| 全局搜索（Ctrl+K） | 从教务导入 |
|---|---|
| ![搜索](docs/screenshots/search.png) | ![登录](docs/screenshots/login.png) |

| 上课提醒 | 提醒设置 |
|---|---|
| ![提醒](docs/screenshots/reminder.png) | ![设置](docs/screenshots/reminder_settings.png) |

| 成绩查询（应用内） | 考试安排（粘贴通知解析） |
|---|---|
| ![成绩](docs/screenshots/grades.png) | ![考试](docs/screenshots/exams.png) |

| 考试通知解析预览 | 导入可疑时的替换确认 |
|---|---|
| ![解析预览](docs/screenshots/exams-parse.png) | ![导入确认](docs/screenshots/import-guard.png) |

| 配置体检（我的 → 数据） | 学习笔记（服务 → 笔记） |
|---|---|
| ![体检](docs/screenshots/checklist.png) | ![笔记](docs/screenshots/notes.png) |

首启引导（三步，可跳过；第一步就把「登录教务导真实课表」摆在最前面）：

![首启引导](docs/screenshots/onboarding.png)

## 功能

### 已还原（与 Android 端同一套逻辑）

- **课表**：周次/单双周、连堂课合并、按课程自动配色（15 色 + FNV 手写哈希，与手机端同一算法的移植）、非本周课程弱化显示、列表视图、前后周切换、导出 `.ics`。
- **首页**：按时段算的「下一节课」（含明天/后天提示）、今日课程时间线、本周课时统计、待办（含逾期提示）、校历倒计时。
- **鲤鱼**：会话列表 + 流式回答；校园知识库检索（BM25 风格打分，含过期策略）、快问快答、校历/课表/待办工具调用（Function Calling）；兼容任何 OpenAI 协议接口（默认 DeepSeek），Key 只存本机。
- **服务**：服务入口（教务/图书馆/第二课堂/智慧校园/一卡通/电费…按校园配置跳官方页面）、校历（按学期算第几周，内置 2026–2027）、知识库、快问快答。
- **我的**：学期设置（开始日 + 周数）、节次时间（12 节可改）、学生信息（页面自动脱敏学号）、深色/跟随系统、强调色、数据导入导出、恢复示例数据。
- **视觉**：浅色/深色两套色板、强调色切换、所有弹窗与空状态、`Ctrl+K` 全局搜索、`Ctrl+1..5` 切页、`Ctrl+,` 打开设置。

### 桌面端新增

- 三栏/宽屏布局（窗口最小 1000×660），侧边栏导航 + 右键/快捷键。
- **系统托盘**：点 × 默认收进托盘而不是退出（「我的 → 外观」可关）；托盘菜单可打开主窗口/课表，也可以退出。
- **全局搜索（`Ctrl+K`）**：一处搜课程、待办、知识库、校历事件和页面；上下键选择、Enter 打开、Esc 关闭。命中课程直接进课表编辑器，待办打开详情，知识库定位到文档，校历跳到事件当天。
- **从教务导入**：我的 → 数据 → 从教务导入，学号 + 密码 + 验证码登录正方（与手机端同一套登录/解析），导入后替换当前学期课表、保留自建日程。密码不保存。
- **成绩查询（应用内）**：服务 → 成绩，登录一次后直接拉学期列表与成绩（总分/绩点/学分/分项），不再跳浏览器；登录会话与「从教务导入」共用。
- **考试安排（应用内）**：服务 → 考试。正方教务**没有考试接口**（手机端同样如此），所以走「粘贴通知 → 规则解析 → 核对 → 添加」：一行一场认出课程/日期/时间/考场/座位，重复项默认不勾选，列表按开考日排序并显示倒计时（`还有 N 天`/`今天`/`已完成`/`时间待定`）。解析与倒计时算法在共享核心 `core/exam/`，与手机端同一份。
- **考试提醒**：开考前 3 天内托盘气泡告知一次、考试当天再提醒一次（「我的 → 提醒」可关）；与上课/待办提醒共用同一个 30 秒 ticker。
- **清掉占位内容**：首页「示例数据」卡上有 `清空示例课程` / `清空示例待办`，只删内置 seed 的那几条（按 id 判定，手建 `c_…` 与导入 `jw_…` 不动），清前自动备份 `pocket-desktop.bak-<时间>.json`（留最近 10 份）。
- **导入替换确认**：教务导入是整学期覆盖，所以先算「将导入 N 门 · 覆盖 M 门 · 保留 K 条」；门数 ≤4、老师名与内置示例一致、或替换后数量变少时**停在确认框**，不静默覆盖现有课表。导入前同样先备份。
- **导入诊断**（默认关）：「我的 → 数据 → 导入诊断」打开后，把每一次教务请求的 URL / 表单 / 重定向链 / 响应正文落到 `<数据目录>/logs/import-<时间>/`，旁边有「打开诊断日志」。只写这些，**不写请求头与响应头**（Cookie 在里面），表单里的 `mm`/`password`/`pwd` 整值打码。用来复现「导入进来是空的、少了几门课」。
- **上课前提醒**：应用运行时按「我的 → 提醒」的提前量（5/10/15/20 分钟）弹托盘气泡和应用内提示；同一门课同一天只提醒一次。
- **待办到期提醒**：到期当天 09:00 起提醒一次（同一天多条合并成一条），和上课提醒共用托盘通道。
- **配置体检卡**（我的 → 数据顶部）：教务 / AI / 数据来源 / 启动 四行，每行右侧是状态或动作 —— 未登录给「登录」，没配 Key 给「配置」，还是示例数据给「导入课表」，点该行直接跳到对应弹窗。「启动」量的是 JVM→首帧（不含 jpackage 启动器，比双击到窗口出现小 0.3~0.5s）。
- **首启引导改版**：第一步直接给「从教务导入 / 手填学期 / 先看看示例数据」三张卡，第二步是 AI（可跳过，弹窗复用主界面那一个），第三步填学生信息；任意一步可跳过。
- 导入/导出走**文件对话框**，和手机端 AppData JSON 互通。
- 深色模式独立开关 + 强调色（品牌蓝/靛/紫/青/金/玫红）。
- **成绩本地缓存 + CSV**：查过的学期按学期存进本机数据文件；下次打开不必登录也能看，顶部写明「缓存于 …」并提示不是实时数据。「导出 CSV」写带 BOM 的 UTF-8，Excel/WPS 直接打开不乱码。统计多了「平均绩点」一格。
- **课表冲突检测 + 老师/教室候选**：新建/编辑时若与已有课时间撞上（同天、节次区间相交、周次有实际重合），先列出撞的课程与重合周次，再点一次「仍然保存」才写进去；单双周错开不算撞。老师/教室输入框下方给已有值候选，点一下复用同一写法。
- **待办按截止日分组 + 批量清空**：首页待办分「已逾期 / 今天截止 / 本周内 / 以后 / 没填截止日」，已完成折成一行可一键清空（清前自动备份）；日期写法认不出时算「没填日期」，不会误标逾期。新建待办时「关联课程」给课程名候选。
- **鲤鱼离线可答**：没配 API Key 时不再只弹配置框 —— 校园事实题直接走本机知识库与快问快答，答案末尾写明来源；没收录就明说没收录，不编。
- **课表按老师/教室筛选**：课表页顶部一条筛选（候选来自现有课程，按出现次数排），选中后不相关的格子**弱化而不是消失**，右侧显示「匹配 N 门」与「清除筛选」；老师与教室同时选中要都满足。
- **开机自启（默认关）**：「我的 → 数据 → 开机自启」打开才写当前用户的注册表启动项（`HKCU\...\Run`，值名 `PocketXiaoli`，指向安装版 exe），关掉即删除。用 `reg.exe` + 参数列表调用，不经 shell；参数形状有单测钉住，没装安装版时会明确说开不了。
- **单实例 + 原子写盘**：数据是整份 JSON 覆盖写的，两个进程同时开就会互相盖掉对方的改动（开机自启 + 手动双击必然撞上）。所以第二次启动不开新窗口，而是把已在跑的窗口从最小化/托盘里叫到前台再自己退出；守卫走 `127.0.0.1` 上一个回环端口的一行握手，端口被别的服务占用时认得出来并放弃守卫照常启动，绝不误判成「已有实例」。写盘同时改成「同目录临时文件 + 原子改名」，消掉原来「先删再改名」那个文件不存在的空窗，失败会写进 `app.log` 而不是静默丢掉。
- **界面统一过一遍（0.2.7 内）**：可点元素补齐悬停 + 按压缩放（桌面端没有涟漪，原来按钮/芯片/侧栏导航点了没反应）；轻提示从浮在内容上改到**侧栏固定状态位**——底部会压住「清空已完成」和发送键，顶部会压住周切换器，侧栏两头都不挡；分段控件选中态从"白压灰"改成强调色填充；设置行图标底座与空状态图标统一用主题色软底；课表空格子由灰块改成发丝边、悬停才亮出「+」；非本周课程块保留自己的课程色而不是糊成一块灰；周次文案由「1、2、3…16 周」压成「1-16 周」；删掉五处复述性小字（首页日期行、今日安排重复标题、待办「还剩 N 项」、服务分区「教务 / 图书 / 第二课堂」、鲤鱼三处"去配置 AI"）。
- **天气（从手机端搬过来）**：解析、WMO 代码映射、30 分钟节流、「失败但仍有缓存」的语义全部提到共享核心 `core/weather`，Android 与桌面吃同一份（46 例测试跑在核心里）。桌面首页问候行下面一条：烟台 · 天气 · 温度/体感 · 风向风力，降水概率大于 0 才出现「未来 6 小时降水 N%」，右边一个刷新。数据源 Open-Meteo（免 Key、开源、CC BY 4.0，**页脚署名不能删**）；请求里只有坐标，没有设备号与账号。**「我的 → 数据 → 首页天气」可以整个关掉**（默认开），关掉后既不发请求也不显示那条。
- **学习笔记（从手机端搬过来）**：模型 `NoteItem` 提到 `core/commonMain`，Android 用 typealias 指向它、两端一份定义；排序/摘要/空标题兜底留在桌面自己的 logic —— Android 的排序在 `AppContainer`、文案也不同，硬塞进核心就变成"只有一端用的共享代码"。入口在服务页新增的「笔记」标签，列表 + 弹窗编辑，全部落本机。
- **本机日志与诊断包**：教务抓取日志（共享层 `AcademicLog`）与未捕获异常写进 `<数据目录>/logs/app.log`（5MB×2 滚动）；「我的 → 数据 → 导出诊断包」把日志、数据形状、导入诊断样本和**脱敏后的数据文件**打成 zip（`settings.aiApiKey` 一律替换成 `(已脱敏)`，且这条路径有单测钉住）。
- 一键导出课表 `.ics`（可导入系统日历）。

### 刻意没搬

- 青果 / 强智教务抓取：目前只支持正方（V8）学号密码 + 验证码登录；青果/强智仍走手机端导出 JSON。
- 验证码自动识别、WebView 登录：桌面端不识别验证码，也不内嵌浏览器，验证码由用户手动输入。
- Android 专属能力：通知、桌面小组件、生物识别、分享卡片。
- 服务页海报轮播：桌面端不出广告位（沿用项目红线）。

完整用法见 **[docs/桌面端手册.md](docs/桌面端手册.md)**（导入课表 / 查成绩 / 记考试 / 待办 / 鲤鱼 / 出问题怎么办 / 隐私边界）。

## 快速开始

### 方式一：安装包（推荐）

双击 `dist/口袋小鲤-<版本>-安装程序.exe`：

1. 弹出进度窗，自动装到 `%LOCALAPPDATA%\Programs\PocketXiaoli`（**不需要管理员权限**，不需要装 Java）；
2. 建开始菜单 + 桌面快捷方式，写「设置 → 应用」里的卸载项；
3. 装完自动启动。

卸载：Windows「设置 → 应用 → 口袋小鲤 桌面版」，或运行安装目录里的 `uninstall.ps1`。
**本地数据默认保留**（删数据要显式 `-RemoveData`），重装后课表/待办还在。

![安装程序](docs/screenshots/installer.png)

> 安装包是自解压式单文件 EXE（内嵌 jpackage app-image），不是 MSI，所以首次运行可能被 SmartScreen 拦一下。

### 方式二：绿色包

1. 解压 `dist/PocketXiaoli-<版本>-win-x64.zip`
2. 双击 `PocketXiaoli.exe`

包里自带裁剪过的 Java 运行时，**不需要另外装 JDK**。首次启动可以「跳过」引导直接看示例课程。

### 方式三：从源码跑（开发）

需要 JDK 21（本机已验证 Temurin 21.0.11）。

```powershell
cd desktop
.\gradlew.bat run          # 直接跑
.\gradlew.bat :app:fatJar  # 只要 fat jar：app\build\libs\pocket-xiaoli-desktop.jar
java -jar app\build\libs\pocket-xiaoli-desktop.jar
```

开发用参数：`--tab=0..4` 启动时直接进某一页（0 首页 / 1 课表 / 2 鲤鱼 / 3 服务 / 4 我的）。

## 数据在哪

- Windows：`%APPDATA%\PocketXiaoli\pocket-desktop.json`（首启标记：`onboarded.flag`）
- 兜底（没有 APPDATA 时）：`~/.pocket-xiaoli/`
- 「我的 → 数据」可以**导出 JSON / 导入 JSON**，结构与 Android 端 `AppData` 一致，可以直接和手机互搬。

数据全部在本机，没有账号体系，没有第三方统计/埋点/广告 SDK（与项目红线一致）。

## 配置 AI

「我的 → AI → 模型配置」：

| 项 | 说明 |
|---|---|
| Base URL | 任何兼容 OpenAI 协议的地址，默认 `https://api.deepseek.com/v1` |
| API Key | 只写本机 JSON，不上传 |
| 模型 | 默认 `deepseek-chat`，也可 `deepseek-reasoner` 或你自己的模型名 |
| 温度 | 默认 0.3（查课表这类事实问题建议 0.2~0.4） |

没配 Key 也能用：知识库检索、快问快答、课表/作业/校历的回答都走本地工具，只是不联网。

## 打包

```powershell
cd desktop
pwsh tools\package.ps1              # fatJar → jpackage app-image → zip
pwsh tools\package.ps1 -SkipBuild   # 复用已有 jar，只重做包
pwsh tools\installer.ps1            # app-image → 安装包（单文件 EXE）
pwsh tools\installer.ps1 -SkipPackage  # 复用已有 zip，只重做安装包
```

产物：

- `dist/PocketXiaoli/`：免安装目录
- `dist/PocketXiaoli-<版本>-win-x64.zip`：绿色包
- `dist/口袋小鲤-<版本>-安装程序.exe`：安装包（内嵌 green zip + 解压逻辑 + 进度窗）

### 包体与裁剪（实测数字）

- jpackage 默认带 jlink 裁剪。裁剪后 `runtime/release` 里 `MODULES` 只剩 9 个：
  `java.base java.datatransfer java.xml java.prefs java.desktop java.logging jdk.charsets
  jdk.crypto.ec jdk.unsupported`，`runtime/lib/modules` **24.4 MB**（未裁剪时 114.9 MB）。
  保留 `jdk.crypto.ec` 是为了 TLS 的 ECDHE 套件，删了会连不上 HTTPS。
- 建包后再从 app jar 里删掉重复的 skiko 原生库（`app/native` 已有一份），**省 23.4 MB**。
- 实测：runtime 47.6 MB + app 51.9 MB = 目录 **100.0 MB**；zip **70 MB**；安装包 **117 MB**。
- TLS 自检（不开窗口，进脚本用）：

  ```powershell
  .\dist\PocketXiaoli\PocketXiaoli.exe --selftest=net
  # stdout: selftest: ok http=401 tls=TLS_AES_256_GCM_SHA384 java=21.0.11 modules=trimmed
  ```

  `http=401` 是没带 Key 的正常结果 —— 说明 TLS 握手与真实 HTTPS 请求都通了；
  这条比看界面可靠（界面上「缺加密模块」和「网络不通」长得一样）。

三个环境坑，脚本里已经处理：

1. **skiko 原生库要随包走**。skiko 默认把 `skiko-windows-x64.dll` 解压到 `~/.skiko`；本机 jpackage 出来的 JVM 写用户目录会被系统拦（`AccessDeniedException`），应用直接起不来。所以 `package.ps1` 会把 `skiko-windows-x64.dll`、`icudtl.dat` 解到 `app/native`，并在 `PocketXiaoli.cfg` 里加 `-Dskiko.library.path=$APPDIR\native`（缺 `icudtl.dat` 会在文本渲染时直接崩）。
2. **没有 WiX 就出不了 MSI/EXE 安装包**（WiX 要管理员 + NetFx3，本机没装）。所以安装器改成自写的 .NET WinForms 单文件 EXE：`installer/app/`，`payload.zip` 以嵌入资源打包，运行时解压到用户目录并建快捷方式/卸载项。
3. **单文件 EXE 必须放到「工作区之外」运行**。在本仓库里直接双击/启动它时，运行环境会给子进程降权（实测：同一程序读工作区内资源正常，写 Temp/开始菜单目录被拒 `UnauthorizedAccessException`；把 exe 复制到 `%TEMP%` 下运行就完全正常）。这不是安装器 bug，验收脚本 `tools/installer.ps1` 会把产物复制到 `%TEMP%` 再测。
4. **打包后的 JVM 写 `%TEMP%` 也会被拒**（同一个降权问题的另一面）。所以 `--selftest=net` 把结果打在 **stdout**，文件副本写进数据目录、写失败也不影响退出码 —— 别把日志写到临时目录再指望能读回来。

## 架构边界（硬约束）

两个 Gradle 模块，**边界由自动检查守着**，不靠自觉：

```
:core   纯 Kotlin 逻辑（模型 / Store / 课表算法 / 知识库检索 / AI 客户端）
:app    Compose Desktop 界面 + 应用状态（AppState）
```

- `:core` **不得** import 任何 `androidx.compose.*` / `org.jetbrains.compose.*`。
  违反会被 `gradlew check` 的 `verifyCoreBoundary` 任务拦下，报错直接给「文件:行 + 违规 import」。
- 为什么这么严：Compose 的 state / collectAsState 一旦长进逻辑层，逻辑就再也拆不出来了
  （桌面端和 Android 端要共用同一份算法，逻辑层不能绑死某个 UI 框架）。
- UI 层不做 IO、不承载算法：会话记录读写收进 `:core` 的 `ChatStore`，导入导出的 JSON
  解析在 `Store.parseImport / exportJson`。

```powershell
.\gradlew.bat check          # 含边界检查 + 单测
.\gradlew.bat :app:fatJar    # 只打可双击的 jar
```

## 目录结构

```
desktop/
├─ core/src/main/kotlin/com/pocket/xiaoli/desktop/   # 纯逻辑（无 Compose）
│  ├─ ai/                      # LlmConfig / LlmClient(SSE) / PocketTools(函数定义+执行)
│  ├─ data/                    # 模型、Store(JSON 持久化)、ChatStore、ChatTypes、默认节次、校园配置
│  └─ logic/                   # 课表算法（ScheduleLogic）、课程简称、知识库检索
├─ app/src/main/kotlin/com/pocket/xiaoli/desktop/    # Compose 界面
│  ├─ Main.kt                  # 窗口入口（1240×820，最小 1000×660）
│  ├─ AppState.kt              # 界面状态（当前页、弹窗、对话草稿、流式开关）
│  └─ ui/
│     ├─ App.kt                # 侧边栏外壳、快捷键、弹窗宿主、提示气泡
│     ├─ components/           # 卡片/按钮/输入框/弹窗/Markdown 渲染/鲤鱼吉祥物
│     ├─ dialogs/              # 学期、学生信息、AI 配置、待办、节次、导入导出、关于
│     ├─ icons/                # 自绘矢量图标（不依赖 material-icons）
│     ├─ screens/              # Home / Timetable / Assistant / Campus(服务) / Profile / Onboarding
│     ├─ theme/                # 色板、排版、圆角与间距、课程配色
│     └─ util/                 # 平台能力（打开链接/文件对话框/剪贴板）与格式化
├─ core/src/main/resources/    # 知识库/校历/快问快答/校园地图（从 ../app/src/main/assets 复制进包）
├─ installer/app/              # 自写安装器（.NET WinForms 单文件 EXE：解压 payload、快捷方式、卸载项）
├─ tools/                      # package.ps1 打包；installer.ps1 出安装包；qa.ps1 截图验收；capture/click/focus 辅助
└─ docs/screenshots/           # 验收截图
```

## 验收工具（写给自己看的）

桌面应用的 UI 验收没法靠单测，所以 `tools/` 里有一套基于 Win32 的小工具：

- `tools/qa.ps1`：一步完成「聚焦窗口 → 可选点击/滚轮 → PrintWindow 截图」。必须在一个进程里做完，分成几次调用会因为新起的控制台窗口抢焦点而点空。
- `tools/mock-jwxt.py`：本地 Mock 正方教务（登录页 → 公钥 → 验证码 → 登录 → 课表 / 成绩），配合
  `--demo=login|grades --login-url=http://127.0.0.1:18080/jwglxt --login-user=… --login-pass=… --login-captcha=… --login-auto`
  做端到端验收；真实学校环境仍需单独验。
- `tools/capture-window.ps1`：只截图（DPI 感知 + `PW_RENDERFULLCONTENT`，窗口被遮挡也能截）。
- `tools/click.ps1`、`tools/focus-window.ps1`、`tools/fullscreen-shot.ps1`：拆分动作/排查点击落点用。

注意两件事，都踩过：

- 滚轮不能走 `mouse_event`，Compose Desktop 收不到；要 `PostMessage(hwnd, WM_MOUSEWHEEL)`，而且 **lParam 用屏幕坐标**，`qa.ps1` 里已经这么改。
- `PrintWindow` 截到的是合成后的帧，偶尔会缺一块（曾把「弹窗被裁掉」误判成布局 bug）。判断布局问题前先用 `fullscreen-shot.ps1` 截真屏复核一次。
- 合成点击前要先晃一下鼠标（窗口坐标别用客户区坐标推算），否则命中测试可能落在旧位置。

`qa.ps1` 的参数：`-ClickX/-ClickY` 点击、`-ScrollY` 滚轮（负=向下）、`-Paste` 先把文本写剪贴板再 Ctrl+V（用来模拟输入法「上屏」后的插入结果）、`-OutPath` 截图路径。

- `tools/measure-coldstart.ps1`：量冷启动（默认 3 次取中位数），阈值 2 s。

  ```powershell
  pwsh tools\measure-coldstart.ps1 -Exe dist\PocketXiaoli\PocketXiaoli.exe -Runs 3
  # 本机实测：中位数 1.52 s（min 1.45 / max 1.53）
  ```

  两个坑：① jpackage 的 exe 会再起一个同名子进程，**窗口在子进程上**，按启动器 pid 找窗口永远找不到；
  ② `Process.MainWindowHandle` 轮询不可靠（一直返回 0），要按「进程名 + 有标题的窗口」查。
  1.52 s 已经低于 2 s 阈值，所以没有引入 AppCDS —— 真到了 >2 s 再上（先实测，别直接跳 GraalVM）。
- `../tools/verify-shared.ps1` 里钉了一步**单实例**：起 A 等窗口 → 起 B 必须在 15 s 内以退出码 0 自己退 →
  带窗口的实例仍是 1 个，而且就是 A。它防的是「守卫被改坏之后两个进程互相盖数据」，
  判定按窗口标题而不是启动器 PID（同上面 ① 那个坑）。

开发用启动参数（不影响普通用户）：

| 参数 | 作用 |
|---|---|
| `--tab=0..4` | 直接进某一页（0 首页 / 1 课表 / 2 鲤鱼 / 3 服务 / 4 我的） |
| `--demo=markdown` | 在鲤鱼里塞一篇长文样例：纯中文长段 / 中英混排 / 列表 / 表格 / 围栏代码块 |
| `--demo=api` / `term` / `student` / `import` / `about` / `onboarding` / `login` / `grades` | 直接打开对应弹窗/页面，用于截图验收 |
| `--demo=exam` | 跳到服务 → 考试，并把一段示例期末通知填进粘贴框（只改内存，点「添加」才落盘） |
| `--search=关键词` | 打开全局搜索并填入关键词 |
| `--ask=问题` | 切到鲤鱼并直接发送这一句（验收没配 Key 时的离线回答） |
| `--login-url=… --login-user=… --login-pass=… --login-captcha=… --login-auto` | 连本地 Mock 教务跑端到端登录截图 |
| `--reminder-at=2026-11-02T07:52` | 把「现在」钉在指定时刻，截图验收上课/待办/考试提醒（固定时钟时会往 stdout 打 `exam-reminder:` 一行） |

## 已知限制

- 教务登录/抓取只支持正方（V8）。**真实校园环境已跑通登录 + 课表**（2026-11-02，烟理正方账号导入 14 门课，
  日志里能看到接口试探 5 次后命中 `xskbcx_cxXsgrkb` 的列表形态）；**成绩抓取仍只有 Mock 服务端覆盖**
  （`ZhengfangGradeMockTest`），真实环境的成绩字段与学期周次还没人核对过。
- **考试安排没有教务接口可查**（正方只提供课表/成绩，手机端 `data/exam` 早就写明这点），所以这一页是通知解析：格式歪的通知可能认错列或漏认，因此刻意要求「先核对再添加」；解析不出日期的行不当考试，缺年份的「1月15日」按半年阈值补今年/明年。
- AI 流式回答需要真实 Key；离线时只有本地知识库/工具链。
- **天气是城市级（烟台）不是校区实测**：`CampusConfig` 的校区没有经纬度，而地图数据写着「不要用猜测值填坐标」，所以界面如实标「烟台」。拿到校区坐标后只改 `WEATHER_LATITUDE/LONGITUDE` 两个常量。桌面端笔记的编辑弹窗只做到「列表 + 落盘」的自动验收，弹窗内输入没做 UI 级验收（合成点击会先切标签页，容易误点外链）。
- 多窗口还没做，而且现在被单实例守卫明确挡住了：第二次启动只会唤起已有窗口（对照两个学期请先关掉前一个）。开机自启已做但**默认关**（要用户自己在「我的 → 数据」打开才写注册表）。
- **MSI 安装包没做**：`jpackage --type msi/exe` 依赖 WiX（本机没有，装它要管理员 + NetFx3），继续用自写 .NET 单文件安装器（效果等价：装到当前用户目录、建快捷方式、写卸载项）。绿色包 zip 的条目是**没有顶层目录**的（`PocketXiaoli.exe` / `app` / `runtime` 直接在根上），因为安装器按这个约定解压；自己解压绿色包时请先建一个空目录再解。
- 中文输入法只做了「粘贴上屏」级别的验证（输入框能正确插入中文、不串行）；**组合态**（候选框、连打过程中光标跳动）需要真人用 IME 再验一遍，重点看「AI 配置」「新建课程」「待办」三个弹窗里的输入框。
- Markdown 支持标题 / 列表 / 引用 / 粗体斜体 / 行内代码 / 链接 / 表格 / 围栏代码块；中文避头尾用 WORD JOINER（U+2060）在渲染层插零宽粘合符，不写进数据。复杂嵌套（列表里的表格、四层以上嵌套）没做。
- 打包产物没有代码签名，Windows SmartScreen 首次运行可能提示（绿色包和安装包都一样）。
