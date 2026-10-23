package com.pocket.xiaoli.data.proactive

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 一句闲聊。
 *
 * [hours] / [weekdays] 为空表示"什么时候都能说"。
 * ⚠️ **大量句子带时段限定是有意的**：无条件句会永远躺在候选池里，
 * 把有时段的生活句挤掉（参考实现里宣传句一度占到 30%，就是这么来的）。
 */
data class ChatterLine(
    val id: String,
    val text: String,
    /** 只在小时落在这个闭区间时说（如 `7..9` 的早八吐槽）。 */
    val hours: IntRange? = null,
    /** 只在月份落在这里时说（如考试月的句子）。 */
    val months: Set<Int>? = null,
    val weekdays: Set<DayOfWeek>? = null,
    /** 需要知道"今天有几节课"才有意义；不知道时这条不参与抽取。 */
    val needsCourseCount: Boolean = false,
    val weight: Double = 1.0,
)

/** 抽中的一句（id 用于"最近说过的不再说"）。 */
data class ChatterPick(val id: String, val text: String)

/**
 * 鲤鱼的闲聊池（**纯本地内容，不调模型**）。
 *
 * ## 为什么是自己写的一份，而不是照搬参考实现
 *
 * 参考实现的 `ChatterPool.kt` 有 431 行，内容全是西交语境（樱花节、兴庆/雁塔、
 * 保研九推、夏令营）。那种句子搬到烟台理工来是**错的信息**，对用户来说是噪音。
 * 所以这里重写了一份：烟台理工 / 烟大南校的日常、鲤鱼自己的性格、
 * 以及一句都不编的"事实句"（今天几节课、今天放假 —— 这两个是真数据）。
 *
 * ## 内容纪律（改这个文件前先看）
 *
 * 1. **不编事实**：不写具体的食堂窗口、店铺、价格、天气。不知道的事不许说
 *    （本项目的用户可见文案已经被抓过一次"宣称支持五套教务系统"的谎）。
 * 2. **不调侃正事**：成绩、考勤、挂科一律不拿来开玩笑。
 * 3. **不推销**：不在这池子里引导用户去用某个功能（要引导就走正事提醒）。
 * 4. 句子必须**短**：它们以 [ProactiveMessage.fullReveal]（最多两行）显示。
 */
object ChatterPool {

    val lines: List<ChatterLine> = listOf(
        // ── 时段：早 ──
        ChatterLine("early_rise", "早八人，起床啦", hours = 6..9),
        ChatterLine("early_breakfast", "别忘了吃早饭，一上午的课呢", hours = 6..9),
        ChatterLine("early_fog", "这么早就醒了，今天一定很顺", hours = 6..9, weight = 0.6),

        // ── 时段：中午 ──
        ChatterLine("noon_lunch", "到饭点了，别在教室硬撑", hours = 11..13),
        ChatterLine("noon_nap", "中午眯二十分钟，下午能顶两小时", hours = 12..14, weight = 0.7),

        // ── 时段：下午 ──
        ChatterLine("pm_sleepy", "这个点最容易犯困，起来接杯水", hours = 14..16),
        ChatterLine("pm_library", "下午适合去图书馆把作业解决掉", hours = 14..17, weight = 0.7),

        // ── 时段：晚上 ──
        ChatterLine("eve_done", "今天的课都上完了吗", hours = 18..21, needsCourseCount = true),
        ChatterLine("eve_homework", "晚上安静，趁现在把作业写了吧", hours = 19..22),
        ChatterLine("eve_rest", "忙完就早点休息，明天还有课", hours = 21..23),

        // ── 时段：深夜 ──
        ChatterLine("night_late", "这个点了，明天早八还起得来吗", hours = 23..23),
        ChatterLine("night_sleep", "夜深了，作业和代码都先放放", hours = 0..2),
        ChatterLine("night_insomnia", "还不睡？那我陪你待一会儿", hours = 0..3, weight = 0.6),

        // ── 周节奏 ──
        ChatterLine(
            "weekend_sat", "周末了，别把作业全留到周日晚上", weekdays = setOf(DayOfWeek.SATURDAY),
        ),
        ChatterLine(
            "weekend_sun", "明天又是周一，今晚收收心", weekdays = setOf(DayOfWeek.SUNDAY),
        ),
        ChatterLine("monday", "新的一周，先看看这周有几节课吧", weekdays = setOf(DayOfWeek.MONDAY), weight = 0.8),
        ChatterLine("friday", "周五了，再撑一天", weekdays = setOf(DayOfWeek.FRIDAY), weight = 0.8),

        // ── 考试月（12 月 / 1 月 / 6 月）──
        ChatterLine(
            "exam_month", "期末快到了，绩点保卫战开始",
            months = setOf(12, 1, 6), weight = 0.6,
        ),

        // ── 鲤鱼的脾气（低权重：它是搭句话，不是自我介绍）──
        ChatterLine("koi_not_lucky", "我是鲤鱼，不是锦鲤，许愿得自带实力", weight = 0.35),
        ChatterLine("koi_poke", "点我一下我就有反应，别客气", weight = 0.35),
        ChatterLine("koi_fish", "摸鱼可以，别把我摸走了", weight = 0.3),
        ChatterLine("koi_here", "我一直在这儿，有事喊我", weight = 0.4),
        ChatterLine("koi_water", "鱼离不开水，我离不开你的课表", weight = 0.3),

        // ── 勾一句对话（不指向具体功能名的推销，只是"你可以问我"）──
        ChatterLine("ask_me", "课表、成绩、教室在哪，都可以问我", weight = 0.5),
        ChatterLine("ask_any", "有想问的随时叫我", weight = 0.5),
    )

