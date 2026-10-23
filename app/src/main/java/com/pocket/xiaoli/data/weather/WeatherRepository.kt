package com.pocket.xiaoli.data.weather

import com.pocket.xiaoli.network.NetworkLogInterceptor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 天气仓储：**拉一次、缓存 30 分钟、失败回落上一次**。
 *
 * ## 为什么需要缓存层，而不是页面自己拉
 *
 * 天气页会被反复翻到（课间看一眼、出门前看一眼）。每次都联网：
 * 1. 校园网/移动网络下要等 1–3 秒，页面每次都转圈；
 * 2. Open-Meteo 是公益免费接口，反复请求不礼貌，也更容易被限流。
 *
 * 与 `AppFeedRepository` 同一条纪律：**失败不等于没东西看**。有缓存时
 * 返回 [Result.Failure.cached]，页面照常渲染，只在顶部加一句「显示的是上次的天气」。
 *
 * ## 数据源
 *
 * Open-Meteo（github.com/open-meteo/open-meteo），免 Key、HTTPS。
 * 请求里只有坐标与字段名，**不含任何设备/用户标识** —— 符合本项目
 * 「不接第三方统计、不上传用户数据」的红线。
 * 坐标为什么是烟台市见 [WeatherSnapshot] 的 KDoc。
 */
class WeatherRepository(
    private val store: WeatherStore,
    private val endpoint: String = FORECAST_ENDPOINT,
    private val clock: () -> Long = System::currentTimeMillis,
    private val client: OkHttpClient = defaultClient(),
) {

    sealed interface Result {
        /** 拿到了一份（刚联网的或仍在 TTL 内的）天气。 */
        data class Success(
            val snapshot: WeatherSnapshot,
            val fromCache: Boolean,
        ) : Result

        /**
         * 这次没拿到。
         *
         * @param cached 上一次成功的数据；非 null 时页面应照常渲染并标注"上次的天气"，
         *   而不是把整页换成错误提示。
         */
        data class Failure(
            val message: String,
            val cached: WeatherSnapshot?,
        ) : Result
    }

    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)

    /**
     * 全 App 共享的**最后一份可用天气**。
     *
     * 三个消费方：首页时钟旁的小胶囊、鲤鱼的 `get_weather` 工具、主动提醒的气泡。
     * 它们都不该各自拿着仓储再发起一次请求 —— 否则首页和天气页会各拉一份，
     * 断网时还会一个有一个没有。
     *
     * ⚠️ 只发布「成功」或「失败但仍有缓存」的快照；纯失败**不清空**已有值。
     * 清空会让首页那块已经显示出来的天气在用户眼前凭空消失，而它上一秒还是对的。
     */
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    /**
     * 读取天气。
     *
     * @param force true = 用户主动点刷新，跳过 30 分钟节流。
     */
    suspend fun load(force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val now = clock()
        val cached = readCache()

        if (!force && cached != null && now - store.cachedAt() in 0 until TTL_MS) {
            _snapshot.value = cached
            return@withContext Result.Success(cached, fromCache = true)
        }

        try {
            val request = Request.Builder()
                .url("$endpoint?$FORECAST_QUERY")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext failure("天气服务暂时不可用（HTTP ${response.code}）", cached)
                }
                val snapshot = try {
                    parseWeather(body, now)
                } catch (e: Exception) {
                    return@withContext failure("天气数据解析失败，请稍后重试", cached)
                }
                store.save(body, now)
                _snapshot.value = snapshot
                Result.Success(snapshot, fromCache = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure("无法连接天气服务，请检查网络后重试", cached)
        }
    }

    /** 失败时把缓存（如果有）同步给观察者，再原样返回错误。 */
    private fun failure(message: String, cached: WeatherSnapshot?): Result.Failure {
        cached?.let { _snapshot.value = it }
        return Result.Failure(message, cached)
    }

    private fun readCache(): WeatherSnapshot? {
        val json = store.cachedJson() ?: return null
        return try {
            parseWeather(json, store.cachedAt())
        } catch (e: Exception) {
            // 结构损坏的缓存留着没有意义，删掉以免每次都白白解析一遍。
            store.clear()
            null
        }
    }

    companion object {
        /** Open-Meteo 的预报端点（开源项目：github.com/open-meteo/open-meteo）。 */
        internal const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"

        /**
         * 请求参数。
         *
         * `timezone=Asia/Shanghai` 固定为烟台所在时区：`timezone=auto` 也会得到同一结果，
         * 但显式写出来才能保证「字符串比较当前小时」这一解析前提不随服务端默认值变化。
         *
         * ⚠️ 不要删 `wind_speed_unit=kmh`：默认单位是 km/h，但写出来才能让
         * [windScale] 的档位与接口单位永久对齐。
         */
        internal const val FORECAST_QUERY =
            "latitude=$WEATHER_LATITUDE&longitude=$WEATHER_LONGITUDE" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day," +
                "precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
                "precipitation_probability_max,uv_index_max" +
                "&timezone=Asia%2FShanghai&forecast_days=7&wind_speed_unit=kmh"

        /** 缓存新鲜度：30 分钟内不再联网。 */
        const val TTL_MS = 30 * 60 * 1000L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(NetworkLogInterceptor)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}
