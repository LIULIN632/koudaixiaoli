package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.academic.ParsedCourse
import com.pocket.xiaoli.desktop.data.Course

/**
 * 教务导入的「替换计划」：先把会动到什么算清楚，再决定要不要让用户确认。
 *
 * 之所以要这一步：导入是**整学期覆盖**（`importAcademic` 会换掉该学期所有非日程课程）。
 * 学校部署五花八门，接口分页或学期参数不对时会「导入成功但只有 2 门课」——
 * 静默覆盖掉用户原本完整的课表，比导入失败严重得多。
 */
data class ImportPlan(
    val willImport: Int,
    val willReplace: Int,
    val willKeep: Int,
    val warnings: List<String>,
) {
    val needsConfirm: Boolean get() = warnings.isNotEmpty()
    val summary: String get() = "将导入 $willImport 门 · 覆盖 $willReplace 门 · 保留 $willKeep 条"
}

/**
 * @param sampleTeachers 内置示例数据的老师名（用来识别「导入进来的其实还是占位内容」）
 */
fun planImport(
    courses: List<ParsedCourse>,
    existing: List<Course>,
    termId: String,
    sampleTeachers: Set<String> = emptySet(),
): ImportPlan {
    val kept = existing.filter { it.isAgenda || it.termId != termId }
    val replaced = existing.size - kept.size
    val warnings = buildList {
        if (courses.isEmpty()) {
            add("教务没有返回任何课程，替换后这一学期会空掉。")
        } else {
            if (courses.size <= 4) {
                add("只认出 ${courses.size} 门课，可能不完整（正方课表常分页，或学年学期参数不对）。")
            }
            if (sampleTeachers.isNotEmpty() && courses.all { it.teacher.isNotBlank() && it.teacher in sampleTeachers }) {
                add("老师名与内置示例数据完全一致，这批课可能是占位内容。")
            }
            if (replaced > courses.size) {
                add("现有 $replaced 门课会被 ${courses.size} 门替换，数量变少。")
            }
        }
    }
    return ImportPlan(
        willImport = courses.size,
        willReplace = replaced,
        willKeep = kept.size,
        warnings = warnings,
    )
}
