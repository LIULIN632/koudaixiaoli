package com.pocket.xiaoli.desktop.state

import com.pocket.xiaoli.core.academic.ParsedCourse
import com.pocket.xiaoli.desktop.data.Course
import com.pocket.xiaoli.desktop.data.ImportDiagnostics
import com.pocket.xiaoli.core.academic.ZfGradeItem
import com.pocket.xiaoli.desktop.DESKTOP_VERSION
import com.pocket.xiaoli.desktop.data.AppLog
import com.pocket.xiaoli.desktop.data.AutoLaunch
import com.pocket.xiaoli.desktop.data.Exam
import com.pocket.xiaoli.desktop.ui.util.Platform
import com.pocket.xiaoli.desktop.data.GradesCache
import com.pocket.xiaoli.core.academic.ZfTerm
import com.pocket.xiaoli.desktop.logic.answerFromKnowledge
import com.pocket.xiaoli.desktop.logic.gradesToCsv
import com.pocket.xiaoli.desktop.logic.matchQuickAnswer
import com.pocket.xiaoli.desktop.data.Store
import com.pocket.xiaoli.desktop.data.Term
import com.pocket.xiaoli.desktop.logic.AcademicLoginOutcome
import com.pocket.xiaoli.desktop.logic.ImportPlan
import com.pocket.xiaoli.desktop.logic.planImport
import java.time.LocalDate

/**
 * AppState 的「数据可信」动作：教务导入的备份与确认、清占位数据、导入诊断。
 *
 * 拆出来放这里和 `AppStateExams.kt` 同一个理由：800 行门槛，而且这一组动作的共同点是
 * **都会动到磁盘上的数据**，值得集中在一处看。
 */

/** 把登录拿到的课表写进本地数据（整学期替换，自建日程与其他学期保留）。 */
internal fun AppState.applyImport(outcome: AcademicLoginOutcome) {
    if (outcome.courses.isEmpty()) return
    // 覆盖之前先备份：用户发现「少了几门课」往往是半小时以后
    val backup = Store.backup()
    mutate { current ->
        val existing = current.activeTerm
        val startDate = outcome.semesterStart
            ?: existing?.startDate
            ?: LocalDate.now().toString()
        val totalWeeks = outcome.semesterWeeks?.takeIf { it > 0 }
            ?: existing?.totalWeeks
            ?: 20
        val term = existing?.copy(startDate = startDate, totalWeeks = totalWeeks)
            ?: Term(
                id = "term_" + System.currentTimeMillis(),
                name = "教务导入",
                startDate = startDate,
                totalWeeks = totalWeeks,
            )
        val terms = if (existing == null) {
            current.terms + term
        } else {
            current.terms.map { if (it.id == term.id) term else it }
        }
        val kept = current.courses.filter { it.isAgenda || it.termId != term.id }
        val imported = outcome.courses.mapIndexed { index, course -> course.toCourse(term.id, index) }
        current.copy(
            origin = "import",
            terms = terms,
            activeTermId = term.id,
            courses = kept + imported,
            settings = current.settings.copy(activeTermId = term.id),
        )
    }
    importBackupFile = backup?.name
    notify("已导入 ${outcome.courses.size} 门课" + (backup?.let { "（旧数据已备份为 ${it.name}）" } ?: ""))
}

private fun ParsedCourse.toCourse(termId: String, index: Int): Course = Course(
    id = "jw_${termId}_$index",
    termId = termId,
    name = name,
    teacher = teacher,
    room = room,
    dayOfWeek = dayOfWeek,
    startSlot = startSlot,
    endSlot = endSlot,
    weekType = weekType,
    weeks = weeks,
    colorIndex = colorIndex,
    colorPinned = false,
    shortName = shortName,
    note = note,
    kind = "course",
    startMinute = startMinute,
    endMinute = endMinute,
)

/**
 * 教务登录成功后走这里：先算「会动到什么」，有可疑就停在确认框，不静默覆盖。
 *
 * @return true 表示已直接导入；false 表示结果停在 `pendingImport` 等用户确认
 */
