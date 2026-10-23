package com.pocket.xiaoli.data.exam

import com.pocket.xiaoli.data.agent.AssistantLlmClient
import com.pocket.xiaoli.data.agent.AssistantLlmConfig
import com.pocket.xiaoli.data.agent.ChatMessage
import com.pocket.xiaoli.data.agent.ChatRole
import com.pocket.xiaoli.data.agent.LlmRequest
import com.pocket.xiaoli.data.agent.LlmStreamEvent
import kotlinx.coroutines.flow.toList
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用 LLM 兜底抽取考试安排。
 *
 * ## 什么时候才用它
 *
 * **只在规则解析（[ExamNoticeParser]）认不出条目时**才调用。理由是成本与确定性：
 *
 * - 规则解析免费、离线、每次结果一致，常规教务模板它都能处理
 * - LLM 调用要联网、要花钱、结果有随机性（同一份通知可能两次结果不同）
 *
 * 所以调用方应先跑规则；规则没结果时，再问用户"要不要让 AI 试试"。
 * **不要无条件走 LLM** —— 那会为一个本可离线完成的事引入网络依赖与费用。
 *
 * ## 为什么要求返回 JSON
 *
 * 让模型输出 JSON 而不是自然语言，是为了**可校验**：字段缺失、类型不对
 * 都能立刻发现并当作失败处理，而不是把一段散文塞进 courseName。
 *
 * 模型常把 JSON 包在 ``` 代码块里，所以剥离围栏是必需的（不是可选优化）。
 */
class ExamLlmExtractor(
    private val client: AssistantLlmClient,
) {

    /**
     * @return 解析出的考试；无法解析或调用失败时返回 null（**不抛异常** ——
     *         这是"兜底"路径，它失败应当静默退回规则解析的结果，
     *         而不是把整个导入流程弄崩）
     */
    suspend fun extract(config: AssistantLlmConfig, noticeText: String): List<ExamSchedule>? {
        if (noticeText.isBlank()) return null

        val request = LlmRequest(
            messages = listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = SYSTEM_PROMPT),
                ChatMessage(role = ChatRole.USER, content = noticeText),
            ),
            // 抽取任务要的是稳定复现，不要创造性
            temperature = 0.0,
        )

        val text = try {
            val events = client.stream(config, request).toList()
            events.filterIsInstance<LlmStreamEvent.TextDelta>().joinToString("") { it.text }
                .ifBlank {
                    // 有些实现只在 Completed 里给完整正文
                    events.filterIsInstance<LlmStreamEvent.Completed>()
                        .firstOrNull()?.message?.content.orEmpty()
                }
        } catch (_: Exception) {
            return null
        }

        if (text.isBlank()) return null
        return parseJson(text)
    }

    /**
     * 容错解析：剥围栏 → 取数组 → 逐条读字段。任何一步失败都返回 null。
     *
     * 标 `internal` 而不是 `private`，是为了让测试能直接覆盖这段 ——
     * 它面对的是**不可控的模型输出**（可能带 markdown 围栏、前后说明、
     * 缺字段、甚至根本不是 JSON），是最容易出错也最值得测的一环。
     * 网络调用本身难以单测，但这里可以。
     */
    internal fun parseJson(raw: String): List<ExamSchedule>? {
        val cleaned = stripCodeFence(raw)
        val arrayText = extractArray(cleaned) ?: return null
        return try {
            val arr = JSONArray(arrayText)
            if (arr.length() == 0) return null
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val course = o.optString("courseName").trim()
                    // 没有课程名的条目直接丢 —— 一条没有名字的考试对用户毫无用处
                    if (course.isBlank()) continue
                    add(
                        ExamSchedule(
                            courseName = course,
                            date = o.optString("date").trim(),
                            time = o.optString("time").trim(),
                            location = o.optString("location").trim(),
                            seat = o.optString("seat").trim(),
                        )
                    )
                }
            }
            list.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    /** 剥离 ```json ... ``` 围栏。模型几乎总会加，所以这是必需步骤。 */
    private fun stripCodeFence(raw: String): String {
        val t = raw.trim()
        if (!t.contains("```")) return t
        // 取第一段围栏内容；没有闭合时取围栏之后到结尾
        val start = t.indexOf("```")
        val afterFence = t.substring(start + 3)
        val body = afterFence.substringAfter('\n', afterFence)
        val end = body.indexOf("```")
        return (if (end >= 0) body.substring(0, end) else body).trim()
    }

    /** 从可能带前后说明文字的输出里截出 JSON 数组。 */
    private fun extractArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private companion object {
        /**
         * 提示词设计要点：
         *
         * 1. **明确字段名与空值约定** —— 否则模型会自创字段或写"未提供"
         * 2. **要求"识别不出就返回空数组"** —— 防止它为了完成任务而编造考试。
         *    这是本功能最重要的一条安全约束：**宁可返回空，也不能编考试**。
         * 3. **日期保持原文** —— 与规则解析器一致（通知常省略年份，重排格式反而丢信息）
         */
        val SYSTEM_PROMPT = """
            你是考试安排抽取器。从用户给出的教务通知文本中，提取每一场考试。
            只输出 JSON 数组，不要任何解释文字，不要 markdown 围栏。

            数组元素字段：
            - courseName: 课程名（必填，字符串）
            - date: 考试日期（保留原文写法，如 "2026-01-15" 或 "1月15日"；没有就空字符串）
            - time: 考试时间（如 "14:00-16:00"；没有就空字符串）
            - location: 考场（如 "教三楼305"；没有就空字符串）
            - seat: 座位号（只要数字；没有就空字符串）

            规则：
            1. 只提取**确实存在的考试**。文本里没有考试安排就返回 []
            2. 绝对不要编造或推测日期、考场、座位号。宁可留空字符串
            3. 没有日期的条目不要输出（无法确定考试时间的不是有效考试）
            4. 表头行、注意事项、落款都不是考试
        """.trimIndent()
    }
}
