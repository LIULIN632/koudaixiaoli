package com.pocket.xiaoli.data.announcement

import com.pocket.xiaoli.network.Announcement
import com.pocket.xiaoli.network.AnnouncementLevel
import com.pocket.xiaoli.network.AppReleaseFeed
import com.pocket.xiaoli.network.AppReleaseFeedParser
import com.pocket.xiaoli.network.ReleaseGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AppFeedView.build] 的单测 —— 红点、已读、强更、「稍后提醒」这些判定
 * 一旦算错，用户看到的就是「有红点点开是空的」或「该逼他升级时没逼」。
 *
 * 不碰 Context，全是纯函数。
 */
class AppFeedViewTest {

    private fun build(
        feed: AppReleaseFeed?,
        currentVersionCode: Int = 40,
        nowMillis: Long = 1_000_000L,
        seen: Set<String> = emptySet(),
        snoozedVersionCode: Int = 0,
        lastCheckedAt: Long = 999L,
        loaded: Boolean = true,
        error: String? = null,
        rolloutId: String? = null,
        betaEnabled: Boolean = false,
        updatesEnabled: Boolean = true,
        pushEnabled: Boolean = true,
    ) = AppFeedView.build(
        feed = feed,
        currentVersionCode = currentVersionCode,
        nowMillis = nowMillis,
        seen = seen,
        snoozedVersionCode = snoozedVersionCode,
        lastCheckedAt = lastCheckedAt,
        loaded = loaded,
        error = error,
        rolloutId = rolloutId,
        betaEnabled = betaEnabled,
        updatesEnabled = updatesEnabled,
        pushEnabled = pushEnabled,
    )

    private fun announcement(id: String, level: AnnouncementLevel = AnnouncementLevel.INFO) =
        Announcement(id = id, level = level, title = "标题-$id", body = "")

    @Test
    fun `没拿到 feed 时保留错误与上次检查时间`() {
        val state = build(feed = null, loaded = false, error = "检查失败，请稍后再试")

        assertTrue(state.announcements.isEmpty())
        assertFalse(state.updateAvailable)
        assertFalse(state.showRedDot)
        assertEquals("检查失败，请稍后再试", state.error)
        assertEquals(999L, state.lastCheckedAt)
        assertFalse(state.loaded)
    }

    @Test
    fun `没看过的公告计入未读并亮红点`() {
        val feed = AppReleaseFeed(
            announcements = listOf(announcement("a"), announcement("b")),
        )

        val state = build(feed, seen = setOf("a"))

        assertEquals(2, state.announcements.size)
        assertEquals(1, state.unread)
        assertTrue(state.showRedDot)
    }

    @Test
    fun `全部看过之后红点消失但公告仍在列表里`() {
        val feed = AppReleaseFeed(announcements = listOf(announcement("a"), announcement("b")))

        val state = build(feed, seen = setOf("a", "b"))

        assertEquals(2, state.announcements.size) // 看过 ≠ 删除
        assertEquals(0, state.unread)
        assertFalse(state.showRedDot)
    }

    @Test
    fun `有新版时带上版本信息并且亮红点`() {
        val feed = AppReleaseFeed(
            versionCode = 41,
            versionName = "0.1.40",
            apkUrl = "https://example.com/x.apk",
            sha256 = "abc",
        )

        val state = build(feed, currentVersionCode = 40)

        assertTrue(state.updateAvailable)
        assertTrue(state.showRedDot)
        assertEquals(41, state.latestVersionCode)
        assertEquals("0.1.40", state.latestVersionName)
        assertEquals("https://example.com/x.apk", state.apkUrl)
        assertEquals("abc", state.sha256)
    }

    @Test
    fun `版本已经是最新时不提示更新`() {
        val feed = AppReleaseFeed(versionCode = 41, versionName = "0.1.40")

        val state = build(feed, currentVersionCode = 41)

        assertFalse(state.updateAvailable)
        assertFalse(state.showRedDot)
    }

    @Test
    fun `低于 minSupported 时 forceUpdate 且 blocking 让位给强更`() {
        val feed = AppReleaseFeed(
            versionCode = 50,
            versionName = "0.2.0",
            minSupported = 45,
            announcements = listOf(announcement("c", AnnouncementLevel.CRITICAL)),
        )

        val state = build(feed, currentVersionCode = 40)

        assertTrue(state.forceUpdate)
        assertTrue(state.updateAvailable) // 强更当然算「有更新」
        // 强更对话框已经挡住一切，不必再叠一条公告
        assertNull(state.blocking)
    }

