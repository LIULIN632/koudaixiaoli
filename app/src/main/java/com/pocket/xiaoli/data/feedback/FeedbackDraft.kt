package com.pocket.xiaoli.data.feedback

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 反馈的分类。
 *
 * 分类不是为了统计，是为了**让开发者一眼知道要往哪儿看**：
 * 「功能坏了」去看崩溃日志，「用着别扭」去看交互，「想要新功能」去看排期。
 */
enum class FeedbackCategory(val label: String) {
    BUG("功能坏了"),
    UX("用着别扭"),
    FEATURE("想要新功能"),
    OTHER("其他"),
    ;

    companion object {
        fun fromLabel(label: String): FeedbackCategory = entries.firstOrNull { it.label == label } ?: OTHER
    }
}

/** 用户在反馈页填的东西。全部纯数据，便于单测与复用。 */
data class FeedbackDraft(
    val category: FeedbackCategory = FeedbackCategory.BUG,
    val content: String = "",
    val contact: String = "",
    /** 是否附带版本 / 机型 / 系统。**默认关**：不勾就一个字节设备信息都不带。 */
    val includeDiagnostics: Boolean = false,
) {
    /** 正文在可发送区间内：太短定位不了问题，太长会把表格和本地队列撑爆。 */
    val isSendable: Boolean
        get() = content.trim().length in FeedbackFormatter.MIN_CONTENT_LENGTH..FeedbackLimits.MAX_CONTENT_LENGTH
}

/**
 * 提交侧的闸门。没有服务端验证，客户端限流是唯一能挡住「拿这个包刷表」的东西。
 *
 * 常量集中在这里，UI 只引用不另写数值 —— 两处各写一份，改了一处就等于没改。
 */
object FeedbackLimits {

    const val MAX_CONTENT_LENGTH = 2000

    const val MAX_CONTACT_LENGTH = 120

    /** 两条「直接发送」之间至少隔一分钟。复制/邮件/GitHub 不受这条限制。 */
    const val COOLDOWN_MILLIS = 60_000L

    const val DAILY_MAX = 5

    /** 本地待发队列上限。满了就让用户先手动发，不再往队列里塞。 */
    const val MAX_PENDING = 5

    /**
     * 提交闸门的纯函数部分。日期切分（今天提了几条）由 [FeedbackStore] 负责，
     * 这里只判断「现在这次能不能提」，便于单测。
     */
    fun gate(nowMillis: Long, lastSubmitAt: Long, todayCount: Int): SubmitGate {
        if (lastSubmitAt > 0L) {
            val elapsed = nowMillis - lastSubmitAt
            if (elapsed < COOLDOWN_MILLIS) {
                val left = (COOLDOWN_MILLIS - elapsed).coerceIn(1_000L, COOLDOWN_MILLIS)
                return SubmitGate.Cooling(left / 1000L)
            }
        }
        if (todayCount >= DAILY_MAX) return SubmitGate.DailyLimit
        return SubmitGate.Ok
    }
}

sealed interface SubmitGate {
    data object Ok : SubmitGate
    data class Cooling(val secondsLeft: Long) : SubmitGate
    data object DailyLimit : SubmitGate
}

/**
 * 反馈内容的拼装器 —— 纯函数集合，所有渠道共用同一份正文。
 *
 * ## 为什么三个渠道共用一份正文
 *
 * 用户可能先复制去微信、发现发不出去再改成提 Issue。如果每个渠道各拼一份，
 * 内容会飘（"我复制的那条怎么少了一段"）。统一在这里拼，渠道只负责**运输**。
 */
object FeedbackFormatter {

    const val MIN_CONTENT_LENGTH = 5
    const val SUBJECT = "口袋小鲤 · 意见反馈"

    /**
     * 工单号：`T` + 年月日（yyMMdd）+ 6 位十六进制随机。
     *
     * 旧格式 `FB20261018-00FF` 只有 16 位尾号，且由「时间秒 xor 内容哈希」推出：
     * 同一天提两条一样的话就是同一个号，而撞号意味着两个人的回复串门。
     * 现在日期只负责可读，唯一性交给 24 位随机（约 1600 万/天），
     * 叠加 60 秒冷却后，同一台设备实际不会撞。
     *
     * @param random 24 位随机数。UI 传随机值，测试传固定值。
     */
    fun ticketId(nowMillis: Long, random: Int): String {
        val day = SimpleDateFormat("yyMMdd", Locale.US).format(Date(nowMillis))
        return "T$day" + String.format(Locale.US, "%06X", random and 0xFFFFFF)
    }

    /** 设备信息。只在用户勾了「附带设备信息」时才拼进正文。 */
    fun diagnostics(
        versionName: String,
        versionCode: Int,
        manufacturer: String,
        model: String,
        androidRelease: String,
        sdkInt: Int,
    ): String = buildString {
        append("版本 口袋小鲤 ").append(versionName).append(" (").append(versionCode).append(")\n")
        append("机型 ").append(manufacturer).append(' ').append(model).append('\n')
        append("系统 Android ").append(androidRelease).append(" (API ").append(sdkInt).append(')')
    }

    /** 完整正文。[diagnostics] 只有 draft.includeDiagnostics 为真时才会被带上。 */
    fun buildBody(
        draft: FeedbackDraft,
        ticketId: String,
        nowMillis: Long,
        diagnostics: String?,
    ): String = buildString {
        append("【").append(draft.category.label).append("】\n")
        append(draft.content.trim()).append('\n')
        if (draft.contact.isNotBlank()) {
            append("\n联系方式：").append(draft.contact.trim()).append('\n')
        }
        if (draft.includeDiagnostics && !diagnostics.isNullOrBlank()) {
            append("\n——\n").append(diagnostics).append('\n')
        }
        append('\n')
        append("工单 ").append(ticketId).append(" · ")
        append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(nowMillis)))
    }

    /** Issue 标题：把正文第一行截出来，比"意见反馈"四个字有用得多。 */
    fun issueTitle(draft: FeedbackDraft): String {
        val firstLine = draft.content.trim().lineSequence().firstOrNull().orEmpty().take(40)
        return "[反馈] ${draft.category.label}：$firstLine"
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /**
     * 预填 Issue 的链接。没让用户装 GitHub App、也没调 API —— 点开浏览器就能提交，
     * 这也是**不需要任何凭据**的一条真通道。
     */
    fun issueUrl(ownerRepo: String, draft: FeedbackDraft, ticketId: String, nowMillis: Long, diagnostics: String?): String {
        val body = buildBody(draft, ticketId, nowMillis, diagnostics)
        return "https://github.com/$ownerRepo/issues/new" +
            "?title=" + encode(issueTitle(draft)) +
            "&body=" + encode(body) +
            "&labels=" + encode("feedback")
    }

    fun mailtoUrl(mail: String, draft: FeedbackDraft, ticketId: String, nowMillis: Long, diagnostics: String?): String {
        val body = buildBody(draft, ticketId, nowMillis, diagnostics)
        return "mailto:$mail?subject=" + encode(SUBJECT) + "&body=" + encode(body)
    }
}
