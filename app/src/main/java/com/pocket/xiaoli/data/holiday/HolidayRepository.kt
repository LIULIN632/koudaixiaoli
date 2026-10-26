package com.pocket.xiaoli.data.holiday

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pocket.xiaoli.network.NetworkLogInterceptor
import com.pocket.xiaoli.network.NetworkLogStore
import com.pocket.xiaoli.data.SelfHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 节假日数据仓库 —— 移植自「聚在工大」对 holiday-cn 的接入方式，
 * 改为 OkHttp 直连 + 多镜像回退 + SharedPreferences 本地缓存。
 *
 * 数据源：NateScarlet/holiday-cn（每年一个 JSON，含全年放假与调休上班日）
 * 缓存策略：按年份落盘，命中即不再请求；网络失败静默回退缓存，绝不阻塞 UI。
 * 约束：只读公开静态数据，不上传任何用户数据。
 */
class HolidayRepository(context: Context) {

    companion object {
        private const val PREF_NAME = "pocket_xiaoli_holiday"
        private const val KEY_YEARS = "years"

        /**
         * 多镜像回退：GitHub raw 国内常不可达，jsDelivr 系走 CDN。
         *
         * ⚠️ 顺序是**实测排的**，别把 raw.githubusercontent 挪回第一位：
         * 2026-10-17 的日志导出里，vivo V2307A 上它 `GET ERR 26363ms` ——
         * 卡了 26 秒才失败，而同一时刻 jsDelivr 三家里最快的只用 274ms。
         * 放在首位等于每次冷启动先白等半分钟（这正是「自动更新校园数据」关掉之后
         * 启动仍然慢的原因）。
         *
         * 自建（`SelfHost.BASE_URL`）配置后插在最前面 —— 我们只是把上游那份
         * 公开节假日 JSON 镜像一份，链路本身一个不改。
         */
        private val MIRRORS = SelfHost.holidayMirrors(
            listOf(
                "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json",
                "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json",
                "https://gcore.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json",
                "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/%d.json",
            )
        )
    }

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val client = OkHttpClient.Builder()

        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 同步读取本地全部缓存（启动首帧先出数据，不等网络）。 */
    fun cachedAll(): List<HolidayDay> = readCache().values.flatten().sortedBy { it.date }

    /** 某年是否已有缓存。 */
    fun hasYear(year: Int): Boolean = readCache().containsKey(year.toString())

    /**
     * 确保 [year] 的节假日可用：有缓存直接返回；否则按镜像顺序尝试下载并写缓存。
     * 任何异常都不抛出，最差情况返回已有缓存。
     */
    suspend fun ensureYear(year: Int, force: Boolean = false): List<HolidayDay> =
        withContext(Dispatchers.IO) {
            val cache = readCache()
            if (!force && cache.containsKey(year.toString())) {
                return@withContext cache[year.toString()].orEmpty()
            }
            for (mirror in MIRRORS) {
                val fetched = runCatching { download(mirror.format(year)) }.getOrNull()
                if (!fetched.isNullOrEmpty()) {
                    writeCache(cache.toMutableMap().apply { put(year.toString(), fetched) })
                    return@withContext fetched
                }
            }
            cache[year.toString()].orEmpty()
        }

    private fun download(url: String): List<HolidayDay> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            // 内容类型 + 响应开头：用来分清"上游给了 HTML 拦截页"和"给了 JSON 但 0 条" ——
            // 校园网把 200 换成登录/拦截页时，光看状态码看不出来。
            val clue = "content-type=${resp.header("Content-Type") ?: "?"} 开头「${body.take(40).replace('\n', ' ')}」"
            if (body.isBlank()) return rejected(url, "响应体为空")
            val parsed = try {
                gson.fromJson(body, HolidayYearResponse::class.java)
            } catch (e: Exception) {
                return rejected(url, "解析抛错 ${e.javaClass.simpleName}｜$clue")
            } ?: return rejected(url, "解析结果为 null｜$clue")
            // 只接受 yyyy-MM-dd，防止异常数据污染缓存
            val days = parsed.days.filter { it.date.length == 10 && it.date[4] == '-' && it.date[7] == '-' }
            if (days.isEmpty()) {
                return rejected(url, "解析到 ${parsed.days.size} 条，全被日期格式规则挡掉｜$clue")
            }
            return days
        }
    }

    /**
     * **HTTP 200 但内容用不了**时补一条带原因的日志记录。
     *
     * 为什么单独记：拦截器那条只写状态码，导出里"200 且被拒"和"200 且成功"长得一模一样，
     * 于是四个镜像全部试完看起来像网络问题。2026-10-26 那份导出就是这样：
     * 两年 × 四个镜像全是 `GET 200`，真正的原因（解析后 0 条）没有任何地方说。
     */
    private fun rejected(url: String, reason: String): List<HolidayDay> {
        NetworkLogStore.record(
            method = "GET",
            url = url,
            status = 200,
            durationMs = 0L,
            error = "节假日数据不可用：$reason",
        )
        return emptyList()
    }

    private fun readCache(): Map<String, List<HolidayDay>> {
        val raw = prefs.getString(KEY_YEARS, null) ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, List<HolidayDay>>>(
                raw,
                object : TypeToken<Map<String, List<HolidayDay>>>() {}.type,
            )
        }.getOrNull().orEmpty()
    }

    private fun writeCache(value: Map<String, List<HolidayDay>>) {
        runCatching { prefs.edit().putString(KEY_YEARS, gson.toJson(value)).apply() }
    }
}
