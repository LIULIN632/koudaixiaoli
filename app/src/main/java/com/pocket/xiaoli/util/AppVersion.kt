package com.pocket.xiaoli.util

import android.content.Context

/**
 * 读本机装的是哪个版本。
 *
 * ## 为什么不用 `BuildConfig.VERSION_CODE`
 *
 * 这个项目**没有**开 `buildFeatures.buildConfig`（AGP 8 起默认关闭），所以没有
 * `BuildConfig` 可用；而 `PackageManager` 读出来的是**真正装在机器上的那个包**的版本 ——
 * 比编译期常量更可信（覆盖安装失败、签名不一致这类事故只有它能反映）。
 *
 * 全项目统一走这里，别再各写一遍 `try { packageManager... } catch`。
 */
object AppVersion {

    /** 给人看的版本名，如 `0.1.39`；读不到返回 `"未知"` */
    fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "未知"

    /** 给机器比的版本号（单调整数）；读不到返回 0 */
    fun versionCode(context: Context): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    }.getOrNull() ?: 0
}
