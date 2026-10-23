package com.pocket.xiaoli.ui.timetable

import com.pocket.xiaoli.data.entity.CourseEntity
import kotlin.math.max

/**
 * 课表合并展示模型与算法（纯 Kotlin，无 Compose / Room 依赖，可直接 JUnit 单测）。
 *
 * 坐标约定（与 PeriodGrid 现有渲染一致）：`endSlot` **含尾**（1-4 节 = span 4）；
 * 转 Float 逻辑刻度时 `end = endSlot + 1.0f`，即 "1-4 节" = `start 1.0f .. end 5.0f`（开区间尾）。
 *
 * 遮罩几何全部在此层计算（渲染层只画不算）：
 * - 每门课携带 [BlockCourse.visibleRange]（本周活跃 = 原区间；非活跃 = 对全体活跃区间做减法的 leftover；
 *   被完全覆盖 → null，不渲染但仍在块内、进弹窗列表）
 * - 块级 [MergedCourseBlock.maskRanges] = 全部非活跃 leftover 的**并集**（一次绘制，避免双重变暗）；
 *   整块降级（无任何活跃课）时为空（由整块降级蒙版处理，画局部遮罩反而双重变暗）
 */
data class BlockCourse(
    val course: CourseEntity,
    val isActive: Boolean,                                 // 本周活跃
    val visibleRange: ClosedFloatingPointRange<Float>?     // 可见区间；null = 被本周课程完全覆盖
)

data class MergedCourseBlock(
    val courses: List<BlockCourse>,
    val day: Int,            // 星期几 1-7
    val start: Float,        // 逻辑节次坐标（开区间起点，如 1.0f）
    val end: Float,          // 逻辑节次坐标（开区间尾，如 5.0f 表示覆盖 1-4 节）
    val isConflict: Boolean = false,     // 块内本周活跃课程 ≥2（重叠冲突）
    val isVisualDemoted: Boolean = false,// 块内本周活跃课程 = 0（整块视觉降级）
    val maskRanges: List<ClosedFloatingPointRange<Float>> = emptyList() // 非活跃遮罩区间（已并集）
) {
    /**
     * 这块占几个**节次**（块高 = 这个数 × 行高）。
     *
     * 用途只有一个：课程块在"单节"这种最窄的情形下要省略哪一行
     *（课名两行时省教师、保教室，见 `CourseCardContent` 的 `slots` 参数）。
     * ⚠️ 用 `end - start` 而不是"块内课程数"：合并块的两段课之间可能有间隙，
     * 高度按坐标算才和实际画出来的一样高。
     */
    val slots: Int get() = (end - start).toInt().coerceAtLeast(1)
}

/**
 * 判断课程在指定周是否上课（与 [TimetableViewModel] 的 filteredCourses 过滤逻辑保持一致）：
 * - weekType "odd"/"even" 按奇偶周过滤
 * - weeks 为逗号分隔的具体周次列表，非空且可解析时按列表过滤
 * - week <= 0（未知周）→ 一律视为活跃，避免误降级
 */
fun isCourseActiveInWeek(course: CourseEntity, week: Int): Boolean {
    if (week <= 0) return true
    val isOdd = week % 2 == 1
    val wt = course.weekType
    if (wt == "odd" && !isOdd) return false
    if (wt == "even" && isOdd) return false
    if (course.weeks.isNotEmpty()) {
        val wks = course.weeks.split(",").mapNotNull { it.toIntOrNull() }
        if (wks.isNotEmpty() && week !in wks) return false
    }
    return true
}

/**
 * 贪心合并课程块：
 * 1. 按 (星期, 开始节次, 结束节次) 排序；
 * 2. 与上一块**同天且重叠**（start < 组内最大结束点）→ 并入同一块；
 * 3. 与上一块**同天相邻且同名同色**（start == 组内最大结束点）→ 合并成连续大块（保留两段课程）；
 * 4. 否则新开一块。
 *
 * @param currentWeek 传入 > 0 时计算 isActive/isConflict/isVisualDemoted/maskRanges；
 *                    传默认值 -1 时全部视为活跃（isConflict 即"块内课程数 > 1"，不产生遮罩）。
 */
fun mergeCourses(courses: List<CourseEntity>, currentWeek: Int = -1): List<MergedCourseBlock> {
    val items = courses.map { c ->
        Normalized(
            course = c,
            day = c.dayOfWeek,
            start = c.startSlot.toFloat(),
            end = (c.endSlot + 1).toFloat(), // 含尾约定 → 开区间
            isActive = isCourseActiveInWeek(c, currentWeek)
        )
    }.sortedWith(compareBy({ it.day }, { it.start }, { it.end }))

    val groups = mutableListOf<MutableList<Normalized>>()
    for (item in items) {
        val lastGroup = groups.lastOrNull()
        val groupMaxEnd = lastGroup?.maxOfOrNull { it.end } ?: 0f
        when {
            // 同天重叠 → 并入上一组
            lastGroup != null && lastGroup.first().day == item.day && item.start < groupMaxEnd -> {
                lastGroup.add(item)
            }
            // 同天相邻且与组内首课同名同色 → 合并连续大块（保留两段课程）
            lastGroup != null && lastGroup.first().day == item.day
                && item.start == groupMaxEnd
                && lastGroup.first().course.name == item.course.name
                // 同名且**生效色**相同才合并：直接比 colorIndex（导入时的位置占位值）会让
                // 本该合并的同一门课因占位值不同而断开
                && lastGroup.first().course.effectiveColorIndex == item.course.effectiveColorIndex -> {
                lastGroup.add(item)
            }
            else -> groups.add(mutableListOf(item))
        }
    }
    return groups.map { buildBlock(it) }
}

