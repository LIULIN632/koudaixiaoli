# 阶段 1 报告：共享核心模块 `:core` 与最小接线

> 结论：**技术路线可用（AGP 9.4.1 + Kotlin 2.4.20 的 KMP 模块能同时被 Android 与桌面构建使用）**。
> 本轮只迁了 3 个纯函数，用来证明"能共享、能被两端真正调用、不改变行为"。

## 1. 建了什么

```
core/                                    ← 共享模块（Android 根构建里的一个 project，KMP 插件）
├── src/commonMain/kotlin/com/pocket/xiaoli/core/
│   ├── CourseWeekRules.kt               课表周次规则（唯一真源）
│   ├── TimeSlotDefaults.kt              默认 12 节次 + v20 旧表（只服务迁移判据）
│   └── CourseShortName.kt               课名简称规则与内置表
└── src/commonTest/kotlin/com/pocket/xiaoli/core/
    ├── CourseWeekRulesTest.kt
    ├── TimeSlotDefaultsTest.kt
    └── CourseShortNameTest.kt
```

- 构建插件：`com.android.kotlin.multiplatform.library`（AGP 9 的 KMP 库插件）+ `kotlin("multiplatform")`
  + `jvm()` target（桌面端与 JVM 测试用得到）。
- `group = "com.pocket.xiaoli"`，`version = "1.0"`；根 `settings.gradle.kts` 加 `include(":core")`。
- **桌面端反向引用**：`desktop/settings.gradle.kts` 加 `includeBuild("..")`，
  `desktop/core` 用 `implementation("com.pocket.xiaoli:core:1.0")` 消费 —— 不发布、不走 mavenLocal。

## 2. 两端怎么用（都是"内部替换"，不动对外行为）

| 端 | 文件 | 改法 |
|---|---|---|
| Android | `app/.../util/CourseWeek.kt:3` | `import com.pocket.xiaoli.core.isCourseInWeek as coreIsCourseInWeek`，原函数体删掉，改为委托 |
| Android | `app/.../data/entity/TimeSlotDefaults.kt:3-5` | 默认表与旧表都来自 `core.TimeSlotSpec`，本文件只剩 `toEntity()` 适配 |
| Android | `app/.../util/CourseShortName.kt:3` | `import ... as CoreShortName`，规则与内置表全部委托 |
| Desktop | `desktop/core/.../logic/ScheduleLogic.kt` | `parseWeeks / courseWeeks` 委托 `core.parseWeekSpec / weekSet`（时间段换算等**桌面约定**如"周一开始"留在平台层，并注明理由） |
| Desktop | `desktop/core/.../logic/CourseNames.kt:16,32` | `MAX_CHARS` 与 `suggest()` 委托 `core.CourseShortName`；**`displayName()` 留在桌面**（显示策略） |
| Desktop | `desktop/core/.../data/Defaults.kt:6-15` | `DefaultTimeSlots` 由 `core.DefaultTimeSlots.map { TimeSlot(...) }` 生成，桌面模型自己持有 `label` |

**边界遵守情况**：
- `commonMain` 零 `android.*` / `androidx.*` / `org.jetbrains.compose.*` import；Android 适配只出现在 `androidMain`（`TimeSlotSpec.toEntity()`）。
- 桌面端没有把 Android 类型引进共享层：共享层只有 `TimeSlotSpec`、`List<Int>` 这类纯数据。
- 桌面端 `ScheduleLogic` 里"周末/周一起始/时间片分钟数"这类**平台约定**没被硬塞进 core，避免为了"共享"改变 Android 行为。

## 3. 验证证据

| 检查 | 命令 | 结果 |
|---|---|---|
| 共享模块测试 | `gradlew :core:jvmTest` | **BUILD SUCCESSFUL**；产物 `TEST-com.pocket.xiaoli.core.{CourseWeekRulesTest,TimeSlotDefaultsTest,CourseShortNameTest}.xml` |
| Android 编译 | `gradlew :app:compileDebugKotlin` | **exit 0** |
| Android 相关单测 | `gradlew :app:testDebugUnitTest --tests "com.pocket.xiaoli.util.*"` | 20 个 XML 结果文件，**无 failures/errors** |
| 桌面编译 | `gradlew :app:compileKotlin`（workdir `desktop`） | 通过 |
| 桌面运行 | `java -jar app/build/libs/pocket-xiaoli-desktop.jar --tab=1` | 课表网格、单双周"非本周"标记、12 节次时间全部与改造前一致（`desktop/shots/phase1_timetable.png`） |

## 4. 与计划的两处偏差（需要你确认是否接受）

1. **`:core` 建在 Android 根构建里**（不是独立仓库/独立构建）。理由：`includeBuild` 能双向引用，改动最小，回滚只需删两行。代价：`desktop` 的构建依赖根构建能配置成功（已验证）。
2. **`CourseShortName.displayName()` 与 `ScheduleLogic` 的周起始/时间段换算留在桌面层**。前者是"格子放不下时怎么显示"的 UI 策略，后者是"一周从周几开始"的平台约定 —— 把它们塞进 core 会把 Android 也一起改掉，违反"不改变现有 Android 行为"。

## 5. 遗留问题（阶段 2 之前要处理）

1. **架构守门还没搬进 Android 仓库**：`desktop` 有 `verifyCoreBoundary`（正则扫 import），Android 侧没有 Konsist/守门测试。计划里的架构测试应建在 `:core`（检查 commonMain 依赖）或根构建，并进 CI。
2. **依赖层面未做守门**：只查了源码 import，没查 `core` 的 `implementation` 里会不会被塞进 android 依赖。建议阶段 2 用 `gradle dependencies` 输出做一次快照断言。
3. **`:core` 的 android 目标还没被 Android 单测跑到**（`jvmTest` 跑的是 JVM 目标）。两端用同一份 common 代码，风险低，但严格起来应加 `:core` 的 android host test 任务。
4. **桌面端仍是两份数据模型**（`TimeSlot` vs `TimeSlotSpec`），本轮只统一了"默认值"；模型统一要等阶段 2/3 判断是否值得。

## 6. 下一步（阶段 2 建议）

按计划迁**知识库 + 助手逻辑**，但先做一件事：把架构守门建起来（`commonMain` 禁 android/compose、core 依赖白名单），
否则迁移越多，越容易悄悄把平台类型带进共享层。

迁移顺序建议：`KeywordRetriever`/`KnowledgeModels`（纯）→ `AssistantSseParser`/`AssistantLlmClient`（需注入 HTTP 接口）→ `AssistantTools`（依赖上层模型，放最后）。

**按计划在此停下，等你确认后再进阶段 2。**
## 7. 补记（阶段 2 期间修正）

- 阶段 1 报告说「架构守门还没搬进 Android 仓库」**不准确**：core/build.gradle.kts 当时已有 erifyCoreNoUi（扫 forbidden import、挂 check）。阶段 2 把它升级为 erifyCoreScope（commonMain 禁止 android/androidx/compose/swing/awt + 依赖边界 + 负向验证），并给 ndroidLibrary 加了 withHostTest {}，commonTest 现在 jvm 与 android 两个目标都跑。
