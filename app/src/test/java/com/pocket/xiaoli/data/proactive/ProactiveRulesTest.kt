package com.pocket.xiaoli.data.proactive

import com.pocket.xiaoli.data.weather.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 主动提醒的规则层（纯函数）。
 *
 * 这一层是整个功能里**唯一能用单测钉住的部分**：档位、冷却、优先级、文案封顶都在这里，
 * 而它们全是"过一段时间才会表现出来"的行为 —— 手点很难验证（等 15 分钟看气泡冒没冒）。
 *
 * 断言的重点不是"能冒泡"，而是**什么时候不许冒**：这个功能的失败模式是骚扰用户，不是没提醒。
 */
class ProactiveRulesTest {

    private val now = LocalDateTime.of(2026, 10, 8, 9, 20)

    /**
     * 「现在」用 2026 年的一个真实时间戳，**不能图省事写 1_000_000L**。
     *
     * 单条规则的冷却以 1 小时计，而"某条规则上次出现于 0（从没出现过）"要能被判成
     * "冷却已过" —— 时间戳太靠近纪元时，`now - 0` 还不到一小时，于是**所有规则都被判成
     * 冷却中**，测试会以"什么都没冒"的形式失败（看起来像规则写错了）。
     */
    private val nowMs = 1_782_000_000_000L

    /** 一条"什么都没有"的信号，各用例只改自己关心的那几项。 */
    private fun signals(
        cardBalance: Double? = null,
        exam: ExamSignal? = null,
        newGradeCount: Int = 0,
        nextCourseName: String? = null,
        minutesToClass: Long? = null,
        calendarNode: CalendarNodeSignal? = null,
        weatherAlert: WeatherAlert? = null,
    ) = ProactiveSignals(
        cardBalance = cardBalance,
        exam = exam,
        newGradeCount = newGradeCount,
        nextCourseName = nextCourseName,
        minutesToClass = minutesToClass,
        calendarNode = calendarNode,
        weatherAlert = weatherAlert,
    )

    /**
     * 一个校历节点信号。
     *
     * [key] 按真实现场那样拼（"标题@开始日"）—— 单测里另写一种拼法就测不到
     * "两处拼法不一致"这个真正的失败模式了。
     */
    private fun node(daysLeft: Long, title: String = "寒假", short: String = "寒假"): CalendarNodeSignal {
        val date = now.toLocalDate().plusDays(daysLeft)
        return CalendarNodeSignal(
            title = title,
            short = short,
            date = date.toString(),
            daysLeft = daysLeft,
            key = "$title@$date",
        )
    }

    private fun context(
        level: ProactiveLevel = ProactiveLevel.STANDARD,
        memory: ProactiveMemory = ProactiveMemory(),
        signals: ProactiveSignals = signals(),
        recentChatter: List<String> = emptyList(),
    ) = ProactiveContext(
        level = level,
        memory = memory,
        signals = signals,
        nowMs = nowMs,
        now = now,
        recentChatter = recentChatter,
    )

    // ── 档位 ──

    @Test
    fun `关档什么气泡都不生成`() {
        val ctx = context(
            level = ProactiveLevel.OFF,
            signals = signals(cardBalance = 1.0, nextCourseName = "高等数学", minutesToClass = 5),
        )
        assertNull(ProactiveRules.pick(ctx))
        assertNull(ProactiveRules.pickOnTap(ProactiveLevel.OFF, now, emptyList()))
    }

    @Test
    fun `少档不闲聊但保留正事`() {
        assertTrue(ProactiveRules.alertsAllowed(ProactiveLevel.LOW))
        assertEquals(false, ProactiveRules.chatterAllowed(ProactiveLevel.LOW))

        // 没有任何正事信号 → 少档一句话都不说（标准档这里会去闲聊）
        assertNull(ProactiveRules.pick(context(level = ProactiveLevel.LOW)))
    }

    @Test
    fun `标准档允许闲聊`() {
        assertTrue(ProactiveRules.chatterAllowed(ProactiveLevel.STANDARD))
    }

    @Test
    fun `少档把全局冷却从一刻钟拉到一小时`() {
        assertEquals(15 * 60 * 1000L, ProactiveRules.globalCooldownMs(ProactiveLevel.STANDARD))
        assertEquals(60 * 60 * 1000L, ProactiveRules.globalCooldownMs(ProactiveLevel.LOW))
    }

    // ── 冷却 ──

