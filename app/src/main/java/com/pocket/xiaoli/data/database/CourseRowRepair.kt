package com.pocket.xiaoli.data.database

import com.pocket.xiaoli.util.CourseColorIndex
import com.pocket.xiaoli.util.CourseShortName

/**
 * `MIGRATION_16_17` 的**决策层**：给定 `courses` 表的旧行，算出每一行该改成什么。
 *
 * ## 为什么把决策从迁移里拆出来
 *
 * 迁移跑在 `SupportSQLiteDatabase` 上，而本项目**没有引 `room-testing`** ——
 * 没有 `MigrationTestHelper`，迁移体本身在单测里跑不起来（要 instrumentation）。
 * 但"这一行到底该不该重算颜色"是个**纯判断**，一旦埋在 SQL 与 Cursor 之间，
 * 就只能靠上线后用户报错来发现问题。
 *
 * 所以：判断全在这里（**100% 单测覆盖**），迁移体只负责"读行 → 调 [plan] → 执行 UPDATE"。
 * 这是本项目对"迁移不可测"的通用对策：**把不可测的部分压到最薄**。
 *
 * ## 颜色修复的判据（以及为什么敢猜）
 *
 * 改造前每一个解析器都写死 `colorIndex = (dayOfWeek * 3 + startSlot) % 15`
 *（`ZhengfangScheduleParser` / `ZhengfangHtmlParser` / `QiangZhiClient` / 网页导入 …共 9 处），
 * 于是：
 *
 * | 观察到的值 | 判断 | 处理 |
 * |---|---|---|
 * | **恰好等于**位置公式的值 | 极可能是导入时自动分配的 | 重算成按课名派生的稳定色 |
 * | 不等于位置公式 | 用户手选过颜色（或来自别的解析路径） | **原样保留**，并标记 `colorPinned = true` |
 *
 * ⚠️ 这条判据会**猜错**：用户手选的颜色如果正好等于该位置的值，会被当成自动值重算一次。
 * 后果只是"那门课的颜色变了"（下一次导入也不会再犯），不是丢数据 —— 所以敢这么猜。
 * 反过来（不修）的后果是用户**永远**看着同一门课两种颜色，那才是他报的 P0。
 */
object CourseRowRepair {

    /** 修复需要的那几列（迁移里从 `courses` 读出来的原始值）。 */
    data class Row(
        val id: Long,
        val name: String,
        val dayOfWeek: Int,
        val startSlot: Int,
        val colorIndex: Int,
        val shortName: String,
    )

    /** 一行的修复结果。 */
    data class Fix(
        val id: Long,
        val colorIndex: Int,
        val colorPinned: Boolean,
        val shortName: String,
    )

    /** 改造前所有解析器共用的位置公式 —— 判"这个值是不是自动分配的"就用它。 */
    fun legacyPositionalIndex(dayOfWeek: Int, startSlot: Int): Int =
        (dayOfWeek * 3 + startSlot) % CourseColorIndex.PALETTE_SIZE

    fun plan(rows: List<Row>): List<Fix> = rows.map { row ->
        val pinned = row.colorIndex != legacyPositionalIndex(row.dayOfWeek, row.startSlot)
        Fix(
            id = row.id,
            colorIndex = if (pinned) row.colorIndex else CourseColorIndex.forCourseName(row.name),
            colorPinned = pinned,
            // 用户手改过的简称一律不动（他是看着实际显示效果改的，比规则权威）
            shortName = row.shortName.ifBlank { CourseShortName.suggest(row.name).orEmpty() },
        )
    }
}
