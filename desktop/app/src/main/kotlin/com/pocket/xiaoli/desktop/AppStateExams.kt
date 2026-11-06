package com.pocket.xiaoli.desktop.state

import com.pocket.xiaoli.desktop.data.Exam
import com.pocket.xiaoli.desktop.logic.parseExamNotice

/**
 * AppState 的考试动作（状态字段仍留在 AppState 里，这里只是把写入口拆出去）。
 *
 * 拆文件的理由与 `Campus.kt` 拆成四份一样：800 行门槛。之所以用扩展函数而不是另建一个
 * holder 类：UI 侧的调用点（`state.parseExams()`）一个字都不用改，读代码的人也不需要
 * 在两个对象之间跳转。
 *
 * 链路是「粘贴通知 → 规则解析 → 用户核对 → 才落盘」：教务没有考试接口，
 * 而**错误的考试提醒比没有提醒更糟**（用户会按错的时间走错考场），所以确认这一步不能省。
 */

/** 展开/收起粘贴框；收起时丢掉未确认的解析结果。 */
fun AppState.toggleExamEditor() {
    examEditorOpen = !examEditorOpen
    if (!examEditorOpen) clearExamParse()
}

fun AppState.onExamNoticeChange(text: String) {
    examNotice = text
    examMessage = null
}

/** 规则解析（离线、不要 Key、每次结果一致）；重复项默认不勾选。 */
fun AppState.parseExams() {
    if (examNotice.isBlank()) {
        examMessage = "先把通知内容粘进来"
        return
    }
    val result = parseExamNotice(examNotice, data.exams)
    examPending = result.pending
    examScanned = result.scanned
    examMessage = when {
        result.pending.isEmpty() -> "没能从这段文字里认出考试安排（扫了 ${result.scanned} 行）"
        result.pending.all { it.duplicate } -> "识别到的 ${result.pending.size} 场都已在列表里"
        else -> null
    }
}

fun AppState.toggleExamPending(index: Int) {
    examPending = examPending.mapIndexed { i, pending ->
        if (i == index) pending.copy(selected = !pending.selected) else pending
    }
}

fun AppState.selectAllExams(selected: Boolean) {
    examPending = examPending.map { it.copy(selected = selected && !it.duplicate) }
}

fun AppState.clearExamParse() {
    examPending = emptyList()
    examScanned = 0
    examMessage = null
}

/** 把勾选的考试写入本地数据（备份仍是那一个 JSON 文件）。 */
fun AppState.confirmExams() {
    val selected = examPending.filter { it.selected }.map { it.schedule }
    if (selected.isEmpty()) {
        examMessage = "先勾选要添加的考试"
        return
    }
    val stamp = System.currentTimeMillis()
    mutate { current ->
        current.copy(
            exams = current.exams + selected.mapIndexed { index, schedule ->
                Exam(id = "exam_${stamp}_$index", schedule = schedule)
            },
        )
    }
    clearExamParse()
    examEditorOpen = false
    examNotice = ""
    notify("已添加 ${selected.size} 场考试")
}

/** 考完了 / 记错了：勾掉它，提醒就不再念这场。 */
fun AppState.toggleExam(id: String) {
    mutate { current ->
        current.copy(exams = current.exams.map { if (it.id == id) it.copy(completed = !it.completed) else it })
    }
}

fun AppState.deleteExam(id: String) {
    mutate { current -> current.copy(exams = current.exams.filter { it.id != id }) }
}

/** 仅验收用（`--demo=exam`）：把示例通知填进粘贴框并解析，**不写数据文件**。 */
fun AppState.seedExamNotice(notice: String) {
    examEditorOpen = true
    examNotice = notice
    parseExams()
}
