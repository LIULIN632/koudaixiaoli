package com.pocket.xiaoli.data.card

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import kotlin.coroutines.cancellation.CancellationException

/** 校园卡余额（易校园 / 小富宝平台）。 */
data class CampusCardBalance(
    /** 余额，单位**元**（平台返回的就是元；不像慧新那边是按分存）。 */
    val balance: Double,
    val userName: String,
    val schoolName: String,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
)

/**
 * 校园卡（**易校园** = 云马 / 小富宝平台）的原生接口。
 *
 * ## 接口是哪里来的
 *
 * 不是猜的：GitHub 上 `xhboke/yixiaoyuan`（用 Python 查易校园校园卡消费）是一份**跑得通的实现**，
 * 三条链路与字段名都从它逐行读出来：
 *
 * ```
 * POST compus.xiaofubao.com/login/getPublicKey        （deviceId）→ data.publicKey
 *     密码 = base64( RSA_PKCS1_v1_5( md5(明文密码) ) )      ← 先 MD5（十六进制小写），再 RSA
 * POST compus.xiaofubao.com/login/doLoginByPwd        （mobilePhone/password/osType/deviceId）
 *     → data.id / data.token / data.userName / data.schoolName
 * POST compus.xiaofubao.com/compus/user/getCardMoney  （id/token/deviceId）→ data = 余额（元）
 * ```
 *
 * ⚠️ 三个必须照它来的细节：
 * 1. **参数在 query string 里**，不是表单体（原脚本用的是 `requests.post(url, params=…)`）；
 * 2. **登录用手机号**（`mobilePhone`），不是学号 —— 易校园的账号体系是它自己的；
 * 3. `deviceId` 是客户端自己带的标识（原脚本写死一个），我们**按安装生成一次并持久化**，
 *    设备标识在同一台机器上稳定，才不会被平台当成"新设备"。
 *
 * 另一条更新的路（`application.xiaofubao.com` 的 OAuth：`getCodeV2` → `code` → 取用户信息，
 * 见 `DumpTime/yxy`）需要学校在易校园侧的 `appid` / `unionid`，那两个值要登录门户后才看得到，
 * 所以先走上面这条**密码登录**的路；返回码不为 0 时把平台的原文 message 交给用户看，
 * 不自己编"登录失败"。
 */
object YxyCardRepository {

    /**
     * 本机存校园卡账号用的 key。
     *
     * 与学号那套分开：易校园的账号是**它自己的**（手机号 + 密码），
     * 既不等于教务密码，也不等于智慧校园密码。
     */
    const val CREDENTIAL_KEY = "yxy_card"

    private const val BASE = "https://compus.xiaofubao.com"
    private const val CACHE_TTL_MS = 5 * 60 * 1000L
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var cached: CampusCardBalance? = null
    private var cachedKey: String? = null

    fun cachedBalance(phone: String): CampusCardBalance? {
        if (cachedKey != phone) return null
        val snapshot = cached ?: return null
        if (System.currentTimeMillis() - snapshot.fetchedAtMillis > CACHE_TTL_MS) return null
        return snapshot
    }

    fun clearCache() {
        cached = null
        cachedKey = null
    }

    // ── 设备标识 ──

    private const val PREFS = "yxy_card"
    private const val KEY_DEVICE_ID = "device_id"

    /** 每台机器一个稳定 deviceId（首次生成后落盘，之后一直用它）。 */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString().uppercase()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    // ── 纯函数：密码加密（可单测）──

    /** 明文密码 → MD5 十六进制（小写），与 Python `hashlib.md5(...).hexdigest()` 一致。 */
    internal fun md5Hex(password: String): String =
        MessageDigest.getInstance("MD5").digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * MD5 之后再用平台公钥做 RSA/PKCS#1 v1.5 加密，输出 Base64。
     *
     * `publicKeyBase64` 是接口给的裸密钥体（不带 PEM 头尾、可能带换行）。
     */
    internal fun encryptPassword(password: String, publicKeyBase64: String): String {
        val der = Base64.getDecoder().decode(publicKeyBase64.replace(Regex("\\s"), ""))
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val digest = md5Hex(password).toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(cipher.doFinal(digest))
    }

    // ── 请求 ──

    private fun post(path: String, params: Map<String, String>): JsonObject {
        val url = (BASE + path).toHttpUrlOrNull()
            ?.newBuilder()
            ?.apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            ?.build()
            ?: throw IllegalStateException("接口地址不可用")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(ByteArray(0).toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("校园卡平台返回 HTTP ${response.code}")
            }
            return JsonParser.parseString(body).asJsonObject
        }
    }

    /**
     * 余额查询。`phone` 是易校园账号（手机号），`password` 是易校园密码。
     *
     * 平台自己会给失败原因（`message`），原样交给上层 —— 不自己编。
     */
    suspend fun query(
        context: Context,
        phone: String,
        password: String,
        forceRefresh: Boolean = false,
    ): Result<CampusCardBalance> = withContext(Dispatchers.IO) {
        try {
            if (phone.isBlank() || password.isBlank()) {
                return@withContext Result.failure(IllegalStateException("还没有绑定校园卡账号"))
            }
            if (!forceRefresh) {
                cachedBalance(phone)?.let { return@withContext Result.success(it) }
            }

            val device = deviceId(context)

            // ① 取公钥 → 加密密码
            val keyResponse = post("/login/getPublicKey", mapOf("deviceId" to device))
            requireSuccess(keyResponse, "获取公钥")
            val publicKey = keyResponse.getAsJsonObject("data")?.get("publicKey")?.asString.orEmpty()
            if (publicKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("平台没有返回加密公钥"))
            }
            val encrypted = encryptPassword(password, publicKey)

            // ② 登录
            val login = post(
                "/login/doLoginByPwd",
                mapOf(
                    "mobilePhone" to phone,
                    "password" to encrypted,
                    "osType" to "android",
                    "deviceId" to device,
                ),
            )
            requireSuccess(login, "登录")
            val data = login.getAsJsonObject("data")
                ?: return@withContext Result.failure(IllegalStateException("登录返回缺少 data"))
            val id = data.get("id")?.asString.orEmpty()
            val token = data.get("token")?.asString.orEmpty()
            if (id.isBlank() || token.isBlank()) {
                return@withContext Result.failure(IllegalStateException("登录返回缺少 id/token"))
            }
            val serverDevice = data.get("deviceId")?.asString?.takeIf { it.isNotBlank() } ?: device
            val userName = data.get("userName")?.asString.orEmpty()
            val schoolName = data.get("schoolName")?.asString.orEmpty()

            // ③ 余额
            val money = post(
                "/compus/user/getCardMoney",
                mapOf("id" to id, "token" to token, "deviceId" to serverDevice),
            )
            requireSuccess(money, "查询余额")
            // data 可能是数字也可能是字符串（平台两侧都出现过），两种都要吃得下
            val balance = money.get("data")?.asDouble
                ?: return@withContext Result.failure(IllegalStateException("余额字段无法解析"))

            val result = CampusCardBalance(
                balance = balance,
                userName = userName,
                schoolName = schoolName,
            )
            cached = result
            cachedKey = phone
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 平台用 `statusCode = 0` 表示成功；失败时把 `message` 原文带出去。 */
    private fun requireSuccess(json: JsonObject, action: String) {
        val code = json.get("statusCode")?.asInt ?: 0
        if (code != 0) {
            val message = json.get("message")?.asString?.takeIf { it.isNotBlank() } ?: "未知原因"
            throw IllegalStateException("$action 失败：$message")
        }
    }
}
