package com.pocket.xiaoli.data.agent

/**
 * 流式文本的「时间节拍合并器」。
 *
 * ## 为什么需要它
 *
 * 流式回答每来一个 token 就更新一次界面状态时，滚动期间的代价是**随回复长度二次增长**的：
 * 每条消息都被重新组合、正文重新排版、`LazyColumn` 重新测量。快模型上（token 间隔 < 10ms）
 * 这件事会直接把帧率打穿，表现为长回复越写越卡。
 *
 * 合并的做法是把一段窗口内的增量攒起来，**一个节拍只推一次**界面状态。
 * 常识上会担心"合并后字变成一块一块蹦"，但 50ms ≈ 3 帧，
 * 攒下来的通常只有一两个 token，观感与逐字输出无法区分。
 *
 * ## 两条必须守住的不变量（都有单测）
 *
 * - **不变量 A（顺序）**：文本与工具事件的**相对顺序不能变**。
 *   模型可能"说半句 → 调工具 → 再说"，若把工具事件提前推出去（而半句还压在缓冲里），
 *   界面就会把工具脚注画在那半句**前面** —— 一轮一条目的版式立刻错乱。
 *   所以控制事件到达时只能**把它排在待发文本之后**，绝不能插队；缓冲清空后它自然就"立刻"了。
 *
 * - **不变量 B（不丢文本）**：已经收进缓冲的文本，在 [forceFlush] 或 [canFlush] 之后
 *   必须原样出现在输出里。用户点「停止」时走的就是 [forceFlush] 这条路径 ——
 *   丢掉它等于"打断后已收到的部分凭空消失"。
 *
 * 泛型化是为了让它**不依赖任何界面类型**，从而能被纯单测覆盖
 * （时间窗、顺序、不丢文本这三件事都很难靠人眼在模拟器里验证）。
 *
 * @param T 一次节拍合并出的文本块（本项目里是 `String`）
 * @param E 控制事件（本项目里是 [AgentEvent]，如"工具开始/结束"）
 */
class TextCoalescer<T, E>(
    /** 把同一节拍内的多个文本块合成一个。 */
    private val merge: (List<T>) -> T,
    /**
     * 把同一节拍内的思考链块合成一个。默认与 [merge] 无关，单独注入 ——
     * 思考链与正文是两种内容，合成规则将来可能不同（比如正文要保留换行、思考链可以压掉）。
     */
    private val mergeReasoning: (List<T>) -> T = merge,
) {

    /** 一节拍要推给界面的东西。 */
    sealed interface Flush<out T, out E> {
        /**
         * 一个节拍攒下的「模型自己产出的文本」。
         *
         * 思考链与正文放**同一个块**里，而不是各出一个块：它们在时间上本来就是
         * 同一段（思考先到、正文后到），分成两个块只会让调用方多一层"要不要合并"的判断，
         * 还会破坏"块严格按时间排列"这条不变量（见类注释）。
         */
        data class Text<T>(
            val chunk: T,
            /** 本节的思考链增量；为空表示这一段没收到思考链（服务商不支持，或已经说完）。 */
            val reasoning: T? = null,
        ) : Flush<T, Nothing>

        /** 控制事件，严格保持到达顺序。 */
        data class Events<E>(val events: List<E>) : Flush<Nothing, E>
    }

    private val pendingText = mutableListOf<T>()
    private val pendingReasoning = mutableListOf<T>()
    private val pendingEvents = mutableListOf<E>()

    /** 是否还有没推出去的内容（心跳据此决定要不要做一次状态更新）。 */
    val hasPending: Boolean
        get() = pendingText.isNotEmpty() || pendingReasoning.isNotEmpty() || pendingEvents.isNotEmpty()

    /** 收到一个文本增量。**不**立即产出任何东西 —— 等节拍。 */
    fun onText(chunk: T) {
        pendingText += chunk
    }

    /** 收到一个思考链增量。同样等节拍。 */
    fun onReasoning(chunk: T) {
        pendingReasoning += chunk
    }

    /**
     * 收到一个控制事件。
     *
     * 刻意**不**顺手 flush 文本：那会破坏不变量 A 的"工具事件不能插队"性质，
     * 并且在事件密集时退化成"每个事件都带一次状态更新"，合并就白做了。
     */
    fun onEvent(event: E) {
        pendingEvents += event
    }

    /**
     * 节拍到点时取走这一拍要推的内容；没有待发内容时返回空列表（调用方不必自己判空）。
     *
     * 输出顺序永远是「先文本、后事件」：见不变量 A。
     */
    fun canFlush(): List<Flush<T, E>> {
        if (!hasPending) return emptyList()
        return buildList {
            // 推理与正文一起出（顺序：先思考、后正文），见 Flush.Text 的说明
            if (pendingText.isNotEmpty() || pendingReasoning.isNotEmpty()) {
                add(
                    Flush.Text(
                        chunk = merge(pendingText.toList()),
                        reasoning = if (pendingReasoning.isEmpty()) null else mergeReasoning(pendingReasoning.toList()),
                    )
                )
                pendingText.clear()
                pendingReasoning.clear()
            }
            if (pendingEvents.isNotEmpty()) {
                add(Flush.Events(pendingEvents.toList()))
                pendingEvents.clear()
            }
        }
    }

    /**
     * 立刻取走全部待发内容，不等节拍。
     *
     * 用于三类"顺序敏感"的时刻：整轮结束、工具开始（那一瞬要让界面马上显示"正在查"）、
     * 以及用户点停止。见不变量 B：这里绝不能丢文本。
     */
    fun forceFlush(): List<Flush<T, E>> = canFlush()

    /** 丢弃全部待发内容。只在"这一轮已经彻底作废"时用，正常收尾一律用 [forceFlush]。 */
    fun discard() {
        pendingText.clear()
        pendingReasoning.clear()
        pendingEvents.clear()
    }
}
