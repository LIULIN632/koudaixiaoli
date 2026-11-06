# 阶段 0：只读审计报告（2026-10-27）

> 只读阶段：本报告只做证据记录与分类，**没有修改任何源码、没有升级任何依赖、没有跑破坏性命令**。
> 全部数据来自 `git status`、模块构建文件、`app/src` 与 `desktop/src` 的静态扫描。

## 0. 三句话结论

1. **计划的前提与仓库现状有偏差**：仓库里**已经存在** `desktop/`（嵌套 Gradle 构建，`:core` + `:app`，已跑通、已出 100 MB 绿色包与 117 MB 安装器），但它是**第二份实现** —— 与硬约束「核心逻辑只有一个权威实现」冲突，必须先决定去留。
2. **共享核心是可行的**：Android 侧有 **179 个文件 / 25,328 行** 完全不 import `android.*` / `androidx.*` 的纯 Kotlin，助手链路（agent）、知识库（knowledge）、课表算法（util）几乎都在其中 → 迁移面比预想小。
3. **版本组合不能照抄**：Android 是 AGP 9.4.1 / Kotlin 2.4.20 / Gradle 9.6.1 / Compose BOM 2026.08.00；现有桌面端用的是 Compose Multiplatform 1.12.1（另一套 Gradle 构建）。KMP 与 AGP 9.4.1 的组合**必须先用最小试验验证**，不能凭感觉升级或搬版本号。

---

## 1. 仓库与构建事实

| 项 | 实测值 | 证据 |
|---|---|---|
| 分支 / 工作区 | `main`，3 个已跟踪改动 | `git status --short` |
| Android 模块 | 只有 `:app` | `settings.gradle.kts` → `include(":app")` |
| AGP | 9.4.1 | 根 `build.gradle.kts:2` |
| Kotlin | 2.4.20（compose 插件同版） | 根 `build.gradle.kts:3-4` |
| KSP | 2.3.9 | 根 `build.gradle.kts:5` |
| Gradle Wrapper | 9.6.1 | `gradle/wrapper/gradle-wrapper.properties` |
| 版本号真源 | `versionCode=96` / `versionName=0.1.95` | `gradle.properties` |
| Compose | BOM `2026.08.00`，material3 实际解析 1.4.0（MIUIX 顶上去的） | `app/build.gradle.kts:351`、注释 `:226-339` |
| Room | 2.8.3（2.6.1 与 Kotlin 2.4 字节码不兼容） | `app/build.gradle.kts:368-372` |
| MIUIX | `top.yukonga.miuix.kmp:*:0.9.3`（**本身是 KMP 库**） | `app/build.gradle.kts:384-391` |
| 许可证 | GPL-3.0 | `LICENSE`（GNU GPL v3, 29 June 2007） |
| 桌面端 | 独立构建 `desktop/`：`:core` + `:app`，Kotlin 2.4.20 + CMP 1.12.1 | `desktop/settings.gradle.kts`、`desktop/build.gradle.kts:3-5` |

⚠️ 关键结构事实：`desktop/` 是**嵌套构建**（自己一份 `settings.gradle.kts`），根 `settings.gradle.kts` 并不 include 它。两个构建现在靠「复制代码」而不是「共享模块」发生关系。

## 2. 规模与测试基线

| 范围 | 文件 | 行数 |
|---|---|---|
| `app/src/main/java/.../data` | 177 | 22,145 |
| `app/src/main/java/.../ui` | 249 | 77,379 |
| `app/src/main/java/.../network` | 22 | 7,095 |
| `app/src/main/java/.../util` | 31 | 2,445 |
| 单测 `app/src/test` | 295 | 40,086 |
| Compose 截图基线 | 265 张 PNG | `app/src/test/screenshots/` |
| `desktop/core` | 14 | 1,646 |
| `desktop/app` | 18 | 5,707 |

> 文档口径 `CLAUDE.md` 写「2126 条单测 / 236 张基准图」；实测基准图 **265 张**，测试条数需以 `gradlew :app:testDebugUnitTest` 的报告为准（本次审计未跑，避免污染构建缓存）。

## 3. 代码四分类（真实路径）

### A. 可直接共享的纯 Kotlin（零 `android.*` / `androidx.*` import）

全仓共 **179 文件 / 25,328 行**。与桌面端相关的核心子集：

