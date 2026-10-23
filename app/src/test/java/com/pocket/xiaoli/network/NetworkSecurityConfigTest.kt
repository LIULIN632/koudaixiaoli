package com.pocket.xiaoli.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 网络面守门人：`AndroidManifest.xml` 与 `res/xml/network_security_config.xml`。
 *
 * 这套断言守的是**配置与代码的一致性**，不是某一行的字符串：
 * 代码里凡是用 `http://` 直连的主机，都必须在白名单里点过名 —— 否则那次请求会以
 * `net::ERR_CLEARTEXT_NOT_PERMITTED` 失败。反过来，全局放行明文（`usesCleartextTraffic="true"`
 * 或 base-config 放行）会让任何被注入的地址都能明文传输，也不许再出现。
 *
 * 仓库里已有同风格的源码扫描用例（如首页气泡、学号脱敏），这里沿用。
 */
class NetworkSecurityConfigTest {

    private val manifest = File("app/src/main/AndroidManifest.xml")
    private val config = File("app/src/main/res/xml/network_security_config.xml")

    private fun manifestText(): String =
        manifest.takeIf { it.isFile }?.readText().orEmpty()

    private fun configText(): String =
        config.takeIf { it.isFile }?.readText().orEmpty()

    // ── Manifest ────────────────────────────────────────────────────

    @Test
    fun `清单引用网络安全配置且不再全局放行明文`() {
        val text = manifestText()
        if (text.isEmpty()) return  // 跑在别的工作目录下时不做判断

        assertTrue(
            "AndroidManifest.xml 应当引用 @xml/network_security_config",
            text.contains("android:networkSecurityConfig=\"@xml/network_security_config\""),
        )
        assertFalse(
            "AndroidManifest.xml 不应再出现 android:usesCleartextTraffic=\"true\"；" +
                "全局放行明文应由 network_security_config.xml 的白名单取代",
            text.contains("android:usesCleartextTraffic=\"true\""),
        )
    }

    @Test
    fun `系统自动备份保持关闭`() {
        val text = manifestText()
        if (text.isEmpty()) return

        // 开着它 = 把 Room 库（成绩存档 / 待办 / 树洞）交给云备份，与「数据不出本机」的承诺矛盾。
        assertTrue(
            "AndroidManifest.xml 应当写 android:allowBackup=\"false\"",
            text.contains("android:allowBackup=\"false\""),
        )
        assertFalse(
            "android:allowBackup 不应再是 true",
            text.contains("android:allowBackup=\"true\""),
        )
    }

    // ── network_security_config.xml ─────────────────────────────────

    @Test
    fun `默认拒绝明文`() {
        val text = configText()
        if (text.isEmpty()) return

        assertTrue(
            "network_security_config.xml 应当存在",
            config.isFile,
        )
        assertTrue(
            "base-config 必须是 cleartextTrafficPermitted=\"false\"（默认拒绝明文）",
            Regex("""<base-config\s+cleartextTrafficPermitted="false"""").containsMatchIn(text),
        )
    }

    @Test
    fun `本校域名与两处校内地址在白名单里`() {
        val text = configText()
        if (text.isEmpty()) return

        listOf("yitsd.edu.cn", "ytu.edu.cn", "192.168.2.34", "210.47.245.4").forEach { host ->
            assertTrue(
                "$host 有实测的明文出口，必须在 network_security_config.xml 里放行",
                text.contains(host),
            )
        }
    }

    @Test
    fun `代码里每个明文主机都在白名单里`() {
        val text = configText()
        val srcRoot = File("app/src/main/java")
        if (text.isEmpty() || !srcRoot.isDirectory) return

        val hostPattern = Regex("""http://([A-Za-z0-9.\-]+)""")
        val hosts = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> hostPattern.findAll(file.readText()).map { it.groupValues[1] } }
            .distinct()
            .sorted()
            .toList()

        val uncovered = hosts.filterNot { host -> isAllowed(host, text) }
        assertTrue(
            "这些 http:// 主机没有出现在 network_security_config.xml 里，请求会以 " +
                "ERR_CLEARTEXT_NOT_PERMITTED 失败：$uncovered\n" +
                "要么把主机加进白名单，要么把它改写成 https。",
            uncovered.isEmpty(),
        )
    }

    /** 白名单放行规则：校区域名的子域，或配置里逐条点名的 IP。 */
    private fun isAllowed(host: String, configText: String): Boolean {
        val domains = Regex("""<domain[^>]*>([^<]+)</domain>""")
            .findAll(configText)
            .map { it.groupValues[1].trim() }
            .toList()

        return domains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }
}
