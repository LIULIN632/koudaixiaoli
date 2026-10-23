package com.pocket.xiaoli.data.proactive

import com.pocket.xiaoli.data.weather.WeatherAlert
import com.pocket.xiaoli.util.EXAM_SOON_DAYS
import com.pocket.xiaoli.util.examCountdownText
import java.time.LocalDateTime

/**
 * 主动提醒要用的"记账"——**从磁盘读出来的一份快照**，规则层自己不碰 SharedPreferences。
 *
 * 这样整个 [ProactiveRules] 就是纯函数，冷却与优先级可以直接对着时间戳单测，
 * 不用伪造 Context（参考实现也是这个分层，见它的 `ProactiveRules` 类注释）。
 */
data class ProactiveMemory(
    /** 上一次「自动冒泡」的时刻（正事与闲话**共用**这一个节流）。 */
    val lastAnyAt: Long = 0L,
    /** 每条规则上一次出现的时刻，键 = [ProactiveMessage.id]。 */
    val lastShown: Map<String, Long> = emptyMap(),
    /** 每条规则被用户**关掉**过几次，用来逐步拉长冷却。 */
    val dismissed: Map<String, Int> = emptyMap(),
    /**
     * 已经说过的那个**校历节点**的内容键（`"${title}@${start}"`）。
     *
     * 校历节点与新成绩一样走「冒过即消费信号」：冷却只能解决"多久不再说"，
     * 解决不了"这件事已经说过了"。离散的节点用内容键去重，比时间戳准。
     */
    val lastCalendarSeenKey: String = "",
) {
    fun lastShownAt(id: String): Long = lastShown[id] ?: 0L
    fun dismissedCount(id: String): Int = dismissed[id] ?: 0
}

/**
 * 考试信号。字段与参考实现一致：文案要用课程名，快照要用其余几项。
 *
 * [daysLeft] 由 `util/ExamCountdown.examDaysLeft` 算出来 —— 与首页卡片**同一个解析器**，
 * 否则会出现"卡片说还有 2 天、气泡说还有 3 天"。
 */
data class ExamSignal(
    val courseName: String,
    val daysLeft: Long,
    val date: String = "",
    val time: String = "",
    val location: String = "",
    val seat: String = "",
)

/**
 * 校历节点信号：离今天最近的那条**还没到**的校历事件。
 *
 * [key] 是内容键（`"${title}@${start}"`），**必须由采集方算好带进来** ——
 * 让消费方（`ProactiveEvaluator.consume`）自己重拼一次的话，
 * 两处的拼法迟早会不一致，那个节点就会永远"没说过"、无限重播。
 */
data class CalendarNodeSignal(
    val title: String,
    /** 格内小字（≤4 字），气泡文案用它，短。 */
    val short: String,
    /** `yyyy-MM-dd`，用于快照。 */
    val date: String,
    val daysLeft: Long,
    val key: String,
)

/**
 * 一次评估能拿到的全部本地信号。
 *
 * ⚠️ **只读缓存，不为提醒发任何请求**：这里每个字段都必须能从本机已有数据算出来。
 * 本项目没有"最新教务通知""课表变更 diff"这类数据源，所以参考实现里对应的三条规则
 * 在 `pickAlert` 里是**故意缺席**的（不是忘了）——将来接上数据源再加回来。
 */
data class ProactiveSignals(
    /** 校园卡余额（元）；null = 本机没有可用值。 */
    val cardBalance: Double? = null,
    /** 宿舍电费余额（元）；null = 本机没有可用值。 */
    val electricityBalance: Double? = null,
    val nextCourseName: String? = null,
    val minutesToClass: Long? = null,
    val newGradeCount: Int = 0,
    val exam: ExamSignal? = null,
    /**
     * 最近的那个即将到来的校历节点。
     *
     * 采集方**已经把"说过的"过滤掉了**（见 [ProactiveMemory.lastCalendarSeenKey]）：
     * 命中游标就传 null 进来 —— 过滤放在采集侧，是为了让 [ProactiveRules.pickAlert]
     * 保持纯函数（它拿到的 `memory` 是快照，写不回去）。
     */
    val calendarNode: CalendarNodeSignal? = null,
    /**
     * 眼下值得开口的天气（正在下雨 / 未来 12 小时降水 / 高温低温 / 强紫外线）。
     *
     * 与 [calendarNode] 同一个约定：采集侧已经用**说过的键集合**过滤过，
     * 这里的非 null 值就是"该说、且没说过"。
     */
    val weatherAlert: WeatherAlert? = null,
)

