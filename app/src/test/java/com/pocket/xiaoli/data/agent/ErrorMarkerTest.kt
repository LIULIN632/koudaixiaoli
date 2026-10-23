package com.pocket.xiaoli.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错误标记的出口卫生。
 *
 * 为什么必须守住：[ERROR_MARKER_TOOL_NAME] 是**界面用的**内部标记（据此把失败的
 * 一轮渲染成错误卡片），它借用了 assistant 行的 `tool_name` 字段。一旦漏到请求体里，
 * 模型会把这串 `__error__` 当成工具名，行为不可预期。
 * 这个泄漏在真机上表现为"模型偶尔答得莫名其妙"，几乎不可能人工定位，只能靠单测。
 */
class ErrorMarkerTest {

    @Test
    fun `带标记的历史消息会被摘掉标记`() {
        val marked = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "模型请求失败（HTTP 401）：API Key 可能不对",
            toolName = ERROR_MARKER_TOOL_NAME,
        )

        val clean = marked.withoutErrorMark()

        assertEquals(null, clean.toolName)
        // 正文必须原样保留：它是给用户看的错误文案，也是模型读到的历史
        assertEquals(marked.content, clean.content)
    }

    @Test
    fun `真实工具名不会被误摘`() {
        val tool = ChatMessage(role = ChatRole.TOOL, content = "今天的课：…", toolName = "get_today_courses")

        assertEquals("get_today_courses", tool.withoutErrorMark().toolName)
    }

    @Test
    fun `没有标记的消息原样返回`() {
        val plain = ChatMessage(role = ChatRole.ASSISTANT, content = "今天没课。")

        assertEquals(plain, plain.withoutErrorMark())
    }

    @Test
    fun `标记值带有不可能与真实工具名撞车的形状`() {
        // 真实工具名全是 snake_case 英文（get_today_courses 等），且不以双下划线开头；
        // 这条断言在有人改标记值时会提醒他重新确认这一点
        assertTrue(
            "错误标记要用不可能与真实工具名撞车的形状：$ERROR_MARKER_TOOL_NAME",
            ERROR_MARKER_TOOL_NAME.startsWith("__") && ERROR_MARKER_TOOL_NAME.endsWith("__"),
        )
    }
}
