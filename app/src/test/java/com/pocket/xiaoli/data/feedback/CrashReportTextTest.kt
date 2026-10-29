package com.pocket.xiaoli.data.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 崩溃日志的两个纯函数：摘要把堆栈压成一行，脱敏把本机路径抹掉。
 *
 * 这两件事一旦写错，后果都不是"显示难看"：摘错了会把 `at com.…` 当成标题，
 * 脱敏漏了会把学号一起发到服务器。所以用真实形状的输入钉住。
 */
class CrashReportTextTest {

    @Test
    fun `headline 取第一条异常，不取 at 行和 caused by`() {
        val log = """
            java.lang.RuntimeException: Unable to start activity ComponentInfo{com.pocket.xiaoli/.MainActivity}
            at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3729)
            Caused by: java.lang.IllegalStateException: WebView was not initialised
            at com.pocket.xiaoli.CoursewareScreen.onCreate(CoursewareScreen.kt:120)
        """.trimIndent()

        assertEquals(
            "java.lang.RuntimeException: Unable to start activity ComponentInfo{com.pocket.xiaoli/.MainActivity}",
            CrashReportClient.headline(log),
        )
    }

    @Test
    fun `headline 在没有异常行时退回第一行非空文本`() {
        val log = "\n\n  native crash (SIGSEGV) at libmonochrome.so\nstack follows"
        assertEquals("native crash (SIGSEGV) at libmonochrome.so", CrashReportClient.headline(log))
    }

    @Test
    fun `sanitize 抹掉安卓下载目录里的学号`() {
        val log = "cause: /storage/emulated/0/Android/data/com.pocket.xiaoli/files/Download/2023123456/crash_1.txt"
        val out = CrashReportClient.sanitize(log)

        assertFalse("学号不应出现在日志里", out.contains("2023123456"))
        assertTrue("文件名要留下，才能对上用户说的那次崩溃", out.endsWith("…/crash_1.txt"))
    }

    @Test
    fun `sanitize 抹掉 Windows 用户目录`() {
        val out = CrashReportClient.sanitize("""at D:\Users\liulx\AppData\Local\app.log""")
        assertFalse(out.contains("liulx"))
        assertTrue(out.contains("app.log"))
    }

    @Test
    fun `sanitize 不动普通堆栈行`() {
        val line = "at com.pocket.xiaoli.ui.map.MapScreen(MapScreen.kt:88)"
        assertEquals(line, CrashReportClient.sanitize(line))
    }
}
