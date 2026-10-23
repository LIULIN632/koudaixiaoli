package com.pocket.xiaoli.data.promo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 示例配置与实际校验器的**一致性**测试。
 *
 * ## 为什么需要它
 *
 * `tools/promos.example.json` 是给运营/合作方看的格式样板。它最容易出的问题是
 * **文档与代码漂移**：改了枚举名、改了字段、或写了白名单外的域名，
 * 而样例文件没跟着改 —— 结果别人照着样例配出来的东西**一条都不生效**，
 * 且客户端不会报任何错（校验器只静默丢弃）。这类问题几乎不可能靠人眼发现。
 *
 * 所以这里把样例文件真的喂给 [PromoValidator]，并断言：
 * - 它是合法 JSON
 * - 每个 slot 名都能被识别（不是拼错的枚举值）
 * - 每个外部链接的域名都在白名单内（否则样例本身就会演示一个错误做法）
 */
class PromoExampleConfigTest {

    /** 从 app 模块目录向上找到仓库根，再定位 tools/。 */
    private fun exampleFile(): File {
        val candidates = listOf(
            File("../tools/promos.example.json"),
            File("tools/promos.example.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到 tools/promos.example.json，候选路径：${candidates.map { it.absolutePath }}")
    }

    @Test
    fun `示例配置是合法 JSON 且每个 slot 都能被识别`() {
        val raw = exampleFile().readText()

        // ⚠️ 样例里所有条目都是 enabled:false（故意的 —— 防止有人直接把样例传上线，
        // 结果页面上出现一堆"示例："推广）。所以解析结果**本来就该是空的**，
        // 这恰好也验证了 enabled:false 确实生效。
        val cfg = PromoValidator.parse(raw, nowSec = 0L)
        assertTrue("样例默认应全部下架（enabled:false），解析结果应为空", cfg.promos.isEmpty())

        // 真正的漂移检查在原始文本上做：样例里出现的每个 slot 名都必须命中枚举。
        // 拼错一个字母就完全不生效，而客户端不会报任何错 —— 只能靠这里拦住。
        val slotsInFile = Regex(""""slot"\s*:\s*"([^"]+)"""").findAll(raw)
            .map { it.groupValues[1] }
            .toSet()
        assertEquals("样例应覆盖全部 5 个 slot", 5, slotsInFile.size)
        slotsInFile.forEach { name ->
            assertTrue(
                "样例里的 slot「$name」不在 PromoSlot 枚举内 —— 服务端照此配置将完全不生效",
                PromoSlot.fromKey(name) != null,
            )
        }
    }

    @Test
    fun `样例全部启用后每一条都能通过校验`() {
        // 把样例里的 enabled 翻成 true 再解析：这样能验证**字段本身**都合法
        // （标题/正文长度、时间窗、action 结构），而不只是"被 enabled 挡住了"
        val raw = exampleFile().readText().replace("\"enabled\": false", "\"enabled\": true")
        val cfg = PromoValidator.parse(raw, nowSec = 1_790_000_000L)

        assertEquals("6 条示例应全部通过校验", 6, cfg.promos.size)
        // 带 action 的条目必须解析出可点动作（说明 URL 确实在白名单内）
        assertTrue(
            "样例中带 external action 的条目应能解析出跳转",
            cfg.promos.count { it.action is PromoAction.External } >= 3,
        )
        // 无 action 的条目应保持不可点
        assertTrue(
            "无 action 的样例条目应不可点",
            cfg.promos.any { it.action == null },
        )
    }

    @Test
    fun `示例里的外部链接域名都在白名单内`() {
        val raw = exampleFile().readText()
        val urls = Regex(""""url"\s*:\s*"([^"]+)"""").findAll(raw).map { it.groupValues[1] }.toList()

        assertTrue("样例里应有示例链接", urls.isNotEmpty())
        urls.forEach { url ->
            val host = runCatching { java.net.URI(url).host }.getOrNull()
            val allowed = host != null && PromoValidator.ALLOWED_HOSTS.any { h ->
                host == h || host.endsWith(".$h")
            }
            assertTrue(
                "样例里的链接「$url」不在白名单内 —— 样例本身在演示一个会被拒绝的做法",
                allowed,
            )
        }
    }

    @Test
    fun `白名单里的域名都写成了纯小写且不含协议`() {
        // 白名单是拿 host 做比较的，写成 "https://x.com" 或带大写会导致永远匹配不上
        PromoValidator.ALLOWED_HOSTS.forEach { h ->
            assertEquals("白名单条目必须是小写：$h", h.lowercase(), h)
            assertTrue("白名单条目不该带协议或路径：$h", !h.contains("/") && !h.contains(":"))
        }
    }
}
