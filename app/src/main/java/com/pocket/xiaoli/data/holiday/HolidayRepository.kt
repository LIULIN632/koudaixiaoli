package com.pocket.xiaoli.data.holiday

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pocket.xiaoli.network.NetworkLogInterceptor
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
            if (body.isBlank()) return emptyList()
            val parsed = gson.fromJson(body, HolidayYearResponse::class.java) ?: return emptyList()
            // 只接受 yyyy-MM-dd，防止异常数据污染缓存
            return parsed.days.filter { it.date.length == 10 && it.date[4] == '-' && it.date[7] == '-' }
        }
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
