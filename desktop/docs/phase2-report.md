# 阶段 2（进行中）：知识库检索并入共享核心 + 边界守门加固

> 本轮只做了「能自证」的最小一步：把 **知识库检索算法** 变成两端唯一实现，并把守门从"只扫 import"
> 升级成"源码 + 依赖两层"。助手 LLM/工具调度尚未开始，等确认后继续。

## 1. 共享层新增（`core/src/commonMain/kotlin/com/pocket/xiaoli/core/knowledge/`）

| 文件 | 内容 |
|---|---|
| `KeywordRetrieverCore.kt` | 分块 `chunkTexts`、打分 `search`、时效 `recencyFactor`、`cosineSimilarity`/`ngramVector` |
| `KnowledgeDoc` | 检索输入（id / title / body / tags / updatedAt），**不含平台日期类型** |
| `KnowledgeSnippet` | 命中结果（documentId / chunkId / order / text / score / rankScore），调用方按 chunkId 映射回本端对象 |

关键设计决定（写进注释了）：

- **算法共享，模型各自持有**：Android 模型带 `java.time.LocalDate`，桌面模型是另一套类；把 `LocalDate`
  搬进 `commonMain` 会逼桌面端依赖 java.time（或引入 kotlinx-datetime 新依赖）。所以共享层只吃压平后的
  `KnowledgeDoc`，两端各写 10 行适配。
- **共享层不读系统时钟**：`search(..., nowMillis)` 由调用方注入，便于单测与跨端复现。

## 2. 两端接线

| 端 | 文件 | 改法 |
|---|---|---|
| Android | `app/.../data/knowledge/KeywordRetriever.kt`（重写为适配器） | 映射 `KnowledgeDocument → KnowledgeDoc` → 调 `KeywordRetrieverCore.search` → 按 `chunkId` 映射回 `KnowledgeHit`；对调用方 API（`search/buildChunks/recencyFactor/cosineSimilarity/ngramVector/常量`）**保持不变** |
| Desktop | `desktop/core/.../logic/Knowledge.kt` | `search()` 改为委托共享核心；`snippetOf/formatForModel/catalog/byId` 留在桌面（展示与提示词策略）。顺带删掉了它自己那份「标题 +160 / 正文 +120 / 标签 +60 / 指数时效」的**第三套打分** |

行为差异（已知且有意）：桌面端排序分数从"自研加权"改成"与 Android 同一口径"，因此**排序可能有细微变化**，
但两端从此给出同一顺序 —— 这正是硬约束要的。

## 3. 守门加固（`core/build.gradle.kts` 的 `verifyCoreScope`）

- commonMain 禁止：`import android.` / `androidx.` / `org.jetbrains.compose` / `javax.swing` / `java.awt`。
- 任何源集禁止：`androidx.compose` / `org.jetbrains.compose`（UI 必须留在各自端）。
- 依赖边界：`commonMain.dependencies {}` 里出现 `implementation(` / `api(` 即失败（防悄悄引入 okhttp 之类）。
- 已接入 `check`；**负向验证通过**：临时插入 `import androidx.compose.runtime.Composable` → 任务 exit=1；
  恢复后 exit=0。

## 4. 验证证据

| 检查 | 命令 | 结果 |
|---|---|---|
| 共享层单测（JVM） | `gradlew :core:jvmTest` | BUILD SUCCESSFUL（新增 `KeywordRetrieverCoreTest` 6 例） |
| 共享层单测（Android host） | `gradlew :core:testAndroidHostTest` | BUILD SUCCESSFUL（本轮给 `androidLibrary` 加了 `withHostTest {}`，commonTest 两端都跑） |
| Android 知识库行为回归 | `gradlew :app:testDebugUnitTest --tests "com.pocket.xiaoli.data.knowledge.*"` | BUILD SUCCESSFUL |
| Android 编译 | `gradlew :app:compileDebugKotlin` | exit 0 |
| 桌面编译 + 打包 | `gradlew :core:test` / `:app:fatJar`（workdir `desktop`） | BUILD SUCCESSFUL；jar 39,983,888 B |
| 桌面运行 | `--tab=3` 打开「服务 → 知识库」 | 22 篇文档、分类（办事流程/场馆信息/规章制度）与搜索框正常（`desktop/shots/phase2_knowledge.png`） |

## 5. 遗留（下一步）

1. **助手 LLM/工具调度**尚未迁移：`AssistantLlmClient`（OkHttp SSE）依赖 HTTP 栈，共享层需要先定义
   `HttpStream` 接口再搬 SSE 解析；这条是阶段 2 的主体，工作量比知识库大。
2. `AssistantFormatters`（355 行）与 `AssistantTools`（191 行）依赖知识库/课表模型，建议排在模型统一之后。
3. 桌面 `Knowledge.Hit.score` 语义已变（rankScore），若将来展示"匹配度"需要统一口径。

**在此停下，等确认后继续搬助手链路。**
---

## 6. 追加：校区与校园服务表并入共享核心（本轮）

| 项 | 内容 |
|---|---|
| 共享层新增 | `core/src/commonMain/kotlin/com/pocket/xiaoli/core/campus/CampusConfig.kt`：`CampusInfo`（含 `schoolId/jwType/jwBaseUrl/portalUrl/services/serviceUrls`）、`CampusServiceKeys`（22 个键）、`CampusConfig`（3 个校区 + `defaultCampus` + `byId`）、`jwTypeLabelOf()` |
| Android | `data/CampusConfig.kt` 缩成 4 行 `typealias` + `jwTypeLabel` 委托；调用方零改动 |
| Desktop | `desktop/core/.../data/CampusInfo.kt` 同样缩成 `typealias`；桌面端从「自己维护的 123 行副本」变成读同一张表，从此多一个入口地址两边一起有 |
| 未搬 | `CampusServiceRegistry`（228 行）依赖 `CampusServiceSpec`/`CampusServiceType`/`CampusServiceResolver`，这几个仍在 Android；下一步一起搬（否则会出现半截数据） |

### 踩坑：跨模块 `typealias` 需要 `api()` 可见性

`desktop/core` 原先用 `implementation("com.pocket.xiaoli:core:1.0")` 依赖共享核心。这种写法下**自定义 `typealias` 对下游 `:app` 不可见**（`desktop/app` 报 `Unresolved reference 'CampusConfig'`），因为别名指向的类型没有暴露到 API 面。改成 `api("com.pocket.xiaoli:core:1.0")` 后立刻通过。