    @Test
    fun `全局冷却没过时一句都不说`() {
        val memory = ProactiveMemory(lastAnyAt = nowMs - 14 * 60 * 1000L)
        val ctx = context(
            memory = memory,
            signals = signals(nextCourseName = "高等数学", minutesToClass = 5),
        )
        assertNull("14 分钟前刚说过，标准档 15 分钟内不许再说", ProactiveRules.pick(ctx))
    }

    @Test
    fun `全局冷却过了才说`() {
        val memory = ProactiveMemory(lastAnyAt = nowMs - 16 * 60 * 1000L)
        val ctx = context(
            memory = memory,
            signals = signals(nextCourseName = "高等数学", minutesToClass = 5),
        )
        assertEquals(ProactiveRules.ID_CLASS, ProactiveRules.pick(ctx)?.id)
    }

    @Test
    fun `单条规则自己还有冷却`() {
        // 全局冷却已过，但这一条 10 分钟前刚冒过（单条冷却 1 小时）
        val memory = ProactiveMemory(
            lastAnyAt = nowMs - 60 * 60 * 1000L,
            lastShown = mapOf(ProactiveRules.ID_CLASS to nowMs - 10 * 60 * 1000L),
        )
        val ctx = context(
            memory = memory,
            signals = signals(nextCourseName = "高等数学", minutesToClass = 5),
        )
        // ⚠️ 断言落在 `pickAlert` 而不是 `pick`：后者在正事都冷却时会**转去闲聊**，
        // 于是"没正事可说"被闲聊掩盖，用例就测不到冷却了
        assertNull(ProactiveRules.pickAlert(ctx))
    }

    @Test
    fun `被关掉过就按次数翻倍冷却`() {
        val once = ProactiveRules.cooldownFor(ProactiveRules.ID_CLASS, dismissedCount = 1)
        val twice = ProactiveRules.cooldownFor(ProactiveRules.ID_CLASS, dismissedCount = 2)
        assertEquals(ProactiveRules.RULE_COOLDOWN_MS * 2, once)
        assertEquals(ProactiveRules.RULE_COOLDOWN_MS * 4, twice)
    }

    @Test
    fun `退避有上限`() {
        // 关掉 5 次也只放大到 8 倍（否则一个被反复关掉的提醒会永久消失得毫无道理）
        assertEquals(
            ProactiveRules.RULE_COOLDOWN_MS * 8,
            ProactiveRules.cooldownFor(ProactiveRules.ID_CLASS, dismissedCount = 5),
        )
    }

    @Test
    fun `考试用半天冷却`() {
        assertEquals(
            ProactiveRules.EXAM_COOLDOWN_MS,
            ProactiveRules.cooldownFor(ProactiveRules.ID_EXAM, dismissedCount = 0),
        )
    }

    // ── 优先级与前提 ──

    @Test
    fun `考试优先于快上课`() {
        val ctx = context(
            signals = signals(
                exam = ExamSignal(courseName = "高等数学", daysLeft = 1),
                nextCourseName = "大学英语",
                minutesToClass = 10,
            ),
        )
        assertEquals(ProactiveRules.ID_EXAM, ProactiveRules.pick(ctx)?.id)
    }

    @Test
    fun `高优先级那条在冷却中时后面的规则仍能冒`() {
        // 这是"列出候选再取第一条冷却已过的"的意义：
        // 若实现成"取最高优先级、冷却没过就放弃"，快上课会被考试一起憋住
        val memory = ProactiveMemory(
            lastShown = mapOf(ProactiveRules.ID_EXAM to nowMs - 1000L),
        )
        val ctx = context(
            memory = memory,
            signals = signals(
                exam = ExamSignal(courseName = "高等数学", daysLeft = 1),
                nextCourseName = "大学英语",
                minutesToClass = 10,
            ),
        )
        assertEquals(ProactiveRules.ID_CLASS, ProactiveRules.pick(ctx)?.id)
    }

    @Test
    fun `考试超出阈值不提醒`() {
        val far = context(
            signals = signals(exam = ExamSignal(courseName = "高等数学", daysLeft = 30)),
        )
        assertNull(ProactiveRules.pickAlert(far))
    }

    @Test
    fun `三十一分钟后的课不算快上课`() {
        val ctx = context(signals = signals(nextCourseName = "高等数学", minutesToClass = 31))
        assertNull(ProactiveRules.pickAlert(ctx))
    }

    @Test
    fun `余额达到阈值才提醒`() {
        assertNull(ProactiveRules.pickAlert(context(signals = signals(cardBalance = 60.0))))
        assertEquals(
            ProactiveRules.ID_BALANCE_CARD,
            ProactiveRules.pickAlert(context(signals = signals(cardBalance = 12.4)))?.id,
        )
    }

