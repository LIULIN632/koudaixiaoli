package com.pocket.xiaoli.data.weather

import android.content.Context
import android.content.SharedPreferences
import com.pocket.xiaoli.core.weather.WeatherCache

/**
 * 天气缓存的落盘层：上一次的**原始 JSON** + 写入时刻。
 *
 * 实现共享核心的 [WeatherCache] —— 仓储（`com.pocket.xiaoli.core.weather`）不认识
 * SharedPreferences，各端自己把落盘方式接上；桌面端另有它自己的实现。
 *
 * ## 为什么存原文而不是解析后的对象
 *
 * 解析规则会随版本变化（比如以后想多要一个字段），存对象就得为老缓存做迁移；
 * 存原文则永远能用**当前**解析器重读旧数据 —— 最坏情况也只是解析失败一次，
 * 然后下一次联网覆盖它。
 *
 * ## 为什么按城市分槽（[cityKey]）
 *
 * 天气是按**学校所在城市**取数的，而"淄博的温度挂着烟台的标签"是最容易漏的一种串台：
 * 换学校之后缓存里留着的还是另一座城市的报文。给缓存打标记要么改核心接口、
 * 要么改桌面端存档结构，两头都不划算；**按城市分键**则在结构上不可能读到别城的缓存，
 * 也就不需要任何"记得清一次"的纪律。
 *
 * 代价：升级那一次老键（不分城市的 `cached_json`）读不到，等于天气缓存清一次 ——
 * 与本文件本来就写明的"丢了最多重新联网一次"同一档，可以接受。
 *
 * ## 为什么单独一个 prefs 文件
 *
 * 与 `AppFeedStore` / `ProactiveStore` 同一条理由：这些键只有天气用，
 * 丢了最多重新联网一次。塞进主容器只会让主 prefs 更难读。
 *
 * `AppContainer` 的「清空全部数据」按 `shared_prefs/` 目录枚举，不需要额外登记。
 */
class WeatherStore(
    context: Context,
    /**
     * 当前取数城市（[com.pocket.xiaoli.core.weather.WeatherCityAnchor] 的 cityKey）。
     *
     * 做成函数是因为缓存属于**哪座城市**跟着当前学校变，而本类是单例。
     * null = 这所学校的城市还没配坐标 —— 那时读到的是空槽，写也写不进去（不编缓存）。
     */
    private val cityKey: () -> String? = { null },
) : WeatherCache {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 键按城市后缀分开；城市名是"烟台 / 淄博 / 廊坊"这种短名。 */
    private fun jsonKey(): String? = cityKey()?.let { "$KEY_CACHED_JSON#$it" }

    private fun atKey(): String? = cityKey()?.let { "$KEY_CACHED_AT#$it" }

    override fun cachedJson(): String? = jsonKey()?.let { prefs.getString(it, null) }

    override fun cachedAt(): Long = atKey()?.let { prefs.getLong(it, 0L) } ?: 0L

    override fun save(json: String, at: Long) {
        // 没有城市就不落盘：写进一个"没人会读"的键，等于往 prefs 里堆再也清不掉的垃圾。
        val cityJsonKey = jsonKey() ?: return
        val cityAtKey = atKey() ?: return
        prefs.edit()
            .putString(cityJsonKey, json)
            .putLong(cityAtKey, at)
            .apply()
    }

    override fun clear() {
        // 只清当前城市那一槽：换回原城市时它自己的缓存还在，别的城市一概不动。
        val keys = listOfNotNull(jsonKey(), atKey())
        if (keys.isEmpty()) return
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    companion object {
        private const val PREFS = "pocket_weather"
        private const val KEY_CACHED_JSON = "cached_json"
        private const val KEY_CACHED_AT = "cached_at"
    }
}