> 同理，只含 `typealias` 的文件不产生元数据载体，所以桌面端别名校验文件里保留了一个真实的扩展属性（`jwTypeLabel`），与 Android 侧保持一致。

### 验证

| 检查 | 结果 |
|---|---|
| `gradlew :core:jvmTest :core:testAndroidHostTest`（Android 根） | BUILD SUCCESSFUL |
| `gradlew :app:testDebugUnitTest --tests "com.pocket.xiaoli.data.*"` | 114 个测试类，`failures="[1-9]"` 无命中 |
| `gradlew :app:compileDebugKotlin`（Android） | exit 0 |
| `gradlew :core:compileKotlin` / `:app:compileKotlin` / `:app:fatJar`（desktop） | SUCCESS 或 exit 0；jar 39,986,730 B |
| 桌面运行 `--tab=3` | 服务入口/校历/知识库/快问快答四段正常，「学习（教务/图书/第二课堂）」与「生活（智慧校园/一卡通/宿舍电费）」分组来自共享表（`desktop/shots/phase2_campus_shared.png`） |

### 下一步（按依赖顺序）

1. 搬 `CampusServiceSpec` + `CampusServiceCatalog` + `CampusServiceResolver` → 共享层（纯 Kotlin，无 android import），然后 `CampusServiceRegistry` 才能跟着搬。
2. 助手链路仍卡在 JSON/日期：`AssistantSseParser` 用 Gson（JVM-only），要进 `commonMain` 需换 kotlinx.serialization（新依赖，按约束 ⑦ 需先做最小验证再申请）。**需要你拍板：允许新增 `kotlinx-serialization-json` 吗？**
---

## 7. 追加：服务登记表（spec / registry / channel）并入共享核心（本轮）

| 共享层新增 | 内容 |
|---|---|
| `core/.../campus/CampusServiceSpec.kt` | `CampusServiceType` 枚举、`CampusServiceSpec`、`ResolvedCampusService` |
| `core/.../campus/CampusServiceRegistry.kt` | 229 行登记表：公共 spec（今日校园/乘车码）+ 各校区 spec（一卡通/洗衣/校园跑支付宝等） |
| `core/.../campus/SchoolService.kt` | `SchoolService` 与 `ServiceChannel`（APP / WEBVIEW / WECHAT_MP / ALIPAY） |

Android 侧：`CampusServiceSpec.kt`、`CampusServiceRegistry.kt`、`SchoolService.kt` 三个文件各自缩成 `typealias`（含 `ResolvedCampusService`、`ServiceChannel` 两个转发别名），调用方零改动。

### 踩的两个坑

1. **`internal` 跨模块不可见**：`CampusServiceRegistry.schoolRunSpec` 原为 `internal`，搬到共享层后 Android **单元测试**编译失败（`Cannot access ... it is internal in 'com.pocket.xiaoli.core.campus.CampusServiceRegistry'`）。改成 `public` 后通过 —— 共享层被下游测试使用的辅助函数必须是 public。
2. **别名要成套**：`ResolvedCampusService`、`ServiceChannel` 是在被移动文件里声明的伴随类型，移动后 Android 端必须同时补别名，否则 `CampusServiceResolver`/`CampusServiceAdapter` 等文件整片报 `Unresolved reference`。

### 验证

| 检查 | 结果 |
|---|---|
| `gradlew :core:jvmTest :core:testAndroidHostTest` | exit 0 |
| `gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `gradlew :app:testDebugUnitTest --tests "com.pocket.xiaoli.data.*"` | 114 个测试类，无 failures/errors（含 `SchoolRunSpecTest` 直接测共享层 `schoolRunSpec`） |
| desktop `:app:compileKotlin` | exit 0 |

### 尚未搬（有前置依赖）

`CampusServiceCatalog` / `CampusServiceResolver` / `CampusServiceAdapter` 依赖 `data.model.DormInfo` 与 `DormServiceRouter`（宿舍生活那条链），要搬得先搬 dorm 模型 + 路由；`SchoolServiceLauncher`（155 行，6 处 `import android`）属于平台 UI 层，**按边界就该留在 Android**。
---

## 8. 追加：宿舍链 + 服务目录/解析/适配器全部并入共享核心（本轮）

共享层 `core/.../campus/` 现有 11 个文件（共 990 行）：

| 文件 | 行 | 职责 |
|---|---|---|
| `CampusConfig.kt` | 145 | 校区表 + 服务键 + 教务类型展示名 |
| `CampusServiceRegistry.kt` | 228 | 服务 spec 登记表 |
| `CampusServiceSpec.kt` | 52 | spec / type / resolved 模型 |
| `CampusServiceCatalog.kt` | 40 | key → spec 目录与兜底 |
| `CampusServiceResolver.kt` | 188 | 能力判定（WEB/INFO/RESERVED…）+ 默认方案包装 |
| `CampusServiceAdapter.kt` | 72 | 摘要状态（`ServiceSummaryState/ServiceMetric/ServiceSummary`）+ `CampusServiceAdapterRegistry` |
| `DormLifeServiceAdapter.kt` | 64 | 宿舍生活类入口适配 |
| `DormInfo.kt` | 69 | 宿舍信息（含 `sanitized()` 的归零语义） |
| `DormServiceRouter.kt` | 29 | 宿舍 → 校区路由 |
| `DormBuildingCatalog.kt` | 73 | 楼栋 → 校区静态目录 |
| `SchoolService.kt` | 30 | 跳转渠道模型（APP/WEBVIEW/WECHAT_MP/ALIPAY） |

Android 侧 10 个文件缩成 `typealias`，调用方（`AppContainer`、`TreasureBoxScreen`、`ServiceEntry`、`DormBindingForm`、`ElectricityScreen`…）零改动。

### 本轮踩坑：别名要“连坐式”补齐

移动一个文件时，**同文件里声明的伴随类型会一起搬走**，Android 端每漏一个就整片报错：
`ResolvedCampusService`（Spec）→ `ServiceChannel`（SchoolService）→ `CampusServiceCapability`（Resolver）→ `ServiceSummaryState/ServiceMetric/ServiceSummary/CampusServiceAdapterRegistry`（Adapter）。经验：搬完一个文件后先 `Select-String '^(internal )?(object|class|data class|enum class|sealed|fun|val)'` 列出声明的全部类型，一次性补齐别名，别等到编译报错逐个试。

### 验证

| 检查 | 结果 |
|---|---|
| `gradlew :core:jvmTest :core:testAndroidHostTest` | exit 0 |
| `gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL（只剩 2 条 map 层的既有 warning） |
| `gradlew :app:testDebugUnitTest --tests "com.pocket.xiaoli.data.*"` | 114 个测试类，无 failures/errors |
| desktop `:app:compileKotlin` | exit 0 |

