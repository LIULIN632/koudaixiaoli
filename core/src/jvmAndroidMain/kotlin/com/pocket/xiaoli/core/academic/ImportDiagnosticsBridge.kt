package com.pocket.xiaoli.core.academic

/**
 * 教务抓取诊断出口。
 *
 * 桌面端在「我的 → 数据 → 导入诊断」打开时挂一个 sink，把每一次请求/响应的
 * **URL、表单、重定向链、响应正文**落到本地目录，用来复现「导入进来是空的 / 少了几门课」。
 * 手机端不挂（它有自己的网络日志页），所以默认 `null` = 一次布尔判断，零开销。
 *
 * ## 写盘前必须脱敏
 *
 * 登录表单里有 RSA 密文密码，会话 Cookie 在头里。这条链路**只记 URL / 表单 / 正文**，
 * 不记任何请求头与响应头；表单里的密码类字段整值打码（见 [ImportDiagnosticsBridge.redact]）。
 */
object ImportDiagnosticsBridge {

    @Volatile
    var onExchange: ((AcademicExchange) -> Unit)? = null

    val active: Boolean get() = onExchange != null

    /** 需要整值打码的字段名（正方登录用 `mm`，别的部署常见 password/pwd）。 */
    private val SECRET_KEYS = setOf("mm", "password", "passwd", "pwd", "ymm", "jmm")

    /** 正文最大落盘长度：诊断要的是形状不是全量，200KB 足够看清表格变体。 */
    const val MAX_BODY_CHARS = 200_000

    /**
     * 打码 `k=v&k=v` 形式的表单或 URL 查询串。
     *
     * 只动值、不动键，也不改字段顺序 —— 复现问题时顺序和空值都是有信息量的。
     */
    fun redact(text: String): String =
        text.split('&').joinToString("&") { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) pair else {
                val key = pair.substring(0, idx)
                if (key.substringBeforeLast('[').removePrefix("_").lowercase() in SECRET_KEYS) {
                    "$key=«已打码(${pair.length - idx - 1} 字符)»"
                } else {
                    pair
                }
            }
        }

    fun clampBody(body: String): String =
        if (body.length <= MAX_BODY_CHARS) body else body.take(MAX_BODY_CHARS) + "\n…（截断，共 ${body.length} 字符）"
}

/** 一次教务请求/响应的可诊断记录（不含任何头信息）。 */
data class AcademicExchange(
    val method: String,
    val url: String,
    val status: Int,
    val requestForm: String,
    /** 跟随重定向经过的 URL 链（正方未登录时会一路 302 回登录页，这一串最能说明问题）。 */
    val redirectChain: List<String>,
    val body: String,
)
