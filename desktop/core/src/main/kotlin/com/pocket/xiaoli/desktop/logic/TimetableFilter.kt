package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.Course

/**
 * 课表页的老师 / 教室筛选。
 *
 * 桌面端的「搜索」不是跳到一个结果页，而是**在当前视图里把不相关的弱化掉**：
 * 网格与列表都还在，一眼能看出「这个老师占了我哪些格子」，
 * 这与「非本周课程弱化」用的是同一种表现手法，不需要用户学新交互。
 */
data class TimetableFilter(
    val teacher: String? = null,
    val room: String? = null,
) {
    val active: Boolean get() = !teacher.isNullOrBlank() || !room.isNullOrBlank()

    /** 两个条件同时选中时要**都满足**（"李慧敏在三教 A-302 的课"）。 */
    fun matches(course: Course): Boolean {
        if (!teacher.isNullOrBlank() && !course.teacher.trim().equals(teacher, ignoreCase = true)) return false
        if (!room.isNullOrBlank() && !course.room.trim().equals(room, ignoreCase = true)) return false
        return true
    }

    fun matchCount(courses: List<Course>): Int = courses.count { matches(it) }

    /** 给筛选条显示的一句话标签。 */
    val label: String
        get() = listOfNotNull(
            teacher?.takeIf { it.isNotBlank() }?.let { "老师 $it" },
            room?.takeIf { it.isNotBlank() }?.let { "教室 $it" },
        ).joinToString(" · ")
}