至此**服务/宿舍/校区这条数据链已完全共享**。仍留在 Android 的只有真平台层：`SchoolServiceLauncher`（6 处 `import android`）、`CampusCardLauncher`、`ExpressPickup`、`TodayCampusPortal`、`ServiceAutoLogin`、`CasLogin`（登录/拉起属于平台能力）。
---

## 9. 追加：把校区/宿舍的测试也搬进共享层（本轮）

原来 `DormServiceRouterTest`（7 例）与 `SchoolRunSpecTest`（4 例）在 `app/src/test` 里，测的是**已经搬进共享层**的代码 —— 等于最该跨端跑的用例只在一个平台跑。

本轮把它们移到 `core/src/commonTest/kotlin/com/pocket/xiaoli/core/campus/`，断言从 JUnit4 换成 `kotlin.test`：

| 用例 | jvmTest | testAndroidHostTest |
|---|---|---|
| `DormServiceRouterTest` | 7 / 0 failures | 7 / 0 failures |
| `SchoolRunSpecTest` | 4 / 0 failures | 4 / 0 failures |

`app/src/test` 下对应两个文件已删除（避免同一逻辑两份测试）。

> 注意：commonTest 走 `kotlin.test` + JUnit Platform（`core/build.gradle.kts` 里 `tasks.test { useJUnitPlatform() }`），别再用 `org.junit.Test`，否则 jvm 目标能过、androidHostTest 目标会找不到断言库。

### 本轮验证

| 检查 | 结果 |
|---|---|
| `gradlew :core:jvmTest` | SUCCESS，两个用例各 7/4 通过 |
| `gradlew :core:testAndroidHostTest` | SUCCESS，同一批用例在 Android host 上也通过 |
| `gradlew :app:testDebugUnitTest --tests "com.pocket.xiaoli.data.*"` | exit 0（删除该两个文件后其余 data 测试仍全绿） |

至此「共享层代码 = 两端同一份，且两端都跑同一批测试」这条闭环在 campus 这条链上成立了。
---

## 10. 追加：课表配色算法并入共享核心（本轮）

`CourseColorIndex`（FNV-1a 32 位 → 15 色取模，按**课名**而不是按节次/行号分配颜色）在两端各有一份：

- Android：`app/.../util/CourseColorIndex.kt`（真源，带详细注释）
- Desktop：`desktop/core/.../logic/CourseNames.kt` 里的 `autoColorIndex()`（内联同一段 FNV 实现）

本轮搬到 `core/src/commonMain/kotlin/com/pocket/xiaoli/core/course/CourseColorIndex.kt`：

| 端 | 改法 |
|---|---|
| Android | `util/CourseColorIndex.kt` → `typealias`（`forCourseName/forTeacherName/PALETTE_SIZE/normalize` 全部照旧） |
| Desktop | `autoColorIndex(name)` 改为 `com.pocket.xiaoli.core.course.CourseColorIndex.forCourseName(name)`，删掉内联 FNV；`normalizeCourseName` 保留（桌面侧另有展示用途） |

### 测试拆分

原 `CourseColorIndexTest`（7 例）里最后一条依赖 `CourseEntity`，其余 6 条是纯函数：

- 6 条纯函数用例 → `core/src/commonTest/.../course/CourseColorIndexTest.kt`（`kotlin.test`），**JVM 与 Android host 各跑一遍**
- 实体生效色用例 → 留在 `app/src/test/.../util/CourseColorIndexTest.kt`（改名 `CourseColorEntityTest`）

修了两处迁移适配：`assertTrue(message, condition)` → `assertTrue(condition, message)`（kotlin.test 参数顺序）；`CourseEntity` 没有 `termId` 参数（构造时去掉）。

### 验证

| 检查 | 结果 |
|---|---|
| `gradlew :core:jvmTest` | SUCCESS；`CourseColorIndexTest[jvm]` 6/6 通过 |
| `gradlew :core:testAndroidHostTest` | exit 0（同一批用例在 Android host 再跑一遍） |
| `gradlew :app:testDebugUnitTest --tests "com.pocket.xiaoli.util.*"` | exit 0（实体用例） |
| `gradlew :app:testDebugUnitTest --tests "com.pocket.xiaoli.data.*"` | 112 个测试类，无 failures |
| desktop `:app:fatJar` + `--tab=1` 实跑 | 课表渲染与改动前一致（`desktop/shots/phase2_color.png`，`非本周` 置灰仍在） |
---

## 11. 追加：知识库「时效判定」并入共享核心（第 10 轮）

### 为什么这块可以共享

两端的过期判断本质是同一条规则：`today > effective_to` 才算过期（**最后有效日当天仍有效**），
`9999-12-31` 是「永不过期」哨兵，日期写坏了按无期限处理而不是抛异常。

### 共享层（新建）

`core/src/commonMain/kotlin/com/pocket/xiaoli/core/knowledge/ExpiryPolicyCore.kt`

| 符号 | 用途 |
|---|---|
| `ExpiryDecision { Include / IncludeExpired(expiredOnIso, daysAgo) / Exclude }` | 平台无关的判定结果 |
| `expiryDecision(effectiveToIso, hardExpiry, todayEpochDay)` | 单篇判定（硬过期 → Exclude，软过期 → IncludeExpired） |
| `expiryNotice(expiredOnIso, daysAgo)` | 过期提醒文案（两端同一句） |
| `parseIsoDateToEpochDay` / `civilToEpochDay` / `epochDayToIso` | 纯整数日期换算（Howard Hinnant `days_from_civil`），不依赖 `java.time` |

