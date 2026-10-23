package com.pocket.xiaoli.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.data.entity.toExamEntities
import com.pocket.xiaoli.data.exam.ExamNoticeParser
import com.pocket.xiaoli.data.exam.ExamSchedule
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.pocket.xiaoli.data.agent.AssistantLlmClient
import com.pocket.xiaoli.data.exam.ExamLlmExtractor

/**
 * 一条待确认的考试（解析结果 + 界面的勾选态）。
 *
 * 把 `selected` 放在这里而不是界面里：界面重组频繁，状态必须由 ViewModel 持有，
 * 否则滚动/旋转后会丢勾选。
 */
data class PendingExam(
    val schedule: ExamSchedule,
    val selected: Boolean = true,
    /** 与库里已有记录重复（重复项默认不勾选） */
    val duplicate: Boolean = false,
)

data class ExamUiState(
    /** 用户粘贴的通知原文 */
    val noticeText: String = "",
    /** 解析出的候选考试（空表示还没解析或没解析出） */
    val pending: List<PendingExam> = emptyList(),
    /** 已解析过一次（用于区分"还没点解析"与"解析了但没结果"） */
    val parsed: Boolean = false,
    val scanned: Int = 0,
    /** 落库后的提示 */
    val savedCount: Int = 0,
    val message: String? = null,
    /** AI 兜底正在调用（界面显示加载态，避免用户重复点击） */
    val aiLoading: Boolean = false,
    /** 已尝试过 AI 兜底（用于决定是否还显示「让 AI 试试」） */
    val aiTried: Boolean = false,
    /** 当前课表下已有的考试 */
    val existing: List<ExamEntity> = emptyList(),
) {
    val selectedCount: Int get() = pending.count { it.selected }
    val pendingCount: Int get() = existing.count { !it.completed }
}

/**
 * 考试安排：从通知文本解析 → 预览确认 → 写入待办。
 *
 * ## 为什么必须"先预览再确认"
 *
 * 通知格式千差万别，规则解析**无法保证 100% 准确**。直接写入待办的话，
 * 一条解析错的考试会变成**错误的提醒** —— 用户可能因此走错考场，
 * 这比"没有提醒"严重得多。
 *
 * 所以流程刻意设计成：解析 → 列出来让用户过一眼 → 用户改选后确认 → 才写库。
 */
