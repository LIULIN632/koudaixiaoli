package com.pocket.xiaoli.ui.treasurebox

import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicTypeInfo

/**
 * 课程存档汇总：门数 / 总学分 / 平均分 / 平均绩点 / 通过率。
 * 纯函数，便于单测；空列表返回全 0 / null。
 */
data class SavedGradeStats(
    val count: Int = 0,
    val totalCredit: Float = 0f,
    val averageScore: Float? = null,
    val averageGpa: Float? = null,
    /** 0..1；无有效分数时为 null */
    val passRate: Float? = null,
) {
    companion object {
        fun of(grades: List<SavedGradeEntity>): SavedGradeStats {
            val credits = grades.mapNotNull { it.credits.trim().toFloatOrNull() }
            val scores = grades.mapNotNull { it.totalScore.trim().toFloatOrNull() }
            val gpas = grades.mapNotNull { it.gpa.trim().toFloatOrNull() }
            return SavedGradeStats(
                count = grades.size,
                totalCredit = credits.sum(),
                averageScore = scores.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                averageGpa = gpas.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                passRate = scores.takeIf { it.isNotEmpty() }
                    ?.let { list -> list.count { it >= 60f } * 1f / list.size },
            )
        }
    }
}

/** 学分进度：已获 / 要求；[ratio] 在要求缺失或为 0 时为 null。 */
data class CreditProgress(
    val earned: Float = 0f,
    val required: Float = 0f,
) {
    val ratio: Float?
        get() = if (required > 0f) (earned / required).coerceIn(0f, 1f) else null

    companion object {
        /** 各类型学分求和；全空返回 null，调用方据此隐藏进度条。 */
        fun of(types: List<AcademicTypeInfo>): CreditProgress? {
            if (types.isEmpty()) return null
            val earned = types.mapNotNull { it.earnedCredit.trim().toFloatOrNull() }.sum()
            val required = types.mapNotNull { it.requiredCredit.trim().toFloatOrNull() }.sum()
            if (earned <= 0f && required <= 0f) return null
            return CreditProgress(earned = earned, required = required)
        }
    }
}

/**
 * 类型卡第二行的文案：**门次**与**课程门数**的完成情况。
 *
 * 页面原文一行两件事（2026-10 用户贴出的真实页面）：
 *
 * ```
 * 思想政治类课程  要求学分:18.0 获得学分:17.0  未获得学分:1.0
 *  要求门次:14 到达要求:12  未达到要求:2 共（14）门 通过（12）门
 * ```
 *
 * 学分（18.0/17.0）说的是**学分**够不够，门次（14/12）说的是**门数**够不够 ——
 * 两者会打架（学分满而门次不够，或反过来），所以必须分开显示，不能只看一个。
 *
 * 规则：
 * - `要求门次` 缺失（如「其他选修课程」只有 `共（2）门`）就不提门次；
 * - `未达到要求` 为 0 **不显示**「还差」—— 0 是"够了"这个结论，不是缺数据；
 * - 全都没有 → 空串，调用方整行不渲染（老部署的页面没有这一段）。
 *
 * [includeShortfall] 给"'还差'已经单独做成药丸"的调用方用（培养方案树的类型行）：
 * 那里再把「还差 N 门」写进这一行就是**同一句话说两遍**（渲图抓到的）。
 *
 * 纯函数（在 `StudyStats.kt` 与 `CreditProgress` 同类），便于单测。
 */
fun AcademicTypeInfo.countSummary(includeShortfall: Boolean = true): String {
    val required = requiredCount.toIntOrNull()
    val reached = reachedCount.toIntOrNull()
    val missed = missedCount.toIntOrNull()
    val total = totalCourses.toIntOrNull()
    val passed = passedCourses.toIntOrNull()
    return buildList {
        if (required != null) add("门次 $required")
        if (reached != null) add("到达 $reached")
        if (includeShortfall && missed != null && missed > 0) add("还差 $missed 门")
        if (total != null) add("课程通过 ${passed ?: 0}/$total 门")
    }.joinToString(" · ")
}

/**
 * 一门课在**修读进度**上属于哪一档 —— 决定它在卡片上用哪种颜色（纯函数，便于单测）。
 *
 * ## 为什么单独抽出来
 *
 * 2026-10 用户要求「正在修课和未修课也用不同颜色隔开」。查代码发现旧实现是按
 * `status.contains("在读")` 判的，而解析层给的词是「**在修**」（学校码表 `XDZT=1` 的 i18n 值）
 * —— 于是"正在修"的课落进 `else` 分支，跟"未修"一样都是**灰色** ✗✗。
 * 颜色写死在 `when` 里、判据又是散落的字符串，这个 bug 光看图发现不了（两行都是灰的，
 * 看着像"还没修"，谁也不觉得是错的）。
 *
 * 现在判据收敛到这里一处，颜色在调用方按档取令牌 —— 加一种状态只要改这里 + 它的测试。
 */
