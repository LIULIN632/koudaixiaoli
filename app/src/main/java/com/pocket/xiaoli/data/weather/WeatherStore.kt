package com.pocket.xiaoli.data.weather

import android.content.Context
import android.content.SharedPreferences

/**
 * 天气缓存的落盘层：上一次的**原始 JSON** + 写入时刻。
 *
 * ## 为什么存原文而不是解析后的对象
 *
 * 解析规则会随版本变化（比如以后想多要一个字段），存对象就得为老缓存做迁移；
 * 存原文则永远能用**当前**解析器重读旧数据 —— 最坏情况也只是解析失败一次，
 * 然后下一次联网覆盖它。
 *
 * ## 为什么单独一个 prefs 文件
 *
 * 与 `AppFeedStore` / `ProactiveStore` 同一条理由：这些键只有天气用，
 * 丢了最多重新联网一次。塞进主容器只会让主 prefs 更难读。
 *
 * `AppContainer` 的「清空全部数据」按 `shared_prefs/` 目录枚举，不需要额外登记。
 */
class WeatherStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cachedJson(): String? = prefs.getString(KEY_CACHED_JSON, null)

    fun cachedAt(): Long = prefs.getLong(KEY_CACHED_AT, 0L)

    fun save(json: String, at: Long) {
        prefs.edit()
            .putString(KEY_CACHED_JSON, json)
            .putLong(KEY_CACHED_AT, at)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "pocket_weather"
        private const val KEY_CACHED_JSON = "cached_json"
        private const val KEY_CACHED_AT = "cached_at"
    }
}
