package com.pocket.xiaoli.data.secondclass

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 到梦空间接口的**响应信封**解析。
 *
 * ⚠️ 这一层的形状不是设计出来的，是**实测出来的**（2026-10-10，依据见架构文档 §11.8 / §11.9）。
 * 它和"常规 REST 的直觉"有四处不一样，每一处都有自己的坑：
 *
 * | 实测事实 | 后果 |
 * |---|---|
 * | 成功响应里**没有 `code`**（`{"rows":[…],"total":"5","records":"88","page":"1"}`） | 判成功要判"**没有 `code`**"，**不能**判 `code == 0` |
 * | 未登录是 `{"code":"400","result":"token不能为空","success":false}`，而 **HTTP 仍是 200** | 绝不能看 HTTP 状态码，必须解析 `code` |
 * | 服务端对 **GET** 返回**整页首页 HTML**（只有 POST 才返回 JSON，见 §11.12） | 解析前先确认拿到的是 JSON；否则会死在一句无从下手的语法错误上 |
 * | 列表键是 `rows`；`records` / `page` / `total` 都是**字符串** | 分页字段必须容错解析 |
 *
 * ### 为什么「未登录」值得一个专门的类型与一组专门的用例
 * 数据源 B 的登录态会**静默过期**。如果它和普通失败混在一起，UI 只会显示"加载失败"，
 * 用户根本不知道要回去重新登录 —— 架构文档 §4 把这条点名为"本模块最容易做错的一处体验"。
 * 所以 [SecondClassResult.Unauthorized] 是一等公民，而**它唯一的产地就是这里**。
 */
object SecondClassEnvelope {

    /**
     * 实测：**无 token，或 token 无法解析** —— 原话 `token不能为空`。
     *
     * 出处：`POST certificate/certificate/scorelist`。三次探测都是这一条：不带 token、
     * 带格式非法的 cookie、带格式非法的 `token` 参数。页面侧的 `ajaxsetup.js` 也只特判了它。
     */
    const val CODE_MISSING_TOKEN = "400"

    /**
     * 实测：**登录已失效** —— 原话 `登录失效，请重新刷新扫码`。
     *
     * 出处：`POST token/checkToken` 带一个格式非法的 token（§11.12）。
     *
     * ⚠️ 这是**第二个**"未登录"码，必须收进来：漏了它，用户会话过期时 UI 会显示"加载失败"，
     * 而真正该说的是"请重新登录" —— 它自己的文案就在叫人重新扫码。
     */
    const val CODE_SESSION_EXPIRED = "90000019"

    private val UNAUTHORIZED_CODES = setOf(CODE_MISSING_TOKEN, CODE_SESSION_EXPIRED)

    private const val KEY_CODE = "code"
    private const val KEY_RESULT = "result"
    private const val KEY_MSG = "msg"
    private const val KEY_ROWS = "rows"
    private const val KEY_RECORDS = "records"
    private const val KEY_TOTAL = "total"
    private const val KEY_PAGE = "page"

    /** 非 JSON 响应（例如那整页 HTML）在报错信息里最多回显多少字符。 */
    private const val SNIPPET_LIMIT = 60

    /**
     * 把一段响应体判成「可用 / 未登录 / 失败」。
     *
     * 成功时返回 [SecondClassResult.Ok]，其值就是整个 JSON 根对象，交由 [toPage] 或调用方取字段。
     */
    fun validate(body: String?): SecondClassResult<JsonObject> {
        val text = body?.trim().orEmpty()
        if (text.isEmpty()) {
            return SecondClassResult.Failed(null, "服务端返回了空响应")
        }
        val root = runCatching { JsonParser.parseString(text) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return SecondClassResult.Failed(
                null,
                "服务端返回的不是 JSON（${text.take(SNIPPET_LIMIT)}…）。" +
                    "最常见的原因是**用了 GET** —— 实测服务端对 GET 会返回整页首页 HTML，" +
                    "而这套接口一律要 POST（见架构文档 §11.9 与 §11.12）",
            )
        return classify(root)
    }

    /** 只判错误、不解析数据。成功响应没有 `code`，所以"没有 code"就是成功。 */
    fun classify(root: JsonObject): SecondClassResult<JsonObject> {
        val code = root.stringOrNull(KEY_CODE)
            ?: return SecondClassResult.Ok(root)
        if (code in UNAUTHORIZED_CODES) {
            // 服务端的原话不往外抛：它对用户没意义，该显示的是"请重新登录到梦空间"。
            // 原话留在调试日志与 [SecondClassResult.Failed] 里。
            return SecondClassResult.Unauthorized
        }
        val message = messageOf(root).orEmpty()
        return SecondClassResult.Failed(
            code = code.toIntOrNull(),
            message = message.ifBlank { "请求失败（code=$code）" },
        )
    }

    /**
     * 错误文案。
     *
     * ⚠️ 实测 `result` **既可能是字符串**（`"token不能为空"`）**也可能是对象**（`{"msg":"登录失效，…"}`）。
     * 只按字符串取会让后者的文案整个丢掉 —— 而那些文案恰恰是排查时唯一有用的东西。
     */
    private fun messageOf(root: JsonObject): String? =
        root.stringOrNull(KEY_RESULT) ?: root.objOrNull(KEY_RESULT)?.stringOrNull(KEY_MSG)

    /**
     * 把根对象映射成一页数据。
     *
     * [mapRow] 负责单个 row → 领域模型；返回 `null` 表示这一行不可用，会被丢掉（**不抛异常**）：
     * 一条脏数据不该让整页变成"加载失败"。
     */
    fun <T> toPage(root: JsonObject, mapRow: (JsonObject) -> T?): SecondClassPage<T> {
        // `rows` 缺失、或存在但不是数组，都退化成空页 —— 不让分页元数据把整页拖成"加载失败"。
        val rows = runCatching { root.getAsJsonArray(KEY_ROWS) }.getOrNull()
        val items: List<T> = rows?.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.let(mapRow)
        }.orEmpty()
        return SecondClassPage(
            items = items,
            totalRecords = root.stringOrNull(KEY_RECORDS)?.toIntOrNull(),
            totalPages = root.stringOrNull(KEY_TOTAL)?.toIntOrNull(),
            page = root.stringOrNull(KEY_PAGE)?.toIntOrNull(),
        )
    }

}

