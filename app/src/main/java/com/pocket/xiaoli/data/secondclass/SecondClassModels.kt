package com.pocket.xiaoli.data.secondclass

/**
 * 第二课堂（到梦空间）的**领域模型**与结果类型。
 *
 * ### 这里刻意没有 DTO
 * 架构文档 §3 把 `SecondClassModels.kt` 描述为「DTO + 领域模型 + sealed 结果类型」。
 * 但 **DTO 无法在 §7.6 关闭之前写**：B 链路（`www.5idream.net` 的 H5 取数接口）的确切 URL、
 * 鉴权位置（Cookie / header / query）、是否另有前端 `ts`+`sign`，三条都还没实测确认；
 * 而 A 链路更根本 —— 原以为的 `openapi.5idream.net` **连存在形态都已被推翻**：
 * 到梦空间官方对外开放的是**校方级数据同步接口**（同步到"学校指定的存储空间"），
 * 不是发给第三方客户端的 `openId`（见架构文档 §11.5）。
 * 照着口述猜 DTO 正是文档明令禁止的「照口述猜」。
 *
 * ### 官方给的 5 个数据域（写 DTO 时的词汇表）
 * 「学生基本信息 / **成长记录** / **学分记录** / 活动数据 / **部落数据**」
 * （出处：[到梦空间官网「到梦服务」](https://www.5idream.net/service/service.html)）。
 * ⚠️ 其中**成长记录与学分记录是分开的两类**，别把两者混成一个字段。
 * 本文件当前只建模了「活动」与「成长记录」；**部落**与**学分记录**待 wire format 确认后再补 ——
 * 不凭这 5 个词去猜字段名。
 *
 * 所以本文件只放**与传输格式无关**的那一层：UI 与 Repository 之间的契约。
 * wire format 确认后，DTO 加在 DataSource 实现内部（`WebViewSessionDataSource` / `OpenPlatformDataSource`），
 * 由它们负责 DTO → 本文件领域模型的映射。**领域模型不该被接口字段牵着走。**
 *
 * ### 为什么每个字段都有默认值
 * 这些模型将来大概率走 JSON 轻量存档（架构文档 §7.3 倾向 JSON 而非 Room ——
 * 数据只是「一份成绩单 + 一页活动列表」，无查询需求，上 Room 是过度设计）。
 * 而「每个字段都有默认值」正是 Gson 那道防线本身：只要有一个字段漏了默认值，
 * Kotlin 就不生成无参构造，Gson 改用 `UnsafeAllocator` 反射赋值，**所有**字段的默认值一起失效
 * ——`DormInfo` v1.0.93 的闪退就是这么来的（见 `CLAUDE.md`）。
 *
 * ### ⚠️ 学分与时间一律保留原始字符串
 * 到梦空间的学分是「字符串还是数字、几位小数」、时间是「时间戳还是 `2026-10-05 14:00`」
 * 都还没确认。**不在这里猜格式**：猜错会让 UI 显示错值而不报错。
 * 领域模型先原样承载，格式化留给 UI 层（且架构文档 B5 明确「不做综合测评核算」，所以没有求和的刚需）。
 *
 * ⚠️ 连**单位**都是各校不同的，这是实测出来的，不是推测：
 * - 烟理自己的口径是「**积分置换学分**」（信息工程系 2020-11-20 第二课堂培训会原文）；
 * - 但山东理工的院级指南写的是「活动学分一般在活动结束后发放」——那里活动**直接发学分**。
 *
 * 所以 `credit` 这个字段承载的可能是**积分**、也可能是**学分**，
 * **不要拿它做任何跨活动求和或换算**（`docs/superpowers/specs/2026-10-05-…-design.md` §11 有出处）。
 */

/** 活动状态。无法识别的服务端取值一律落到 [UNKNOWN]，绝不抛异常。 */
enum class SecondClassActivityStatus {
    UNKNOWN,
    SIGNUP,
    ONGOING,
    ENDED,
    CANCELLED,
}

/** 活动列表 / 详情共用的活动摘要。 */
data class SecondClassActivity(
    val id: String = "",
    val title: String = "",
    val status: SecondClassActivityStatus = SecondClassActivityStatus.UNKNOWN,
    /** 原始时间字符串（见文件头「学分与时间一律保留原始字符串」）。 */
    val startAt: String? = null,
    val endAt: String? = null,
    /** 原始学分字符串。**不做求和**（B5）。 */
    val credit: String? = null,
    val organizer: String? = null,
    val location: String? = null,
)

