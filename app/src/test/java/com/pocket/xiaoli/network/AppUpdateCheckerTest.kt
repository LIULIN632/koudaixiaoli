package com.pocket.xiaoli.network

import java.io.File
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * AppUpdateChecker 版本比较边界用例。
 */
class AppUpdateCheckerTest {

    private val checker = AppUpdateChecker()

    /**
     * 守门人：feed 的主镜像必须打在**发版推的那个分支**（`main`）。
     *
     * 2026-10-19 出事过一次：`GITEE_RAW_FEED_URL` 写的是 `/raw/master/`，而发版脚本推 `main`，
     * Gitee 上的 `master` 一路停在 v0.1.68 —— 客户端读到的 feed 永远是旧的，
     * 于是「新版已经发了，App 还说已是最新」。这条测试就是钉死这个前提：
     * 主镜像只能是 `main`，`master` 只配做兜底。
     */
    @Test
    fun feedMirror_主镜像是main分支() {
        assertTrue(
            "GITEE_RAW_FEED_URL 应指向 /raw/main/：${AppUpdateChecker.GITEE_RAW_FEED_URL}",
            AppUpdateChecker.GITEE_RAW_FEED_URL.contains("/raw/main/announce/feed.json"),
        )
        assertFalse(
            "GITEE_RAW_FEED_URL 不能再用 /raw/master/",
            AppUpdateChecker.GITEE_RAW_FEED_URL.contains("/raw/master/"),
        )
        assertTrue(
            "保留 master 兜底常量",
            AppUpdateChecker.GITEE_RAW_FEED_URL_MASTER.contains("/raw/master/announce/feed.json"),
        )
    }
    @Test
    fun isNewer_patchBump_true() {
        assertTrue(checker.isNewer("v11.0.17", "11.0.16"))
        assertTrue(checker.isNewer("11.0.16", "11.0.15"))
    }

    @Test
    fun isNewer_minorBump_true() {
        assertTrue(checker.isNewer("v11.1.0", "11.0.16"))
        assertTrue(checker.isNewer("v12.0.0", "11.99.99"))
    }

    @Test
    fun isNewer_majorBump_true() {
        assertTrue(checker.isNewer("v12.0.0", "11.0.16"))
    }

    @Test
    fun isNewer_sameOrOlder_false() {
        assertFalse(checker.isNewer("v11.0.16", "11.0.16")) // 相同
        assertFalse(checker.isNewer("v11.0.15", "11.0.16")) // 更旧
        assertFalse(checker.isNewer("v10.9.0", "11.0.0"))   // 主版本更旧
    }

    @Test
    fun isNewer_malformed_false() {
        assertFalse(checker.isNewer("", "11.0.16"))
        assertFalse(checker.isNewer("abc", "11.0.16"))
        assertFalse(checker.isNewer("v11", "11.0.16"))       // 不足三段
        assertFalse(checker.isNewer("v11.0.16", "bad"))       // 当前版本非法
    }

    @Test
    fun isNewer_withVAndWithoutV() {
        assertTrue(checker.isNewer("v11.0.16", "11.0.15"))
        assertTrue(checker.isNewer("11.0.16", "v11.0.15"))
    }

    // ── 镜像响应拆信封（Gitee contents API vs GitHub raw）───────────────

    @Test
    fun unwrapContentsEnvelope_giteeBase64_isDecoded() {
        // 注意 versionName 必须带上：只有 versionCode 没有 versionName 的 app 块被判为空
        val base64 = java.util.Base64.getEncoder()
            .encodeToString("""{"app":{"versionCode":1,"versionName":"0.1.1"}}""".toByteArray())
        val envelope = """{"type":"file","encoding":"base64","content":"$base64"}"""

        val decoded = checker.unwrapContentsEnvelope(envelope)

        assertTrue(decoded.startsWith("{"))
        assertTrue(decoded.contains("\"versionCode\":1"))
        // 拆完能被 feed 解析器认出来，才算这条链真的通了
        assertTrue(AppReleaseFeedParser.parse(decoded) != null)
    }

    @Test
    fun unwrapContentsEnvelope_plainJson_isReturnedAsIs() {
        val plain = """{"app":{"versionCode":41,"versionName":"0.1.40"}}"""
        assertEquals(plain, checker.unwrapContentsEnvelope(plain))
    }