日期在共享层用 **epochDay + ISO 字符串** 表达，因此 `commonMain` 不需要引第三方日期库。

### 两端改法

| 端 | 改动 |
|---|---|
| Android | `ExpiryPolicy.decide()` 改为调 `expiryDecision`，再把结果映射回原有 `Decision`（保留 `IncludeExpired(expiredOn: LocalDate)` 的类型，调用点零改动）；`notice()` 委托 `expiryNotice` |
| Desktop | `KnowledgeDoc.isExpired()` 改为 `expiryDecision(..., hardExpiry = false).is IncludeExpired`（原先自己 `LocalDate.parse` + `runCatching`） |

### 验证

| 检查 | 结果 |
|---|---|
| `:core:jvmTest` — `ExpiryPolicyCoreTest` | 8/8 通过（含闰年 2/29、脏日期、哨兵、边界 +1 天） |
| `:core:testAndroidHostTest` | exit 0（同一批在 Android host 再跑） |
| `:app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest --tests "com.pocket.xiaoli.data.knowledge.*"` | 12 个类 / 114 个用例，0 failures |
| desktop `:app:compileKotlin` + `:app:fatJar` + 实跑「知识库」页 | 正常（`desktop/shots/phase2_expiry.png`，22 条文档） |

### 下一步（仍是同一阻塞）

助手链路（SSE 解析 / 工具调用 JSON）需要 `kotlinx-serialization-json`。已两次请示，尚未答复；
在此之前只能做「无 JSON 依赖」的切面。
---

## 12. 追加：学期周次与教学周边界并入共享核心（第 11 轮）

### 共享层（新建）

`core/src/commonMain/kotlin/com/pocket/xiaoli/core/calendar/WeekCalendarCore.kt`

| 符号 | 语义 |
|---|---|
| `EXAM_WEEKS = 2` | 校历「第 19–20 周为期末考试时间」 |
| `weekOf(startEpochDay, todayEpochDay): Int?` | **不夹取**；开学前返回 null（不存在第 0 周），不足 7 天算第 1 周 |
| `weekOfClamped(..., totalWeeks)` | 课表口径：夹到 `[1, totalWeeks]`，总周数非法按 1 |
| `teachingEndEpochDay(startEpochDay, teachingWeeks)` | 教学+考试区间的最后一天（含当天） |

关键区分：**校历页**要判断「这天跳不跳得过去」，必须用不夹取的 `weekOf`（否则寒假会被夹成第 20 周）；
**课表页**要画格子，必须有一个可展示的周次，用 `weekOfClamped`。

### 两端改法

| 端 | 改动 |
|---|---|
| Android | `SemesterUtils.currentWeekFrom` → `weekOfClamped`；`CalendarUtils.teachingEndOf` → `LocalDate.ofEpochDay(teachingEndEpochDay(...))`；`legalWeekOf` 继续走不夹取语义 |
| Desktop | `ScheduleLogic.termWeekOf` → `weekOf(...) ?: 0`（开学前显示 0，与原先一致） |

### 验证

| 检查 | 结果 |
|---|---|
| `:core:jvmTest` — `WeekCalendarCoreTest` | 6/6（开学前 null、第 6 天仍第 1 周、第 7 天进第 2 周、夹取、边界日落在第 20 周而次日属第 21 周、负教学周） |
| `:core:testAndroidHostTest` | BUILD SUCCESSFUL |
| `:app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest --tests "com.pocket.xiaoli.util.*" --tests "com.pocket.xiaoli.data.calendar.*"` | 58 个相关用例，0 failures |
| desktop `:app:fatJar` + 实跑课表 | 标题仍为「第一学期 · 第 9 周」（[phase2_week.png](desktop/shots/phase2_week.png)） |

### 阶段 2 现状

已共享：校区/服务/宿舍/知识库检索/时效判定/周次与教学周边界、课表颜色、课名简称、默认节次、周次表达式、ICS 导出。
仍阻塞：助手链路（SSE / 工具调用 JSON）需要 `kotlinx-serialization-json`，已两次请示未获答复。
## 13. 第 12 轮：ICS 按周展开共享

**问题**：Android 与桌面导出 ICS 的策略不同步 —— 桌面用 `RRULE:FREQ=WEEKLY;UNTIL`，把单双周/指定周次里**没课的周也展开**（导入日历会多出几节不存在的课）；两端各自维护一份周次解析。

**改动**：
- `core/.../course/IcsExpansion.kt`：`IcsCourseInput` / `IcsWeekEvent` / `expandIcsWeekEvents` / `icsEscape` / `formatClock` / `compactClock` / `compactDate`（`commonMain` 不依赖 `java.time`，日期用 epochDay）。
- 周次集合改由既有 `weekSet(weekType, weeks, totalWeeks)` 计算，**删掉新写的 `weeksOf` 变体**（否则又多一份口径）。
- Android `util/CourseIcsExporter`、桌面 `logic/ScheduleLogic.buildIcs` 均改为调用共享展开；桌面删除本地 `icsLocal/icsEscape` 与 RRULE 分支。
- 测试：`IcsExpansionTest` 7 例（只导出生效周、日期落点、分钟优先/回退、节次表缺失兜底、非法星期与周次上限、转义）。

**验证**：`:core:jvmTest` 通过；`:app:compileDebugKotlin` 通过；桌面重启后「我的」页正常（`shots/phase2_ics_profile.png`）。

**行为变化（需要留意）**：桌面导出的 ICS 从「每门课一条 RRULE」变成「每周一条 VEVENT」，文件条目变多但**周次正确**；Android 输出结构不变。

**已知缺口**：Android 侧没有 ICS 单测（`--tests "*Ics*"` 无匹配），ICS 正确性目前只由共享 `IcsExpansionTest` 覆盖；桌面 UI 未暴露「导出 ICS」入口（`buildIcs` 目前没有被界面调用）。

### 13.1 收尾：双端测试 + 「我的 → 导出课表 ICS」入口

- Android 新增 `app/src/test/java/com/pocket/xiaoli/util/CourseIcsExporterTest.kt`（4 例）：单周课只出 8 条、空周次=全学期、日期/节次落点、非法开始日期导出空日历；`:app:testDebugUnitTest` 通过。
- 桌面新增 `desktop/core/src/test/kotlin/.../ScheduleIcsTest.kt`（3 例）锁定同一接线；`:core:test` 通过。
- 桌面「我的 → 数据」加了「导出课表 ICS」行：点开是 Compose 弹窗（可改文件名），确定后写入 `<数据目录>/exports/`，提示「已导出：路径（共 N 节）」。
  - 不用 AWT `FileDialog`：它是独立顶层窗口、与 Compose 模态层脱节，且自动化无法验证。
