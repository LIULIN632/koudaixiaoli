package com.pocket.xiaoli.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.util.CourseColorIndex

/** 课表课程着色规则。 */
enum class CourseColorMode(
    val key: String,
    val label: String,
) {
    COURSE("course", "按课程"),
    TEACHER("teacher", "按教师"),
    WEEK_TYPE("week_type", "按单双周"),
    ;

    companion object {
        fun fromKey(key: String?): CourseColorMode {
            return entries.firstOrNull { it.key == key } ?: COURSE
        }
    }
}

/** 当前着色规则；由 MainActivity 从 AppContainer 注入。 */
val LocalCourseColorMode = compositionLocalOf { CourseColorMode.COURSE }

/**
 * 根据着色规则返回实际使用的调色板下标。
 *
 * - COURSE：**按课名派生**（[CourseEntity.effectiveColorIndex]，同一门课永远同色）；
 *   用户手选过颜色（`colorPinned`）时用他选的那个
 * - TEACHER：教师名稳定哈希，保证同名教师同色
 * - WEEK_TYPE：全周 0 / 单周 1 / 双周 2
 *
 * ⚠️ COURSE 这档**曾经**是"直接用 `colorIndex`"，而那个值在导入时是按
 * `(星期 * 3 + 节次) % 15` 分配的 —— 于是同一门课在周二粉色、周五橙色（2026-10 的 P0）。
 * 现在改由课名派生，改回去等于把这个 bug 放回来。
 */
fun CourseEntity.colorIndexFor(mode: CourseColorMode): Int {
    return when (mode) {
        CourseColorMode.COURSE -> effectiveColorIndex
        CourseColorMode.TEACHER -> {
            if (teacher.isBlank()) effectiveColorIndex else CourseColorIndex.forTeacherName(teacher)
        }
        CourseColorMode.WEEK_TYPE -> when (weekType) {
            "odd" -> 1
            "even" -> 2
            else -> 0
        }
    }
}
