package com.pocket.xiaoli.core.academic

/**
 * 「上次成功的课表接口 URL 模式 + body 模板」的持久化桥。
 *
 * Android 的实现在 `network/ZhengfangImportMemory`（SharedPreferences）；桌面端可以不装实现
 * （每次走全量试错）。共享客户端只认这个接口，不碰任何平台存储。
 */
interface ImportMemoryStore {
    fun load(baseUrl: String): Pair<String, String>?
    fun save(baseUrl: String, urlPattern: String, bodyTemplate: String)
}

object ImportMemoryBridge {
    @Volatile
    var impl: ImportMemoryStore? = null

    fun load(baseUrl: String): Pair<String, String>? = impl?.load(baseUrl)

    fun save(baseUrl: String, urlPattern: String, bodyTemplate: String) {
        impl?.save(baseUrl, urlPattern, bodyTemplate)
    }
}
