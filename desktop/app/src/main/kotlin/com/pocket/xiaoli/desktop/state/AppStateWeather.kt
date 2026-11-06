package com.pocket.xiaoli.desktop.state

import com.pocket.xiaoli.core.weather.WeatherRepository
import com.pocket.xiaoli.desktop.data.DesktopWeather
import kotlinx.coroutines.CancellationException

/**
 * AppState 的天气动作。
 *
 * 解析、WMO 代码映射、30 分钟节流、「失败但仍有缓存」的语义全在共享核心
 * `core/weather`（Android 与桌面吃同一份，测试也跑在核心里）；这里只做两件事：
 * 把缓存接到本机数据文件上，把结果搬到界面上。
 */

/**
 * 读取天气。30 分钟内不重复联网（除非 [force] = 用户主动点刷新）。
 *
 * 失败**不清空**已有快照：断网时那一块天气上一秒还是对的，让它凭空消失比标注
 * 「用的是上次的缓存」更糟。
 */
suspend fun AppState.loadWeather(force: Boolean = false) {
    if (weatherBusy) return
    weatherBusy = true
    weatherNote = null
    try {
        when (val result = DesktopWeather.load(
            read = { data.weatherRaw to data.weatherAt },
            write = { json, at -> mutate { it.copy(weatherRaw = json, weatherAt = at) } },
            force = force,
        )) {
            is WeatherRepository.Result.Success -> {
                weather = result.snapshot
                if (result.fromCache) weatherNote = "半小时前拉过，用的是缓存"
            }
            is WeatherRepository.Result.Failure -> {
                result.cached?.let { weather = it }
                weatherNote = result.message
            }
        }
    } catch (e: CancellationException) {
        throw e
    } finally {
        weatherBusy = false
    }
}