- 未完成：合成点击在这台机器上没稳定命中该行（坐标换算反复出错），弹窗与落盘目前只由单测 + 编译保证；下次人工点一次即可确认。

### 13.2 导出入口与可验证性（收尾）

- 导出弹窗**移到全局弹窗宿主**（`Dialogs.kt` 的 `IcsExportDialog`，属性 `AppState.showIcsDialog/icsFileName`）。
  原因：最初放在「我的」页内部，`PocketModal` 会被页面滚动容器裁掉 —— 表现是「点了没反应」。
  落盘逻辑收敛到 `AppState.exportIcs(name)`，弹窗与 CLI 共用，避免两份实现。
- 新增无头入口 `--export-ics[=文件名]`：不开窗口，直接写 `<数据目录>/exports/` 并打印结果。
  实测：`java -jar pocket-xiaoli-desktop.jar --export-ics` → `C:\Users\24721\AppData\Roaming\PocketXiaoli\exports\课表.ics`，
  **43,149 B / 181 条 VEVENT / RRULE 0 条**，`DTSTART` 每周递进（20260831、20260907、20260914 …）。
- 仍未做：GUI 里点「导出」的自动化截图（合成点击在这台机器上坐标不稳，属验收工具问题，不是功能问题）。

## 14. 守门加固 + 共享层清单文档

- `core/build.gradle.kts` 的 `verifyCoreScope` 扩了禁用清单：`commonMain` 现在也禁止 `import java.*` / `import javax.*` / `import com.pocket.xiaoli.desktop*`。
  **实测过守门有效**：临时塞一个 `import java.time.LocalDate` 的文件 → `:core:verifyCoreScope` 失败并点名 `文件:行`；删掉后恢复通过。
- 新增仓库文档 `docs/共享核心.md`：拓扑、19 个共享文件的「哪端哪里在用」、守门两层规则、验证命令、阶段进度、新增共享函数的步骤、已知差异。
- 本轮验证矩阵全绿：`:core:jvmTest`、`:core:testAndroidHostTest`、`:core:verifyCoreScope`、`:app:compileDebugKotlin`、桌面 `:core:test` + `:app:compileKotlin`。
- 导出链路复核：`--export-ics=验证-课表.ics` → 43,149 B / 181 条 VEVENT / RRULE 0 条。
- 仍等拍板：助手链路共享需要 `kotlinx-serialization-json`。

## 15. 第 15 轮：「下一节课」挑选规则共享

- 新增 `core/src/commonMain/kotlin/com/pocket/xiaoli/core/course/UpcomingCourseCore.kt`：
  `UpcomingCandidate(dayOfWeek, startSlot, startMinute, endMinute, occursThisWeek)` + `UpcomingPick(index, dayOffset)` + `pickUpcomingCourse(...)`。
  判定口径写进注释：`endMinute <= 0`（时间未知）**算还没下课**；今天放假直接跳过今天；从 1..7 天往后扫并跳过放假日。
- Android `util/UpcomingCourse.findUpcomingCourse` 改为委托（`coursesOn` 等其它调用点不动），
  `UpcomingCourseTest` **10 例全过**；`com.pocket.xiaoli.util.*` 合计 **186 例 0 失败**。
- 桌面 `logic/ScheduleLogic.findUpcomingCourse` 同样改为委托，顺手消掉两端一处口径差：
  原来桌面用 `endMinute >= nowMinute`（正好到下课点还算没下课），现在统一为「到点即下课」。
- 新增共享测试 `core/src/commonTest/.../UpcomingCourseCoreTest.kt` 7 例（今天顺延、全都下课、未知下课时间、放假日跳过、周次不命中、全空、周日绕回周一）。
- 桌面实测（`shadows/phase3_upcoming.png`）：今天 10/31 周六、今日 0 门，卡片显示「下一节课 · 后天 / 计算机网络 / 11-02 周一」——周末被跳过符合预期。
- 验证：`:core:jvmTest`、`:core:testAndroidHostTest`、`:app:compileDebugKotlin`、桌面 `:core:test` + `:app:compileKotlin` + `:app:fatJar` 全绿。

## 16. 第 16 轮：验收矩阵脚本化

- 新增仓库根 `tools/verify-shared.ps1`：一条命令依次跑 8 项并输出 PASS/FAIL 表
  （`:core:jvmTest`、`:core:testAndroidHostTest`、`:core:verifyCoreScope`、`:app:compileDebugKotlin`、
  桌面 `:core:test` + `:app:compileKotlin` + `:app:fatJar`、`--export-ics` 无头导出断言 181 条事件）。
  `-Quick` 跳过打包与导出。实测总耗时 ~35 s（增量缓存命中时），8/8 PASS。
- 试过但**放弃**的两条「编译器级守门」路线（记录以免后人重踩）：
  1. 给 `:core` 加 `js(IR)` target 当编译守门 —— `compileKotlinJs` 能过，但 `:core:check` 会拉起
     `kotlinNodeJsSetup`，与本项目 `settings.gradle.kts` 的 `FAIL_ON_PROJECT_REPOS` 策略冲突
     （`Distributions at https://nodejs.org/dist was added by unknown code`），无法进 `check`。
  2. 用 `metadataCommonMainClasses` 当守门 —— 实测**加了 `import java.time.LocalDate` 仍然 BUILD SUCCESSFUL**，
     说明这条任务在当前双 target（android+jvm）配置下不做真正的 common 解析，守不住。
  结论：编译器级守门不可靠，继续用 `verifyCoreScope` 的正则守门 + 脚本化验收矩阵。
- 文档：`docs/共享核心.md` §4 增加「一条命令版」。

## 17. 第 17 轮：学号脱敏收敛到共享层（修掉一处跨端不一致）

- **发现真问题**：桌面 `Format.maskStudentNo` 是「前 3 + 后 2」（`202****89`），Android `util.maskStudentId`
  是「末 4 位」（`*********6789`）—— 同一个学号两种遮法，正是 `CLAUDE.md` §2.7 警告的信任问题，只是这次跨了端。