    /** 进入候选池的句子（时段 / 月份 / 星期 / 事实依赖都满足）。测试直接查这个。 */
    fun eligible(now: LocalDateTime, facts: ChatterFacts): List<ChatterLine> =
        (lines + situational(facts)).filter { line ->
            (line.hours == null || now.hour in line.hours) &&
                (line.months == null || now.monthValue in line.months) &&
                (line.weekdays == null || now.dayOfWeek in line.weekdays) &&
                (!line.needsCourseCount || facts.todayCourseCount != null)
        }

    /**
     * 由**真实数据**生成的情景句（不知道就不生成）。
     *
     * 这里是整个池子里唯一允许出现具体数字的地方 —— 数字来自本地课表，
     * 不是编的。放假那天不排课，所以"今天没课"与放假句可能同时出现，
     * 两条都真，谁被抽中都成立。
     */
    private fun situational(facts: ChatterFacts): List<ChatterLine> = buildList {
        facts.todayHoliday?.let { holiday ->
            add(ChatterLine("holiday_$holiday", "今天$holiday，课表已经按放假算了"))
        }
        when (facts.todayCourseCount) {
            0 -> add(ChatterLine("no_class_today", "今天没课，睡到自然醒吧"))
            1 -> add(ChatterLine("one_class_today", "今天只有一节课，很轻快"))
            in 5..Int.MAX_VALUE -> add(
                ChatterLine("heavy_day", "今天有 ${facts.todayCourseCount} 节课，撑住")
            )
        }
    }

    /**
     * 抽一句。
     *
     * ① 先过滤出此刻合法的句子；② 排除最近说过的（[recent]，最多记 12 条）；
     * ③ 按权重随机。
     *
     * ③ 用**真随机**而不是"按时间取模"：参考实现踩过这个坑 —— 确定性索引会让
     * 相邻的句子在三分钟窗口里连着蹦出来，用户很容易看出规律。
     * 防重复是 ② 的职责，不该由"取模"兼职。
     *
     * 候选被 ② 清空时**退回不过滤的一版**：宁可说一句刚说过的，
     * 也不要因为它哑掉（池子小的时候这是真会发生的）。
     */
    fun pick(
        now: LocalDateTime,
        recent: List<String> = emptyList(),
        facts: ChatterFacts = ChatterFacts(),
        random: Random = Random.Default,
    ): ChatterPick? {
        val all = eligible(now, facts)
        if (all.isEmpty()) return null
        val fresh = all.filterNot { it.id in recent }
        val picked = weightedPick(fresh.ifEmpty { all }, random) ?: return null
        return ChatterPick(picked.id, picked.text)
    }

    private fun weightedPick(lines: List<ChatterLine>, random: Random): ChatterLine? {
        if (lines.isEmpty()) return null
        val total = lines.sumOf { it.weight }
        if (total <= 0.0) return lines.first()
        var roll = random.nextDouble() * total
        for (line in lines) {
            roll -= line.weight
            if (roll <= 0.0) return line
        }
        return lines.last()
    }
}