    @Test
    fun `新成绩提醒带门数`() {
        val message = ProactiveRules.pickAlert(context(signals = signals(newGradeCount = 3)))
        assertEquals("有 3 门新成绩出了", message?.text)
        assertTrue("新成绩要进对话，不能只是跳页面", message!!.prompt.isNotBlank())
    }

    @Test
    fun `快上课点气泡去课表而不是进对话`() {
        val message = ProactiveRules.pickAlert(
            context(signals = signals(nextCourseName = "高等数学", minutesToClass = 20)),
        )
        assertEquals("timetable", message?.openRoute)
        assertEquals("", message?.prompt)
    }

    // ── 文案与快照 ──

    @Test
    fun `文案封顶到 24 字并留省略号`() {
        val long = "毛泽东思想和中国特色社会主义理论体系概论"
        val message = ProactiveRules.pickAlert(
            context(signals = signals(nextCourseName = long, minutesToClass = 5)),
        )
        val text = message!!.text
        assertTrue("超长课名要被裁", text.length <= ProactiveMessage.MAX_CHARS)
        assertTrue("裁掉时要留省略号，用户才知道被裁了", text.endsWith("…"))
    }

    @Test
    fun `考试快照只写非空字段`() {
        val message = ProactiveRules.pickAlert(
            context(
                signals = signals(
                    exam = ExamSignal(
                        courseName = "高等数学",
                        daysLeft = 2,
                        date = "2026-10-10",
                        location = "A101",
                    ),
                ),
            ),
        )
        val snapshot = message!!.eventSnapshot.orEmpty()
        assertTrue(snapshot.contains("date: 2026-10-10"))
        assertTrue(snapshot.contains("location: A101"))
        assertTrue("空字段不该出现在快照里", !snapshot.contains("seat:"))
    }

    // ── 闲聊 ──

    @Test
    fun `闲聊要走同一个全局冷却`() {
        // 参考实现的教训：闲聊若只写自己的时间戳，就会绕开全局节流，
        // 正事气泡刚收、闲聊立刻接上 —— 用户看到它自顾自连着蹦
        val memory = ProactiveMemory(lastAnyAt = nowMs - 60 * 1000L)
        assertNull(ProactiveRules.pick(context(memory = memory)))
    }

    @Test
    fun `闲聊冷却未过时不闲聊`() {
        val memory = ProactiveMemory(
            lastAnyAt = nowMs - 30 * 60 * 1000L,
            lastShown = mapOf(ProactiveRules.CHATTER_ID to nowMs - 5 * 60 * 1000L),
        )
        assertNull(ProactiveRules.pickChatter(context(memory = memory)))
    }

    @Test
    fun `闲聊文案允许两行显示`() {
        val message = ProactiveRules.pickChatter(context())
        assertTrue("闲聊是完整一句话，不许被裁成一行", message!!.fullReveal)
        assertEquals(ProactiveRules.CHATTER_ID, message.id)
    }

    @Test
    fun `戳一下的回话绕开闲聊冷却但仍记录句子`() {
        val message = ProactiveRules.pickOnTap(ProactiveLevel.STANDARD, now, emptyList())
        assertTrue(message!!.chatterLineId != null)
    }

    @Test
    fun `戳一下不会重复最近说过的那句`() {
        val first = ProactiveRules.pickOnTap(ProactiveLevel.STANDARD, now, emptyList(), ChatterFacts())
        val again = ProactiveRules.pickOnTap(
            ProactiveLevel.STANDARD,
            now,
            recent = listOf(first!!.chatterLineId!!),
            facts = ChatterFacts(),
        )
        assertTrue(
            "刚说过的那句不该马上再来一遍",
            again == null || again.chatterLineId != first.chatterLineId,
        )
    }

    // ── 校历节点 ──

    @Test
    fun `校历节点排在最后不抢正事`() {
        // "离寒假还有 2 天" vs "5 分钟后上课"：后者必须赢。
        // 反过来的话，一句纯预报会把真正赶时间的那条憋住一轮 —— 那是这个排序唯一的用处。
        val message = ProactiveRules.pickAlert(
            context(
                signals = signals(
                    calendarNode = node(daysLeft = 2),
                    nextCourseName = "高等数学",
                    minutesToClass = 5,
                ),
            ),
        )
        assertEquals(ProactiveRules.ID_CLASS, message!!.id)
    }

