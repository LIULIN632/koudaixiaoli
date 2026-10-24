package com.pocket.xiaoli.data.proactive

import android.util.Log
import com.pocket.xiaoli.data.card.YxyCardRepository
import com.pocket.xiaoli.data.electricity.DormElectricityRepository
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.data.weather.WeatherAlert
import com.pocket.xiaoli.data.weather.buildWeatherAlert
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.util.CalendarUtils
import com.pocket.xiaoli.util.HolidayCalendar
import com.pocket.xiaoli.util.coursesOn
import com.pocket.xiaoli.util.currentWeekFrom
import com.pocket.xiaoli.util.examDaysLeft
import com.pocket.xiaoli.util.findUpcomingCourse
import com.pocket.xiaoli.util.parseSemesterStart
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 把本机的数据**读成信号**，交给纯规则层判断该不该冒泡。
 *
 * ## 铁律：不为提醒发任何请求
 *
 * 每个字段都必须能从**已经有**的数据里算出来：Room 里的课表/考试/成绩存档、
 * 仓库的余额缓存、上次记住的余额快照。参考实现把这条写成了"一次抓取、两处消费" ——
 * 气泡自己去拉一次教务，用户看到的就不是提醒，而是"点开才有的等待"。
 *
 * 本项目因此**没有**实现参考里那几条需要额外链路的规则（通知 / 课表变更 / 图书馆待办）：
 * 它们在本项目里没有数据源，硬凑就得新增后台拉取，那是另一个功能的体量。
 */
object ProactiveEvaluator {

    private const val TAG = "Proactive"

    /** 评估一次。返回 null = 这一轮不该冒泡（冷却未过 / 没信号 / 档位关了）。 */
    suspend fun evaluate(container: AppContainer): ProactiveMessage? {
        val store = container.proactiveStore
        val level = store.level.value
        // 「关」档连数据库都不用碰
        if (!ProactiveRules.alertsAllowed(level) && !ProactiveRules.chatterAllowed(level)) return null

        val now = LocalDateTime.now()
        val gathered = gather(container, store, now)
        val message = ProactiveRules.pick(
            ProactiveContext(
                level = level,
                memory = store.memory(),
                signals = gathered.signals,
                nowMs = System.currentTimeMillis(),
                now = now,
                recentChatter = store.recentChatter(),
                facts = gathered.facts,
            )
        )
        Log.d(
            TAG,
            "evaluate: level=$level minutes=${gathered.signals.minutesToClass} " +
                "course=${gathered.signals.nextCourseName} newGrades=${gathered.signals.newGradeCount} " +
                "exam=${gathered.signals.exam?.courseName}/${gathered.signals.exam?.daysLeft} " +
                "card=${gathered.signals.cardBalance} elec=${gathered.signals.electricityBalance} " +
                "weather=${gathered.signals.weatherAlert?.key} " +
                "-> ${message?.id ?: "无"}"
        )
        return message
    }

    /**
     * 气泡已经冒出去了：记账 + **消费信号**。
     *
     * 冷却只负责"多久不再说"，不负责"这件事已经说过了" —— 两者混用会导致冷却一过
     * 又推一遍旧消息。所以"新成绩"提醒过的同时把游标推到现在。
     */
    fun consume(store: ProactiveStore, message: ProactiveMessage) {
        store.markShown(message.id)
        message.chatterLineId?.let(store::rememberChatterLine)
        if (message.id == ProactiveRules.ID_GRADE) {
            store.setGradeSeenAt(System.currentTimeMillis())
        }
        // 内容键随消息带回：校历写单个游标，天气写 `kind@date` 小集合。
        // 这一步必须留在 consume（而不是 evaluate）—— 消息没真的冒出去就不该算"说过"。
        message.seenKey?.let { key ->
            when (message.id) {
                ProactiveRules.ID_CALENDAR -> store.setCalendarSeenKey(key)
                ProactiveRules.ID_WEATHER -> store.rememberWeatherSeenKey(key)
            }
        }
    }

    private data class Gathered(val signals: ProactiveSignals, val facts: ChatterFacts)

    private suspend fun gather(
        container: AppContainer,
        store: ProactiveStore,
        now: LocalDateTime,
    ): Gathered {
        val today = now.toLocalDate()
        val holidays = container.holidays.value
        val isOffDay: (LocalDate) -> Boolean = { date -> HolidayCalendar.isOffDay(holidays, date) }

        val schemeId = container.activeSchemeId.value
        val courses = container.repository.getCoursesByScheme(schemeId).first()
        val slots = container.repository.allTimeSlots.first().associateBy { it.slot }
        val week = parseSemesterStart(container.semesterStart.value)
            ?.let { currentWeekFrom(it, container.semesterWeeks.value.coerceAtLeast(1), today) }
            ?: 1

        val upcoming = findUpcomingCourse(courses, slots, week, today, now.toLocalTime(), isOffDay)
        // 「还有几分钟上课」只对**今天**的课成立：明天的课算出来是几百分钟，
        // 那既没用又会被 30 分钟的门槛挡住（挡是挡对了，但别让它参与运算更清楚）
        val minutes = upcoming
            ?.takeIf { it.dayOffset == 0 }
            ?.startAt
            ?.let { Duration.between(now, it).toMinutes() }

        val exams = container.repository.getExamsByScheme(schemeId).first()
        val grades = container.savedGradeDao.getAll().first()

        return Gathered(
            signals = ProactiveSignals(
                cardBalance = cardBalance(container, store),
                electricityBalance = electricityBalance(container, store),
                nextCourseName = upcoming?.course?.name?.takeIf { it.isNotBlank() },
                minutesToClass = minutes,
                newGradeCount = newGradeCount(grades, store.gradeSeenAt()),
                exam = examSignal(exams, today),
                calendarNode = calendarNode(container, store, today),
                weatherAlert = weatherAlert(container, store, now),
            ),
            facts = ChatterFacts(
                todayCourseCount = coursesOn(courses, week, today, isOffDay).size,
                todayHoliday = HolidayCalendar.offDayOf(holidays, today)?.name?.takeIf { it.isNotBlank() },
            ),
        )
    }