    @Test
    fun unwrapContentsEnvelope_nonJsonOrBroken_isReturnedAsIs() {
        assertEquals("<html>404</html>", checker.unwrapContentsEnvelope("<html>404</html>"))
        assertEquals("", checker.unwrapContentsEnvelope(""))
        // 看着像 JSON 但没有 content 字段 → 原样返回，让上层解析器去判
        val notEnvelope = """{"message":"Not Found"}"""
        assertEquals(notEnvelope, checker.unwrapContentsEnvelope(notEnvelope))
        // 坏 base64 → 原样返回而不是抛异常
        val bad = """{"encoding":"base64","content":"!!!not-base64!!!"}"""
        assertEquals(bad, checker.unwrapContentsEnvelope(bad))
    }

    @Test
    fun unwrapContentsEnvelope_base64WithNewlines_isDecoded() {
        val raw = """{"app":{"versionCode":2}}"""
        val base64 = java.util.Base64.getMimeEncoder(20, "\n".toByteArray())
            .encodeToString(raw.toByteArray())
        val envelope = """{"encoding":"base64","content":"$base64"}"""

        // Gitee 的 base64 会插换行，必须能容忍
        assertTrue(base64.contains("\n"))
        assertTrue(checker.unwrapContentsEnvelope(envelope).contains("\"versionCode\":2"))
    }

    // ── 并行竞速（raceFirstSuccess）──────────────────────────────────

    @Test
    fun raceFirstSuccess_最快那个赢() = runBlocking {
        // 第一个 400ms 才回，第二个 20ms 就回：总耗时必须是 20ms 量级，
        // 不是 420ms（串行）也不是等第一个超时。
        val started = System.currentTimeMillis()
        val winner = checker.raceFirstSuccess(listOf("slow", "fast"), budgetMs = 3_000) { url ->
            if (url == "slow") { delay(400); "slow" } else { delay(20); "fast" }
        }
        val cost = System.currentTimeMillis() - started
        assertEquals("fast", winner)
        assertTrue("耗时 $cost ms 应小于 200ms", cost < 200)
    }

    @Test
    fun raceFirstSuccess_全都失败返回null() = runBlocking {
        val r = checker.raceFirstSuccess(listOf("a", "b"), budgetMs = 1_000) { null }
        assertEquals(null, r)
    }

    @Test
    fun raceFirstSuccess_超预算就收手不抛异常() = runBlocking {
        // 镜像都卡到预算之外：必须自己回来（返回 null），不能把用户永远挂在「正在检查」
        val started = System.currentTimeMillis()
        val r = checker.raceFirstSuccess(listOf("a"), budgetMs = 150) { delay(3_000); "late" }
        val cost = System.currentTimeMillis() - started
        assertEquals(null, r)
        assertTrue("耗时 $cost ms 应接近预算而不是 3s", cost < 1_200)
    }

    @Test
    fun raceFirstSuccess_抛异常的镜像不拖垮整场() = runBlocking {
        val r = checker.raceFirstSuccess(listOf("boom", "ok"), budgetMs = 2_000) { url ->
            if (url == "boom") error("IO 挂了") else { delay(30); "ok" }
        }
        assertEquals("ok", r)
    }

    // ── 预算与镜像记忆（2026-10-21）─────────────────────────────────

    @Test
    fun stableKey_去掉破缓存查询串() {
        assertEquals(
            "https://gitee.com/liulin62633/pocket-carp/raw/main/announce/feed.json",
            checker.stableKey(
                "https://gitee.com/liulin62633/pocket-carp/raw/main/announce/feed.json?t=1761000000000"
            ),
        )
        assertEquals("https://a/b", checker.stableKey("https://a/b"))
    }

    @Test
    fun preferLastWinner_把上次成功的镜像提到队首() {
        // 上次赢的是第 3 条（带 ?t= 也一样能认出来）：下次第一个拨它，省一次 DNS/TLS
        checker.lastWinnerUrl = "https://x/contents"
        val reordered = checker.preferLastWinner(listOf("https://x/raw", "https://x/contents?t=99", "https://x/master"))
        assertEquals(listOf("https://x/contents?t=99", "https://x/raw", "https://x/master"), reordered)

        // 「上次赢的地址根本不在这一轮清单里」——必须原样返回，不能凭空加一条
        checker.lastWinnerUrl = "https://x/gone"
        assertEquals(listOf("https://x/only"), checker.preferLastWinner(listOf("https://x/only")))
    }

