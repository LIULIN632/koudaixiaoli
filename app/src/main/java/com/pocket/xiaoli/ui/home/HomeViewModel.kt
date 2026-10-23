package com.pocket.xiaoli.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.MockScenario
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.HomeworkEntity
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.data.holiday.HolidayDay
import com.pocket.xiaoli.util.HolidayCalendar
import com.pocket.xiaoli.util.findUpcomingCourse
import com.pocket.xiaoli.util.isCourseInWeek
import com.pocket.xiaoli.util.currentWeekFrom
import com.pocket.xiaoli.util.parseSemesterStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.pocket.xiaoli.data.entity.ExamEntity

data class HomeCourse(
    val course: CourseEntity,
    val startTime: String,
    val endTime: String,
    val dayOffset: Int = 0,
    val isOngoing: Boolean = false,
    val isFinished: Boolean = false
)

data class HomeUiState(
    // 原 `nickname: String = "同学"` 已删除（2026-10）：「昵称」整个概念随身份卡改版一起删，
    // 而首页从来没有渲染过它（只在状态里躺着），所以这里没有替换项。
    val school: String = "",
    val week: Int = 1,
    val dateText: String = "",
    val nextCourse: HomeCourse? = null,
    val todayCourses: List<HomeCourse> = emptyList(),
    val pendingTasks: List<TaskEntity> = emptyList(),
    val pendingHomework: List<HomeworkEntity> = emptyList(),
    /** 未完成的考试（来自「服务 → 考试安排」的解析结果，用户确认后落库） */
    val pendingExams: List<ExamEntity> = emptyList(),
    val completedTaskCount: Int = 0,

    /**
     * 每条待办已完整跑完的番茄数（`tasks.id` → 个数），没有记录的待办不在表里。
     *
     * 从 `focus_sessions` **派生**（`FocusSessionDao.observeCompletedByTask`），
     * 不是存在 `tasks` 上的字段 —— 同一件事不存两份。
     */
    val focusCounts: Map<Long, Int> = emptyMap(),

    /**
     * 这个方案里**到底有没有课**。
     *
     * ## 为什么必须有这个字段
     *
     * `nextCourse == null` 有两种完全不同的含义，而界面此前把它们画成同一张卡：
     *
     * - 有课表、只是今天没课 ⇒ "今天没有课啦 / 好好休息" —— **事实，正确**；
     * - **压根还没导入课表** ⇒ 同一句话 —— **事实为假**。
     *
     * 后者是新生第一次打开 App 看到的第一屏：一个还没填任何数据的人被告知
     * "今天没有课啦"，而真正该做的第一件事（导入课表）连影子都没有；
     * 卡片的首选动作反而是"添加一条待办"。见 `NextCourseCard` 的第四态。
     *
     * 判据取自**该方案下的课程条数**，不是"今天有没有课" —— 后者正是要区分的对象。
     */
    val hasTimetable: Boolean = false,

    /**
     * 学期开始日期是否已校准。
     *
     * 未校准时 [week] 静默取 1，界面会显示"第1周"这个**猜测值**。
     * 单看卡片读不出这是猜的，所以要让界面能据此提示去校准（见 `SemesterCalibrateDialog`）。
     */
    val semesterCalibrated: Boolean = false,

    /**
     * 当前课表的课程名（去重），供「添加待办」对话框做课程建议。
     *
     * 来源是**课表**而不是"已有待办里的课程名"：后者在待办清空后就没了建议，
     * 而用户要填的课程恰恰来自课表。
     */
    val courseNames: List<String> = emptyList(),

    /** 开发者实验室 Mock 场景提示；null 表示真实数据 */
    val mockBanner: String? = null
)

