package com.pocket.xiaoli.data.holiday

/**
 * holiday-cn 数据模型（数据源：NateScarlet/holiday-cn，与「聚在工大」使用同一份规范）。
 * 文件形如 https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/2025.json
 *
 * {
 *   "year": 2025,
 *   "days": [ { "name": "元旦", "date": "2025-01-01", "isOffDay": true }, ... ]
 * }
 *
 * isOffDay = true  → 法定休息日（放假）
 * isOffDay = false → 调休上班日（周末补班）
 */
data class HolidayYearResponse(
    val year: String? = null,
    val days: List<HolidayDay> = emptyList(),
)

data class HolidayDay(
    /** 节日/调休名称，如「春节」 */
    val name: String = "",
    /** yyyy-MM-dd */
    val date: String = "",
    /** true = 放假；false = 调休上班 */
    val isOffDay: Boolean = false,
)