| 模块 | 文件（行数） |
|---|---|
| 助手 agent | `data/agent/AssistantAgentLoop.kt`(322)、`AssistantLlmClient.kt`(306)、`AssistantToolRegistry.kt`(242)、`AssistantChatRepository.kt`(162)、`AssistantSseParser.kt`(124)、`AssistantContextBuilder.kt`(56)、`AssistantIdentity.kt`(29)、`AssistantStreamSource.kt`(17) |
| 知识库 | `data/knowledge/AssistantFormatters.kt`(355)、`AssistantTools.kt`(191)、`LocalFaqKnowledgeSource.kt`(128)、`KnowledgeStore.kt`(113，2 处 Android import)、`KnowledgeUpdater.kt`(114，1 处)、`AssistantCard.kt`(106)、`KeywordRetriever.kt`、`KnowledgeModels/JsonMapper/Json/Version`、`AssistantTool.kt` |
| 课表算法 | `util/CourseWeek.kt`(21)、`util/UpcomingCourse.kt`(`findUpcomingCourse` 在 `:44`)、`data/entity/TimeSlotDefaults.kt`(58)、`util/CourseShortName.kt`(`GENERIC_TAILS` 在 `:113`) |
| 校园服务 | `data/CampusConfig.kt`(144)、`data/service/CampusServiceRegistry.kt`(231)、`ui/treasurebox/ServiceEntry.kt`(173，2 处 Android import) |
| 教务抓取 | `network/ZhengfangAcademicApi.kt`(1476)、`ZhengfangClient.kt`(893)、`ZhengfangHtmlParser.kt`(545)、`ZhengfangScheduleParser.kt`(321)、`HtmlTableParser.kt`(208) |

### B. 小重构后可共享

- `data/knowledge/KnowledgeStore.kt`、`KnowledgeUpdater.kt`、`ui/treasurebox/ServiceEntry.kt`、`data/agent/AssistantLlmConfig.kt`：各只有 1–2 个 `android.*` import（多半是 `Context` / `Uri`），抽成接口注入即可。
- `AppContainer`（DI，1,428 行）与 `HomeViewModel`(390)、`TimetableViewModel`(235)：逻辑正确但直接吃 Room DAO / Repository，需要「接口下沉」才能跨端。

### C. Android 专属（不可共享，需接口隔离）

- Room：17 个 `@Entity`、`data/database`(5 文件/734 行)、`data/dao`(9 文件/454 行)。
- `MainActivity`、`Context.getSharedPreferences`（至少 8 处调用点）、通知、应用统计、WebView、AppWidget、`network/*` 里带 OkHttp Android 依赖的封装。
- 平台能力：文件选择、剪贴板、凭据、托盘、开机自启 —— Android 现有一套，桌面端要另一套（同一接口两个实现）。

### D. 独立 UI 与平台适配

- Android：`ui/`(249 文件/77,379 行，MIUIX + AndroidX Compose)。
- Desktop：`desktop/app`(18 文件/5,707 行，CMP + Material3)。两端 UI **不共享是正确的**，不需要为了"统一"改造。

## 4. 唯一权威实现：现在有几份？

| 能力 | Android 唯一真源（证据） | `desktop/core` 现有对应物 | 现状 |
|---|---|---|---|
| 课表周次/时间片/下一节课 | `util/CourseWeek.kt`、`util/UpcomingCourse.kt`、`data/entity/TimeSlotDefaults.kt` | `logic/ScheduleLogic.kt`(230) | **两份实现** |
| 课名简称 | `util/CourseShortName.kt` | `logic/CourseNames.kt`(95) | **两份实现** |
| 知识库检索 | `data/knowledge/KeywordRetriever.kt` + `KnowledgeModels` | `logic/Knowledge.kt`(124) | **两份实现** |
| LLM 流式客户端 | `data/agent/AssistantLlmClient.kt` + `AssistantSseParser.kt` | `ai/LlmClient.kt`(238) | **两份实现** |
| 助手工具调度 | `data/knowledge/AssistantTools.kt` + `AssistantToolRegistry.kt` | `ai/PocketTools.kt`(233) | **两份实现** |
| 数据模型/持久化 | Room 17 实体 + SharedPreferences JSON | `data/Models.kt`(104)、`data/Store.kt`(114) | 结构等价、实现不同 |
| 校园服务目录 | `data/CampusConfig.kt` + `data/service/CampusServiceRegistry.kt` | `data/CampusInfo.kt`(116) | **两份实现** |

**结论**：`desktop/core` 约 1,300 行是「翻译过来的第二份实现」。这正是硬约束 #2/#6 要拦的东西 —— 而它已经存在，且已经被打包发布了。

## 5. 现有桌面资产怎么处置（供决策）

可直接保留（与逻辑无关）：