/** 闲聊可以引用的事实（都由调用方从本地数据算好，池子里不读任何东西）。 */
data class ChatterFacts(
    /** 今天排了几节课；null = 不知道（池子就不出"今天有几节课"这类句子）。 */
    val todayCourseCount: Int? = null,
    /** 今天是不是法定放假日，是的话是哪个节（如"中秋"）。 */
    val todayHoliday: String? = null,
)

/** 一次评估的完整输入。参数都在这里，[ProactiveRules.pick] 只收这一个对象。 */
data class ProactiveContext(
    val level: ProactiveLevel,
    val memory: ProactiveMemory,
    val signals: ProactiveSignals,
    val nowMs: Long,
    val now: LocalDateTime,
    /** 最近说过的闲聊句子 id（防重复）。 */
    val recentChatter: List<String> = emptyList(),
    val facts: ChatterFacts = ChatterFacts(),
)

/**
 * 主动提醒的**全部判断**（纯函数，可直接单测）。
 *
 * ## 三档位分别管什么
 *
 * | 档 | 正事提醒 | 闲聊 | 正事之间的全局冷却 |
 * |---|---|---|---|
 * | 关 | ❌ | ❌ | — |
 * | 少 | ✅ | ❌ | 1 小时 |
 * | 标准 | ✅ | ✅ | 15 分钟 |
 *
 * ## 三条从参考实现学来的"血泪"
 *
 * 1. **闲话与正事共用全局冷却**。参考实现里闲话一度只写自己的时间戳，
 *    于是它绕开了全局节流 —— 正事气泡刚收、闲话立刻接上，用户看到的就是它自顾自连着蹦。
 * 2. **闲话冷却要够长**：气泡只活 8 秒，6 分钟一句在持续使用期间等于不停冒；
 *    参考实现把它从 6 分钟拉到 25 分钟，这里沿用 25 分钟。
 * 3. **冒过即消费信号**（在宿主里做）：冷却只负责"多久不再说"，
 *    不负责"这件事已经说过了"。两者混用会导致冷却一过又推一遍旧消息。
 */
object ProactiveRules {

    const val CHATTER_ID = "chatter"

    /** 进页面后先等 3 秒再评估：刚启动时数据往往还没落位，立刻评估只会白跑一轮。 */
    const val FIRST_DELAY_MS = 3_000L

    /** 评估间隔。循环评估而不是只算一次：余额、临近上课、新成绩都在运行期发生变化。 */
    const val EVAL_INTERVAL_MS = 60_000L

    /** 气泡自动消失时间。 */
    const val AUTO_DISMISS_MS = 8_000L

    /** 标准档下两条正事提醒之间的全局冷却。 */
    const val GLOBAL_COOLDOWN_MS = 15 * 60 * 1000L

    /** 「少」档把全局冷却从 15 分钟拉到 1 小时，只留真正要紧的事。 */
    const val LOW_GLOBAL_COOLDOWN_MS = 60 * 60 * 1000L

    /** 单条规则的默认冷却。 */
    const val RULE_COOLDOWN_MS = 60 * 60 * 1000L

    /** 闲聊冷却。见类注释第 2 条。 */
    const val CHATTER_COOLDOWN_MS = 25 * 60 * 1000L

    /**
     * 考试的单条冷却。
     *
     * 考试窗口本身有 [EXAM_SOON_DAYS] 天，用一小时的通用冷却会在这几天里反复念叨同一件事。
     * 半天一次已经足够 —— 这是"别忘了"，不是"马上去做"。
     */
    const val EXAM_COOLDOWN_MS = 12 * 60 * 60 * 1000L

