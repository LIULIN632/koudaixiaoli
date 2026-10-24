package com.pocket.xiaoli.ui.assistant

import com.pocket.xiaoli.data.knowledge.AssistantTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 追问选词器的单测。
 *
 * 它决定"回答下面摆哪三个问题"，而这段逻辑最容易坏在**边界**：
 * 相关工具一条都没命中、相关问题被问光了、上限为 0。
 * 这些在真机上要复现得先聊好几轮，所以用纯函数直接压。
 */
class FollowUpPlannerTest {

    private fun titles(
        tools: Set<AssistantTool>,
        asked: List<String> = emptyList(),
        limit: Int = 3,
    ): List<String> = FollowUpPlanner.plan(tools, asked, limit).map { it.title }

    @Test
    fun `本轮查了待办时优先推荐待办追问`() {
        assertEquals("还有什么作业没做？", titles(setOf(AssistantTool.TODOS)).first())
    }

    @Test
    fun `相关追问不足时用其余问题补齐`() {
        val result = titles(setOf(AssistantTool.WEATHER))

        assertEquals(3, result.size)
        assertEquals(listOf("今天要带伞吗？", "明天穿什么？"), result.take(2))
        assertEquals("今天有什么课？", result[2])
    }

    @Test
    fun `没有工具上下文时按候选词原顺序兜底`() {
        assertEquals(
            listOf("今天有什么课？", "下节课在哪？", "还有什么作业没做？"),
            titles(emptySet()),
        )
    }

    @Test
    fun `问过的问题不再推荐`() {
        val result = titles(setOf(AssistantTool.TODAY_COURSES), asked = listOf("今天有什么课"))

        assertTrue(result.none { it == "今天有什么课？" })
        assertEquals("下节课在哪？", result.first())
    }

    @Test
    fun `问句的大小写空白与句末标点不影响去重`() {
        val result = titles(emptySet(), asked = listOf("  A101  在哪? "))

        assertTrue(result.none { it == "A101 在哪？" })
    }

    @Test
    fun `limit 限制条数且非正数返回空`() {
        assertEquals(2, titles(emptySet(), limit = 2).size)
        assertTrue(titles(emptySet(), limit = 0).isEmpty())
        assertTrue(titles(emptySet(), limit = -1).isEmpty())
    }

    @Test
    fun `推荐的每一条都来自唯一候选词源`() {
        val all = ASSISTANT_PROMPTS.map { it.title }.toSet()
        val result = titles(emptySet(), limit = 100)

        assertTrue(result.all { it in all })
        assertEquals(ASSISTANT_PROMPTS.size, result.size)
    }

    @Test
    fun `每个候选词都登记了工具以便参与排序`() {
        assertTrue(ASSISTANT_PROMPTS.all { it.relatedTools.isNotEmpty() })
    }
}
