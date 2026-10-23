package com.pocket.xiaoli.data.agent

import com.google.gson.JsonObject
import com.pocket.xiaoli.data.knowledge.AssistantTool
import com.pocket.xiaoli.data.knowledge.ToolOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 工具注册表单测。
 *
 * 这里守的是三条**模型看得见、用户也看得见**的行为：
 * 未知工具不能执行、坏参数不能崩、执行失败必须如实回灌而不是抛穿。
 * 还有一条容易漏的：超长结果必须截断，否则一次知识检索就能把上下文预算吃光。
 */
class AssistantToolRegistryTest {

    private fun spec(
        name: String,
        handler: suspend (JsonObject) -> ToolOutcome,
    ) = AssistantToolSpec(
        tool = AssistantTool.TODAY_COURSES,
        name = name,
        description = "测试用工具",
        parameters = JsonObject().apply { addProperty("type", "object") },
        handler = handler,
    )

    private fun registry(vararg specs: AssistantToolSpec) = AssistantToolRegistry(specs.toList())

    @Test
    fun `未知工具返回可读错误而不是抛异常`() = runBlocking {
        val result = registry(spec("known") { ToolOutcome("ok") }).execute("missing", "{}")

        assertTrue("错误文案要带上工具名", result.text.contains("不存在名为 missing"))
        assertTrue("错误文案要列出可用工具", result.text.contains("known"))
        assertNull("失败路径不能带卡片", result.card)
    }

    @Test
    fun `参数不是合法 JSON 时按空参数执行`() = runBlocking {
        var argCount = -1
        val result = registry(spec("echo") { args ->
            argCount = args.size()
            ToolOutcome("ok")
        }).execute("echo", "{这不是 json")

        assertEquals("坏参数不应让工具收到乱七八糟的东西", 0, argCount)
        assertEquals("ok", result.text)
    }

    @Test
    fun `执行体抛异常转成失败文本且不带卡片`() = runBlocking {
        val result = registry(spec("boom") { error("数据库没开") }).execute("boom", "{}")

        assertTrue(result.text.contains("执行失败"))
        assertTrue("要把根因带给模型", result.text.contains("数据库没开"))
        assertNull(result.card)
    }

    @Test
    fun `协程取消必须穿透而不是被吞成失败文本`() {
        val registry = registry(spec("slow") { throw CancellationException("用户点了停止") })

        try {
            runBlocking { registry.execute("slow", "{}") }
            fail("CancellationException 应当原样抛出")
        } catch (e: CancellationException) {
            assertEquals("用户点了停止", e.message)
        }
    }

    @Test
    fun `超长结果按行截断并带标记`() = runBlocking {
        // 1200 行 × 4 字符 ≈ 4.8k，必然超过 4000 上限
        val line = "第x行\n"
        val result = registry(spec("long") { ToolOutcome(line.repeat(1_200)) })
            .execute("long", "{}")

        assertTrue(
            "结果必须被截到上限附近，实际 ${result.text.length}",
            result.text.length <= AssistantToolRegistry.MAX_TOOL_RESULT_CHARS + 32,
        )
        assertTrue("要有截断标记", result.text.endsWith("…[结果过长已截断]"))
        assertTrue("不能从半行中间切", result.text.removeSuffix("\n…[结果过长已截断]").endsWith("行"))
    }

    @Test
    fun `未超上限的结果原样返回`() = runBlocking {
        val text = "今天有 2 节课。"
        val result = registry(spec("short") { ToolOutcome(text) }).execute("short", "{}")

        assertEquals(text, result.text)
    }
}
