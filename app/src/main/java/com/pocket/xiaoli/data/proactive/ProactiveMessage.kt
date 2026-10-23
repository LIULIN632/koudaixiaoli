package com.pocket.xiaoli.data.proactive

/**
 * 主动提醒的**三档**：关 / 少 / 标准。
 *
 * 只决定「要不要生成气泡」「要不要闲聊」「全局冷却多长」——不碰磁盘也不碰时间，
 * 所以这几条判断都是**纯函数**，可以直接对着枚举值单测（见 [ProactiveRules]）。
 *
 * 默认 [STANDARD]（与参考实现一致）：用户装了就该看见这个功能是活的；
 * 改成 OFF 是他的权利，入口在「AI 设置 → 主动提醒」。
 */
enum class ProactiveLevel { OFF, LOW, STANDARD }

/**
 * 一条主动提醒。
 *
 * ## 三种去向（互斥，按顺序判断）
 *
 * - [openRoute] 非空 → 点击**直接开 App 页面**，不进对话。气泡在说一件 App 里已经有页面的事
 *   （比如"快上课了"对应课表）时走这一支；
 * - [prompt] 非空 → 带着这句话**开一个新会话**发给鲤鱼；
 * - 两者都空 → 只是切到鲤鱼页（闲聊之类）。
 *
 * ## 为什么文案要封顶
 *
 * 气泡是**单行**的浮层，挂在底栏上方。文案一旦换行，气泡就会长成一块板子，
 * 把底下的内容挡掉一片。所以所有文案进气泡前都过 [MAX_CHARS]。
 * 参考实现同样这么做（`ProactiveMessage.MAX_CHARS`）。
 */
data class ProactiveMessage(
    val id: String,
    val text: String,
    val prompt: String = "",
    /**
     * 允许两行显示（闲聊用）。**只有全文本身就很短的句子**才该打开它 ——
     * 它是"别把这句话截了"，不是"允许溢出"。
     */
    val fullReveal: Boolean = false,
    /** 闲聊句子的 id，用来做"最近说过的不再说"的去重。 */
    val chatterLineId: String? = null,
    /** 非空则点击打开此路由，不进对话。 */
    val openRoute: String? = null,
    /**
     * 进对话时**只给模型看、不进聊天气泡**的本地快照。
     *
     * 说明"用户点的是哪件事"以及当时已知的字段；缺的字段不要编，
     * 要完整或最新的数据仍然去调工具。
     */
    val eventSnapshot: String? = null,
    /**
     * 冒泡之后要写回 store 的**内容键**，用来做"这件事已经说过了"的去重。
     *
     * 声明的意义是：这个键必须**随消息一起走到 [ProactiveEvaluator.consume]**。
     * 在采集侧（`evaluate`）就写回是错的 —— 消息可能因为展示位被占而根本没冒出去
     * （见 AppNavigation 里的 `ProactiveBubbleHost.message == null` 判断），
     * 那样这个节点就被白白烧掉、再也不会被提起了。
     * 目前用它的有两类：校历节点（单个游标）与天气事件（`kind@date` 集合）。
     */
    val seenKey: String? = null,
) {
    companion object {
        /**
         * 气泡文案的字符上限。
         *
         * 24 是从参考实现搬来的：它保证这句话在常见机型上**一行**放得下，
         * 又是中文里能说清一件小事的最短长度（"30分钟后上高等数学" = 11）。
         */
        const val MAX_CHARS = 24
    }
}

/** 把文案裁到 [ProactiveMessage.MAX_CHARS]，被裁掉时留一个"…"（而不是硬切）。 */
fun clipProactiveText(text: String): String =
    if (text.length <= ProactiveMessage.MAX_CHARS) {
        text
    } else {
        text.take(ProactiveMessage.MAX_CHARS - 1) + "…"
    }
