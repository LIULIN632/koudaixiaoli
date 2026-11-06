package com.pocket.xiaoli.desktop.data

import com.pocket.xiaoli.core.weather.WeatherRepository
import com.pocket.xiaoli.core.weather.WeatherSnapshot
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 桌面端天气入口 —— 把共享核心的仓储包一层，让 UI 模块不必看见 OkHttp。
 *
 * `desktop/core` 用 `implementation` 依赖 OkHttp，所以 `desktop/app` 里连类型都不可见；
 * 客户端与连接池留在这里，UI 只拿 [WeatherRepository.Result]。
 */
object DesktopWeather {

    /** 连接池复用：仓储对象每次新建（很便宜），OkHttpClient 不便宜。 */
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @param read 当前缓存（原始 JSON + 写入时刻）
     * @param write 落盘新缓存；空串表示清掉
     * @param force true = 用户主动刷新，跳过 30 分钟节流
     */
    suspend fun load(
        read: () -> Pair<String, Long>,
        write: (json: String, at: Long) -> Unit,
        force: Boolean = false,
    ): WeatherRepository.Result = WeatherRepository(
        store = StoreWeatherCache(read, write),
        client = client,
    ).load(force)

    /** 给 UI 直接读的那份解析结果（缓存里的原文按当前规则重读）。 */
    fun parse(json: String, now: Long): WeatherSnapshot? =
        runCatching { com.pocket.xiaoli.core.weather.parseWeather(json, now) }.getOrNull()
}