internal fun AppState.routeImportAfterLogin(outcome: AcademicLoginOutcome): Boolean {
    val plan = planImport(
        courses = outcome.courses,
        existing = data.activeCourses,
        termId = data.activeTerm?.id ?: "",
        sampleTeachers = Store.sampleTeacherNames(),
    )
    importPlan = plan
    if (plan.needsConfirm) {
        pendingImport = outcome
        return false
    }
    applyImport(outcome)
    return true
}

fun AppState.confirmPendingImport() {
    val outcome = pendingImport ?: return
    pendingImport = null
    applyImport(outcome)
}

fun AppState.cancelPendingImport() {
    pendingImport = null
    importPlan = null
    notify("已取消替换，课表保持原样")
}

/** 清掉内置示例课程（只删 id 来自 seed 的那些，用户手建与教务导入的不动）。 */
fun AppState.clearSampleCourses() {
    val sampleIds = Store.sampleCourseIds()
    val removed = data.courses.count { it.id in sampleIds }
    if (removed == 0) {
        notify("没有内置示例课程可清")
        return
    }
    val backup = Store.backup()
    mutate { current ->
        val left = current.courses.filterNot { it.id in sampleIds }
        current.copy(
            courses = left,
            // 示例课清完且没有别的来源时，不再标成 seed，免得首页一直挂着「示例数据」
            origin = if (current.origin == "seed" && left.isEmpty()) "manual" else current.origin,
        )
    }
    notify("已清掉 $removed 门示例课程" + (backup?.let { "（备份 ${it.name}）" } ?: ""))
}

/** 清掉内置示例待办（同样只删 seed 的那几条）。 */
fun AppState.clearSampleTodos() {
    val sampleIds = Store.sampleTodoIds()
    val removed = data.todos.count { it.id in sampleIds }
    if (removed == 0) {
        notify("没有内置示例待办可清")
        return
    }
    val backup = Store.backup()
    mutate { current ->
        val left = current.todos.filterNot { it.id in sampleIds }
        current.copy(
            todos = left,
            origin = if (current.origin == "seed" && left.isEmpty() && current.courses.isEmpty()) "manual" else current.origin,
        )
    }
    notify("已清掉 $removed 条示例待办" + (backup?.let { "（备份 ${it.name}）" } ?: ""))
}

/** 「我的 → 数据 → 导入诊断」开关：开启后下一次教务请求会落盘原始响应。 */
fun AppState.setImportDiagnostics(enabled: Boolean) {
    setSetting { it.copy(importDiagnostics = enabled) }
    if (!enabled) ImportDiagnostics.end()
}

// ═══════════ 成绩缓存 / 待办批量 / 诊断包 ═══════════

/** 本机知识库 → 快问快答 → 明确说没收录（绝不编）。 */
internal fun AppState.offlineAnswerFor(question: String): String {
    val hits = com.pocket.xiaoli.desktop.logic.Knowledge.search(question, 3)
    answerFromKnowledge(hits, question)?.let { return it.text }
    val qa = matchQuickAnswer(question, com.pocket.xiaoli.desktop.data.Assets.qaBank, limit = 2)
    if (qa.isNotEmpty()) {
        return buildString {
            appendLine("没配 AI Key，但快问快答里有相近的题：")
            appendLine()
            qa.forEach { item ->
                appendLine("· ${item.question}")
                item.answerPoints.take(3).forEach { appendLine("  - $it") }
                if (item.hint.isNotBlank()) appendLine("  提示：${item.hint}")
            }
            appendLine()
            append("要联网再问一轮，去「鲤鱼 → 右上角设置」填 API Key。")
        }.trimEnd()
    }
    return buildString {
        appendLine("没配 AI Key，本机知识库也没收录「${question.take(24)}」。")
        appendLine()
        appendLine("不联网也能用的：课表、待办、校历、考试、校园知识库与快问快答。")
        append("要问更开放的问题，去「鲤鱼 → 右上角设置」填 Base URL 与 API Key（只存本机）。")
    }.trimEnd()
}