    /**
     * 余额低到多少才值得开口（元）。
     *
     * ⚠️ 两个都是**先验值，无数据支撑**：校园卡 50 元沿用参考实现；
     * 电费给 20 元，因为宿舍电费的常见余额量级本来就比校园卡小一个数量级
     * （几十块能撑很久，欠费才是真急事）。要调就改这里。
     */
    const val LOW_BALANCE_CARD = 50.0
    const val LOW_BALANCE_ELECTRICITY = 20.0

    /** 「快上课了」的提前量（分钟）。 */
    const val CLASS_AHEAD_MINUTES = 30L

    /**
     * 校历节点的提前量（天）。
     *
     * 取 3，与 `util/ExamCountdown.kt` 的 [EXAM_SOON_DAYS] 同一个数：两者都是
     * "还有几天"的提醒，用两个不同的窗口只会让人怀疑哪个准。
     */
    const val CALENDAR_AHEAD_DAYS = 3L

    /** 关档完全不生成气泡。 */
    fun alertsAllowed(level: ProactiveLevel): Boolean = level != ProactiveLevel.OFF

    /** 只有标准档才闲聊。 */
    fun chatterAllowed(level: ProactiveLevel): Boolean = level == ProactiveLevel.STANDARD

    fun globalCooldownMs(level: ProactiveLevel): Long =
        if (level == ProactiveLevel.LOW) LOW_GLOBAL_COOLDOWN_MS else GLOBAL_COOLDOWN_MS

    /**
     * 某条规则这一次的冷却时长。
     *
     * 用户每关掉一次就翻倍（最多 8 倍）：他不是"没看到"，是"不想看这条"。
     * 打开过（[markUseful] 的语义）则清零 —— 说明它有用。
     */
    fun cooldownFor(id: String, dismissedCount: Int): Long {
        val factor = (1 shl dismissedCount.coerceAtMost(3)).toLong()
        val base = when (id) {
            CHATTER_ID -> CHATTER_COOLDOWN_MS
            ID_EXAM -> EXAM_COOLDOWN_MS
            else -> RULE_COOLDOWN_MS
        }
        return base * factor
    }

    const val ID_EXAM = "exam"
    const val ID_BALANCE_CARD = "balance_card"
    const val ID_BALANCE_ELECTRICITY = "balance_electricity"
    const val ID_GRADE = "grade"
    const val ID_CLASS = "class"
    const val ID_WEATHER = "weather"
    const val ID_CALENDAR = "calendar"

    /**
     * 一次评估的入口：**先正事、后闲聊**。
     *
     * 「闲话只填空」是刻意的：没有可行动的提醒时才说闲话，且要走同一个全局冷却。
     */
    fun pick(ctx: ProactiveContext): ProactiveMessage? {
        if (!alertsAllowed(ctx.level)) return null
        pickAlert(ctx)?.let { return it }
        if (!chatterAllowed(ctx.level)) return null
        if (ctx.nowMs - ctx.memory.lastAnyAt < globalCooldownMs(ctx.level)) return null
        return pickChatter(ctx)
    }