class ExamScheduleViewModel(
    private val repository: CourseRepository,
    private val settings: AppContainer,
) : ViewModel() {

    /** LLM 兜底抽取器。客户端是轻量的（内部 OkHttp 懒加载），按需自建即可。 */
    private val llmExtractor = ExamLlmExtractor(AssistantLlmClient())

    private val _state = MutableStateFlow(ExamUiState())
    val state: StateFlow<ExamUiState> = _state.asStateFlow()

    /** 是否已配置 API Key（决定要不要显示「让 AI 试试」按钮）。 */
    val aiAvailable: Boolean get() = settings.assistantLlmConfig.apiKey.isNotBlank()

    init {
        // 跟随当前课表：切课表后已有考试列表应随之变化
        viewModelScope.launch {
            settings.activeSchemeId.collect { schemeId ->
                repository.getExamsByScheme(schemeId).collect { list ->
                    _state.update { it.copy(existing = list) }
                }
            }
        }
    }

    fun onNoticeChange(text: String) = _state.update { it.copy(noticeText = text, message = null) }

    /** 规则解析。 */
    fun parse() {
        val text = _state.value.noticeText
        val result = ExamNoticeParser.parse(text)

        viewModelScope.launch {
            // 与库里已有的比对，标出重复项（重复项默认不勾选，避免写两遍）
            val existingKeys = repository.existingExamKeys().toSet()
            val pending = result.exams.map { s ->
                val dup = "${s.courseName}|${s.date}|${s.time}" in existingKeys
                PendingExam(schedule = s, selected = !dup, duplicate = dup)
            }
            _state.update {
                it.copy(
                    pending = pending,
                    parsed = true,
                    scanned = result.scanned,
                    aiTried = false,
                    message = when {
                        result.exams.isEmpty() -> "没能从这段文字里认出考试安排"
                        pending.all { p -> p.duplicate } -> "识别到的考试都已在列表中"
                        else -> null
                    },
                )
            }
        }
    }

    /**
     * LLM 兜底解析。
     *
     * ## 为什么是「用户主动触发」而不是自动兜底
     *
     * 自动兜底意味着**每次规则解析失败都会联网调用一次**（要花钱、有延迟），
     * 而大多数失败其实是"用户粘错了内容"。让用户点一下再调，
     * 既省费用，也让"这要联网"这件事对用户是可见的。
     *
     * ## API Key 未配置时
     *
     * 明确告知**去哪配**，而不是笼统失败 —— 用户需要知道下一步做什么。
     */
    fun parseWithAi() {
        val text = _state.value.noticeText
        if (text.isBlank()) return

        if (!aiAvailable) {
            _state.update {
                it.copy(
                    aiTried = true,
                    message = "还没配置 AI 接口。去「鲤鱼 → 右上角设置」填 Base URL 与 API Key 后可用。",
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(aiLoading = true, message = "正在让 AI 识别……") }
            val extracted = llmExtractor.extract(settings.assistantLlmConfig, text)

            if (extracted == null) {
                _state.update {
                    it.copy(
                        aiLoading = false,
                        aiTried = true,
                        message = "AI 也没能识别出考试安排。可以检查一下通知里是否包含日期。",
                    )
                }
                return@launch
            }

            val existingKeys = repository.existingExamKeys().toSet()
            val pending = extracted.map { s ->
                val dup = "${s.courseName}|${s.date}|${s.time}" in existingKeys
                PendingExam(schedule = s, selected = !dup, duplicate = dup)
            }
            _state.update {
                it.copy(
                    pending = pending,
                    parsed = true,
                    aiLoading = false,
                    aiTried = true,
                    message = "AI 识别到 ${pending.size} 场考试，请核对后确认。",
                )
            }
        }
    }

    fun toggle(index: Int) = _state.update { s ->
        s.copy(pending = s.pending.mapIndexed { i, p ->
            if (i == index) p.copy(selected = !p.selected) else p
        })
    }

    fun selectAll(selected: Boolean) = _state.update { s ->
        s.copy(pending = s.pending.map { p -> p.copy(selected = selected && !p.duplicate) })
    }

    /** 把勾选的考试写入待办。 */
    fun confirm() {
        val selected = _state.value.pending.filter { it.selected }.map { it.schedule }
        if (selected.isEmpty()) {
            _state.update { it.copy(message = "请先勾选要添加的考试") }
            return
        }
        viewModelScope.launch {
            val schemeId = settings.activeSchemeId.value
            repository.insertExams(selected.toExamEntities(schemeId))
            // 新增考试后重排提醒 —— 否则新导入的考试不会被提醒
            settings.rescheduleExamReminders()
            _state.update {
                it.copy(
                    pending = emptyList(),
                    noticeText = "",
                    parsed = false,
                    savedCount = selected.size,
                    message = "已添加 ${selected.size} 场考试到待办",
                )
            }
        }
    }

    fun toggleCompleted(exam: ExamEntity) {
        viewModelScope.launch {
            repository.setExamCompleted(exam.id, !exam.completed)
            settings.rescheduleExamReminders()
        }
    }

    fun delete(exam: ExamEntity) {
        viewModelScope.launch {
            repository.deleteExam(exam.id)
            settings.rescheduleExamReminders()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, savedCount = 0) }

    class Factory(
        private val repository: CourseRepository,
        private val settings: AppContainer,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExamScheduleViewModel(repository, settings) as T
    }
}
