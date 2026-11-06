package com.pocket.xiaoli.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 诊断包脱敏的测试。
 *
 * 这条链路会把数据文件整个打进 zip 给用户转发，所以「Key 不能出去」必须是断言出来的，
 * 不能靠导出一次界面看一眼 —— 而且这里必须用**非空 Key** 才测得到打码分支。
 */
class AppLogRedactionTest {

    @Test
    fun `非空 API Key 被替换成占位文本`() {
        val json = """{"settings":{"aiApiKey":"sk-SECRET-123","aiModel":"deepseek-chat"},"courses":[]}"""
        val out = AppLog.redactedJsonFrom(json)
        assertFalse("sk-SECRET-123" in out, "Key 不能留在诊断包里：$out")
        assertTrue("(已脱敏)" in out)
        assertTrue("deepseek-chat" in out, "模型名不是秘密，保留才有诊断价值")
    }

    @Test
    fun `空 Key 保持空而不是写成已脱敏`() {
        val out = AppLog.redactedJsonFrom("""{"settings":{"aiApiKey":""}}""")
        assertTrue("sk-" !in out)
        assertFalse("已脱敏" in out)
    }

    @Test
    fun `解析失败时宁可不给内容`() {
        val out = AppLog.redactedJsonFrom("[]")
        assertTrue(out.contains("解析失败"), out)
    }

    @Test
    fun `其余字段原样保留`() {
        val json = """{"settings":{"aiApiKey":"sk-X","studentNo":"202408501222"},"courses":[{"id":"c-1","name":"高等数学"}]}"""
        val out = AppLog.redactedJsonFrom(json)
        assertTrue("202408501222" in out, "学号要留着才能复现接口问题")
        assertTrue("高等数学" in out)
        assertEquals(false, out.contains("sk-X"))
    }
}