    @Test
    fun preferLastWinner_队首那条不变时原样返回() {
        checker.lastWinnerUrl = "https://x/raw"
        val urls = listOf("https://x/raw", "https://x/contents")
        assertEquals(urls, checker.preferLastWinner(urls))
    }

    /**
     * 守门人：一次检查的总预算不许再涨回 8 秒。
     *
     * 2026-10-21 之前是 8 秒，且「竞速全灭 → 发行版正文兜底」那一轮在预算之外，
     * 最坏能拖到 16 秒；用户看到的就是「卡在正在检查…」。
     */
    @Test
    fun fetchBudget_不超过4秒半() {
        val src = File("src/main/java/com/pocket/xiaoli/network/AppUpdateChecker.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/pocket/xiaoli/network/AppUpdateChecker.kt")
        assertTrue("找不到 AppUpdateChecker.kt", src.exists())
        // 仓库里是 CRLF，断言前先归一，否则跨行的正则永远不命中
        val text = src.readText().replace("\r\n", "\n")
        assertTrue(
            "FETCH_BUDGET_MS 仍是大于 4500 的值",
            Regex("FETCH_BUDGET_MS\\s*=\\s*([0-9_]+)L").find(text)
                ?.groupValues?.get(1)?.replace("_", "")?.toLong()?.let { it <= 4_500L } == true,
        )
        // 兜底那一轮必须带上 deadline，否则又是「两轮预算」
        assertTrue(
            "feedFromReleaseComment 没接 deadline",
            text.contains("feedFromReleaseComment(errors, startedAt + FETCH_BUDGET_MS)") &&
                text.contains("feedFromReleaseComment(errors, deadline)") &&
                Regex("private fun feedFromReleaseComment\\(\\s*\\n?\\s*errors: MutableList<String>,\\s*deadline: Long")
                    .containsMatchIn(text),
        )
    }

    // ── 竞速收集：所有镜像都回来了就别再陪跑 grace ─────────────────────

    /**
     * 守门人：镜像全部返回后必须**立刻**收手。
     *
     * 以前无脑等满 FRESH_GRACE_MS（900ms），哪怕四个镜像早已全部回来、
     * 不可能再有更高的版本 —— 这就是「检查更新比岱宗慢半拍」的来源。
     */
    @Test
    fun pickNewest_通道关闭立即收手且取最高版本() = runBlocking {
        val channel = Channel<Pair<String, Int>>(Channel.UNLIMITED)
        channel.send("raw" to 73)
        channel.send("contents" to 74)
        channel.close()

        val started = System.currentTimeMillis()
        val top = checker.pickNewest(channel, budgetMs = 2_000, graceMs = 900) { it.second }
        val cost = System.currentTimeMillis() - started

        assertEquals("contents" to 74, top)
        assertTrue("所有镜像都回来了还在等 grace：耗时 $cost ms", cost < 400)
    }

    @Test
    fun pickNewest_还有镜像在跑就等满grace() = runBlocking {
        val channel = Channel<Int>(Channel.UNLIMITED)
        channel.send(73)

        val started = System.currentTimeMillis()
        val top = checker.pickNewest(channel, budgetMs = 2_000, graceMs = 300) { it }
        val cost = System.currentTimeMillis() - started
        channel.close()

        assertEquals(73, top)
        assertTrue("应等满 grace 看有没有更高版本，实际 $cost ms", cost >= 200)
    }

    @Test
    fun pickNewest_空通道立刻返回null() = runBlocking {
        val channel = Channel<Int>(Channel.UNLIMITED)
        channel.close()

        val started = System.currentTimeMillis()
        val top = checker.pickNewest(channel, budgetMs = 2_000, graceMs = 900) { it }
        val cost = System.currentTimeMillis() - started

        assertNull(top)
        assertTrue("空通道应立即返回，实际 $cost ms", cost < 200)
    }
}
