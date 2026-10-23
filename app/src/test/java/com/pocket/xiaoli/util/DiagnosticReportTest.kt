package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 诊断报告：重点验证脱敏不漏，以及排版稳定。 */
class DiagnosticReportTest {

    private fun sample(error: String? = null) = DiagnosticInput(
        appVersionName = "1.0.14",
        appVersionCode = 15,
        androidRelease = "14",
        sdkInt = 34,
        deviceModel = "Pixel 6",
        dbVersion = 12,
        courseCount = 42,
        schemeCount = 2,
        timeSlotCount = 12,
        homeworkCount = 3,
        taskCount = 5,
        savedGradeCount = 30,
        roomCount = 88,
        poiCount = 12,
        recentError = error,
    )

    @Test
    fun redactsLongDigitRuns() {
        assertEquals("学号 ***", DiagnosticReport.redact("学号 2024123456"))
        assertEquals("短数字保留 123456", "短数字保留 123456", DiagnosticReport.redact("短数字保留 123456"))
    }

    @Test
    fun redactsAbsolutePaths() {
        assertEquals("at <path>", DiagnosticReport.redact("at /data/user/0/com.pocket.xiaoli/files/a.txt"))
        assertEquals("at <path>", DiagnosticReport.redact("at D:\\AICAN\\YLi\\app\\build"))
    }

    @Test
    fun redactsEmailButKeepsFirstCharAndDomain() {
        assertEquals("z***@outlook.com", DiagnosticReport.redact("zsh0908@outlook.com"))
    }

    @Test
    fun reportContainsKeySections() {
        val text = DiagnosticReport.build(sample(), LocalDateTime.of(2026, 9, 20, 21, 30, 0))
        assertTrue(text.contains("诊断报告"))
        assertTrue(text.contains("2026-09-20 21:30:00"))
        assertTrue(text.contains("v1.0.14 (15)"))
        assertTrue(text.contains("数据库版本：v12"))
        assertTrue(text.contains("课程：42（方案 2 个）"))
        assertTrue(text.contains("教室：88"))
    }

    @Test
    fun missingErrorIsReportedExplicitly() {
        val text = DiagnosticReport.build(sample(null))
        assertTrue(text.contains("【最近错误】"))
        assertTrue(text.contains("（无）"))
    }

    @Test
    fun errorTextIsRedacted() {
        val text = DiagnosticReport.build(
            sample("Caused by: java.lang.IllegalStateException\n  at /data/user/0/x/y.kt:12\n学号 2024123456")
        )
        assertFalse("不应出现原始学号", text.contains("2024123456"))
        assertFalse("不应出现原始路径", text.contains("/data/user/0"))
    }

    @Test
    fun errorTextIsTruncated() {
        val text = DiagnosticReport.build(sample("x".repeat(5000)))
        assertTrue("超长错误应被截断", text.length < 3000)
    }

    @Test
    fun blankErrorTreatedAsNone() {
        assertTrue(DiagnosticReport.build(sample("   ")).contains("（无）"))
    }
}