package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * feed.json 解析与判定逻辑的单测。
 *
 * 这些用例守的是**运维手写 feed 时最容易犯的错**：字段拼错、时间写成中文、
 * 只发公告不发版、灰度填 0、强更边界差一 —— 每一条都对应一个真实会出的事故。
 */
class AppReleaseFeedTest {

    private val fullFeed = """
        {
          "app": {
            "versionCode": 41,
            "versionName": "0.1.40",
            "minSupported": 30,
            "apkUrl": "https://example.com/x.apk",
            "sha256": "ABCDEF",
            "rollout": 50,
            "notes": ["修了课表重叠", "新增校历"]
          },
          "announcements": [
            { "id": "a1", "level": "INFO", "title": "国庆值班安排", "body": "教务处 10/1-10/7 值班" },
            { "id": "a2", "level": "CRITICAL", "title": "账号安全提醒", "body": "请勿外借账号" }
          ]
        }
    """.trimIndent()

    @Test
    fun `完整 feed 的每个字段都解析对`() {
        val feed = AppReleaseFeedParser.parse(fullFeed)!!

        assertEquals(41, feed.versionCode)
        assertEquals("0.1.40", feed.versionName)
        assertEquals(30, feed.minSupported)
        assertEquals("https://example.com/x.apk", feed.apkUrl)
        assertEquals("abcdef", feed.sha256) // 大写 sha256 统一转小写，避免比较时坑
        assertEquals(50, feed.rollout)
        assertEquals(listOf("修了课表重叠", "新增校历"), feed.notes)
        assertEquals(2, feed.announcements.size)
        assertTrue(feed.hasApp)
    }

    @Test
    fun `缺省 rollout 视为全量`() {
        val feed = AppReleaseFeedParser.parse("""{"app":{"versionCode":41,"versionName":"0.1.40"}}""")!!
        assertEquals(100, feed.rollout)
        assertEquals(0, feed.minSupported) // 0 = 不强制
    }