/** 活动详情 = 摘要 + 详情页独有的字段。 */
data class SecondClassActivityDetail(
    val activity: SecondClassActivity = SecondClassActivity(),
    val description: String? = null,
    val participantCount: Int? = null,
    val creditRule: String? = null,
)

/**
 * 第二课堂成绩单。
 *
 * ⚠️ **学分模块与成长记录是两张平行的表**，不是嵌套关系 —— 这一点是实测出来的：
 * `certificate.js` 分**两次独立请求**取它们（`certificate/certificate/scorelist` 与
 * `certificate/certificate/recordlist`），而且用**两个不同的模板**渲染
 * （`scorecerListTmpl` / `recordcerListTmpl`）。见架构文档 §11.9。
 *
 * 早先这里把成长记录挂在 [SecondClassTranscriptModule.items] 下面 —— 那是我**自己发明的结构**，
 * 它会逼出一个**无法验证的假设**：记录的模块名必须与学分模块名**逐字相等**才能对得上。
 * 现在改成平行两张表，数据源只需把两个端点各自的结果放进来，
 * **不需要做任何按名字配对**（配不上就会静默丢记录，那是最难查的一类 bug）。
 */
data class SecondClassTranscript(
    /** 原始总学分字符串。 */
    val totalCredit: String? = null,
    val modules: List<SecondClassTranscriptModule> = emptyList(),
    /** 成长记录（来自另一个端点）。条目自带 [SecondClassGrowthRecord.module]。 */
    val records: List<SecondClassGrowthRecord> = emptyList(),
)

/**
 * 成绩单的一个模块（**只有学分，不含记录**）。
 *
 * [name] 保持自由字符串而不是枚举，理由是**实测出来的**：
 * - 烟理自己的《学生第二课堂管理办法（试行）》口径是「**六大模块**」——
 *   思想政治 / 创新创业 / 社会实践 / 志愿服务 / 文体活动 / 劳动教育
 *   （出处：建筑工程学院 2025-09-30 新生第二课堂宣讲会通稿）；
 * - 而学校 2026-08 招标的「第二课堂管理系统」第一项功能就是「**指标体系配置管理**」。
 *
 * 也就是说模块名与数量**本来就是可配的**：写死成枚举等于把某校某一版的口径焊进代码
 * （传闻里的"七大模块"就与烟理现行六大模块对不上）。
 */
data class SecondClassTranscriptModule(
    val name: String = "",
    val earnedCredit: String? = null,
    val requiredCredit: String? = null,
)

/**
 * 成绩单里的一条**成长记录**。
 *
 * 用「成长记录」而不是自造的名字：这是到梦空间自己的界面词汇
 * （学生登录后点「我的」即看到「二课学分及**成长记录**」，
 * 见山东理工大学电气学院《第二课堂成绩单学分查询及学分获得相关事宜》）。
 * 领域模型与平台用同一套词，写 DTO 映射和 UI 文案时就不需要再做一次翻译。
 */
data class SecondClassGrowthRecord(
    val id: String = "",
    val title: String = "",
    val credit: String? = null,
    val module: String? = null,
    val occurredAt: String? = null,
)

/**
 * 一次查询的结果。
 *
 * ⚠️ [Unauthorized] **必须独立成一支**，不能混进 [Failed]：
 * 数据源 B 的登录态会静默过期，混进去之后 UI 只会显示「加载失败」，
 * 用户根本不知道要回 WebView 重新登录 —— 这是本模块最容易做错的一处体验（架构文档 §4）。
 */
sealed interface SecondClassResult<out T> {
    data class Ok<T>(val value: T) : SecondClassResult<T>

    /** 登录态失效 → UI 引导回 WebView 登录。绝不在这里自动重登。 */
    data object Unauthorized : SecondClassResult<Nothing>

    /** 服务端明确要求稍后再试（如带 `Retry-After`）。 */
    data class RateLimited(val retryAfterMs: Long) : SecondClassResult<Nothing>

    data class Failed(val code: Int?, val message: String) : SecondClassResult<Nothing>
}