enum class CourseStatusTone {
    /** 已修 / 通过 / 替代认定 —— 绿 */
    PASSED,

    /** 在修（本学期正在读）—— 金（与总进度同一个令牌） */
    IN_PROGRESS,

    /** 未过 —— 红 */
    FAILED,

    /** 未修 / 状态未知 —— 中性灰 */
    UNTAKEN,
}

fun AcademicCourseItem.statusTone(): CourseStatusTone {
    val s = status.trim()
    return when {
        s.contains("已修") || s.contains("通过") ||
            s.contains("替代") || s.contains("认定") -> CourseStatusTone.PASSED
        // ⚠️ 「在修」是解析层实际给的词；「在读」是教务另一种说法，两种都认
        //（旧实现只认后者 ⇒ 在修的课显示成灰色，与未修分不开）。
        s.contains("在修") || s.contains("在读") -> CourseStatusTone.IN_PROGRESS
        s.contains("未过") || s.contains("不及格") -> CourseStatusTone.FAILED
        else -> CourseStatusTone.UNTAKEN
    }
}

// ── 筛选与搜索（2026-10：23 个类型 + 上百门课，页面要能回答"我还差什么"） ──

/**
 * 课程明细的筛选口径。
 *
 * 前三个是**课程级**（全部 / 未修·未过 / 已通过），[SHORT_CREDIT] 是**类型级**
 * （培养方案里哪一块还没修满）—— 两者的命中判定不同，见 [filterAcademicView]。
 */
enum class CourseFilter(val label: String) {
    ALL("全部"),
    UNTAKEN("未修 / 未过"),
    PASSED("已通过"),
    SHORT_CREDIT("缺学分"),
}

/**
 * 这门课是不是"还没修"。
 *
 * - 状态写着 `未修` / `未过` → 是；
 * - 状态是别的文字（`已修` / `在修`）→ 不是；
 * - **状态与三样成绩全空** → 也算"还没修"：别的部署可能整列没有状态，
 *   而"没有成绩也没有状态"在教务语义里就是还没读（修过的课必然有成绩或状态）。
 */
fun AcademicCourseItem.isUntaken(): Boolean {
    if (status.contains("未修") || status.contains("未过")) return true
    if (status.isNotBlank()) return false
    return maxGrade.isBlank() && score.isBlank() && gradePoint.isBlank()
}

/**
 * 这门课是不是"已经过了"。
 *
 * 判定顺序刻意是"状态 → 数字成绩 → 文字成绩"：状态是教务自己下的结论，优先级最高。
 * ⚠️ 中间那步"先排掉带「未」的状态"不能省：`未过` 的课往往有补考成绩（≥60），
 * 少了它会被后面的数字成绩判成"已通过" —— 而"未过"正是用户最需要看到的。
 */
fun AcademicCourseItem.isPassed(): Boolean {
    if (status.contains("已修") || status.contains("通过")) return true
    if (status.contains("未")) return false
    score.toFloatOrNull()?.let { return it >= 60f }
    maxGrade.toFloatOrNull()?.let { return it >= 60f }
    val words = listOf("及格", "合格", "良好", "中等", "优秀", "通过", "免修")
    return words.any { score.contains(it) || maxGrade.contains(it) }
}

/** 这个类型是不是"还缺学分"（未获得学分 > 0） */
fun AcademicTypeInfo.isShortOfCredit(): Boolean =
    missedCredit.trim().toFloatOrNull()?.let { it > 0f } == true

/**
 * 用**已经取回的明细行**填上「共 N 门 / 通过 N 门」。
 *
 * ## 为什么要在客户端数
 *
 * 源 HTML 里这两处是占位：`<font name='zms'>0</font>` / `<font name='tgms'>0</font>`，
 * 学校页面上的数字是**前端数出来的**（`cxXsxyqkIndex.js`）：
 *
 * ```js
 * $("#li"+id).find("[name='zms']").html($("tr","#tbody"+id).length);   // 共几门 = 明细行数
 * if (xdzt=='4' || xdzt=='5' || xdzt=='6') { tgms += 1 }               // 通过 = 这几种状态
 * ```
 *
 * 明细行我们本来就全取了（每组发过一次请求），所以这两个数字**不用额外请求**就能算出来，
 * 而且口径与学校页面**逐字一致** —— 2026-10 拿「另存为完整网页」的渲染结果当标准答案核对过：
 * 「思想政治类课程 共（14）门 通过（12）门」。
 *
 * ⚠️ 「通过」用**状态**判，不是分数：学校就是这么数的。
 * 只有状态列缺失的部署才退回按分数判（那种部署下学校页面这一格也是空的）。
 */