- 新增 `core/src/commonMain/kotlin/com/pocket/xiaoli/core/text/StudentIdMask.kt`（规则 + 边界行为写在 KDoc 里），
  `commonTest` 5 例：正常学号、null/空白、≤4 位全遮、前后空格、二次脱敏幂等。
- Android `util/StudentIdMask.kt` 改为**一行委托**（保留原包路径与函数名，几十处调用点不动）。
- 桌面 `Format.maskStudentNo` 改为委托共享层；行为变化：`2023123456789` 现在显示 `*********6789`。
- 桌面实测：把本地数据填上示例学号后截图 `desktop/shots/phase17_mask_filled.png`，身份卡显示 `*********6789`；
  随后已把本地示例身份清空，避免留下假数据。
- 回归：Android `com.pocket.xiaoli.util.*` + `*BoundLogin*` 共 **190 例 0 失败**（含 `BoundLoginTest` 里那条学号脱敏断言）；
  `tools/verify-shared.ps1` 8/8 PASS。
- 待确认（未擅自改）：桌面身份卡在副标题里显示脱敏学号，而 Android 规范是「我的」页正文不收身份字段。
  若要完全对齐，应把学号移进「学生信息」行，只在编辑弹窗里展示 —— 等你一句确认再动。

## 18. 第 18 轮：相对时间分档共享（又一处跨端口径不一致）

- **发现**：Android `util.relativeSinceLabel(past, verb)` 是「刚X / n 分钟前X / n 小时前X / n 天前X / 日期」，
  超过 **30 天**才报日期、报 `yyyy-MM-dd`；桌面 `Format.ago(millis)` 不走动词、超过 **7 天**就报 `MM-dd HH:mm`。
  同一个「上次同步时间」在两个端会是两种读法。
- 新增 `core/src/commonMain/kotlin/com/pocket/xiaoli/core/text/RelativeTimeCore.kt`：
  `relativeSinceLabel(pastMillis, nowMillis, verb, formatDate, cutoffDays = 30)`。
  时区/日期格式仍留在各端（用 `formatDate` 回调注入），共享层只负责分档与拼接。
- Android `util/RelativeTime.kt` 变一行委托（原有的 `Instant`/`ZoneId` 只用于 `formatDate`）。
- 桌面 `Format.ago` 改为委托，保留 `MM-dd HH:mm` 作为「超过 30 天」的日期格式；**行为变化**：7–30 天之间
  从「报日期」变成「n 天前」，与 Android 一致。
- 新增 `core/src/commonTest/.../RelativeTimeCoreTest.kt` 7 例（无时间戳、刚、分钟/小时边界、天数、cutoff 边界、空动词不加尾空格、cutoff 可覆盖）。
  过程中我自己先把「正好 30 天」写成 `30 天前`，跑红后按原 Android 口径（`< 30 天`才说天数）修正 —— 边界值以旧行为为准。
- 回归：Android `util.*` + `data.academic.*` **193 例 0 失败**；`tools/verify-shared.ps1` 8/8 PASS。
- 小插曲：改 `Platform.kt` 时用字符串切片替换函数体，多删了一行右花括号导致 `Missing '}'`，补回后编译通过
  —— 教训：这种整段替换不如按行编辑稳。

## 19. 第 19 轮：会话列表日期档共享（又一处跨端不一致）

- **发现**：手机端会话列表 `formatTime` 是「今天 / 昨天 / 前天 / M月d日」，且**固定按东八区划天**（注释写明：跟设备时区走会把「今天」标在昨天）；
  桌面端同一行用的是 `Format.ago`，显示成「3 小时前 / 2 天前」——读不出是今天还是昨天。
- 新增 `core/src/commonMain/kotlin/com/pocket/xiaoli/core/text/ConversationDayLabelCore.kt`：
  `conversationDayLabel(millis, nowMillis, dayOffsetMinutes = 480, formatMonthDay)`；分档与划天在共享层，
  日期本地化（`M月d日`）由各端注入。
- Android `AssistantConversationListScreen.formatTime` 变一行委托（保留原有 `now` 注入点，快照测试仍可钉死基准时刻）。
- 桌面 `Format` 新增 `conversationDay()`，会话行文案从 `Format.ago` 换成它；`ago()` 仍用于消息时间。
- 新增 `core/src/commonTest/.../ConversationDayLabelCoreTest.kt` **5 例**：无时间戳、当天、昨天/前天、更早回退月日、
  **跨 UTC 日期边界**（2026-11-01 00:26 +08:00 必须算「今天」）。
- 回归：Android `ui.assistant.*` **117 例 0 失败**；`tools/verify-shared.ps1` **8/8 PASS**；
  桌面实跑截图 `desktop/shots/phase19_conv_label.png` 显示会话行「1 条 · 今天」。
- 顺带发现的**下一处候选**：Android 校历「考试周 = 最后 2 周」有专门口径（`CalendarRepository` 里 `+ 2 周`），
  桌面 `WeekCalendarCore` 目前只有教学周 `EXAM_WEEKS = 2` 常量而**没有考试周区间**，服务页「看校历」的考试信息不对齐。

## 20. 第 20 轮：学期阶段与教学进度共享（桌面进度条分母一直是错的）

- **发现**：桌面服务页「校历」里进度条的分母用的是**总周数 26**（含寒暑假），第 9 周看着只过三分之一；
  手机端口径是「教学 18 + 考试 2 = 20 周」（`CalendarUtils.EXAM_WEEKS` + `teachingEndOf`）。另外桌面没有「考试周」这一档。
- 新增 `core/src/commonMain/kotlin/com/pocket/xiaoli/core/calendar/TermWeeksCore.kt`：
  `Phase { BEFORE/TEACHING/EXAM/AFTER }`、`examWeekRange(teachingWeeks)`、`isExamWeek`、`phaseOf(week, teachingWeeks)`、
  `progressFraction(week, teachingWeeks)`（分母 = `teachingWeeks + WeekCalendarCore.EXAM_WEEKS`，考试周钉在 18/20）。
