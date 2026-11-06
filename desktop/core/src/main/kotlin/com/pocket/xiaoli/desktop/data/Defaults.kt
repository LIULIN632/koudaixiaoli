package com.pocket.xiaoli.desktop.data

import com.pocket.xiaoli.desktop.logic.autoColorIndex
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 默认 12 节次时间 —— **唯一实现在共享模块**（`com.pocket.xiaoli.core.DefaultTimeSlots`）。
 *
 * 这里只做「共享模型 → 桌面模型」的映射，不再保留第二份时间表。
 */
val DefaultTimeSlots: List<TimeSlot> = com.pocket.xiaoli.core.DefaultTimeSlots.map {
    TimeSlot(index = it.slot, label = "第${it.slot}节", start = it.startTime, end = it.endTime)
}
/** 节次分组：1-4 上午 / 5-8 下午 / 9-12 晚上。 */
fun slotGroup(index: Int): String = when (index) {
    in 1..4 -> "上午"
    in 5..8 -> "下午"
    else -> "晚上"
}

val WeekDayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun dayOfWeekLabel(day: Int): String = WeekDayLabels.getOrElse((day - 1).coerceIn(0, 6)) { "周一" }

/** 可选强调色（与移动端 8 个预设同源的 8 个色号）。 */
val AccentPresets: List<Pair<String, String>> = listOf(
    "" to "口袋小鲤",
    "#4D6BFE" to "海盐蓝",
    "#4D8DFF" to "清透蓝",
    "#2EA37A" to "青竹绿",
    "#E07A5F" to "陶土橙",
    "#C77DFF" to "紫藤紫",
    "#F48FB1" to "樱花粉",
    "#8B95A8" to "烟灰蓝",
)

/** 首启示例数据：当前学期 + 一套烟台理工风格的课表 + 几条待办。 */
fun seedData(today: LocalDate = LocalDate.now()): AppData {
    val termId = "term-${today.year}-1"
    val startMonday = today.with(DayOfWeek.MONDAY).minusWeeks(8)
    val term = Term(
        id = termId,
        name = "${today.year}-${today.year + 1} 学年 第一学期",
        startDate = startMonday.toString(),
        totalWeeks = 20,
        sortOrder = 0,
    )

    fun course(
        name: String,
        teacher: String,
        room: String,
        day: Int,
        start: Int,
        end: Int,
        weeks: String = "",
        weekType: String = "all",
        short: String = "",
    ) = Course(
        id = "c-" + name.hashCode().toString(16) + "-$day",
        termId = termId,
        name = name,
        teacher = teacher,
        room = room,
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
        weeks = weeks,
        weekType = weekType,
        colorIndex = autoColorIndex(name),
        shortName = short,
    )

    val courses = listOf(
        course("计算机网络", "王海涛", "三教 A-302", 1, 1, 2, "1-16"),
        course("数据结构", "李慧敏", "三教 B-201", 1, 3, 4, "1-16"),
        course("大学英语（三）", "Sarah", "外语楼 305", 1, 5, 6, "1-15"),
        course("概率论与数理统计", "孙志强", "五教 C-108", 2, 1, 2, "1-16"),
        course("操作系统", "张博文", "三教 A-401", 2, 3, 4, "1-16"),
        course("体育（三）", "赵磊", "东操场", 2, 7, 8, "1-12"),
        course("数据库原理", "陈晓东", "实验楼 402", 3, 3, 4, "1-16"),
        course("马克思主义基本原理", "周文娟", "六教 D-201", 3, 5, 6, "1-16"),
        course("软件工程", "刘一鸣", "三教 A-205", 4, 1, 2, "2-16"),
        course("Java 程序设计", "何静", "实验楼 305", 4, 3, 4, "1-14"),
        course("编译原理", "郑凯", "三教 B-303", 5, 1, 2, "1-16"),
        course("形势与政策", "杨帆", "六教 D-105", 5, 3, 4, "1-8"),
        course("统计学实验", "孙志强", "实验楼 401", 5, 5, 6, "3-12", weekType = "odd"),
    )

    val terms = listOf(term, Term("term-${today.year}-2", "${today.year}-${today.year + 1} 学年 第二学期", startMonday.plusWeeks(20).toString(), 20, 1))

    val todos = listOf(
        Todo("t1", "复习数据结构第 5 章", today.plusDays(1).toString(), priority = 1, courseName = "数据结构", createdAt = System.currentTimeMillis()),
        Todo("t2", "计算机网络实验报告（第三次）", today.plusDays(3).toString(), priority = 2, courseName = "计算机网络", createdAt = System.currentTimeMillis()),
        Todo("t3", "图书馆还书：《现代操作系统》", today.plusDays(2).toString(), createdAt = System.currentTimeMillis()),
        Todo("t4", "英语四级单词打卡", today.toString(), done = true, createdAt = System.currentTimeMillis()),
    )

    return AppData(
        origin = "seed",
        terms = terms,
        activeTermId = termId,
        courses = courses,
        todos = todos,
        slots = DefaultTimeSlots,
        settings = AppSettings(activeTermId = termId),
    )
}
