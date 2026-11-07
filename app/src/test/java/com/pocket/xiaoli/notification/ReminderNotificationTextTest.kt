package com.pocket.xiaoli.notification

import com.pocket.xiaoli.util.DeepLinkDebug
import com.pocket.xiaoli.util.DeepLinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 到期提醒的**文案**与**深链**。
 *
 * 两条都属于"错了不会崩、只在锁屏上错"的那一类：
 * - 时间与教室写反 —— 上课前走进错的楼；
 * - 深链 host 改了名 —— 点通知打开 App 停在首页，没人知道本该去课表。
 *
 * 所以文案抽成纯函数逐字钉住，深链直接拿 `DeepLinkDebug` 的解析器验一遍
 *（解析器是运行期的真源，不是另抄一份字符串）。
 */
class ReminderNotificationTextTest {

    @Test
    fun `上课提醒正文按 时间 · 教室 拼接`() {
        assertEquals("09:50 · 一教305", classReminderBody("09:50", "一教305"))
    }

    @Test
    fun `缺一段时不留多余分隔符`() {
        assertEquals("09:50", classReminderBody("09:50", ""))
        assertEquals("一教305", classReminderBody("", "  一教305  "))
    }

    @Test
    fun `两段都空时给一句兜底而不是空白通知`() {
        assertEquals("该去上课了", classReminderBody("", ""))
        assertEquals("该去上课了", classReminderBody("   ", "  "))
    }

    @Test
    fun `截止提醒正文合并内容与截止时间`() {
        assertEquals("P45 习题 3-5  今天 23:59", deadlineBody("P45 习题 3-5", "今天 23:59"))
        assertEquals("今天 23:59", deadlineBody("", "今天 23:59"))
        assertEquals("打开 App 查看详情", deadlineBody("", ""))
    }

    @Test
    fun `点通知落到课表页而不是首页`() {
        val target = DeepLinkDebug.resolve(ReminderNotifications.DEEP_LINK_TIMETABLE)
        assertTrue("深链解析失败：$target", target is DeepLinkTarget.Route)
        target as DeepLinkTarget.Route
        assertEquals("tab", target.routeKey)
        assertEquals("timetable", target.args["tab"])
    }
}
