package com.pocket.xiaoli.ui.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「让鲤鱼看看」的提示词组装。
 *
 * 这个函数的输出会**直接进 LLM 的上下文**，所以校验点不是"好不好看"，而是
 * 三次线上踩过的坑：
 *
 * 1. 没崩溃时若不带任何说明，模型只会回"信息不足" —— 必须明确告诉它为什么没有堆栈；
 * 2. 崩溃日志可能几万行，不截断会一次请求打爆上下文窗口；
 * 3. 提示词必须**给出判断维度**，否则模型倾向于输出"建议重启 App"这类废话。
 */
class AnalyzeLogPromptTest {

    private val report = """
        诊断报告
        v1.0.62 (63)
        数据库版本：v15
        课程：42（方案 2 个）
    """.trimIndent()

    @Test
    fun promptContainsReportBodyVerbatim() {
        val prompt = buildAnalyzePrompt(report, null)
        assertTrue("报告正文必须原样带上", prompt.contains("v1.0.62 (63)"))
        assertTrue(prompt.contains("数据库版本：v15"))
    }

    @Test
    fun promptExplainsMissingCrashInsteadOfSilence() {
        val prompt = buildAnalyzePrompt(report, null)
        // 关键：要主动解释"为什么没有堆栈"，否则模型只会说信息不足
        assertTrue(prompt.contains("没有捕获到崩溃堆栈"))
        assertTrue("应提示去看网络日志与数据量", prompt.contains("网络日志"))
    }

    @Test
    fun blankCrashLogTreatedAsMissing() {
        assertTrue(buildAnalyzePrompt(report, "   ").contains("没有捕获到崩溃堆栈"))
    }

    @Test
    fun crashStackIsIncludedWhenPresent() {
        val stack = "java.lang.IllegalStateException: boom\n  at com.pocket.xiaoli.ui.services.ServicesScreenKt.x(ServicesScreen.kt:210)"
        val prompt = buildAnalyzePrompt(report, stack)
        assertTrue("类名必须保留，它是定位问题的唯一线索", prompt.contains("ServicesScreen.kt:210"))
        assertFalse("有堆栈时不应再说没有", prompt.contains("没有捕获到崩溃堆栈"))
    }

    @Test
    fun longCrashStackIsTruncated() {
        val huge = "x".repeat(60_000)
        val prompt = buildAnalyzePrompt(report, huge)
        // 12_000 的堆栈上限 + 提示词本身的固定开销，留足余量即可
        assertTrue("超长堆栈必须截断，否则会撑爆上下文窗口", prompt.length < 20_000)
    }

    @Test
    fun promptAsksForConcreteAnswers() {
        val prompt = buildAnalyzePrompt(report, null)
        assertTrue("要具体到类名与行号", prompt.contains("哪一行"))
        assertTrue("要区分崩溃/内存/卡顿", prompt.contains("崩溃、内存问题、还是网络/数据库卡顿"))
        assertTrue("要给出用户可执行的规避动作", prompt.contains("规避动作"))
        assertTrue("要明确禁止泛泛而谈", prompt.contains("不要泛泛而谈"))
    }
}