- `desktop/app` 的 UI 10 屏 + 组件 + 主题令牌（5,707 行）—— 这是「独立优秀的桌面 UI」，计划里本来就要。
- 工具链：`tools/`（打包、安装器、截图 QA、冷启动测量）；`installer/`（.NET 单文件安装器，117 MB）；`README.md`、`docs/screenshots/`。
- 已验证的产物数据：包体 100 MB（runtime 47.6 + app 51.9）、冷启动 1.52 s、`--selftest=net` 的 TLS 结论。

需要替换（而不是继续维护）：

- `desktop/core` 里的 7 个「第二份实现」文件 → 换成语义等价的**共享模块**，`desktop` 只保留平台适配（文件、凭据、托盘、通知、窗口）。

## 6. KMP 可行性与风险（阶段 1 要验证的东西）

- Kotlin 2.4.20 本身支持 `kotlin("multiplatform")`；**未知项是 AGP 9.4.1**：AGP 9 起内置 Kotlin 支持、移除了 `kotlin-android` 插件（见 `app/build.gradle.kts:6-9` 注释），KMP 的 `androidTarget` 在 AGP 9 下走 `com.android.kotlin.multiplatform.library` 新插件，行为必须先实测。
- 共享模块**不能**依赖 Compose / Android Context / Room；现有 `desktop/build.gradle.kts` 已有一个 `verifyCoreBoundary` 正则任务，可升级成正式守门（含依赖层面检查）。
- 构建拓扑三选一（需拍板）：
  1. **单构建**：根 `settings.gradle.kts` 加 `:core`（KMP）+ `:desktop`，Android 与桌面都从项目内引用 —— 最干净，但动根构建，风险最高；
  2. **嵌套 + 复合构建**：保留 `desktop/` 独立构建，用 `includeBuild("../")` 反向引用根构建的 `:core` —— 改动小，Gradle 语义稍绕；
  3. **发布物**：`:core` 发到 mavenLocal，桌面端按坐标依赖 —— 最隔离，但每次改动要发版，迭代慢。
- 迁移顺序建议仍按计划：`CourseWeek / TimeSlotDefaults / CourseShortName`（最小、纯函数、有单测）→ 知识库 → agent → 服务目录。

## 7. 风险清单

| 风险 | 影响 | 缓解 |
|---|---|---|
| `desktop/core` 已发布过（第二份实现） | 约束冲突、行为漂移（例如课周规则改动后两端不一致） | 阶段 1 先迁 3 个纯函数，替换后跑双端测试；在 README 标注「desktop/core 为过渡实现」 |
| AGP 9.4.1 + KMP `androidTarget` 未知 | 阶段 1 可能直接卡住 | 先在独立分支做 30 分钟最小试验（空模块 + 1 个纯函数 + 两端调用），失败就走方案 3 |
| 大而全的 `data/` 与 Room 耦合 | 表面可共享，实际编译不过（Room 注解、Context） | 只搬零 Android import 的文件；Room 留在 Android，桌面走 JSON 实现同一接口 |
| 文档口径与实测不一致（236 vs 265 张基线） | 验收基线漂移 | 阶段 1 先跑一次 `testDebugUnitTest` 固化数字，再改文档 |
| GPL-3.0 传染性 | 新增依赖/素材许可证要登记 | 阶段 1 起在 `desktop/THIRD-PARTY.md` 记录（CMP Apache-2.0、Material3 Apache-2.0、okhttp Apache-2.0、gson Apache-2.0、jsoup MIT、.NET 安装器 MIT） |

## 8. 阶段 0 未做（按约束禁止）

- 没建/删模块、没改 `settings.gradle.kts`、没动 `app/` 与 `desktop/` 源码、没升级任何版本。
- 没跑 `gradlew`（避免污染构建缓存与截图基线）；测试基线数字以阶段 1 首跑为准。

---

## 需要拍板的三件事（拍板前我不动源码）

1. **`desktop/` 现有实现怎么处置？**
   - A. **保留 UI/打包，替换 `desktop/core` 为共享模块**（推荐：保住已验收的界面与安装器，只重做逻辑层，工作量约 1–2 天）
   - B. 冻结现有 `desktop/`，另起 `:core` 重新接一遍（更干净，但要重接 5 个页面，约 3–5 天）
   - C. 明确「桌面端作为独立实现」并修改约束文档（不推荐：违背单一实现原则，长期漂移）
2. **构建拓扑选哪种？** 单构建 / 嵌套 + `includeBuild` / mavenLocal 发布物（我倾向先「嵌套 + includeBuild」，改动最小且可回滚）。
3. **阶段 1 试点选谁？** 建议 `CourseWeek + TimeSlotDefaults + CourseShortName`（纯函数、Android 有现成单测、桌面端也有对照实现，能立刻验证两端一致）。
