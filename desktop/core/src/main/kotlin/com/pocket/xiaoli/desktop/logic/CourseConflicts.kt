package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.Course

/**
 * 课程冲突检测与「老师/教室」候选。
 *
 * 判据与课表页画格子用的是同一套（`courseOccursInWeek`），所以不会出现
 * 「课表上看着撞了，编辑器说不撞」这种对不上。
 */
data class CourseConflict(
    val other: Course,
    /** 两边都实际上课的周次（最多给 6 个，够写清提示） */
    val sharedWeeks: List<Int>,
) {
    val summary: String
        get() {
            val slots = if (other.startSlot == other.endSlot) "第${other.startSlot}节" else "第${other.startSlot}-${other.endSlot}节"
            val weeks = sharedWeeks.take(6).joinToString(",")
            return "${other.name} · 周${other.dayOfWeek} $slots${if (weeks.isEmpty()) "" else "（重合周 $weeks）"}"
        }
}

/**
 * 候选课程与已有课程的时间重叠。
 *
 * 只比**同一学期**（`termId` 相同，或任意一方没标学期 —— 老数据不标）；
 * 自建日程（agenda）用的是分钟区间，节次区间不可比，交给 [findTimeClashesByMinute]。
 */
fun findConflicts(
    candidate: Course,
    existing: List<Course>,
    totalWeeks: Int = 20,
): List<CourseConflict> {
    if (candidate.isAgenda) return emptyList()
    return existing
        .filter { it.id != candidate.id }
        .filter { !it.isAgenda }
        .filter { it.termId == candidate.termId || it.termId.isBlank() || candidate.termId.isBlank() }
        .filter { it.dayOfWeek == candidate.dayOfWeek }
        .filter { it.startSlot <= candidate.endSlot && candidate.startSlot <= it.endSlot }
        .mapNotNull { other ->
            val shared = (1..totalWeeks.coerceAtLeast(1)).filter { week ->
                courseOccursInWeek(candidate, week, totalWeeks) && courseOccursInWeek(other, week, totalWeeks)
            }
            if (shared.isEmpty()) null else CourseConflict(other, shared)
        }
}

/** 自建日程之间按分钟区间比（它们没有节次概念）。 */
fun findAgendaClashes(candidate: Course, existing: List<Course>): List<CourseConflict> {
    if (!candidate.isAgenda || candidate.startMinute < 0) return emptyList()
    return existing
        .filter { it.id != candidate.id }
        .filter { it.isAgenda && it.startMinute >= 0 }
        .filter { it.termId == candidate.termId || it.termId.isBlank() || candidate.termId.isBlank() }
        .filter { it.dayOfWeek == candidate.dayOfWeek }
        .filter { it.startMinute < candidate.endMinute && candidate.startMinute < it.endMinute }
        .map { CourseConflict(it, emptyList()) }
}

/**
 * 老师 / 教室的候选值（按出现次数排，供编辑器下面的一键填）。
 *
 * 为什么要这个：正方导进来的课名与老师名常常带空格、全角括号、课程代码，手打一遍既慢又容易
 * 打成另一个值，之后按老师搜就搜不到。给候选、点一下就复用同一个写法。
 */
fun suggestValues(courses: List<Course>, pick: (Course) -> String, limit: Int = 8): List<String> =
    courses.map { pick(it).trim() }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { it.key }
