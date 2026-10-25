package com.pocket.xiaoli.data.qzxy

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 趣智校园（趣智校园 / QZXY）HTTP 客户端。
 *
 * ## 与参考实现的关系
 *
 * 移植自 [`yehu-imei/linyu`](https://github.com/yehu-imei/linyu)（**MIT 许可**，可自由使用与修改）。
 * 那边用 Retrofit，这里改成 **OkHttp + Gson 手写** —— 理由是本项目已经有这两样，
 * 而要用到的接口只有十来个；为此引一套 Retrofit 会平白多一个版本冲突面
 *（本项目的 `verifyComposeAlignment` 任务就是为了防这类版本错配而存在的）。
 *
 * ## 两条必须遵守的协议细节（都是参考实现踩坑换来的）
 *
 * 1. **业务失败也是 HTTP 200**：一律只认响应体里的 `success` / `errorCode`，
 *    绝不拿状态码当业务结果。
 * 2. **认证参数 GET 走 query、POST 走 form body**，两处字段必须一致
 *    （见 [QzxyAuth.asQuery] / [QzxyAuth.asForm]），少一个就会「参数不全」。
 */
object QzxyApi {

    const val BASE_URL = "https://v3-api.china-qzxy.cn"

    /** 官方 App 的版本号，服务端会看它。所有请求都要带。 */
    const val VERSION = "6.5.24"

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // ─────────────────────────────────────────────
    //  底层：GET / POST + 统一解析
    // ─────────────────────────────────────────────

    private suspend fun rawGet(path: String, query: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val url = (BASE_URL + path).toHttpUrl().newBuilder().apply {
                query.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            execute(Request.Builder().url(url).get().build())
        }

    private suspend fun rawPost(path: String, form: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply {
                form.forEach { (k, v) -> add(k, v) }
            }.build()
            execute(Request.Builder().url(BASE_URL + path).post(body).build())
        }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            // 这里**不**因为 !resp.isSuccessful 就抛 —— 业务错误也是 200，
            // 而真正的 HTTP 错误（5xx）服务端通常也会带一个 JSON 壳，统一交给 parse 处理。
            if (text.isBlank()) {
                throw QzxyException("服务端返回空响应（HTTP ${resp.code}）")
            }
            return text
        }
    }

    /**
     * 解析统一响应壳。
     *
     * 用 `JsonParser` 手工拆壳而不是直接 `gson.fromJson(json, QzxyResponse::class.java)`：
     * `data` 在业务失败时是 `null`，而泛型 `T` 在运行时会被擦除 ——
     * 手工取 `data` 节点再按具体类型解析，才能保证「失败时不去反序列化 data」。
     */
    private fun <T> parse(json: String, dataClass: Class<T>): QzxyResponse<T> {
        val obj = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { throw QzxyException("响应不是合法 JSON：${json.take(200)}") }

        val success = obj.get("success")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val errorCode = obj.get("errorCode")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val errorMessage = obj.get("errorMessage")?.takeIf { !it.isJsonNull }?.asString
        val msg = obj.get("msg")?.takeIf { !it.isJsonNull }?.asString
        val dataNode = obj.get("data")
        val data: T? = if (dataNode != null && !dataNode.isJsonNull) {
            runCatching { gson.fromJson(dataNode, dataClass) }.getOrNull()
        } else null

        return QzxyResponse(success, data, errorCode, errorMessage, msg)
    }

    private fun <T> parseList(json: String, elementClass: Class<T>): QzxyResponse<List<T>> {
        val obj = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { throw QzxyException("响应不是合法 JSON：${json.take(200)}") }
        val success = obj.get("success")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val errorCode = obj.get("errorCode")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val errorMessage = obj.get("errorMessage")?.takeIf { !it.isJsonNull }?.asString
        val msg = obj.get("msg")?.takeIf { !it.isJsonNull }?.asString
        val dataNode = obj.get("data")
        val list: List<T>? = if (dataNode != null && dataNode.isJsonArray) {
            dataNode.asJsonArray.mapNotNull { runCatching { gson.fromJson(it, elementClass) }.getOrNull() }
        } else null
        return QzxyResponse(success, list, errorCode, errorMessage, msg)
    }

    // ─────────────────────────────────────────────
    //  登录（不需要认证）
    // ─────────────────────────────────────────────

    /**
     * 发短信验证码。`secret` 用 [QzxySign.smsSecret] 按手机号本地算，**任何手机号都能用**。
     *
     * @param typeId `3` = 登录/注册（默认，不需要登录态）· `2` = 重置密码 · `5` = 更换手机号
     */
    suspend fun sendSmsCode(telephone: String, typeId: Int = 3, platform: Int = 1): QzxyResponse<Unit> =
        parse(
            rawGet(
                "/user/verification/code/get",
                mapOf(
                    "telephone" to telephone,
                    "secret" to QzxySign.smsSecret(telephone),
                    "typeId" to typeId.toString(),
                    "platform" to platform.toString(),
                )
            ),
            Unit::class.java,
        )

    /** 短信验证码登录。**未注册的手机号会自动创建账号。** */
    suspend fun smsLogin(telephone: String, smsCode: String): QzxyResponse<QzxyLoginData> =
        parse(
            rawPost(
                "/user/registerAndLogin",
                mapOf(
                    "telephone" to telephone,
                    "smsCode" to smsCode,
                    "type" to "5",
                    "phoneSystem" to "android",
                    "version" to VERSION,
                )
            ),
            QzxyLoginData::class.java,
        )

    // ─────────────────────────────────────────────
    //  设备
    // ─────────────────────────────────────────────

    /** 用蓝牙 MAC 换设备详情。 */
    suspend fun deviceByMac(mac: String, auth: QzxyAuth): QzxyResponse<QzxyDeviceInfo> =
        parse(rawGet("/device/info/mac", auth.asQuery() + ("macAddress" to mac)), QzxyDeviceInfo::class.java)

    // ─────────────────────────────────────────────
    //  洗澡控制
    // ─────────────────────────────────────────────

    /** 开阀。 */
    suspend fun downRate(snCode: String, auth: QzxyAuth): QzxyResponse<QzxyDownRateResult> =
        parse(
            rawPost(
                "/order/tcpDevice/downRate/rateOrder",
                auth.asForm() + mapOf("xfModel" to "0", "snCode" to snCode),
            ),
            QzxyDownRateResult::class.java,
        )

    /** 开阀结果确认。**`orderNo` 在这里就能拿到**，不必等 MQTT。 */
    suspend fun downRateResult(snCode: String, auth: QzxyAuth): QzxyResponse<QzxyDownRateResult> =
        parse(
            rawPost("/order/tcpDevice/query/downRateResult", auth.asForm() + ("snCode" to snCode)),
            QzxyDownRateResult::class.java,
        )

    /** 关阀。 */
    suspend fun closeOrder(snCode: String, orderNo: String, auth: QzxyAuth): QzxyResponse<Unit> =
        parse(
            rawPost(
                "/order/tcpDevice/closeOrder",
                auth.asForm() + mapOf("snCode" to snCode, "orderNo" to orderNo),
            ),
            Unit::class.java,
        )

    /**
     * 关阀结果确认。
     *
     * ⚠️ 参考实现的实测结论：**这个接口确认不了任何东西**（`data` 恒为 `null`）。
     * 想知道设备到底关没关，用 [queryUsing]。
     */
    suspend fun closeOrderResult(snCode: String, orderNo: String, auth: QzxyAuth): QzxyResponse<QzxyCloseResult> =
        parse(
            rawPost(
                "/order/tcpDevice/closeOrder/result/query",
                auth.asForm() + mapOf("snCode" to snCode, "orderNo" to orderNo),
            ),
            QzxyCloseResult::class.java,
        )

    /**
     * 查询设备是否有进行中的订单。
     *
     * ⚠️ 判读规则（三条，缺一条就会误判）：
     * | 响应 | 含义 |
     * |---|---|
     * | `errorCode == 307` 或 `data.orderNo != null` | **有订单**（还在用） |
     * | `success == true` 且没有订单 | **没有订单**（确实停了） |
     * | 请求本身失败 | **不确定** —— 绝不能当成"没有订单" |
     */
    suspend fun queryUsing(snCode: String, auth: QzxyAuth): QzxyResponse<QzxyOrderStatus> =
        parse(
            rawPost(
                "/order/tcpDevice/query/rateOrder/using",
                auth.asForm() + mapOf("xfModel" to "0", "snCode" to snCode),
            ),
            QzxyOrderStatus::class.java,
        )

    /** 消费结算结果。⚠️ 金额单位是**厘**，用 [QzxyCloseResult.consumeYuan] 转。 */
    suspend fun consumeOrderResult(snCode: String, orderNo: String, auth: QzxyAuth): QzxyResponse<QzxyCloseResult> =
        parse(
            rawPost(
                "/order/consumeOrder/result/query",
                auth.asForm() + mapOf("snCode" to snCode, "orderNo" to orderNo),
            ),
            QzxyCloseResult::class.java,
        )

    // ─────────────────────────────────────────────
    //  账号 / 余额 / 账单
    // ─────────────────────────────────────────────

    suspend fun accountInfo(auth: QzxyAuth): QzxyResponse<QzxyAccountInfo> =
        parse(rawGet("/account/info", auth.asQuery()), QzxyAccountInfo::class.java)

    suspend fun wallet(auth: QzxyAuth): QzxyResponse<QzxyWallet> =
        parse(rawGet("/account/wallet", auth.asQuery()), QzxyWallet::class.java)

    /**
     * 余额查询（趣智校园代理了易校园，不必自己破签名）。
     *
     * ⚠️ 路径写的是 `campus`，但实测返回的是**趣智钱包**余额（见 `QzxyCampusUserInfo`）。
     */
    suspend fun campusUserInfo(auth: QzxyAuth): QzxyResponse<QzxyCampusUserInfo> =
        parse(rawGet("/settlement/campus/userInfo", auth.asQuery()), QzxyCampusUserInfo::class.java)

    suspend fun projectInfo(auth: QzxyAuth): QzxyResponse<QzxyProjectInfo> =
        parse(rawGet("/project/info/triple", auth.asQuery()), QzxyProjectInfo::class.java)

    /**
     * 这个账号名下的**全部项目**。
     *
     * ⚠️ 一个账号可能有多个项目（钱包 / 账单 / 设备分开记），而登录响应只下发其中一个。
     * 详见 [QzxyUserProject]。
     */
    suspend fun userProjectList(auth: QzxyAuth): QzxyResponse<List<QzxyUserProject>> =
        parseList(rawGet("/user/project/list", auth.asQuery()), QzxyUserProject::class.java)

    /** @param month 格式 `yyyy-MM` */
    suspend fun billList(month: String, auth: QzxyAuth): QzxyResponse<List<QzxyBillItem>> =
        parseList(
            rawGet("/order/query/account/bill/list", auth.asQuery() + mapOf("month" to month, "billRequestType" to "2")),
            QzxyBillItem::class.java,
        )

    // ─────────────────────────────────────────────
    //  使用码（给热水器物理键盘用的 8 位码）
    // ─────────────────────────────────────────────
    //
    // ⚠️ 三个接口分工很容易搞混，注释写清免得被当成冗余"优化"掉：
    //   currentUseCode  —— 看当前码，**不改**任何东西
    //   generateUseCode —— 换一个**候选**码，**不改**当前生效的码；3 分钟内要 set
    //   setUseCode      —— **只有它**会改服务端当前生效的码
    // 参考实现抓包确认过：连换 7 次期间，服务端生效的码一直没变。
    // 所以「先预览、再决定领不领」是安全的 —— 取消零成本。

    /** 当前使用码 + 今日额度。 */
    suspend fun currentUseCode(auth: QzxyAuth): QzxyResponse<QzxyUseCode> =
        parse(rawGet("/account/useCode/new", auth.asQuery()), QzxyUseCode::class.java)

    /** 「换一个」—— 只换候选码，**不生效**。 */
    suspend fun generateUseCode(auth: QzxyAuth): QzxyResponse<QzxyUseCodeCandidate> =
        parse(rawPost("/account/useCode/new/generate", auth.asForm()), QzxyUseCodeCandidate::class.java)

    /** 「确定领取」—— **唯一**会把候选码设为当前生效码的一步。 */
    suspend fun setUseCode(useCode: String, auth: QzxyAuth): QzxyResponse<Unit> =
        parse(
            rawPost("/account/useCode/new/set", auth.asForm() + mapOf("useCode" to useCode)),
            Unit::class.java,
        )

    /** 开 / 关使用码。`1` = 开启，`0` = 关闭。⚠️ 它**也会**改写 `useCodeStartTime`。 */
    suspend fun updateUseCodeStatus(status: Int, auth: QzxyAuth): QzxyResponse<Unit> =
        parse(
            rawPost(
                "/account/useCode/new/status/update",
                auth.asForm() + mapOf("useCodeStatus" to status.toString()),
            ),
            Unit::class.java,
        )
}

/** 趣智校园交互失败。`message` 直接来自服务端，可展示给用户。 */
class QzxyException(message: String, cause: Throwable? = null) : Exception(message, cause)
