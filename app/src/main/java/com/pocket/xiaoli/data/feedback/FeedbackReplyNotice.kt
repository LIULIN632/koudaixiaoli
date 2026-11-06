package com.pocket.xiaoli.data.feedback

/**
 * 「哪条回复还没通知过」的判断，抽成纯函数好测。
 *
 * ## 为什么要单独记一本账
 *
 * 回复是**拉**来的（客户端按工单号查），服务器不知道我们哪天弹过通知。
 * 如果只看「有没有回复」，用户每进一次页面触发一次轮询就会再响一遍；
 * 如果只记「最后一次拉取时间」，同一条回复在重新安装后会再响。
 * 所以按 **工单号 + 回复正文** 记账：正文变了才算新回复，同一句话只敲一次门。
 */
internal object FeedbackReplyNotice {

    /** 通知正文最多带这么多字，剩下的让用户点进去看。 */
    const val MAX_PREVIEW = 120

    /**
     * 找出「值得发一条通知」的那条回复。
     *
     * @param replies 服务器返回的回复（工单号 → 回复）
     * @param notified 已经通知过的（工单号 → 当时那版回复正文）
     * @return 最新的一条未通知回复；没有就返回 null
     */
    fun newest(
        replies: Map<String, FeedbackReply>,
        notified: Map<String, String>,
    ): Pair<String, FeedbackReply>? = replies
        .filter { (ticket, reply) -> notified[ticket] != reply.text }
        .maxByOrNull { (_, reply) -> reply.at }
        ?.toPair()

    /** 通知正文：压成一行、超长截断，末尾补省略号。 */
    fun preview(text: String): String {
        val oneLine = text.replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length <= MAX_PREVIEW) oneLine else oneLine.take(MAX_PREVIEW) + "…"
    }
}