    /**
     * 正事提醒：**按优先级列出候选，再取第一条"冷却已经过了"的**。
     *
     * ⚠️ 不是"取优先级最高的一条然后看它冷却没到就放弃" —— 那样高优先级规则一冷却，
     * 后面所有规则都会被一起憋住。
     *
     * 优先级从高到低（参考实现的顺序，去掉本项目没有数据源的那几条）：
     * 考试 > 余额 > 新成绩 > 快上课 > 天气 > 校历节点。
     */
    fun pickAlert(ctx: ProactiveContext): ProactiveMessage? {
        val level = ctx.level
        if (!alertsAllowed(level)) return null
        val now = ctx.nowMs
        val cooldown = globalCooldownMs(level)
        // 全局冷却没过：一句话都不说（连候选都不用算）
        if (now - ctx.memory.lastAnyAt < cooldown) return null

        val s = ctx.signals
        // 采集侧已经把"说过的那个节点"过滤成 null 了，这里不再查游标。
        val node = s.calendarNode
        // 去重靠内容键（[ProactiveMemory.lastCalendarSeenKey]），一个节点一辈子只说一次；
        // 同一个节点反复出现的情况由采集侧过滤掉，不靠冷却。
        // 冷却表里那条 1 小时（RULE_COOLDOWN_MS）仍然生效，它管的是另一件事：
        // "刚说过一个节点，别马上又说下一个"。
        val candidates = buildList {
            // 考试：点气泡进对话，并把这场考试的已知字段当快照 —— 对准"点的是哪场"，
            // 缺的仍去调工具（气泡不为提醒拉一次教务）。
            val exam = s.exam
            if (exam != null && exam.daysLeft <= EXAM_SOON_DAYS) {
                add(
                    ProactiveMessage(
                        id = ID_EXAM,
                        text = clipProactiveText(exam.courseName + examCountdownText(exam.daysLeft)),
                        prompt = "${exam.courseName}什么时候考、在哪考？帮我排一下复习",
                        eventSnapshot = eventFields(
                            "kind" to "exam",
                            "course" to exam.courseName,
                            "date" to exam.date,
                            "time" to exam.time,
                            "location" to exam.location,
                            "seat" to exam.seat,
                            "daysLeft" to exam.daysLeft.toString(),
                        ),
                    )
                )
            }
            // 校园卡余额
            val card = s.cardBalance
            if (card != null && card < LOW_BALANCE_CARD) {
                add(
                    ProactiveMessage(
                        id = ID_BALANCE_CARD,
                        text = clipProactiveText("校园卡只剩 ¥${"%.2f".format(card)} 了，记得充"),
                        prompt = "我的校园卡余额还有多少？最近都花在哪了？",
                        eventSnapshot = eventFields(
                            "kind" to "campus_card",
                            "balance" to "%.2f".format(card),
                            "note" to "余额来自本机缓存，流水与是否最新需查工具",
                        ),
                    )
                )
            }
            // 宿舍电费余额
            val electricity = s.electricityBalance
            if (electricity != null && electricity < LOW_BALANCE_ELECTRICITY) {
                add(
                    ProactiveMessage(
                        id = ID_BALANCE_ELECTRICITY,
                        text = clipProactiveText("宿舍电费只剩 ${"%.2f".format(electricity)} 元了"),
                        prompt = "我的宿舍电费还剩多少？",
                        eventSnapshot = eventFields(
                            "kind" to "dorm_electricity",
                            "balance" to "%.2f".format(electricity),
                            "note" to "余额来自本机缓存，是否最新需去服务页刷新",
                        ),
                    )
                )
            }
            // 新成绩：措辞保持中性，不调侃（成绩、考勤这类事一律不调侃）
            if (s.newGradeCount > 0) {
                add(
                    ProactiveMessage(
                        id = ID_GRADE,
                        text = "有 ${s.newGradeCount} 门新成绩出了",
                        prompt = "帮我看看新出的成绩",
                        eventSnapshot = eventFields(
                            "kind" to "grades",
                            "newCount" to s.newGradeCount.toString(),
                            "note" to "快照只有门数，没有科目和分数",
                        ),
                    )
                )
            }
            // 快上课：课表页里已经有这件事，所以点了直接去课表，不进对话
            val name = s.nextCourseName
            val minutes = s.minutesToClass
            if (name != null && minutes != null && minutes in 0..CLASS_AHEAD_MINUTES) {
                add(
                    ProactiveMessage(
                        id = ID_CLASS,
                        text = clipProactiveText("${minutes}分钟后上$name"),
                        openRoute = "timetable",
                    )
                )
            }
            // 天气：带不带伞是马上要做出的决定，所以排在「快上课」之后、「校历」之前。
            // 点气泡直接进天气页（小时降水条比复述更有用），不进对话。
            val weather = s.weatherAlert
            if (weather != null) {
                add(
                    ProactiveMessage(
                        id = ID_WEATHER,
                        text = clipProactiveText(weather.text),
                        openRoute = "weather",
                        // 内容键随消息走到 consume 再写回 —— 冒出去才算说过。
                        seenKey = weather.key,
                    )
                )
            }
            // 校历节点：一条纯"别忘了"的预报，不催你立刻做任何事 —— 所以排在最后。
            // 放在「快上课」前面的话，离假期还有 3 天这句会把"5 分钟后上课"憋住一轮，
            // 那是真正耽误事的排序。
            if (node != null && node.daysLeft <= CALENDAR_AHEAD_DAYS) {
                add(
                    ProactiveMessage(
                        id = ID_CALENDAR,
                        // 点了直接去校历页，不进对话 —— 与「快上课」同一个理由：
                        // 详情在校历页里已经有了，让鲤鱼复述一遍反而绕。
                        text = clipProactiveText(calendarNodeText(node)),
                        openRoute = "campus_calendar",
                        eventSnapshot = eventFields(
                            "kind" to "calendar",
                            "title" to node.title,
                            "date" to node.date,
                            "daysLeft" to node.daysLeft.toString(),
                        ),
                        // 内容键随消息走到 consume 再写回 —— 冒出去才算说过。
                        seenKey = node.key,
                    )
                )
            }
        }
        return candidates.firstOrNull { message ->
            now - ctx.memory.lastShownAt(message.id) >=
                cooldownFor(message.id, ctx.memory.dismissedCount(message.id))
        }
    }

