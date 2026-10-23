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
    /** 正文至少几个字才算一条能看的反馈。 */
    val isSendable: Boolean get() = content.trim().length >= FeedbackFormatter.MIN_CONTENT_LENGTH
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

    /** 工单号：`FB + 日期 + 4 位十六进制`。同日多条靠 [salt] 区分。 */
    fun ticketId(nowMillis: Long, salt: Int): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMillis))
        val mixed = ((nowMillis / 1000L) xor (salt.toLong() * 2654435761L)).toInt()
        return "FB$day-" + String.format(Locale.US, "%04X", mixed and 0xFFFF)
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