fun AcademicTypeInfo.withGateCounts(courses: List<AcademicCourseItem>): AcademicTypeInfo {
    if (courses.isEmpty()) return this
    val passed = courses.count { c ->
        if (c.status.isNotBlank()) {
            // 与 `ZhengfangAcademicApi.statusLabel` 的码表同源：4/21=已修，5=校内被替代课程，6=校内课程替代
            c.status == "已修" || c.status == "校内被替代课程" || c.status == "校内课程替代"
        } else {
            c.isPassed()
        }
    }
    return copy(totalCourses = courses.size.toString(), passedCourses = passed.toString())
}

/** 单门课是否命中当前筛选（[CourseFilter.SHORT_CREDIT] 是类型级，课程一律放行） */
fun courseMatches(item: AcademicCourseItem, filter: CourseFilter, query: String): Boolean {
    val q = query.trim()
    if (q.isNotEmpty() && !item.courseName.contains(q, true) && !item.typeName.contains(q, true)) {
        return false
    }
    return when (filter) {
        CourseFilter.ALL, CourseFilter.SHORT_CREDIT -> true
        CourseFilter.UNTAKEN -> item.isUntaken()
        CourseFilter.PASSED -> item.isPassed()
    }
}

/** 每个筛选项能筛出多少（未修 / 已通过 = 课程数；缺学分 = 类型数），给 chip 上的角标用 */
fun filterBadgeCounts(
    types: List<AcademicTypeInfo>,
    courses: List<AcademicCourseItem>,
): Map<CourseFilter, Int> = mapOf(
    CourseFilter.UNTAKEN to courses.count { it.isUntaken() },
    CourseFilter.PASSED to courses.count { it.isPassed() },
    CourseFilter.SHORT_CREDIT to types.count { it.isShortOfCredit() },
)

/**
 * 筛选 + 搜索的**唯一实现**（纯函数，便于单测）。
 *
 * | 命中方式 | 结果 |
 * |---|---|
 * | 类型名 / 类型级筛选命中 | 该类型留下，**它名下的课按课程级筛选留下** |
 * | 某门课命中 | 只留那门课，它所属类型留下 |
 * | 都没命中 | 不显示 |
 *
 * 另外**把命中类型的祖先一并带上**：培养方案是四级树，只留叶子会读不通
 * （屏幕上出现一堆缩进很深的行，却看不到它们属于哪个平台）。
 */
fun filterAcademicView(
    types: List<AcademicTypeInfo>,
    courses: List<AcademicCourseItem>,
    filter: CourseFilter,
    query: String,
): Pair<List<AcademicTypeInfo>, List<AcademicCourseItem>> {
    val q = query.trim()
    if (filter == CourseFilter.ALL && q.isEmpty()) return types to courses

    val typeById = types.associateBy { it.id }
    val coursesByType = courses.groupBy { it.typeId }
    val keptTypeIds = LinkedHashSet<String>()
    val keptCourses = LinkedHashSet<AcademicCourseItem>()

    for (type in types) {
        val own = coursesByType[type.id].orEmpty()
        if (filter == CourseFilter.SHORT_CREDIT) {
            // ⚠️ 类型级筛选**不能**走 `courseMatches`：那个口径对课程一律放行，
            // 于是"有课的都能过" —— 这块不缺学分也会被留下（单测抓到过）。
            if (!type.isShortOfCredit()) continue
            val matching = if (q.isEmpty()) {
                own
            } else {
                own.filter { it.courseName.contains(q, true) || type.name.contains(q, true) }
            }
            if (q.isEmpty() || matching.isNotEmpty()) {
                keptTypeIds += type.id
                keptCourses += matching
            }
            continue
        }
        val typeHit = q.isNotEmpty() && type.name.contains(q, true)
        if (typeHit) {
            keptTypeIds += type.id
            keptCourses += own.filter { courseMatches(it, filter, "") }
        } else {
            val hit = own.filter { courseMatches(it, filter, q) }
            if (hit.isNotEmpty()) {
                keptTypeIds += type.id
                keptCourses += hit
            }
        }
    }

    val withAncestors = LinkedHashSet(keptTypeIds)
    for (id in keptTypeIds) {
        var parentId = typeById[id]?.parentId
        var guard = 0
        while (!parentId.isNullOrBlank() && guard < 8) {
            withAncestors += parentId
            parentId = typeById[parentId]?.parentId
            guard++
        }
    }
    return types.filter { it.id in withAncestors } to keptCourses.toList()
}
