package com.pocket.xiaoli.data.calendar

import java.time.LocalDate

/**
 * 校历事件的类别。
 *
 * [weight] 决定**同一天上格子的是哪一条** —— B 版式的格子里只放得下一行小字，
 * 同日多条必须有个确定的赢家。顺序取设计文档：`exam > holiday > vacation > activity > milestone`。
 *
 * 用「越小越优先」的整数而不是另立一张优先级表：排序就是一次 `minBy`，
 * 排序与枚举不会各说各话（「同一个病的两种写法」本项目已经踩过）。
 */
enum class EventKind(val label: String, val weight: Int) {
    EXAM("考试", 0),
    HOLIDAY("放假", 1),
    VACATION("假期", 2),
    ACTIVITY("活动", 3),
    MILESTONE("节点", 4),
    ;

    companion object {
        /**
         * 从资产里的字面量解析。
         *
         * 这里收**字符串**，而不是让 Gson 直接反序列化成这个枚举：Gson 按枚举名精确匹配，
         * 而资产里写的是小写（`"kind": "exam"`）。资产是人手录的 ——
         * 大小写写错只该退化成 [MILESTONE]，不该让整份校历解析失败。
         * 真正的守门人是资产校验单测：它直接读资产文件、断言每个已知事件的具体类别。
         */
        fun from(raw: String): EventKind =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: MILESTONE
    }
}

/**
 * 一个学期。
 *
 * ⚠️ 三个「周数」是三件不同的事，不要合并：
 * - [teachingWeeks] = 18，教学周（学校明文）；
 * - [totalWeeks] = 26，**校历口径**，把期末考试周与寒暑假都编进周次表了 —— 校历页的周次编号用它；
 * - App 的 `semester_weeks`（默认 20）= `teachingWeeks + 2`，语义是「课表排到第几周」。
 *
 * 前两个住在校历资产里，第三个住在用户的学期设置里。把一个当成另一个，
 * 校历页会把寒假的日子算成第 20 周，或者课表会多排 6 周空列。
 *
 * ⚠️ 还有一处容易看走眼：[end] 是**校历周次表的最后一天**（26 周，含寒暑假），
 * 所以它**不能**用来判「今天在不在学期里」—— 寒假整整六周都会落在第一学期的 `[start, end]` 里。
 * 那个判断用 `CalendarUtils.teachingEndOf(term)`（教学 + 考试周的最后一天）。
 */
data class CalendarTerm(
    val name: String,
    val start: LocalDate,
    val end: LocalDate,
    val teachingWeeks: Int,
    val totalWeeks: Int,
)

/** 一条校历事件。[start]..[end] 是**闭区间**，单日事件两者相等。 */
data class CalendarEvent(
    val title: String,
    val start: LocalDate,
    val end: LocalDate,
    val kind: EventKind,
    /** 格子内那行小字（资产约定 ≤4 字）。为空则回退到 [title] 前 4 字。 */
    val short: String,
) {
    /**
     * 格子里实际显示的字。
     *
     * 「取前 4 字」只在这里算一次 —— UI 若各自再算一份，改了截断规则就会有地方漏改。
     */
    val gridLabel: String get() = short.ifBlank { title.take(GRID_LABEL_MAX) }

    /**
     * 气泡去重用的内容键（标题 + 开始日）。
     *
     * **产生方与消费方共用这一个定义**：采集侧用它判断"说过没有"，
     * 消费侧用它写回游标。两边各拼一次的话，拼法迟早不一致，
     * 那个节点就会永远"没说过"、无限重播 —— 而离散的节点又不能靠冷却兜住。
     */
    val nodeKey: String get() = "$title@$start"

    /** 这一天是否落在事件区间内。 */
    fun covers(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        /**
         * 格子内小字上限（B 版式定案）。
         *
         * 资产校验单测按这个数报错，而**不是**上屏截断：格内只有约 46dp 宽，
         * 超出的字会被裁掉且不报任何错，录错数据唯一的守门人就是那条单测。
         */
        const val GRID_LABEL_MAX = 4
    }
}

/** 一个学年的校历。[schema] 是资产格式版本，留给将来真正需要迁移时用。 */
data class CalendarAsset(
    val schema: Int,
    val academicYear: String,
    val source: String,
    val terms: List<CalendarTerm>,
    val events: List<CalendarEvent>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/**
 * 顶部右侧那个阶段位。
 *
 * 只表达**校历自己说得清**的五种状态；刻意不做「距期末还有 N 天」——
 * 那要多维护一个「期末」定义，而校历里本来就有考试周，
 * 两套定义迟早会对不上（用户已定：侧状态位取 A 方案，只显示阶段文字）。
 */
enum class CalendarPhase(val label: String) {
    BEFORE_TERM("未开学"),
    IN_TERM("学期中"),
    EXAM_WEEKS("考试周"),
    WINTER_VACATION("寒假"),
    SUMMER_VACATION("暑假"),
}