class HomeViewModel(
    private val repository: CourseRepository,
    private val settings: AppContainer
) : ViewModel() {

    private data class HomeMeta(
        val school: String,
        val semesterStart: String,
        val totalWeeks: Int,
        val schemeId: Long
    )

    private data class TimetableSnapshot(
        val courses: List<CourseEntity>,
        val timeSlots: List<TimeSlotEntity>
    )

    private data class TodoSnapshot(
        val tasks: List<TaskEntity>,
        val homework: List<HomeworkEntity>,
        val exams: List<ExamEntity>,
        val mockScenario: MockScenario,
        /** 每条待办的番茄数；见 [HomeUiState.focusCounts] 的注释。 */
        val focusCounts: Map<Long, Int>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timetableFlow: Flow<TimetableSnapshot> = combine(
        settings.activeSchemeId.flatMapLatest { repository.getCoursesByScheme(it) },
        repository.allTimeSlots
    ) { courses, slots -> TimetableSnapshot(courses, slots) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todoFlow: Flow<TodoSnapshot> = combine(
        settings.activeSchemeId.flatMapLatest { repository.getTasksByScheme(it) },
        repository.getAllHomework(),
        settings.activeSchemeId.flatMapLatest { repository.getExamsByScheme(it) },
        settings.mockScenario,
        settings.focusCompletedByTask,
    ) { tasks, homework, exams, scenario, focusCounts ->
        TodoSnapshot(tasks, homework, exams, scenario, focusCounts)
    }

    private val metaFlow: Flow<HomeMeta> = combine(
        settings.school,
        settings.semesterStart,
        settings.semesterWeeks,
        settings.activeSchemeId
    ) { school, start, weeks, schemeId ->
        HomeMeta(school, start, weeks, schemeId)
    }

    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        metaFlow, timetableFlow, todoFlow, ticker, settings.holidays
    ) { meta, timetable, todo, _, holidays ->
        buildState(meta, timetable, todo, holidays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildState(
        meta: HomeMeta,
        timetable: TimetableSnapshot,
        todo: TodoSnapshot,
        holidays: List<HolidayDay>
    ): HomeUiState {
        val today = LocalDate.now()
        val now = LocalTime.now()
        val semesterStart = parseSemesterStart(meta.semesterStart)
        val week = semesterStart
            ?.let { currentWeekFrom(it, meta.totalWeeks.coerceAtLeast(1), today) }
            ?: 1
        val todayDay = today.dayOfWeek.value
        // 法定节假日不排课（调休上班日 isOffDay=false，不受影响）
        val todayIsHoliday = HolidayCalendar.isOffDay(holidays, today)
        val slotMap = timetable.timeSlots.associateBy { it.slot }
        val activeCourses = timetable.courses.filter { isCourseInWeek(it, week) }
        val todayCourses = activeCourses
            .filter { it.dayOfWeek == todayDay && !todayIsHoliday }
            .sortedBy { it.startSlot }
            .map { it.toHomeCourse(slotMap, now, dayOffset = 0) }

        // 「下一节课」与**主动气泡**共用 `util/UpcomingCourse.findUpcomingCourse`：
        // 气泡说"30 分钟后上高数"的那一刻，这张卡片必须也写着高数 ——
        // 两处各写一份判断，迟早会在跨天 / 节假日 / 单双周的边界上分叉。
        val nextCourse = todayCourses.firstOrNull { !it.isFinished }
            ?: findUpcomingCourse(
                courses = timetable.courses,
                slots = slotMap,
                week = week,
                today = today,
                now = now,
                isOffDay = { date -> HolidayCalendar.isOffDay(holidays, date) },
            )?.let { it.course.toHomeCourse(slotMap, now, it.dayOffset) }

        val schemeHomework = todo.homework.filter {
            it.schemeId == meta.schemeId || it.schemeId == 0L
        }

        val realState = HomeUiState(
            school = meta.school,
            week = week,
            dateText = today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
            nextCourse = nextCourse,
            todayCourses = todayCourses,
            pendingTasks = todo.tasks.filter { !it.completed }.sortedBy { it.sortOrder },
            pendingHomework = schemeHomework
                .filter { !it.completed }
                .sortedBy { it.dueDate.ifBlank { "9999-99-99" } },
            pendingExams = todo.exams
                .filter { !it.completed }
                // 解析器已把日期分隔符统一成 `-`，故字典序与时间序一致
                .sortedBy { it.date.ifBlank { "9999-99-99" } },
            completedTaskCount = todo.tasks.count { it.completed },
            focusCounts = todo.focusCounts,
            // ⚠️ 判据是**该方案下的课程条数**（timetable.courses），不是 todayCourses.size ——
            // 后者正是要与"没有课表"区分开的那个量。
            hasTimetable = timetable.courses.isNotEmpty(),
            semesterCalibrated = semesterStart != null,
            courseNames = timetable.courses
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted(),
        )
        return MockHomeState.apply(realState, todo.mockScenario)
    }

    private fun CourseEntity.toHomeCourse(
        slots: Map<Int, TimeSlotEntity>,
        now: LocalTime,
        dayOffset: Int
    ): HomeCourse {
        val startTime = slots[startSlot]?.startTime.orEmpty()
        val endTime = slots[endSlot]?.endTime.orEmpty()
        val start = parseTimeOrNull(startTime)
        val end = parseTimeOrNull(endTime)
        val isToday = dayOffset == 0
        val ongoing = isToday && start != null && end != null &&
            !now.isBefore(start) && now.isBefore(end)
        val finished = isToday && end != null && !now.isBefore(end)
        return HomeCourse(
            course = this,
            startTime = startTime,
            endTime = endTime,
            dayOffset = dayOffset,
            isOngoing = ongoing,
            isFinished = finished
        )
    }

    private fun parseTimeOrNull(value: String): LocalTime? = try {
        if (value.isBlank()) null else LocalTime.parse(value)
    } catch (_: Exception) {
        null
    }

    /**
     * 新增一条待办 —— 首页右下角 FAB 的**唯一**落库出口。
     *
     * ## 为什么落两张表（这不是产品概念，是存储细节）
     *
     * 用户口径是"只有待办"，但 `tasks` 表**没有截止日期字段**。所以：
     * - 没填课程也没填截止日期 → `tasks`（纯自定义待办）；
     * - 填了其中之一 → `homework`（那张表本来就是"带截止日期的待办"的既有存储，
     *   而且学习通/智慧树导入的作业也落在那里，两者正好同类）。
     *
     * 于是界面上仍然只有一种东西（[TodoRow] 渲染），"作业"二字不出现在首页。
     * 想真正合并成一张表要先加 Room 迁移，那是另一个量级的改动 —— 本次刻意没做。
     *
     * 改造前这里是两个方法（`addTask` / `addHomework`），对应两个录入入口。
     */
    fun addTodo(content: String, dueDate: String, courseName: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        val due = dueDate.trim()
        val course = courseName.trim()
        viewModelScope.launch {
            if (due.isEmpty() && course.isEmpty()) {
                repository.insertTask(
                    TaskEntity(
                        name = text,
                        sortOrder = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                        schemeId = settings.activeSchemeId.value
                    )
                )
            } else {
                repository.insertHomework(
                    HomeworkEntity(
                        courseName = course,
                        content = text,
                        dueDate = due,
                        schemeId = settings.activeSchemeId.value,
                    )
                )
            }
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task.copy(completed = !task.completed))
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { repository.deleteTask(task.id) }
    }

    fun toggleHomework(homework: HomeworkEntity) {
        viewModelScope.launch {
            repository.setHomeworkCompleted(homework.id, !homework.completed)
        }
    }

    fun deleteHomework(homework: HomeworkEntity) {
        viewModelScope.launch { repository.deleteHomework(homework.id) }
    }

    // ── 考试（首页待办区与「服务 → 考试安排」共用同一份数据） ──
    fun toggleExam(exam: ExamEntity) {
        viewModelScope.launch {
            repository.setExamCompleted(exam.id, !exam.completed)
            // 完成状态变化会影响下一场要提醒的考试，必须重排
            settings.rescheduleExamReminders()
        }
    }

    fun deleteExam(exam: ExamEntity) {
        viewModelScope.launch {
            repository.deleteExam(exam.id)
            settings.rescheduleExamReminders()
        }
    }

    class Factory(
        private val repository: CourseRepository,
        private val settings: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository, settings) as T
    }
}