/** 批量清掉已完成待办（清前备份，与清示例数据同一口径）。 */
internal fun AppState.clearCompletedTodos() {
    val done = data.todos.count { it.done }
    if (done == 0) {
        notify("没有已完成的待办可清")
        return
    }
    val backup = Store.backup()
    mutate { current -> current.copy(todos = current.todos.filterNot { it.done }) }
    notify("已清掉 $done 条已完成待办" + (backup?.let { "（备份 ${it.name}）" } ?: ""))
}

/** 导出脱敏诊断包（日志 + 数据形状 + 导入诊断样本）。 */
internal fun AppState.exportDiagnosticBundle(): String {
    val file = AppLog.exportBundle(com.pocket.xiaoli.desktop.DESKTOP_VERSION)
        ?: return "导出失败：写不了数据目录（${AppLog.logDir.absolutePath}）"
    AppLog.info("App", "诊断包已导出：${file.absolutePath}")
    return "诊断包已导出：${file.absolutePath}（API Key 已脱敏，可直接发给我）"
}

/** 按学期存一份成绩缓存（同一学期只留最新一份）。 */
internal fun AppState.cacheGrades(term: ZfTerm, items: List<com.pocket.xiaoli.core.academic.ZfGradeItem>) {
    mutate { current ->
        val others = current.gradesCache.filterNot { it.xnm == term.xnm && it.xqm == term.xqm }
        current.copy(
            gradesCache = others + GradesCache(
                termLabel = term.label,
                xnm = term.xnm,
                xqm = term.xqm,
                savedAt = System.currentTimeMillis(),
                items = items,
            )
        )
    }
}
/** 导出当前显示的成绩为 CSV；返回给用户的文案。 */
/** 页面上正在显示的成绩：实时结果优先，没登录时退到最新一份缓存。 */
/** 缓存列表按查询时间倒序：最近查的那学期排第一，符合"我想看现在"的直觉。 */
val AppState.cachedTermsSorted: List<GradesCache> get() = cachedGradeTerms.sortedByDescending { it.savedAt }

/** 缓存视图当前选中的那一份。 */
val AppState.currentCachedTerm: GradesCache? get() = cachedTermsSorted.getOrNull(cachedGradeIndex)

val AppState.displayedGrades: List<ZfGradeItem>
    get() = grades.ifEmpty { currentCachedTerm?.items.orEmpty() }

/** 显示中的成绩属于哪个学期（缓存视图也要有学期名，导出文件名靠它）。 */
val AppState.displayedGradesTerm: String
    get() = gradeTerms.getOrNull(gradeTermIndex)?.label
        ?: currentCachedTerm?.termLabel
        ?: "成绩"

/** 现在看到的是缓存而不是实时结果 —— 界面据此挂「缓存于」和清除缓存。 */
val AppState.gradesAreCached: Boolean get() = academicSession == null && displayedGrades.isNotEmpty()

internal fun AppState.exportGradesCsv(): String {
    val items = displayedGrades
    if (items.isEmpty()) return "没有可导出的成绩（先登录查询一个学期）"
    val csv = gradesToCsv(items)
    val name = "成绩-${displayedGradesTerm}.csv"
    return runCatching {
        val path = Platform.saveTextFile(name, csv)
        if (path == null) "已取消导出" else "已导出 ${items.size} 条：$path"
    }.getOrElse { "导出失败：${it.message ?: "未知错误"}" }
}
internal fun AppState.clearGradesCache() {
    mutate { current -> current.copy(gradesCache = emptyList()) }
    cachedGradeIndex = 0
    notify("成绩缓存已清空（下次要重新登录查询）")
}

/**
 * 「我的 → 数据 → 开机自启」开关。
 *
 * 打开才写 `HKCU\...\Run`，关掉就删掉；注册表失败时**不把设置标成已开启** ——
 * 界面说的必须和机器上的状态一致。
 */
internal fun AppState.setAutoLaunch(enabled: Boolean): String {
    val message = if (enabled) AutoLaunch.enable() else AutoLaunch.disable()
    val actuallyOn = if (enabled) AutoLaunch.isEnabled() else false
    setSetting { it.copy(autoLaunch = actuallyOn) }
    return message
}
