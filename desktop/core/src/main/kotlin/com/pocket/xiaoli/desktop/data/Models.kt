package com.pocket.xiaoli.desktop.data

import com.pocket.xiaoli.core.academic.ZfGradeItem
import com.pocket.xiaoli.core.exam.ExamSchedule

/** 学期。日期一律用 `yyyy-MM-dd` 字符串存盘（Gson 没有 LocalDate 适配器）。 */
data class Term(
    val id: String,
    val name: String,
    val startDate: String,
    val totalWeeks: Int = 20,
    val sortOrder: Int = 0,
) {
    val displayName: String get() = name.ifBlank { "未命名学期" }
}

/** 一节课（或一条日程）。与 Android 端 `CourseEntity` 字段对齐。 */
data class Course(
    val id: String,
    val termId: String = "",
    val name: String,
    val teacher: String = "",
    val room: String = "",
    /** 1=周一 … 7=周日 */
    val dayOfWeek: Int = 1,
    /** 从 1 开始，闭区间 */
    val startSlot: Int = 1,
    val endSlot: Int = 1,
    /** all / odd / even */
    val weekType: String = "all",
    /** "1-16,18"；空 = 全学期 */
    val weeks: String = "",
    val colorIndex: Int = 0,
    /** 用户手选过颜色（则不再按课名自动配色） */
    val colorPinned: Boolean = false,
    val shortName: String = "",
    val note: String = "",
    /** course = 课程；agenda = 日程 / 非标准时段 */
    val kind: String = "course",
    /** 仅 agenda 用：自午夜起的分钟数，-1 = 用节次时间 */
    val startMinute: Int = -1,
    val endMinute: Int = -1,
) {
    val isAgenda: Boolean get() = kind == "agenda"
}

/** 节次时间（默认 12 节，可在「我的 → 节次时间」里改）。 */
data class TimeSlot(
    val index: Int,
    val label: String,
    val start: String,
    val end: String,
) {
    fun startMinuteOfDay(): Int = parseClock(start) ?: 0
    fun endMinuteOfDay(): Int = parseClock(end) ?: 0
}

fun parseClock(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

data class Todo(
    val id: String,
    val title: String,
    val due: String = "",
    val done: Boolean = false,
    /** 0 = 普通，1 = 重要，2 = 紧急 */
    val priority: Int = 0,
    val note: String = "",
    val courseName: String = "",
    val createdAt: Long = 0L,
)

/**
 * 一场考试 = 共享解析结果 + 本地完成态。
 *
 * 字段口径（日期原文、考场、座位）全在共享的 [ExamSchedule] 里，桌面不另立一份模型，
 * 所以手机端解析出来的写法与这里存的、显示的完全一致。
 */
data class Exam(
    val id: String,
    val schedule: ExamSchedule = ExamSchedule(courseName = ""),
    val completed: Boolean = false,
) {
    val courseName: String get() = schedule.courseName
    val date: String get() = schedule.date
    val summary: String get() = schedule.summary
}

data class AppSettings(    val darkMode: Boolean = false,
    val followSystemDark: Boolean = false,
    /** 空 = 用主题自带主色；否则 "#4D6BFE" 这类 6 位十六进制 */
    val accentHex: String = "",
    val activeTermId: String = "",
    val studentNo: String = "",
    val studentName: String = "",
    val major: String = "",
    val className: String = "",
    val campusId: String = "ytlg_main",
    val aiBaseUrl: String = "https://api.deepseek.com/v1",
    val aiApiKey: String = "",
    val aiModel: String = "deepseek-chat",
    val aiTemperature: Double = 0.3,
    val gridBgIndex: Int = -1,
    val showWeekend: Boolean = true,

    /** 节假日数据每天自动拉一次（拉不到就继续用本地/内置） */
    val holidayAutoUpdate: Boolean = false,
    /** null = 旧数据没这个字段，按默认「收进托盘」处理（Gson 缺字段会填 false，不能用非空 Boolean） */
    val closeToTray: Boolean? = null,
    /** null = 旧数据，默认开启上课提醒 */
    val classReminder: Boolean? = null,
    /** null = 旧数据，默认提前 10 分钟 */
    val reminderLeadMinutes: Int? = null,
    /** null = 旧数据，默认开启待办到期提醒 */
    val todoReminder: Boolean? = null,
    /** null = 旧数据，默认开启考试提醒 */
    val examReminder: Boolean? = null,
    /** null = 旧数据，默认**关**：导入诊断会把教务返回的原始页面写到本机日志目录 */
    val importDiagnostics: Boolean? = null,
    /** null = 旧数据，默认**关**：开机自启要写注册表启动项，必须用户自己打开 */
    val autoLaunch: Boolean? = null,
    /** null = 旧数据没这个字段，默认**开**：首页天气条会在打开首页时联网拉一次（半小时不重复） */
    val homeWeather: Boolean? = null,
) {
    val minimizeToTray: Boolean get() = closeToTray ?: true
    val homeWeatherEnabled: Boolean get() = homeWeather ?: true
    val classReminderEnabled: Boolean get() = classReminder ?: true
    val classReminderLead: Int get() = (reminderLeadMinutes ?: 10).coerceIn(1, 60)
    val todoReminderEnabled: Boolean get() = todoReminder ?: true
    val examReminderEnabled: Boolean get() = examReminder ?: true
    val importDiagnosticsEnabled: Boolean get() = importDiagnostics ?: false
    val autoLaunchEnabled: Boolean get() = autoLaunch ?: false
}

/**
 * 成绩缓存：登录成功后存一份，下次打开不必重新登录也能看上次的成绩。
 *
 * 缓存必须带学期（`xnm`/`xqm`/`termLabel`）—— 不带的话切学期会把上学期的分数摆出来，
 * 那比"没有缓存"更糟。`savedAt` 用来显示"缓存于 …"，让用户知道这不是实时数据。
 */
data class GradesCache(
    val termLabel: String = "",
    val xnm: String = "",
    val xqm: String = "",
    val savedAt: Long = 0L,
    val items: List<ZfGradeItem> = emptyList(),
)

data class AppData(
    val version: Int = 1,

    /** 数据来源：seed（内置示例）/ import（教务导入）/ manual（自己手改过）。UI 用它区分演示与真实数据。 */
    val origin: String = "",
    val terms: List<Term> = emptyList(),
    val activeTermId: String = "",
    val courses: List<Course> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val gradesCache: List<GradesCache> = emptyList(),
    /** 学习笔记：与 Android 端同一份模型（core/commonMain），只存在本机。 */
    val notes: List<com.pocket.xiaoli.core.notes.NoteItem> = emptyList(),
    /**
     * 天气缓存存**原始 JSON** 而不是解析结果：解析规则会随版本变化，存原文才能用当前解析器
     * 重读旧缓存，最坏情况只是解析失败一次、下一次联网覆盖（与 Android 端 WeatherStore 同一口径）。
     */
    val weatherRaw: String = "",
    val weatherAt: Long = 0L,
    val slots: List<TimeSlot> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    val activeTerm: Term? get() = terms.firstOrNull { it.id == activeTermId } ?: terms.firstOrNull()
    val activeCourses: List<Course>
        get() {
            val term = activeTerm?.id
            val scoped = if (term == null) courses else courses.filter { it.termId == term || it.termId.isBlank() }
            return scoped
        }
}