    @Test
    fun `非强更时 CRITICAL 公告成为 blocking`() {
        val feed = AppReleaseFeed(
            versionCode = 41,
            versionName = "0.1.40",
            announcements = listOf(announcement("c", AnnouncementLevel.CRITICAL)),
        )

        val state = build(feed, currentVersionCode = 40)

        assertFalse(state.forceUpdate)
        assertEquals("c", state.blocking?.id)
    }

    @Test
    fun `稍后提醒压掉红点但压不掉强更`() {
        val feed = AppReleaseFeed(versionCode = 41, versionName = "0.1.40")

        val snoozed = build(feed, currentVersionCode = 40, snoozedVersionCode = 41)
        assertFalse(snoozed.updateAvailable)
        assertFalse(snoozed.showRedDot)
        assertEquals("0.1.40", snoozed.latestVersionName) // 页面上仍然看得见

        val forced = AppReleaseFeed(versionCode = 50, versionName = "0.2.0", minSupported = 45)
        val notSnoozable = build(forced, currentVersionCode = 40, snoozedVersionCode = 50)
        assertTrue(notSnoozable.updateAvailable)
        assertTrue(notSnoozable.forceUpdate)
    }

    @Test
    fun `稍后提醒只对那一个版本有效 出了新版要重新催`() {
        val nextVersion = AppReleaseFeed(versionCode = 42, versionName = "0.1.41")

        val state = build(nextVersion, currentVersionCode = 40, snoozedVersionCode = 41)

        assertTrue(state.updateAvailable)
    }