    @Test
    fun `只推公告不带版本也能解析`() {
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[{"id":"m1","title":"今晚 22 点维护","body":"约 10 分钟"}]}"""
        )!!

        assertFalse(feed.hasApp)
        assertEquals(1, feed.announcements.size)
        assertEquals(AnnouncementLevel.INFO, feed.announcements[0].level) // 没写等级 → INFO
    }

    @Test
    fun `公告缺 id 或缺标题就丢掉而不是整份作废`() {
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[
                 {"id":"ok","title":"保留"},
                 {"id":"no-title"},
                 {"title":"no-id"},
                 {"id":"  ","title":"  "}
               ]}"""
        )!!

        assertEquals(listOf("保留"), feed.announcements.map { it.title })
    }

    @Test
    fun `整份不是 JSON 对象时返回 null`() {
        assertNull(AppReleaseFeedParser.parse(null))
        assertNull(AppReleaseFeedParser.parse(""))
        assertNull(AppReleaseFeedParser.parse("   "))
        assertNull(AppReleaseFeedParser.parse("<!DOCTYPE html><html>404</html>"))
        assertNull(AppReleaseFeedParser.parse("[1,2,3]"))
        assertNull(AppReleaseFeedParser.parse("{}")) // 空对象 = 什么都没有
    }

    @Test
    fun `BOM 与前后空白不影响解析`() {
        val feed = AppReleaseFeedParser.parse("\uFEFF\n  {\"announcements\":[{\"id\":\"x\",\"title\":\"x\"}]}  \n")
        assertEquals(1, feed!!.announcements.size)
    }

    @Test
    fun `版本号字段写成字符串也认`() {
        val feed = AppReleaseFeedParser.parse("""{"app":{"versionCode":"41","versionName":"0.1.40"}}""")!!
        assertEquals(41, feed.versionCode)
    }

    @Test
    fun `时间窗认 ISO 字符串也认 epoch 毫秒`() {
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[
                 {"id":"iso","title":"ISO","startsAt":"2026-10-20T00:00:00Z"},
                 {"id":"num","title":"数字","startsAt":1700000000000},
                 {"id":"bad","title":"写错的","startsAt":"2026年10月20日"}
               ]}"""
        )!!

        val iso = feed.announcements.first { it.id == "iso" }
        val num = feed.announcements.first { it.id == "num" }
        val bad = feed.announcements.first { it.id == "bad" }
        assertEquals(1792454400000L, iso.startsAt)
        assertEquals(1700000000000L, num.startsAt)
        assertNull(bad.startsAt) // 解析不出 = 不限，绝不因此丢掉公告
    }

    @Test
    fun `CRITICAL 默认必须确认，INFO 默认不用`() {
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[
                 {"id":"c","title":"严重","level":"CRITICAL"},
                 {"id":"i","title":"普通","level":"INFO"},
                 {"id":"o","title":"显式关掉","level":"CRITICAL","mustAck":false}
               ]}"""
        )!!

        assertTrue(feed.announcements.first { it.id == "c" }.mustAck)
        assertFalse(feed.announcements.first { it.id == "i" }.mustAck)
        assertFalse(feed.announcements.first { it.id == "o" }.mustAck) // 显式 false 要能覆盖默认
    }

    // ── 判定 ────────────────────────────────────────────────────────────

    @Test
    fun `versionCode 整数比较比版本字符串干净`() {
        val feed = AppReleaseFeed(versionCode = 41, versionName = "0.1.40")
        assertTrue(ReleaseGate.isNewer(feed, currentVersionCode = 40))
        assertFalse(ReleaseGate.isNewer(feed, currentVersionCode = 41))
        assertFalse(ReleaseGate.isNewer(feed, currentVersionCode = 42))
        // 只推公告的 feed 不能让人以为"有新版"
        assertFalse(ReleaseGate.isNewer(AppReleaseFeed(announcements = emptyList()), 40))
    }

    @Test
    fun `强更边界是严格小于 minSupported`() {
        val feed = AppReleaseFeed(versionCode = 50, versionName = "0.2.0", minSupported = 40)
        assertTrue(ReleaseGate.mustUpdate(feed, 39))
        assertFalse(ReleaseGate.mustUpdate(feed, 40)) // 等于仍受支持
        assertFalse(ReleaseGate.mustUpdate(feed, 45))
    }

    @Test
    fun `minSupported 为 0 时永不强更`() {
        val feed = AppReleaseFeed(versionCode = 50, versionName = "0.2.0", minSupported = 0)
        assertFalse(ReleaseGate.mustUpdate(feed, 1))
    }

    @Test
    fun `灰度 0 谁都不命中 100 谁都命中`() {
        assertFalse(ReleaseGate.isRolloutHit("dev-1", 41, 0))
        assertTrue(ReleaseGate.isRolloutHit("dev-1", 41, 100))
        assertTrue(ReleaseGate.isRolloutHit("dev-1", 41, 1000)) // 越界按 100 处理
        assertFalse(ReleaseGate.isRolloutHit("dev-1", 41, -5))
    }

    @Test
    fun `同一个设备同一版本永远落在同一个桶`() {
        val first = ReleaseGate.isRolloutHit("stable-uuid", 41, 30)
        repeat(50) {
            assertEquals(first, ReleaseGate.isRolloutHit("stable-uuid", 41, 30))
        }
        // 分桶值本身在 0..99
        repeat(20) { i ->
            val b = ReleaseGate.bucket("dev-$i")
            assertTrue("bucket out of range: $b", b in 0..99)
        }
    }

    @Test
    fun `升版本会重新分桶 不同设备分布不同`() {
        val hits41 = (1..200).count { ReleaseGate.isRolloutHit("uuid-$it", 41, 30) }
        // 200 个设备 × 30% ≈ 60，给足容差只验证"不是全中也不是全不中"
        assertTrue("hits41=$hits41", hits41 in 20..120)

        val different = (1..200).filter { v ->
            ReleaseGate.isRolloutHit("uuid-$v", 41, 30) != ReleaseGate.isRolloutHit("uuid-$v", 42, 30)
        }
        assertTrue("换版本后应有不少设备分桶变化", different.size > 10)
    }

    @Test
    fun `放量只能向前推 小比例命中的设备在大比例里也命中`() {
        // 10% → 50% → 100% 这三档必须**包含**：否则推到 50% 时，
        // 已经在 10% 档里的用户会退回"看不到新版"，等于把人刚收到的提醒又抢走。
        // 判定是 bucket < percent，天然单调；这里用 1000 个设备把它钉死成契约。
        repeat(1000) { i ->
            val id = "device-$i"
            val hit10 = ReleaseGate.isRolloutHit(id, 41, 10)
            val hit50 = ReleaseGate.isRolloutHit(id, 41, 50)
            val hit90 = ReleaseGate.isRolloutHit(id, 41, 90)
            if (hit10) assertTrue("10% 命中的 $id 必须也在 50% 命中", hit50)
            if (hit50) assertTrue("50% 命中的 $id 必须也在 90% 命中", hit90)
        }
    }

    @Test
    fun `公告按等级降序 id 降序排列`() {
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[
                 {"id":"b","title":"B","level":"INFO"},
                 {"id":"a","title":"A","level":"CRITICAL"},
                 {"id":"c","title":"C","level":"WARN"}
               ]}"""
        )!!

        val order = ReleaseGate.visible(feed, nowMillis = 0, currentVersionCode = 40).map { it.id }
        assertEquals(listOf("a", "c", "b"), order)
    }

    @Test
    fun `时间窗外与版本窗外的公告不显示`() {
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[
                 {"id":"future","title":"未来","startsAt":"2027-01-01T00:00:00Z"},
                 {"id":"expired","title":"过期","endsAt":"2020-01-01T00:00:00Z"},
                 {"id":"old-client","title":"只给新版","minVersionCode":99},
                 {"id":"too-new","title":"只给旧版","maxVersionCode":10},
                 {"id":"now","title":"当下"}
               ]}"""
        )!!

        val visible = ReleaseGate.visible(feed, nowMillis = 1792454400000L, currentVersionCode = 40)
        assertEquals(listOf("now"), visible.map { it.id })
    }

    @Test
    fun `低于 forceBelow 的公告被提成强更并要求确认`() {
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[
                 {"id":"min-version","title":"请升级","level":"WARN","forceBelow":45}
               ]}"""
        )!!

        val onOld = ReleaseGate.visible(feed, nowMillis = 0, currentVersionCode = 40).single()
        assertEquals(AnnouncementLevel.FORCE_UPDATE, onOld.level)
        assertTrue(onOld.mustAck)

        val onNew = ReleaseGate.visible(feed, nowMillis = 0, currentVersionCode = 45).single()
        assertEquals(AnnouncementLevel.WARN, onNew.level)
        assertFalse(onNew.mustAck)
    }

    @Test
    fun `blocking 只挑必须确认的那条`() {
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[
                 {"id":"i","title":"普通","level":"INFO"},
                 {"id":"c","title":"严重","level":"CRITICAL"}
               ]}"""
        )!!

        assertEquals("c", ReleaseGate.blocking(feed, nowMillis = 0, currentVersionCode = 40)?.id)
        assertNull(ReleaseGate.blocking(AppReleaseFeed(), nowMillis = 0, currentVersionCode = 40))
    }

    // ── Release 正文兜底（第三通道） ─────────────────────────────────────

    @Test
    fun `能从 Release 正文里抽出 feed 块`() {
        val body = """
            口袋小鲤 v0.1.40（versionCode 41）

            <!-- feed
            {"versionCode":41,"versionName":"0.1.40","minSupported":30}
            -->

            ## 本次改动
            - 修了课表重叠
        """.trimIndent()

        val feed = AppReleaseFeedParser.parseFeedComment(body)!!
        assertEquals(41, feed.versionCode)
        assertEquals(30, feed.minSupported)
    }

    @Test
    fun `正文里没有 feed 块或块写坏时返回 null 退回老路`() {
        assertNull(AppReleaseFeedParser.parseFeedComment(null))
        assertNull(AppReleaseFeedParser.parseFeedComment("## 本次改动\n- 啥也没有"))
        assertNull(AppReleaseFeedParser.parseFeedComment("<!-- feed { 不是json } -->"))
    }

    @Test
    fun `嵌套对象不会被第一个右花括号截断`() {
        // 线上 feed 的真实形状：app 里还有一层对象，announcements 是数组。
        // 旧的 `(\{.*?})` 非贪婪写法会在 `"app":{...}` 的 `}` 处截断，这里钉住。
        val body = """
            ## v0.1.54
            <!-- feed {"app":{"versionCode":54,"versionName":"0.1.54","sha256":"ABC"},"announcements":[{"id":"a1","title":"T"}]} -->
            正文继续
        """.trimIndent()
        val feed = AppReleaseFeedParser.parseFeedComment(body)!!
        assertEquals(54, feed.versionCode)
        assertEquals("0.1.54", feed.versionName)
        assertEquals("abc", feed.sha256)
        assertEquals(1, feed.announcements.size)
    }

    @Test
    fun `跳过不像 feed 的注释继续往后找`() {
        val body = """
            <!-- 这是一段普通说明 -->
            <!-- feed {"versionCode":42,"versionName":"0.1.42"} -->
        """.trimIndent()
        assertEquals(42, AppReleaseFeedParser.parseFeedComment(body)!!.versionCode)
    }

    @Test
    fun `注释没闭合时返回 null 而不是抛异常`() {
        assertNull(AppReleaseFeedParser.parseFeedComment("<!-- feed {\"versionCode\":42} "))
    }

    /**
     * 守门人：**解析器里不许再出现正则**。
     *
     * 上一版用 `"(?s)<!--\s*feed\s*(\{.*?})\s*-->"`，`{` 转义了、`}` 忘了转义。
     * 桌面 JVM 认这个写法，单测全绿；Android 的 ICU 正则不认，直接
     * `PatternSyntaxException` → `<clinit>` 失败 → 每次引用都 `NoClassDefFoundError`。
     * 所以这里用源码扫描钉住：这个文件里一旦出现 `Regex(`，测试就红。
     */
    @Test
    fun `AppReleaseFeed 里不再出现正则`() {
        val source = java.io.File("src/main/java/com/pocket/xiaoli/network/AppReleaseFeed.kt")
        val file = if (source.exists()) source else java.io.File("app/$source")
        assertTrue("找不到源文件：${file.absolutePath}", file.exists())
        // 只看代码行：注释里为了讲清历史会引用旧写法，那不算违规
        val code = file.readLines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .joinToString("\n")
        assertFalse(
            "AppReleaseFeed.kt 里又出现了 Regex( —— Android 的 ICU 正则和桌面 JVM 不一样，" +
                "裸 `}` 会 PatternSyntaxException 并把整个类变成 NoClassDefFoundError",
            code.contains("Regex(")
        )
    }

    // ── 内测通道（顶层 beta 块）────────────────────────────────────────

    @Test
    fun `beta 块解析成第二条通道 结构与 app 相同`() {
        val feed = AppReleaseFeedParser.parse(
            """{"app":{"versionCode":41,"versionName":"0.1.40"},
               "beta":{"versionCode":42,"versionName":"0.1.41-beta","apkUrl":"https://x/beta.apk",
                       "sha256":"ABC","rollout":20,"minSupported":42,"notes":["内测新东西"]}}"""
        )!!

        assertTrue(feed.hasBeta)
        val beta = feed.beta!!
        assertEquals(42, beta.versionCode)
        assertEquals("0.1.41-beta", beta.versionName)
        assertEquals("https://x/beta.apk", beta.apkUrl)
        assertEquals("abc", beta.sha256) // 与稳定块同一口径：统一小写
        assertEquals(20, beta.rollout)
        assertEquals(42, beta.minSupported)
        assertEquals(listOf("内测新东西"), beta.notes)
        assertEquals(41, feed.versionCode) // 稳定块不受影响
    }

    @Test
    fun `beta 块不读自己的 announcements`() {
        // 公告只有根上那一份：闭馆通知不该因为你在内测就看不见
        val feed = AppReleaseFeedParser.parse(
            """{"announcements":[{"id":"a","title":"闭馆"}],
               "beta":{"versionCode":42,"versionName":"0.1.41-beta","announcements":[{"id":"b","title":"内测专属"}]}}"""
        )!!

        assertEquals(listOf("a"), feed.announcements.map { it.id })
        assertTrue(feed.beta!!.announcements.isEmpty())
    }

    @Test
    fun `只有 beta 没有 app 的 feed 不算空`() {
        // 内测先出包、稳定版还没跟上的那半天：这份 feed 有东西可给，不能被整份丢掉
        val feed = AppReleaseFeedParser.parse(
            """{"beta":{"versionCode":42,"versionName":"0.1.41-beta"}}"""
        )!!

        assertFalse(feed.hasApp)
        assertTrue(feed.hasBeta)
    }

    @Test
    fun `半截 beta 块等于没有内测包`() {
        val feed = AppReleaseFeedParser.parse(
            """{"app":{"versionCode":41,"versionName":"0.1.40"},"beta":{"versionName":"0.1.41-beta"}}"""
        )!!

        assertFalse(feed.hasBeta) // 没写 versionCode 的 beta 块不能拿来做任何判定
        assertEquals(41, feed.versionCode)
    }

    @Test
    fun `没有 beta 块的老 feed 解析结果里 beta 为空`() {
        val feed = AppReleaseFeedParser.parse("""{"app":{"versionCode":41,"versionName":"0.1.40"}}""")!!

        assertNull(feed.beta)
        assertFalse(feed.hasBeta)
    }
}
