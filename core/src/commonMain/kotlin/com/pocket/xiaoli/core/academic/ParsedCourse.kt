package com.pocket.xiaoli.core.academic

/**
 * 教务解析出的「一门课」，Android 与桌面共用的中立模型。
 *
 * 字段与 Android `CourseEntity` 对齐，但**不带 Room / Compose 注解**：
 * 共享层只负责「网页 → 结构化数据」，落库与 UI 由两端各自适配。
 */
data class ParsedCourse(
    val id: Long = 0,
    val name: String,
    val credits: String = "",
    val teacher: String = "",
    val room: String = "",
    /** 1=周一 … 7=周日 */
    val dayOfWeek: Int = 1,
    /** 从 1 开始，闭区间 */
    val startSlot: Int = 1,
    val endSlot: Int = 2,
    /** all / odd / even */
    val weekType: String = "all",
    /** 逗号分隔的周次，空 = 全学期 */
    val weeks: String = "",
    val colorIndex: Int = 0,
    val schemeId: Long = 0,
    val shortName: String = "",
    val colorPinned: Boolean = false,
    val note: String = "",
    /** 距 00:00 的分钟；-1 = 用节次推导 */
    val startMinute: Int = -1,
    /** 距 00:00 的分钟；-1 = 用节次推导 */
    val endMinute: Int = -1,
    val kind: String = "course",
)
