package com.pocket.xiaoli.ui.assistant

import com.pocket.xiaoli.data.knowledge.AssistantTool

/**
 * 回答下方「追问」的选词器。
 *
 * ## 为什么不是随机抽 / 固定五条
 *
 * 固定列表的问题在于**答非所问**：刚查完天气，下面推荐「我绩点多少」，
 * 用户看不出这和上一句有什么关系，于是把整排 chip 当装饰。
 * 这里先看本轮**真的调过哪些工具**，把依赖这些工具的问题排到前面，
 * 再按原顺序补齐 —— 既有上下文感，又不会因为工具太专一而只剩一条。
 *
 * ## 两条硬约束
 *
 * - **只从 [ASSISTANT_PROMPTS] 里选**：那份列表的每一条都保证今天能真答出来，
 *   另起一份候选词必然漂移（见该常量的 KDoc）。
 * - **问过的不再推荐**：同一个问题反复出现，比推荐得不相关更烦人。
 *
 * ⚠️ 纯函数、不读状态：界面把"本轮工具 + 本会话问过的话"传进来，
 * 这样排序规则可以脱离 Compose / ViewModel 单测。
 */
internal object FollowUpPlanner {

    const val DEFAULT_LIMIT = 3

    /**
     * @param tools 本轮成功执行过的工具（失败的工具不算 —— 再推荐同一类问题大概率还失败）
     * @param asked 本会话里用户问过的话（原话，不必与候选词完全一致）
     * @param limit 最多几条；`<= 0` 返回空
     * @param prompts 候选词源，默认即唯一真源
     */
    fun plan(
        tools: Set<AssistantTool>,
        asked: Collection<String>,
        limit: Int = DEFAULT_LIMIT,
        prompts: List<AssistantPrompt> = ASSISTANT_PROMPTS,
    ): List<AssistantPrompt> {
        if (limit <= 0) return emptyList()
        val askedKeys = asked.mapTo(mutableSetOf()) { normalize(it) }
        val available = prompts.filter { normalize(it.title) !in askedKeys }
        val related = available.filter { prompt -> prompt.relatedTools.any { it in tools } }
        val relatedTitles = related.mapTo(mutableSetOf()) { it.title }
        val rest = available.filter { it.title !in relatedTitles }
        return (related + rest).take(limit)
    }

    /**
     * 只抹平"明显是同一句"的差异：空白、大小写与句末标点。
     *
     * 刻意不做同义改写（"今天有课吗" vs "今天有什么课"）：那需要模型或词典，
     * 做不好会把两个不同的问题误判成重复。
     */
    private fun normalize(question: String): String =
        question.trim()
            .lowercase()
            .replace(Regex("\\s+"), "")
            .trimEnd('？', '?', '。', '！', '!', '，', ',')
}
