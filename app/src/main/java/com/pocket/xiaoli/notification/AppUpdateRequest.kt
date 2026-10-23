package com.pocket.xiaoli.notification

import java.io.File

/**
 * 一次「从通知里直接更新」的请求。
 *
 * 通知只能塞字符串，所以真正要用的东西（下载地址、期望的 SHA-256、版本号）
 * 在跳转时被塞进 Intent extras，再由 [AppUpdateReceiver] 还原成这个对象。
 *
 * ## 为什么要有一个单独的类
 *
 * 校验逻辑（地址不能空、版本号不能空、文件名怎么拼）如果散在 Receiver 和 Worker 里，
 * 就会出现"通知能点但 Worker 里才发现地址是空的"这种哑火。集中在这里以后
 * 可以直接被单测覆盖，不用拖 Android 运行时进来。
 */
data class AppUpdateRequest(
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
) {

    /** 地址或版本号是空的 ⇒ 这个请求没法用，Receiver 直接放弃 */
    val isValid: Boolean
        get() = apkUrl.isNotBlank() && versionName.isNotBlank()

    /** 下载落点；同名文件复用，重试不会堆一堆半截包 */
    fun targetFile(directory: File): File = File(directory, "pocket-xiaoli-v$versionName.apk")

    companion object {

        /** 把 Intent 里的原始字符串规整成请求对象（`v0.1.59` 与 `0.1.59` 等价） */
        fun of(rawVersion: String?, rawUrl: String?, rawSha256: String?): AppUpdateRequest =
            AppUpdateRequest(
                versionName = rawVersion.orEmpty().trim().removePrefix("v").removePrefix("V"),
                apkUrl = rawUrl.orEmpty().trim(),
                sha256 = rawSha256.orEmpty().trim(),
            )
    }
}