- 桌面 `Campus.kt` 学期卡：周次改用 `WeekCalendarCore.weekOf(epochDay)`（原来自己用 `ChronoUnit.DAYS / 7 + 1` 又算一遍），
  阶段与进度改用 `TermWeeksCore`；标签从「第 N 周 / 未开始 / 已结束」扩为「第 N 周 / 考试周 / 未开始 / 已结束」。
- 新增 `core/src/commonTest/.../TermWeeksCoreTest.kt` **5 例**：学期前、教学周、19–20 为考试周、21 周后已结束、进度 0.4（第 9 周）。
- 验证：`tools/verify-shared.ps1` **8/8 PASS**；桌面实跑截图 `desktop/shots/phase20_calendar3.png`
  （第一学期「第 9 周」进度条约 40%，第二学期「未开始」）。
- **仍是两端不一致、但故意没搬的**：手机端的假期阶段（寒假/暑假）来自校历事件表（`EventKind.VACATION`），
  桌面没有事件驱动，只有「考试周」这一档能靠周次推出来。要完全对齐，得先把校历事件 JSON 也读进桌面数据层，作为下一轮候选。

## 21. 第 21 轮：五档阶段位共享（寒暑假终于两端一致）

- **发现**：上一轮只把手机上「教学/考试周/前后」四档搬成 `TermWeeksCore`，假期两档（寒假/暑假）仍只存在于手机端 ——
  桌面端完全不显示「假期」。而桌面**已经**读入了校历事件表（`Assets.calendar.events`），其实能算。
- 新增 `core/src/commonMain/kotlin/com/pocket/xiaoli/core/calendar/TermPhaseCore.kt`：
  `Phase { BEFORE_TERM/IN_TERM/EXAM_WEEKS/WINTER_VACATION/SUMMER_VACATION }`、`Window(kind, startEpochDay, endEpochDay, name)`、
  `phaseOf(todayEpochDay, terms, events, monthOf)`（判定顺序：**假期 > 考试周 > 学期区间 > 未开学**，先判假期是为了不让跨界考试事件把寒假说成考试周）、
  `isWinter(name, month)`（名字含「寒/暑」优先，否则 12/1/2 月算寒假）。
- 手机端 `CalendarUtils.calendarPhase` 改为：把 `CalendarTerm`/`CalendarEvent` 翻成 epochDay 窗口 → 调共享层 → 映射回 `CalendarPhase`；
  顺手删掉已无引用的 `isWinterVacation`。
- 桌面 `Campus.kt` 校历顶部加「当前阶段」行（`TagChip(sharedPhase.label)`），事件表喂给同一个函数。
- 新增 `core/src/commonTest/.../TermPhaseCoreTest.kt` **6 例**：假期优先于考试、考试周、学期内、之外、名称/月份兜底、暑假。
- 验证：Android `util.*` **186 例 0 失败**；`tools/verify-shared.ps1` **8/8 PASS**；
  桌面实跑截图 `desktop/shots/phase21_calendar.png` 顶部显示「当前阶段 学期中」，与手机端文案一致。

## 22. 第 22 轮：共享层回归到打包链路（无新共享规则）

- 这一轮不新增共享规则，目的是确认「25 个共享文件 + 嵌套构建」没有把打包打坏：
  - `tools/package.ps1 -SkipBuild` 重打 → `dist/PocketXiaoli/` + `PocketXiaoli-0.1.92-win-x64.zip`（**70.1 MB**）；
  - 启动 `dist/PocketXiaoli/PocketXiaoli.exe --tab=3` → 服务页正常，「当前阶段 学期中」「第 9 周」进度条都在
    （截图 `desktop/shots/phase22_packaged.png`），说明 jpackage 产物确实吃到了 `:core`；
  - `tools/installer.ps1` 重打安装器 → `dist/口袋小鲤-0.1.92-安装程序.exe`（**117.1 MB**）。
- 另出给决策用的一页纸：`docs/桌面端-阶段验收.md`（结构现状 / 已同源口径 / 修掉的 6 个缺陷 / 缺口与拍板项 / 交付物）。

## 23. 第 23 轮：把「周次从哪来」也收回共享层 + 加跨函数不变量测试

- **又一处手写周次**：桌面 `Store.currentWeek` 自己用 `ChronoUnit.DAYS / 7 + 1` 算，且**不夹取**
  —— 学期结束后会一直涨（手机端 `SemesterUtils` 用的是 `WeekCalendarCore.weekOfClamped`，会夹到总周数）。
  已改为调 `WeekCalendarCore.weekOf`，开学前返回 0、超过总周数夹到最后一上周（与手机一致）。
- `TermWeeksCore.phaseOf` 里的考试周判断改为走 `isExamWeek()`（此前 `examWeekRange/isExamWeek` 只有测试在调，属半死代码）。
- 新增 `core/src/commonTest/.../CalendarInvariantsTest.kt` **5 例**，专测「函数之间对不上」这类问题：
  教学结束日 = 最后一周周日、次日属于下一周、`phaseOf(weekOf(结束日)) == EXAM`、
  开学前一天无周次、教学结束时进度 = 18/20。
  说明：各自单测都绿但彼此错位，是这类日历代码最典型的漏网方式。
- 验证：`tools/verify-shared.ps1` **8/8 PASS**（jvmTest 12.1s、androidHostTest 7.7s，含新不变量测试）。

## 24–25. 第 24–25 轮：冷启动实测并进一键验收

- **测法**：新增 `tools/measure-startup.ps1` —— 启动进程后轮询「标题含『口袋小鲤』的窗口」出现时刻，默认跑 3 次报中位数。
  坑：jpackage 的 `PocketXiaoli.exe` 只是启动器，真正的窗口在**另一个子 JVM 进程**里，
  按启动器 pid 找窗口会一律超时（第一次测出来三次都是 30s 超时，就是这个原因）。
- **实测**（i5 机器、150% 缩放、已装 .NET 无关）：
  - 打包版 exe（内置运行时）：中位 **1.45–1.61s**（3 次 1.53/1.61/1.61；后续复测 1.41/1.91）；
  - fat jar（系统 JDK）：中位 **1.31–1.55s**。
  都低于事实卡的 2s 线，所以本轮不做 AppCDS/类预加载，只把指标固化下来。
- `tools/verify-shared.ps1` 从 8 项扩到 **9 项**，新增「冷启动中位 ≤ 2.5s」这一步（超过还给出查 IO/JIT 的提示）。
  实测：**9/9 PASS**（冷启动中位 1.45s）。