/**
 * 把一次结果接到"解析数据"上：成功走 [onOk]，三支失败**原样透传**。
 *
 * 存在的两个理由：
 *
 * 1. ⚠️ **`Unauthorized` 必须原样透传，不许折叠成 `Failed`** —— 架构文档 §4 点名这是本模块
 *    最容易做错的一处体验。把它收成唯一的接线函数，就不存在"某个调用点顺手写一句兜底文案"的机会；
 *    所以这里刻意**不用** `map` / `getOrElse` 之类的通用组合子（它们太容易让人在失败分支补一句）。
 * 2. 这条 `when` 有四个分支，而调用点有三处（`SecondClassSession` / `SecondClassApi` /
 *    将来的 `WebViewSessionDataSource`）。抄三遍，迟早漂移。
 *
 * 成功载荷的类型是自由的（[S]）：`String`（原始响应体）与 `JsonObject`（已校验的根对象）
 * 共用同一条接线。
 *
 * ⚠️ 本函数早先的 KDoc 写着"泛型擦除导致 `is SecondClassResult.Ok` 只能拿到 `Ok<*>`、
 * `value` 退化成 `Any?`，所以需要强转 + `@Suppress`" —— **那是错的**：
 * 只要接收者写成带类型参数的 `SecondClassResult<S>`，Kotlin 就能正常智能转换
 * （编译器明确报了 `Cast is redundant`）。强转与 `@Suppress` 已随之删掉，**别再加回来**。
 */
fun <S, T> SecondClassResult<S>.mapValidated(
    onOk: (S) -> SecondClassResult<T>,
): SecondClassResult<T> = when (this) {
    is SecondClassResult.Ok -> onOk(value)
    is SecondClassResult.Unauthorized -> SecondClassResult.Unauthorized
    is SecondClassResult.RateLimited -> SecondClassResult.RateLimited(retryAfterMs)
    is SecondClassResult.Failed -> SecondClassResult.Failed(code, message)
}

/** 只在包内用：给"响应里某个对象字段"取值。与 `SecondClassEnvelope` 里的那个同源。 */
internal fun JsonObject.objOrNull(key: String): JsonObject? =
    runCatching { getAsJsonObject(key) }.getOrNull()

/** 只在包内用：给"响应里某个字符串字段"取值。缺字段/类型不对一律 `null`，绝不抛。 */
internal fun JsonObject.stringOrNull(key: String): String? {
    val value = get(key) ?: return null
    return if (value.isJsonPrimitive) value.asString else null
}

/**
 * 一页列表数据。
 *
 * [totalRecords] / [totalPages] / [page] 在服务端都是**字符串**，解析不出就是 `null`
 * —— 分页元数据缺失不该让列表本身不可用。
 */
data class SecondClassPage<T>(
    val items: List<T> = emptyList(),
    /** 总条数（服务端 `records`）。 */
    val totalRecords: Int? = null,
    /** 服务端自己算好的总页数（服务端 `total`）。 */
    val totalPages: Int? = null,
    /** 服务端回显的页码（服务端 `page`，**1 起**）。 */
    val page: Int? = null,
) {
    /**
     * 按页大小自己算总页数：`ceil(records / rows)`。
     *
     * 与页面 JS 的算法一致（`certificate.js` 里就是 `Math.ceil(t.records / a)`），
     * 因此可与服务端的 [totalPages] 相互印证 —— 实测样本里两者都是 5。
     */
    fun pageCount(pageSize: Int): Int? {
        val records = totalRecords ?: return null
        if (pageSize <= 0) return null
        return (records + pageSize - 1) / pageSize
    }
}