/** 归一化中间对象 */
private data class Normalized(
    val course: CourseEntity,
    val day: Int,
    val start: Float,
    val end: Float,
    val isActive: Boolean
)

private fun buildBlock(group: List<Normalized>): MergedCourseBlock {
    val day = group.first().day
    val start = group.minOf { it.start }
    val end = group.maxOf { it.end }
    val active = group.filter { it.isActive }
    val activeRanges = active.map { it.start to it.end }
    // 冲突 = 存在时间上重叠的活跃课程对（相邻同名合并块的两段是顺序的，不算冲突）
    val isConflict = hasOverlap(activeRanges)
    val isVisualDemoted = active.isEmpty()

    val blockCourses = group.map { item ->
        val visibleRange = if (item.isActive) {
            item.start..item.end
        } else {
            // 非活跃课程：对全体活跃区间做减法，取最长残留段；完全覆盖 → null
            subtractIntervals(activeRanges, listOf(item.start to item.end))
                .maxByOrNull { it.second - it.first }
                ?.let { it.first..it.second }
        }
        BlockCourse(item.course, item.isActive, visibleRange)
    }

    // 遮罩区间 = 非活跃课程可见区间并集；整块降级时不画局部遮罩（避免双重变暗）
    val maskRanges = if (isVisualDemoted) emptyList()
    else unionRanges(blockCourses.filter { !it.isActive }.mapNotNull { it.visibleRange })

    return MergedCourseBlock(
        courses = blockCourses,
        day = day,
        start = start,
        end = end,
        isConflict = isConflict,
        isVisualDemoted = isVisualDemoted,
        maskRanges = maskRanges
    )
}

/**
 * 区间并集：合并相交或相邻的闭区间，返回有序、互不重叠的区间列表。
 * 用于把多个非活跃课程的 leftover 合并成块级遮罩区，一次绘制避免双重变暗。
 */
fun unionRanges(ranges: List<ClosedFloatingPointRange<Float>>): List<ClosedFloatingPointRange<Float>> {
    if (ranges.isEmpty()) return emptyList()
    val sorted = ranges.sortedBy { it.start }
    val result = mutableListOf<ClosedFloatingPointRange<Float>>()
    var cur = sorted[0]
    for (r in sorted.drop(1)) {
        if (r.start <= cur.endInclusive) {
            cur = cur.start..max(cur.endInclusive, r.endInclusive)
        } else {
            result.add(cur)
            cur = r
        }
    }
    result.add(cur)
    return result
}

/**
 * 区间减法：从 [inactive] 区间中逐段减去 [active] 区间覆盖的部分，返回剩余的"纯非活跃"区间。
 * 供 buildBlock 计算每门非活跃课程的 leftover（visibleRange）。
 */
fun subtractIntervals(
    active: List<Pair<Float, Float>>,
    inactive: List<Pair<Float, Float>>
): List<Pair<Float, Float>> {
    if (inactive.isEmpty()) return emptyList()
    val result = mutableListOf<Pair<Float, Float>>()
    inactive.forEach { (naStart, naEnd) ->
        var segments = listOf(naStart to naEnd)
        active.forEach { (aStart, aEnd) ->
            val next = mutableListOf<Pair<Float, Float>>()
            segments.forEach { (sStart, sEnd) ->
                if (aStart >= sEnd || aEnd <= sStart) {
                    // 无交集，保留原片段
                    next.add(sStart to sEnd)
                } else {
                    // 有交集，切割：左侧 [sStart, aStart] + 右侧 [aEnd, sEnd]
                    if (aStart > sStart) next.add(sStart to aStart)
                    if (aEnd < sEnd) next.add(aEnd to sEnd)
                }
            }
            segments = next
        }
        result.addAll(segments)
    }
    return result
}

/** 判定一组开区间中是否存在重叠（相邻不算重叠）；不足两段恒为 false */
private fun hasOverlap(ranges: List<Pair<Float, Float>>): Boolean {
    if (ranges.size < 2) return false
    val sorted = ranges.sortedBy { it.first }
    var maxEnd = sorted[0].second
    for (r in sorted.drop(1)) {
        if (r.first < maxEnd) return true
        maxEnd = maxOf(maxEnd, r.second)
    }
    return false
}
