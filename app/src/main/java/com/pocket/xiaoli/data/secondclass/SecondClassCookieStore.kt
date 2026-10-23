package com.pocket.xiaoli.data.secondclass

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 第二课堂 Cookie 的落盘口。
 *
 * ## 为什么是加密存储
 *
 * 这份 cookie **等价于用户本人的登录态**：拿到它就能以该学生的身份读到梦空间的成绩单。
 * 明文放在 `SharedPreferences` 里，root / adb backup / 调试工具都能直接冒用。
 * 项目里教务凭据（`credPrefs`）与趣智校园登录态（`QzxySessionStore`）都是这个规格，这里保持一致。
 *
 * ## 为什么单独一个文件
 *
 * 退出第二课堂账号时只清这一个文件，不碰教务凭据、不碰学习平台 cookie。
 * 另有一个真实的原因：`WebImportScreen.kt:260` 已经踩过
 * **`CookieManager` 是全 App 单例**这个坑 —— 清一处会顺手清掉别的服务的登录态。
 * 落盘这一侧同理，分开存才能分开清。
 *
 * ## 为什么 `load` 返回 `Map` 而不是列表
 *
 * 按域分桶存：一个域的快照坏掉（比如手工改过存档）只影响那一个域，
 * 不会把其它域一起读废 —— "全有或全无"的存储在这里是缺点。
 */
interface SecondClassCookieStore {

    fun load(): Map<String, SecondClassCookieBucket>

    fun save(domain: String, bucket: SecondClassCookieBucket)

    fun remove(domain: String)

    fun clear()
}

/**
 * 加密实现。设备不支持加密时**退回明文并打日志**，而不是让功能不可用
 * （与 `AppContainer.kt:561`、`QzxySessionStore.kt:45-50` 的处理一致）。
 */
class EncryptedSecondClassCookieStore(context: Context) : SecondClassCookieStore {

    private val prefs: SharedPreferences by lazy {
        val ctx = context.applicationContext
        try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // 退回明文时打日志：这意味着登录态会以明文落盘，排查线上问题时需要知道这件事。
            Log.w(TAG, "EncryptedSharedPreferences 不可用，第二课堂 Cookie 退化为明文存储", e)
            ctx.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    override fun load(): Map<String, SecondClassCookieBucket> {
        val out = mutableMapOf<String, SecondClassCookieBucket>()
        for ((rawKey, rawValue) in prefs.all) {
            if (!rawKey.startsWith(KEY_PREFIX)) continue
            val domain = rawKey.removePrefix(KEY_PREFIX)
            if (domain.isBlank()) continue
            val bucket = decodeBucket(rawValue as? String) ?: continue
            if (bucket.cookies.isEmpty()) continue
            out[domain] = bucket
        }
        return out
    }

    override fun save(domain: String, bucket: SecondClassCookieBucket) {
        val key = KEY_PREFIX + domain.lowercase()
        val raw = runCatching { gson.toJson(SecondClassCookiePayload(cookies = bucket.cookies)) }
            .onFailure { Log.w(TAG, "第二课堂 Cookie 序列化失败，本次不落盘", it) }
            .getOrNull() ?: return
        prefs.edit().putString(key, raw).apply()
    }

    override fun remove(domain: String) {
        prefs.edit().remove(KEY_PREFIX + domain.lowercase()).apply()
    }

    override fun clear() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * 解一份存档。**任何形式的坏数据都只丢这一桶，不抛异常**——
     * 落盘内容可能来自旧版本、被手工改过、或加密密钥轮换后解不开。
     */
    private fun decodeBucket(raw: String?): SecondClassCookieBucket? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val payload = gson.fromJson(raw, SecondClassCookiePayload::class.java)
            if (payload == null || payload.version != SecondClassCookiePayload.CURRENT_VERSION) {
                // 版本不认识：宁可当作没登录（引导用户重新走一次登录），
                // 也不要按旧结构硬读出一堆 null 字段再往下传。
                Log.i(TAG, "第二课堂 Cookie 快照版本不符（${payload?.version}），丢弃")
                return null
            }
            // 字段级兜底：即便 Gson 走了 UnsafeAllocator 把值填成 null，
            // 这里也把它们收拾成"这条 cookie 不存在"，不让 null 漏进业务层。
            // ⚠️ 静态类型上元素"不可能"为 null，但反射赋值那条路径真的会塞 null 进来，
            // 所以 `?:` 那道过滤不能省（写成 takeIf 是为了不触发"多余安全调用"告警）。
            val cookies = payload.cookies.orEmpty()
                .map { it.takeIf { c -> c.name.isNotBlank() } }
                .filterNotNull()
            // 只还回 cookie 本身：dirty / lastDirtyAt / lastFlushedAt 是**进程内实时状态**，
            // 从存档恢复毫无意义（恢复出来的 dirty=true 只会让启动后白写一次盘）。
            SecondClassCookieBucket(cookies = cookies)
        }.getOrElse {
            Log.w(TAG, "第二课堂 Cookie 快照解析失败，丢弃该域快照", it)
            null
        }
    }

    companion object {
        private const val TAG = "SecondClassCookieStore"
        private const val FILE_NAME = "secondclass_cookies_encrypted"
        private const val KEY_PREFIX = "cookies@"

        private val gson = com.google.gson.Gson()
    }
}
