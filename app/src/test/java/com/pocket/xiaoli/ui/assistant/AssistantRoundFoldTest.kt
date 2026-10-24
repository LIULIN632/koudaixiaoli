package com.pocket.xiaoli.ui.assistant

import com.pocket.xiaoli.data.agent.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ask(text: String, id: String = text) =
    AssistantUiMessage(ChatRole.USER, text, id = id)

private fun say(text: String, id: String = text) =
    AssistantUiMessage(ChatRole.ASSISTANT, text, id = id)

private fun badAnswer(text: String = "请求失败", id: String = "err") =
    AssistantUiMessage(ChatRole.ASSISTANT, text, isError = true, needsRetry = true, id = id)

private fun call(name: String, id: String = "tool-$name") =
    AssistantUiMessage(ChatRole.TOOL, "…", toolName = name, id = id)

/**
 * 「一问一答」的配对与折叠判定。
 *
 * 折叠的单位必须是一问一答：只折回答，问题会孤零零留在上面；只折问题，回答又失去上下文。
 * 这两条规则一旦被改坏，界面上表现为"历史问题排在回答下面"或"某轮回答凭空消失"，
 * 而截图上未必看得出来（折叠态本来就只剩一行摘要）。
 */
class AssistantRoundFoldTest {

    @Test
    fun `用户发言与它的回答配成一轮`() {
        val rounds = listOf(ask("今天有什么课"), say("有 3 节课")).toTurns().toRounds()

        assertEquals(1, rounds.size)
        assertEquals("今天有什么课", rounds[0].question)
        assertEquals("有 3 节课", rounds[0].reply?.answer)
    }

    @Test
    fun `工具调用归在回答那一轮 不会自成一轮`() {
        val rounds = listOf(
            ask("今天有什么课"),
            call("get_today_courses"),
            say("有 3 节课"),
        ).toTurns().toRounds()

        assertEquals(1, rounds.size)
        assertEquals(1, rounds[0].reply?.tools?.size)
    }

    @Test
    fun `连续两条用户发言时 前一条保留为没有回答的一轮`() {
        // 异常历史（比如重试时旧消息没清干净）：不能把前一个问题吞掉
        val rounds = listOf(
            ask("第一个问题", id = "u1"),
            ask("第二个问题", id = "u2"),
            say("只答了第二个", id = "a2"),
        ).toTurns().toRounds()

        assertEquals(2, rounds.size)
        assertEquals("第一个问题", rounds[0].question)
        assertNull(rounds[0].reply)
        assertEquals("第二个问题", rounds[1].question)
        assertEquals("只答了第二个", rounds[1].reply?.answer)
    }

    @Test
    fun `最后一条用户发言还没等到回答时也保留`() {
        val rounds = listOf(ask("问题"), say("回答"), ask("追问", id = "u2"))
            .toTurns().toRounds()

        assertEquals(2, rounds.size)
        assertEquals("追问", rounds[1].question)
        assertNull(rounds[1].reply)
    }

    @Test
    fun `轮次的 key 取用户那条的 id`() {
        // 折叠状态（rememberSaveable）挂在 key 上：回答换了 id 也不能把展开状态丢掉
        val rounds = listOf(
            ask("问题", id = "user-1"),
            say("回答", id = "answer-1"),
        ).toTurns().toRounds()

        assertEquals("user-1", rounds[0].key)
    }

    @Test
    fun `空列表不炸`() {
        assertTrue(emptyList<AssistantUiMessage>().toTurns().toRounds().isEmpty())
    }

    // ── 折叠判定 ──

    @Test
    fun `少于四轮一律不折`() {
        for (total in 1..3) {
            for (index in 0 until total) {
                assertFalse(
                    "总共 $total 轮、第 $index 轮不该折",
                    isRoundFoldable(index, total),
                )
            }
        }
    }

    @Test
    fun `四轮时只有前两轮可折 最近两轮保持展开`() {
        assertTrue(isRoundFoldable(0, 4))
        assertTrue(isRoundFoldable(1, 4))
        assertFalse(isRoundFoldable(2, 4))
        assertFalse(isRoundFoldable(3, 4))
    }

    @Test
    fun `轮次更多时 折叠线始终跟着最近两轮走`() {
        assertTrue(isRoundFoldable(6, 9))
        assertFalse(isRoundFoldable(7, 9))
        assertFalse(isRoundFoldable(8, 9))
    }

    @Test
    fun `出错的一轮永远不折`() {
        // 错误卡片的意义就是"必须被看见"；折进摘要等于把失败藏起来
        val rounds = listOf(
            ask("旧问题", id = "u1"),
            badAnswer(id = "a1"),
            ask("新问题", id = "u2"),
            say("新回答", id = "a2"),
            ask("再问", id = "u3"),
            say("再答", id = "a3"),
            ask("最后一问", id = "u4"),
            say("最后一答", id = "a4"),
        ).toTurns().toRounds()

        assertFalse(isRoundFoldable(0, rounds.size, hasError = rounds[0].reply?.error != null))
        // 同一位置若没有错误则照常可折 —— 证明上面的 false 来自 hasError 而不是位置
        assertTrue(isRoundFoldable(0, rounds.size))
    }
}
