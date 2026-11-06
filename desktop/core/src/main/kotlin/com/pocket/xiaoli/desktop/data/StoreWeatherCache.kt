package com.pocket.xiaoli.desktop.data

import com.pocket.xiaoli.core.weather.WeatherCache

/**
 * 桌面端的天气缓存 —— 落在 `pocket-desktop.json` 的 [AppData.weatherRaw] / [AppData.weatherAt]。
 *
 * 为什么不另开一个文件：天气是这份数据里最小的一个装饰性字段，单独一个 JSON 只会多一个
 * 备份/迁移要考虑的对象；而 [Store] 已经是「临时文件 + 原子改名」写的，跟着它走最省心。
 *
 * 读写都通过回调交给调用方（AppState），所以这里不直接依赖 Store 单例，测试可以给一对内存闭包。
 */
class StoreWeatherCache(
    private val read: () -> Pair<String, Long>,
    private val write: (json: String, at: Long) -> Unit,
) : WeatherCache {

    override fun cachedJson(): String? = read().first.takeIf { it.isNotBlank() }

    override fun cachedAt(): Long = read().second

    override fun save(json: String, at: Long) = write(json, at)

    override fun clear() = write("", 0L)
}