- `docs/桌面端-阶段验收.md` 增补「性能现状」小节（含两个测量口径与脚本路径）。

## 26. 第 26 轮：月历几何共享 + 桌面补上月历卡

- **共享函数**：`core/src/commonMain/kotlin/com/pocket/xiaoli/core/calendar/MonthGridCore.kt`
  `monthGrid(firstEpochDayOfMonth, daysInMonth, weekdayOfFirst, firstDayOfWeek = 0)` → `List<MonthCellSpec(epochDay, inMonth)>`。
  只用「已算好的事实」做参数（epochDay、天数、周几），共享层不碰 `java.time`，符合守门规则。
  **不写死 6 行**：28 天且从周一开始的月只有 4 行（手机端原来就是这个口径，现在两端共用）。
- **测试** `core/src/commonTest/.../MonthGridCoreTest.kt` 5 例：11 月（周日开头）补 6 格共 42；周日为一周起点时 11/1 落在第一格共 35；2027-02 从周一开 28 格 4 行；28–31 天 × 7 种起始全覆盖自洽；参数越界抛 `IllegalArgumentException`。
- **Android 接线**：`app/src/main/java/com/pocket/xiaoli/util/CalendarUtils.kt` 的 `monthGrid(...)` 改为一行委托（`import ... as sharedMonthGrid` 避免自递归），`MonthCell` 类型不动 → 截图基线不受影响。
- **桌面补功能**：校历页新增「本月」卡（`Campus.kt` 的 `MonthCard`）：共享几何 + 今天实心圆 + 有事件的日子点小圆点。
  截图 `desktop/shots/phase26_month_pre.png`：2026 年 10 月，1 号周四（前补 3 格灰字 28/29/30 正确），31 号（今天）实心。
- **说明**：桌面仍**没有节假日数据**（手机端也没有，这是事实卡列为「加数据」的那一项），本轮只做几何共享 + 视图，不引入假数据。
- 验收：`tools/verify-shared.ps1` **9/9 PASS**（冷启动中位 1.44s）。

## 27. 第 27 轮：事件覆盖判定共享 + 桌面月历可翻月/可选日

- **共享函数**：`core/src/commonMain/kotlin/com/pocket/xiaoli/core/calendar/EventCoverageCore.kt`
  `eventCoversDay(startEpochDay, endEpochDay, dayEpochDay)`（闭区间）+ 重载 `endEpochDay: Long?`（缺省按单天）。
  Android 原来是 `!date.isBefore(start) && !date.isAfter(end)`，两端各写一遍。
- **测试** 4 例：单天事件、多天首尾都算、结束日缺省、脏数据（start > end）不命中。
- **Android 接线**：`CalendarModels.kt` 的 `covers(date)` 改为一行委托。
- **桌面**：月历卡升级为**可翻月 + 可选日**：
  - 标题右侧 ‹ › 翻月、非本月时出现「回到本月」chip；
  - 点某天 → 下方显示「M月d日 周X」+ 当天安排列表（无则「没有安排」）；
  - 有事件的日子保留小圆点，今天实心、选中日淡色。
  - 截图 `desktop/shots/phase27_month_jan.png`：2027 年 1 月，「2 天有安排」，4 日 / 18 日圆点正确，选中 1/1 显示「没有安排」。
- **验收用的两个开发开关**（与 `--tab` 同一套思路，生产无影响）：
  `-Dxiaoli.campus.section=1` 直接打开校历段；`-Dxiaoli.campus.monthOffset=3` 直接翻到三个月后。
  加这两个开关的原因：**这台机器上合成点击对段选择器不稳定**（点了不生效），靠坐标试错太费时间。
- 验收：`tools/verify-shared.ps1` **9/9 PASS**（冷启动中位 1.55s）。

## 28. 第 28 轮（+ 本节补记）：节假日数据（内置 + 可选在线更新）

**口径**：只收「法定节假日 + 调休上班」，不含「与周末连休」的普通周末；数据来自国务院通知（经 NateScarlet/holiday-cn 结构化），**不涉及任何用户数据**。

- 共享层 `core/src/commonMain/kotlin/com/pocket/xiaoli/core/calendar/HolidayCalendarCore.kt`：
  `HolidayDay(epochDay, name, isOff)`、`epochDayOf(y,m,d)`（Howard Hinnant 民用历算法，不碰 `java.time`）、
  `parseHolidayJson(text, expectYear)`（三字段正则解析，脏条目跳过而不是抛异常）、`holidayOn`、`nextOffDay`、`daysToNextOffDay`。
- 内置数据 `BuiltinHolidays2026.kt`：**39 条（33 天放假 / 6 天调休）**，由脚本从真实 JSON 直出，文件头写来源与「不要手改」。
- 测试 `HolidayCalendarCoreTest` 7 例：绝对锚点（1970-01-01 = 0、2026-01-01 = 20454、2026-11-01 = 20758）、条数与口径、元旦/调休命中、下一个放假日与倒计时、解析与年份过滤、脏条目、与内置表可互换。
  ⚠️ 顺带修正：`MonthGridCoreTest` 里两个日期魔数原来标错（`nov1` 实际是 11-02），改为 `epochDayOf(...)` 计算并加跨文件自洽断言。
- 桌面 `HolidayStore`（`desktop/core/.../data/HolidayStore.kt`）：内置优先 → 若 `holidays/2026.json` 存在且解析出 ≥10 条就用文件 → `updateFromNetwork()` 拉 jsDelivr 镜像，成功才原子落盘。
  因为 **core 模块禁 Compose**，它用 `version: Int` 变更通知，界面侧 `remember(HolidayStore.version)`。
- 界面：
  - 校历月历：放假橘色圆点、调休上班灰点、当天抬头加 `国庆节 · 放假`/`· 调休上班` chip（截图 `desktop/shots/holiday_oct.png`）；
  - 我的 → 数据：「节假日数据 2026 · 33 天放假 / 6 天调休 · 已更新 · 来源：国务院放假通知」+「更新」chip；「自动更新节假日」开关默认**关**（离线也能用内置数据，失败静默）；
- 命令行验收：`--update-holidays`（不开窗口，成功打印 `count=39` 并写文件，实测 4843B）。