    @Test
    fun `时间窗外的公告不算未读也不亮红点`() {
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[{"id":"old","title":"过期了","endsAt":500}]}"""
        )!!

        val state = build(feed, nowMillis = 1_000L)

        assertTrue(state.announcements.isEmpty())
        assertEquals(0, state.unread)
        assertFalse(state.showRedDot)
    }

    @Test
    fun `错误与缓存并存时仍然渲染上次内容`() {
        val feed = AppReleaseFeed(versionCode = 41, versionName = "0.1.40")

        val state = build(feed, currentVersionCode = 40, error = "检查失败，显示的是上次的内容")

        assertTrue(state.updateAvailable) // 断网不该让人以为「没有新版」
        assertEquals("检查失败，显示的是上次的内容", state.error)
    }

    @Test
    fun `点过知道了之后不再 blocking`() {
        val feed = AppReleaseFeed(
            versionCode = 41,
            versionName = "0.1.40",
            announcements = listOf(announcement("c", AnnouncementLevel.CRITICAL)),
        )

        // seen 同时是「已读」和「已确认」的账本 —— 点掉就不该再拦人
        val state = build(feed, currentVersionCode = 40, seen = setOf("c"))

        assertNull(state.blocking)
        assertEquals(1, state.announcements.size) // 但公告仍在抽屉里
    }

    @Test
    fun `INFO 与 WARN 公告永远不 blocking`() {
        val feed = AppReleaseFeed(
            announcements = listOf(
                announcement("i", AnnouncementLevel.INFO),
                announcement("w", AnnouncementLevel.WARN),
                announcement("u", AnnouncementLevel.UPDATE),
            ),
        )

        assertNull(build(feed).blocking)
    }

    // ── 灰度 ────────────────────────────────────────────────────────────

    @Test
    fun `灰度没命中的设备看不到这个版本 红点也不亮`() {
        val feed = AppReleaseFeed(
            versionCode = 41,
            versionName = "0.1.40",
            apkUrl = "https://example.com/x.apk",
            rollout = 0,
        )

        val state = build(feed, currentVersionCode = 40, rolloutId = "dev-1")

        assertFalse(state.updateAvailable)
        assertFalse(state.showRedDot)
        assertEquals(0, state.latestVersionCode) // 更新卡拿不到版本号，自然渲染不出来
        assertEquals("", state.latestVersionName)
    }

    @Test
    fun `灰度只管版本 公告照样给所有人看`() {
        val feed = AppReleaseFeed(
            versionCode = 41,
            versionName = "0.1.40",
            rollout = 0,
            announcements = listOf(announcement("a")),
        )

        val state = build(feed, currentVersionCode = 40, rolloutId = "dev-1")

        assertEquals(1, state.announcements.size)
        assertEquals(1, state.unread) // 公告没看过就亮红点，跟灰度无关
        assertFalse(state.updateAvailable)
    }

    @Test
    fun `灰度全量时照常提示更新`() {
        val feed = AppReleaseFeed(
            versionCode = 41,
            versionName = "0.1.40",
            rollout = 100,
        )

        assertTrue(build(feed, currentVersionCode = 40, rolloutId = "dev-1").updateAvailable)
    }

    @Test
    fun `不传 rolloutId 时不判灰度`() {
        // 老调用点（没有设备标识）必须保持"feed 说有新版就有新版"，
        // 否则漏传一个参数会静默地把更新提示全关掉。
        val feed = AppReleaseFeed(versionCode = 41, versionName = "0.1.40", rollout = 0)

        assertTrue(build(feed, currentVersionCode = 40).updateAvailable)
    }

    @Test
    fun `强更不吃灰度 没进桶也要逼人升级`() {
        val feed = AppReleaseFeed(
            versionCode = 50,
            versionName = "0.2.0",
            minSupported = 45,
            rollout = 0, // 只放量 0%
        )

        val state = build(feed, currentVersionCode = 40, rolloutId = "dev-1")

        // minSupported 说的是"手上这版已经不能用"，压掉提示只会让这人停在坏版本上
        assertTrue(state.forceUpdate)
        assertTrue(state.updateAvailable)
        assertEquals(50, state.latestVersionCode)
    }

    @Test
    fun `半量灰度下命中与未命中的设备各按各的显示`() {
        val hit = (1..500).map { "uuid-$it" }.firstOrNull { ReleaseGate.isRolloutHit(it, 41, 30) }
        val miss = (1..500).map { "uuid-$it" }.firstOrNull { !ReleaseGate.isRolloutHit(it, 41, 30) }
        assertNotNull(hit)
        assertNotNull(miss)

        val feed = AppReleaseFeed(versionCode = 41, versionName = "0.1.40", rollout = 30)

        assertTrue(build(feed, currentVersionCode = 40, rolloutId = hit!!).updateAvailable)
        assertFalse(build(feed, currentVersionCode = 40, rolloutId = miss!!).updateAvailable)
    }

    // ── 内测通道 ────────────────────────────────────────────────────────

    private val betaFeed = AppReleaseFeed(
        versionCode = 41,
        versionName = "0.1.40",
        apkUrl = "https://example.com/stable.apk",
        beta = AppReleaseFeed(
            versionCode = 42,
            versionName = "0.1.41-beta",
            apkUrl = "https://example.com/beta.apk",
            sha256 = "beef",
            notes = listOf("内测才有的改动"),
        ),
    )

    @Test
    fun `开了内测就装内测包并且标出内测版`() {
        val state = build(betaFeed, currentVersionCode = 40, betaEnabled = true)

        assertTrue(state.updateAvailable)
        assertTrue(state.updateIsBeta)
        assertEquals(42, state.latestVersionCode)
        assertEquals("0.1.41-beta", state.latestVersionName)
        assertEquals("https://example.com/beta.apk", state.apkUrl) // 装错包比不装更糟
        assertEquals("beef", state.sha256)
        assertEquals(listOf("内测才有的改动"), state.notes)
        assertEquals(" · 内测版", state.updateBadge)
    }

    @Test
    fun `没开内测的人看不见内测包`() {
        val state = build(betaFeed, currentVersionCode = 40, betaEnabled = false)

        assertEquals(41, state.latestVersionCode)
        assertFalse(state.updateIsBeta)
        assertEquals("", state.updateBadge) // 默认后缀是空串：非内测的每一句文案都不该变
    }

    @Test
    fun `内测包不比本机新时退回稳定通道`() {
        // 内测用户已经装上 42 了 —— 这时该按稳定通道说话（别让他反复装同一个包）
        val state = build(betaFeed, currentVersionCode = 42, betaEnabled = true)

        assertFalse(state.updateAvailable)
        assertFalse(state.updateIsBeta)
        assertEquals(41, state.latestVersionCode)
    }

    @Test
    fun `没有 beta 块的老 feed 开了内测也一模一样`() {
        val plain = AppReleaseFeed(versionCode = 41, versionName = "0.1.40")

        // 内测块还没部署时，开关不该产生任何差异 —— 否则"用户拨了个开关结果什么都没变"
        // 会变成"用户拨了个开关结果什么都坏了"。
        assertEquals(build(plain, betaEnabled = false), build(plain, betaEnabled = true))
    }

    @Test
    fun `内测用户也受稳定版下限的强更约束`() {
        val feed = AppReleaseFeed(
            versionCode = 50,
            versionName = "0.2.0",
            minSupported = 45,
            beta = AppReleaseFeed(versionCode = 51, versionName = "0.2.1-beta"),
        )

        // 他现在装的 40 是稳定版：beta 块里没写下限，不代表他可以留在 40
        val state = build(feed, currentVersionCode = 40, betaEnabled = true)

        assertTrue(state.forceUpdate)
        assertTrue(state.updateIsBeta) // 强更的内容仍按他选的通道给（beta 包更新）
    }

    @Test
    fun `灰度压住稳定版时内测用户照样收得到内测包`() {
        // 这条就是「内测通道先全量、稳定通道才走灰度」：同一个包先给 1% 的人试，
        // 内测用户不受那个 1% 的门限制。
        val feed = betaFeed.copy(rollout = 0)

        val state = build(feed, currentVersionCode = 40, rolloutId = "dev-1", betaEnabled = true)

        assertTrue(state.updateAvailable)
        assertTrue(state.updateIsBeta)
        assertEquals(42, state.latestVersionCode)
    }

    @Test
    fun `内测块自己也能灰度 没命中就退回看稳定版`() {
        val feed = betaFeed.copy(beta = AppReleaseFeed(versionCode = 42, versionName = "0.1.41-beta", rollout = 0))

        val state = build(feed, currentVersionCode = 40, rolloutId = "dev-1", betaEnabled = true)

        // beta 被自己压掉之后不回退到 stable：两个通道各判各的，
        // 稳定通道那边 41 对他仍然可见（rollout 缺省 100），所以这里应当显示 stable。
        assertTrue(state.updateAvailable)
        assertEquals(41, state.latestVersionCode)
        assertFalse(state.updateIsBeta)
    }

    @Test
    fun `公告只有根上那一份 内测用户不漏公告`() {
        val feed = betaFeed.copy(announcements = listOf(announcement("a")))

        val on = build(feed, currentVersionCode = 40, betaEnabled = true)
        val off = build(feed, currentVersionCode = 40, betaEnabled = false)

        // beta 块里没有 announcements 这一段（解析器就不读），所以两个通道看到同一份公告
        assertEquals(off.announcements.map { it.id }, on.announcements.map { it.id })
        assertEquals(1, on.announcements.size)
    }

    @Test
    fun `F-Droid 构建不提示更新，但公告照常显示`() {
        // `-Pfdroid=true` 的包走这里：更新交给 F-Droid 自己，应用内不再挂下载入口。
        val feed = AppReleaseFeed(
            versionCode = 41,
            versionName = "0.1.41",
            apkUrl = "https://example.com/a.apk",
            announcements = listOf(announcement("a")),
        )

        val state = build(feed, updatesEnabled = false)

        assertFalse("F-Droid 版不该提示更新", state.updateAvailable)
        assertEquals("公告不受影响", 1, state.announcements.size)
        // 红点只由未读公告撑起来（没有更新可提示）
        assertTrue(state.showRedDot)
    }

    @Test
    fun `普通构建仍然提示更新`() {
        val feed = AppReleaseFeed(
            versionCode = 41,
            versionName = "0.1.41",
            apkUrl = "https://example.com/a.apk",
        )
        val state = build(feed, updatesEnabled = true)
        assertTrue(state.updateAvailable)
        assertEquals(41, state.latestVersionCode)
    }

    // ── 「检查更新」那一行的小字 ─────────────────────────────────────────
    //
    // Toast 两秒就没，这一句是页面上**唯一留下来**的结果，所以每一句都必须对得上
    // "这一轮到底查到了什么"。下面按 [AppFeedRepository.State.checkTrace] 的优先级逐条钉，
    // 顺序本身就是被测对象（把它改成"先说结果、后说进行中"，第一条就会红）。

    private val newerFeed = AppReleaseFeed(
        versionCode = 41,
        versionName = "0.1.41",
        apkUrl = "https://example.com/a.apk",
    )

    @Test
    fun `正在检查时不提前说任何结果`() {
        val state = build(newerFeed, currentVersionCode = 40).copy(checking = true)

        assertEquals("正在检查…", state.checkTrace(1_000_000L))
    }

    @Test
    fun `查到新版就说发现新版 内测包带上后缀`() {
        val stable = build(newerFeed, currentVersionCode = 40).checkTrace(1_000_000L)
        val beta = build(betaFeed, currentVersionCode = 40, betaEnabled = true).checkTrace(1_000_000L)

        assertEquals("发现新版 v0.1.41", stable)
        assertEquals("发现新版 v0.1.41-beta · 内测版", beta)
    }

    @Test
    fun `被稍后提醒压住的新版不能说成已是最新`() {
        // 用户上次点了「稍后提醒」：updateAvailable 被压成 false，但包确实更新
        val state = build(newerFeed, currentVersionCode = 40, snoozedVersionCode = 41)

        assertFalse(state.updateAvailable)
        assertTrue("所以判「有新版」必须认另一个字段", state.newerVersionExists)
        assertEquals("新版 v0.1.41（你选了稍后提醒）", state.checkTrace(1_000_000L))
    }

    @Test
    fun `这一轮没查上时不谎称已是最新`() {
        val state = build(feed = null, loaded = false, error = "检查失败：网络不通")

        assertEquals("这次没查上，可再点一次", state.checkTrace(1_000_000L))
    }

    @Test
    fun `镜像回退时手里那份新版优先`() {
        // error 里装的是"镜像还在返回旧数据"，但缓存那份新版是真读回来的
        val state = build(newerFeed, currentVersionCode = 40, error = "镜像还在返回旧数据（vc 40 < 41）")

        assertEquals("发现新版 v0.1.41", state.checkTrace(1_000_000L))
    }

    @Test
    fun `F-Droid 版把更新交给商店`() {
        val state = build(newerFeed, updatesEnabled = false)

        assertEquals("版本由应用商店更新", state.checkTrace(1_000_000L))
    }

    @Test
    fun `确实没有新版时报出什么时候查的`() {
        // 本机就是 40，feed 也只有 40：这才是唯一能说"已是最新"的场合
        val feed = AppReleaseFeed(versionCode = 40, versionName = "0.1.40")
        val state = build(feed, currentVersionCode = 40, lastCheckedAt = 1_000_000L - 5 * 60_000)

        assertEquals("已是最新 · 5 分钟前查过", state.checkTrace(1_000_000L))
    }

    @Test
    fun `feed 不带版本信息时不下已是最新的结论`() {
        // 只推公告的那份 feed（versionCode 没填）没有核对过任何版本，说"已是最新"就是凭空下结论
        val state = build(
            AppReleaseFeed(versionCode = 0, versionName = "", announcements = listOf(announcement("a"))),
            currentVersionCode = 40,
        )

        assertEquals("", state.checkTrace(1_000_000L))
    }

    @Test
    fun `问号里带的是这一轮的毛病原文`() {
        // 小字只放得下一句结论，"为什么失败"必须有个地方能查到 —— 否则只能导日志
        val failed = build(feed = null, loaded = false, error = "检查失败：Gitee raw 返回 504（read timed out）")

        assertTrue(failed.checkHint.contains("504"))
        assertTrue(failed.checkHint.startsWith("这一轮的毛病：检查失败"))
    }

    @Test
    fun `没毛病时问号还是那句行为说明`() {
        val ok = build(newerFeed, currentVersionCode = 40)

        assertEquals("查到新版会先问你，再下载。", ok.checkHint)
        // 镜像回退那种"半毛病"也要说出来：用户看到的版本号是缓存里的，不是这一轮读到的
        assertTrue(
            build(newerFeed, currentVersionCode = 40, error = "镜像还在返回旧数据").checkHint
                .startsWith("这一轮的毛病：镜像"),
        )
    }

    // ── 「主动提醒更新」开关（2026-10-29）──

    /**
     * 关掉主动提醒后，更新那颗红点不该再亮，但**数据本身要说实话**。
     *
     * `updateAvailable` / `newerVersionExists` 描述的是"feed 上有没有新包"这件事，
     * 开关管的是"要不要主动说"。把前者也压成 false，手动「检查更新」就会报
     * 「已是最新版本」—— 而版本号明明比本机新，那是同一类谎（抽屉里已经因为这处
     * 缺信息修过一次，见 `newerVersionExists` 的注释）。
     */
    @Test
    fun `关掉主动提醒只压红点不改数据`() {
        val off = build(newerFeed, currentVersionCode = 40, pushEnabled = false)

        assertFalse("更新红点不该亮", off.showRedDot)
        assertTrue("但数据上确实有新版", off.updateAvailable)
        assertTrue(off.newerVersionExists)
        // 开关只管"要不要主动说"，不改数据本身：手动检查仍然要能说出"有新版 v0.1.41"
        assertTrue(off.checkTrace().contains("0.1.41"))
    }

    /** 强更的红点**不受开关管**：那条是"这个版本已不受支持"，不是新功能推荐。 */
    @Test
    fun `强更的红点压不掉`() {
        val old = AppReleaseFeed(
            versionCode = 41,
            versionName = "0.1.41",
            apkUrl = "https://example.com/a.apk",
            minSupported = 41,
        )
        val state = build(old, currentVersionCode = 40, pushEnabled = false)

        assertTrue(state.forceUpdate)
        assertTrue("关了提醒也得亮强更", state.showRedDot)
    }
}
