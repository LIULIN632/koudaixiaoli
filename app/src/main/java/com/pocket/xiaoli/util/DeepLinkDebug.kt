package com.pocket.xiaoli.util

import java.net.URI
import java.net.URLDecoder

/** Deep Link 解析结果。 */
sealed interface DeepLinkTarget {
    /** App 内部路由；[routeKey] 由 AppNavigation 映射为具体页面。 */
    data class Route(
        val routeKey: String,
        val args: Map<String, String> = emptyMap(),
    ) : DeepLinkTarget

    /** 交给系统浏览器 / 外部 App 打开的链接。 */
    data class External(val url: String) : DeepLinkTarget

    data class Invalid(val reason: String) : DeepLinkTarget
}

/**
 * Deep Link 调试解析器。
 *
 * 支持：
 * - `pocketxiaoli://timetable` 等内部 Tab / 页面
 * - `pocketxiaoli://map?q=A101`
 * - `http(s)://...` 视为外部链接
 *
 * 纯 JVM 实现，便于单测。
 */
object DeepLinkDebug {

    const val SCHEME = "pocketxiaoli"

    fun resolve(raw: String): DeepLinkTarget {
        val text = raw.trim()
        if (text.isEmpty()) return DeepLinkTarget.Invalid("URI 为空")

        val uri = try {
            URI(text)
        } catch (e: Exception) {
            return DeepLinkTarget.Invalid("URI 格式不正确：${e.message ?: "解析失败"}")
        }

        return when (uri.scheme?.lowercase()) {
            "http", "https" -> DeepLinkTarget.External(text)
            SCHEME -> resolveInternal(uri)
            else -> DeepLinkTarget.Invalid("不支持的 scheme：${uri.scheme ?: "无"}")
        }
    }

    fun describe(target: DeepLinkTarget): String = when (target) {
        is DeepLinkTarget.Route -> buildString {
            append("内部路由：").append(target.routeKey)
            if (target.args.isNotEmpty()) {
                append("  ")
                append(target.args.entries.joinToString("&") { "${it.key}=${it.value}" })
            }
        }
        is DeepLinkTarget.External -> "外部链接：${target.url}"
        is DeepLinkTarget.Invalid -> "无效：${target.reason}"
    }

    private fun resolveInternal(uri: URI): DeepLinkTarget {
        val host = uri.host?.lowercase().orEmpty()
        if (host.isEmpty()) return DeepLinkTarget.Invalid("缺少 host，例如 pocketxiaoli://timetable")

        val params = parseQuery(uri.rawQuery)
        return when (host) {
            "home", "timetable", "services", "profile" ->
                DeepLinkTarget.Route("tab", mapOf("tab" to host))
            "import" -> DeepLinkTarget.Route("import_entry")
            "history" -> DeepLinkTarget.Route("import_history")
            "assistant" -> DeepLinkTarget.Route("assistant")
            // 番茄钟倒计时通知点进来落这里。带不带 `n` 参数都能解析：
            // 那个参数只是为了让每次通知的 PendingIntent 互不相同（否则系统会复用旧的、
            // 停在旧页面），路由本身不关心它。
            "focus" -> DeepLinkTarget.Route("focus")
            "map" -> DeepLinkTarget.Route("campus_map", mapOf("q" to (params["q"] ?: "")))
            "room", "room_layout" -> DeepLinkTarget.Route("room_layout", mapOf("q" to (params["q"] ?: "")))
            "building" -> DeepLinkTarget.Route("building_map", mapOf("name" to (params["name"] ?: "")))
            "service", "web" -> DeepLinkTarget.Route(
                "service_web",
                mapOf(
                    "url" to (params["url"] ?: ""),
                    "title" to (params["title"] ?: ""),
                ),
            )
            "open" -> {
                val url = params["url"].orEmpty()
                if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                    DeepLinkTarget.External(url)
                } else {
                    DeepLinkTarget.Invalid("open 缺少 http(s) url")
                }
            }
            else -> DeepLinkTarget.Invalid("未知深链 host：$host")
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val index = part.indexOf('=')
            val key = if (index >= 0) part.substring(0, index) else part
            val value = if (index >= 0) part.substring(index + 1) else ""
            decode(key) to decode(value)
        }.toMap()
    }

    private fun decode(value: String): String {
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}