    /**
     * 最近的那个**还没开始、也还没说过**的校历节点。
     *
     * 过滤"说过的"放在这里而不是规则层：`ProactiveMemory` 是只读快照，
     * `pickAlert` 是纯函数、写不回去。窗口（提前几天才说）也不在这里卡 ——
     * 采集侧只回答"下一个是什么、还有几天"，"该不该说"是规则层的判断。
     *
     * 资产读不出来（首次安装前 / JSON 坏了）就返回 null：**没有校历就不提校历**，
     * 不许拿学期设置去猜一个"下一个节点"。
     */
    private fun calendarNode(
        container: AppContainer,
        store: ProactiveStore,
        today: LocalDate,
    ): CalendarNodeSignal? {
        val asset = container.calendarRepository.asset() ?: return null
        val event = CalendarUtils.upcomingNode(asset, today) ?: return null
        if (event.nodeKey == store.calendarSeenKey()) return null
        return CalendarNodeSignal(
            title = event.title,
            // gridLabel 就是校历页格子里那行小字（资产约定 ≤4 字），气泡文案跟着它走，
            // 于是气泡说"寒假"、页面上写的也是"寒假"，不会一个说全称一个说简称。
            short = event.gridLabel,
            date = event.start.toString(),
            daysLeft = ChronoUnit.DAYS.between(today, event.start),
            key = event.nodeKey,
        )
    }

    /**
     * 天气提醒：**只读** [com.pocket.xiaoli.data.weather.WeatherRepository] 的快照。
     *
     * 快照由首页 / 天气页 / 天气工具拉取后发布，这里绝不为了提醒再发一次请求
     * （`buildWeatherAlert` 是纯函数，见它的注释）。没快照、快照过期或跨天都返回 null。
     */
    private fun weatherAlert(
        container: AppContainer,
        store: ProactiveStore,
        now: LocalDateTime,
    ): WeatherAlert? = buildWeatherAlert(
        snapshot = container.weatherRepository.snapshot.value,
        seenKeys = store.weatherSeenKeys(),
        now = now,
    )

    /**
     * 校园卡余额：先看仓库的 5 分钟内存缓存，没有就退回上次记住的快照（24 小时内）。
     *
     * 没绑易校园账号时返回 null —— 本机根本不知道余额，不许猜。
     */
    private fun cardBalance(container: AppContainer, store: ProactiveStore): Double? {
        val phone = container.loadCardCredential()?.first?.takeIf { it.isNotBlank() } ?: return null
        val fresh = YxyCardRepository.cachedBalance(phone)?.balance
        if (fresh != null) {
            store.rememberBalance(ProactiveStore.KIND_CARD, phone, fresh)
            return fresh
        }
        return store.balanceSnapshot(
            ProactiveStore.KIND_CARD,
            phone,
            ProactiveStore.BALANCE_MAX_AGE_MS,
        )
    }

    /** 宿舍电费余额，口径同上；身份里带上校区与编号（换了宿舍就不能再念旧数字）。 */
    private fun electricityBalance(container: AppContainer, store: ProactiveStore): Double? {
        val dorm = container.dormInfo.value?.sanitized()?.takeIf { it.isBound } ?: return null
        // 与首页低余额提醒共用同一个键：两边各拼一份，改宿舍编号时会有一边读不出来
        val identity = dorm.balanceIdentity
        val fresh = DormElectricityRepository.cachedSnapshot(dorm)?.balance?.toDoubleOrNull()
        if (fresh != null) {
            store.rememberBalance(ProactiveStore.KIND_ELECTRICITY, identity, fresh)
            return fresh
        }
        return store.balanceSnapshot(
            ProactiveStore.KIND_ELECTRICITY,
            identity,
            ProactiveStore.BALANCE_MAX_AGE_MS,
        )
    }

    /**
     * 最靠前的那场**未来的**没完成的考试。
     *
     * ⚠️ 必须排除已过去的（`daysLeft < 0`）：通知解析会留下一批老考试，
     * 它们没被勾完成不代表还要考 —— 否则气泡会说"高等数学已过去 300 天"。
     */
    internal fun examSignal(exams: List<ExamEntity>, today: LocalDate = LocalDate.now()): ExamSignal? =
        exams.asSequence()
            .filter { !it.completed }
            .mapNotNull { exam ->
                examDaysLeft(exam.date, today)
                    ?.takeIf { it >= 0 }
                    ?.let { days -> ExamSignal(
                        courseName = exam.courseName,
                        daysLeft = days,
                        date = exam.date,
                        time = exam.time,
                        location = exam.location,
                        seat = exam.seat,
                    ) }
            }
            .minByOrNull { it.daysLeft }

    /** 自"上次看到"以来新落库的成绩门数。 */
    internal fun newGradeCount(grades: List<SavedGradeEntity>, seenAt: Long): Int =
        grades.count { it.savedAt > seenAt }
}