    @Test
    fun `校历节点提前超过三天不说`() {
        assertNull(
            "还有 4 天才开学就提醒，等于没提醒",
            ProactiveRules.pickAlert(context(signals = signals(calendarNode = node(daysLeft = 4)))),
        )
        assertEquals(
            "第 3 天是窗口边界，必须说",
            ProactiveRules.ID_CALENDAR,
            ProactiveRules.pickAlert(context(signals = signals(calendarNode = node(daysLeft = 3))))!!.id,
        )
    }

    @Test
    fun `校历节点今天和明天说人话`() {
        val today = ProactiveRules.pickAlert(context(signals = signals(calendarNode = node(daysLeft = 0))))
        assertEquals("寒假今天开始", today!!.text)
        val tomorrow = ProactiveRules.pickAlert(context(signals = signals(calendarNode = node(daysLeft = 1))))
        assertEquals("寒假明天开始", tomorrow!!.text)
        val later = ProactiveRules.pickAlert(context(signals = signals(calendarNode = node(daysLeft = 3))))
        assertEquals("寒假还有 3 天", later!!.text)
    }

    @Test
    fun `校历节点把内容键带在消息上`() {
        // consume 就靠这个键写回游标。它丢了，这个节点会被无限重播；
        // 而 pickAlert 拿到的 memory 是只读快照，规则层自己补不回来。
        val signal = node(daysLeft = 2)
        val message = ProactiveRules.pickAlert(context(signals = signals(calendarNode = signal)))!!
        assertEquals(signal.key, message.seenKey)
        assertEquals("campus_calendar", message.openRoute)
        assertTrue("详情在校历页里，让鲤鱼复述一遍反而绕", message.prompt.isBlank())
    }

    @Test
    fun `刚说过一个节点就不要马上说下一个`() {
        // 内容键去重管的是"同一个节点别说两次"；这条管的是另一件事：
        // 两个节点挨得近时，别连着蹦两个气泡。
        val memory = ProactiveMemory(
            lastAnyAt = nowMs - 30 * 60 * 1000L,
            lastShown = mapOf(ProactiveRules.ID_CALENDAR to nowMs - 5 * 60 * 1000L),
        )
        assertNull(
            ProactiveRules.pickAlert(context(memory = memory, signals = signals(calendarNode = node(daysLeft = 2)))),
        )
    }

    // ── 天气 ──

    private fun weather(text: String = "正在下雨，出门记得带伞") =
        WeatherAlert(text = text, key = "rain@${now.toLocalDate()}")

    @Test
    fun `天气排在快上课后面校历前面`() {
        val all = signals(
            weatherAlert = weather(),
            nextCourseName = "高等数学",
            minutesToClass = 5,
            calendarNode = node(daysLeft = 2),
        )
        assertEquals(
            "5 分钟后上课比「正在下雨」更急",
            ProactiveRules.ID_CLASS,
            ProactiveRules.pickAlert(context(signals = all))?.id,
        )

        val classCooled = ProactiveMemory(
            lastShown = mapOf(ProactiveRules.ID_CLASS to nowMs - 1000L),
        )
        assertEquals(
            ProactiveRules.ID_WEATHER,
            ProactiveRules.pickAlert(context(memory = classCooled, signals = all))?.id,
        )

        val classAndWeatherCooled = ProactiveMemory(
            lastShown = mapOf(
                ProactiveRules.ID_CLASS to nowMs - 1000L,
                ProactiveRules.ID_WEATHER to nowMs - 1000L,
            ),
        )
        assertEquals(
            "一句纯预报不该抢在真正赶时间的那条前面，但也不该被它永远憋住",
            ProactiveRules.ID_CALENDAR,
            ProactiveRules.pickAlert(context(memory = classAndWeatherCooled, signals = all))?.id,
        )
    }

    @Test
    fun `天气气泡点开天气页并带回内容键`() {
        val alert = weather()
        val message = ProactiveRules.pickAlert(context(signals = signals(weatherAlert = alert)))!!

        assertEquals(alert.text, message.text)
        assertEquals("weather", message.openRoute)
        assertEquals("consume 靠这个键写回天气去重集合", alert.key, message.seenKey)
        assertTrue("天气页已有小时降水条，不依赖鲤鱼是否配置了 API Key", message.prompt.isBlank())
    }

    @Test
    fun `天气文案同样封顶`() {
        val long = "烟台市区今天傍晚到夜间有雷阵雨并伴有短时大风，出门记得带伞并注意安全"
        val message = ProactiveRules.pickAlert(context(signals = signals(weatherAlert = weather(long))))!!
        assertTrue(message.text.length <= ProactiveMessage.MAX_CHARS)
        assertTrue(message.text.endsWith("…"))
    }
}