    /**
     * 校历节点的文案。今天/明天单独说 —— 「寒假还有 0 天」是句人话说不出口的话。
     */
    private fun calendarNodeText(node: CalendarNodeSignal): String = when (node.daysLeft) {
        0L -> "${node.short}今天开始"
        1L -> "${node.short}明天开始"
        else -> "${node.short}还有 ${node.daysLeft} 天"
    }

    /**
     * 闲聊：**没有可行动的事时才说**，且要走自己的冷却。
     *
     * 句子池在 [ChatterPool]（纯本地内容，本项目自己写的 —— 参考实现的池子是西交语境，
     * 樱花节/兴庆雁塔那一套搬过来就是错的）。
     */
    fun pickChatter(ctx: ProactiveContext): ProactiveMessage? {
        val now = ctx.nowMs
        if (now - ctx.memory.lastShownAt(CHATTER_ID) <
            cooldownFor(CHATTER_ID, ctx.memory.dismissedCount(CHATTER_ID))
        ) {
            return null
        }
        val line = ChatterPool.pick(
            now = ctx.now,
            recent = ctx.recentChatter,
            facts = ctx.facts,
        ) ?: return null
        return ProactiveMessage(
            id = CHATTER_ID,
            text = line.text,
            fullReveal = true,
            chatterLineId = line.id,
        )
    }

    /**
     * 用户**主动戳一下**鲤鱼时要说的那句，与自动冒泡走两套规则。
     *
     * 刻意绕开闲聊冷却：冷却是给"它自己突然开口"用的，防打扰；
     * 而用户戳它一下却不吭声，看起来就是坏了。但仍然避开最近说过的句子，
     * 免得连点两下讲同一句。
     *
     * 「关」档连点击也不回话 —— 用户把它关了就是不想看见任何气泡，
     * 戳一下只是切到鲤鱼页。
     */
    fun pickOnTap(
        level: ProactiveLevel,
        now: LocalDateTime,
        recent: List<String>,
        facts: ChatterFacts = ChatterFacts(),
    ): ProactiveMessage? {
        if (level == ProactiveLevel.OFF) return null
        val line = ChatterPool.pick(now = now, recent = recent, facts = facts) ?: return null
        return ProactiveMessage(
            id = CHATTER_ID,
            text = line.text,
            fullReveal = true,
            chatterLineId = line.id,
        )
    }

    /** 气泡事件快照：只写非空字段，给模型当"点的是哪件事"。 */
    private fun eventFields(vararg fields: Pair<String, String?>): String =
        fields.mapNotNull { (k, v) -> v?.takeIf { it.isNotBlank() }?.let { "$k: $it" } }
            .joinToString("\n")
}
