package com.pocket.xiaoli.desktop.ui.util

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 桌面平台能力：开链接、剪贴板、文件选择。 */
object Platform {

    /** 用系统默认浏览器打开链接；返回是否真的交出去了（用于给用户如实反馈）。 */
    fun openUrl(url: String): Boolean = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(java.net.URI(url))
            true
        } else {
            false
        }
    }.getOrDefault(false)

    /** 用系统文件管理器打开一个目录（数据目录）；不存在就建出来。 */
    fun openDirectory(dir: File) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir)
            }
        }
    }

    fun copyToClipboard(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }

    /** 保存文本文件。返回保存路径；用户取消返回 null。 */
    fun saveTextFile(defaultName: String, content: String): String? {
        val dialog = FileDialog(null as Frame?, "导出", FileDialog.SAVE).apply {
            file = defaultName
            isVisible = true
        }
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        val target = File(dir, name)
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeText(content, Charsets.UTF_8)
            target.absolutePath
        }.getOrNull()
    }

    /** 选一个文本文件并读回内容；取消返回 null。 */
    fun readTextFile(): String? {
        val dialog = FileDialog(null as Frame?, "导入", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.endsWith(".json") || name.endsWith(".txt") }
            isVisible = true
        }
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return runCatching { File(dir, name).readText(Charsets.UTF_8) }.getOrNull()
    }
}

object Format {

    private val MD = DateTimeFormatter.ofPattern("M月d日")
    private val YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekday(dayOfWeek: Int): String = WEEKDAYS.getOrElse((dayOfWeek - 1).coerceIn(0, 6)) { "周一" }

    fun monthDay(date: LocalDate): String = date.format(MD)

    fun ymd(date: LocalDate): String = date.format(YMD)

    /** 缓存时间戳给 UI 看的写法：`11-02 14:20`（同年不重复写年份）。 */
    fun dateTime(millis: Long): String =
        java.text.SimpleDateFormat("MM-dd HH:mm").format(java.util.Date(millis))

    fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 5..8 -> "早上好"
        in 9..11 -> "上午好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }

    /** 第几周的说法；0/负数要能读出「未开学」。 */
    fun weekLabel(week: Int, totalWeeks: Int = 0): String = when {
        week <= 0 -> "未开学"
        totalWeeks > 0 && week > totalWeeks -> "学期已结束"
        else -> "第 $week 周"
    }

    /** 相对天数：今天 / 明天 / 3 天后 / 已过去 2 天。 */
    fun relativeDays(target: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, target).toInt()
        return when {
            days == 0 -> "今天"
            days == 1 -> "明天"
            days == 2 -> "后天"
            days == -1 -> "昨天"
            days > 0 -> "$days 天后"
            else -> "${-days} 天前"
        }
    }

    /**
     * 学号脱敏：规则在共享层（`core/text/StudentIdMask.kt`），这里只是给界面留一个短名字。
     *
     * 行为变化（2026-10）：桌面端原来抄的是「前 3 + 后 2」（`202****89`），与手机端
     * 「末 4 位」（`*********6789`）**不一致** —— 同一个学号两种遮法会让用户怀疑账号。
     * 现在统一按共享层规则，桌面显示形如 `*********6789`。
     */
    fun maskStudentNo(no: String): String = com.pocket.xiaoli.core.text.maskStudentId(no)

    fun initials(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "鲤"
        return trimmed.take(1)
    }

    fun minutesText(totalMinutes: Int): String = "%d 小时 %d 分".format(totalMinutes / 60, totalMinutes % 60)

    fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    /** 相对时间："3 分钟前" / "昨天 08:12" / "10-24 09:30"。 */
    /**
     * 会话列表的日期标签：今天 / 昨天 / 前天 / M月d日（与手机端同一套划天规则，
     * 规则在 `core/text/ConversationDayLabelCore.kt`，固定按东八区划天）。
     *
     * 为什么不用 [ago]：`3 小时前` 在会话列表里读不出「今天还是昨天」，手机端一直用日期档。
     */
    fun conversationDay(millis: Long, now: Long = System.currentTimeMillis()): String =
        com.pocket.xiaoli.core.text.conversationDayLabel(
            millis = millis,
            nowMillis = now,
            formatMonthDay = { m ->
                val d = java.time.Instant.ofEpochMilli(m).atZone(java.time.ZoneId.systemDefault())
                "${d.monthValue}月${d.dayOfMonth}日"
            },
        )

    /**
     * 相对时间：分档在共享层（`core/text/RelativeTimeCore.kt`），这里只提供「超过 30 天后
     * 用哪种日期格式」。桌面端保留更精确的 `MM-dd HH:mm`（待办/对话里需要看到具体时刻）。
     *
     * 行为变化（2026-10）：原来桌面超过 7 天就报日期，与 Android 的 30 天口径不一致；现统一 30 天。
     */
    fun ago(millis: Long, now: Long = System.currentTimeMillis()): String =
        com.pocket.xiaoli.core.text.relativeSinceLabel(
            pastMillis = millis,
            nowMillis = now,
            formatDate = { m ->
                val t = java.time.Instant.ofEpochMilli(m).atZone(java.time.ZoneId.systemDefault())
                "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
            },
        )
}
