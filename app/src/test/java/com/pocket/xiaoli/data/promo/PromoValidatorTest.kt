package com.pocket.xiaoli.data.promo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 推广配置解析与校验的单测。
 *
 * ## 为什么这个测试是本功能的**核心**
 *
 * 推广位是本 App 唯一一处"**文案与跳转目标都由服务端决定**"的地方。
 * 它同时承担三种风险，而三种都只在"配置被改坏/被改恶"时才暴露：
 *
 * 1. **跳转被劫持** —— 配置里塞一个钓鱼站，用户从"专升本"页点出去就被骗
 * 2. **一条坏数据毁整页** —— 缺字段或类型不符导致整份解析失败，推广区全空
 * 3. **越权展示** —— 给不存在的页面配内容、过期活动继续显示
 *
 * 这些都无法靠人眼看出来（配置正常时一切正常），所以必须钉住。
 */
class PromoValidatorTest {

    /** 固定"现在"：2026-09-26 12:00 UTC，避免测试随时间漂移。 */
    private val now = 1780000000L

    private fun json(vararg slots: String): String =
        """{"version": 3, "slots": [${slots.joinToString(",")}]}"""

    private fun slot(
        id: String = "promo_1",
        slot: String = "专升本",
        title: String = "专升本秋季班",
        body: String = "课程与试听安排见链接",
        enabled: String = "true",
        image: String? = null,
        action: String? = null,
        startAt: Long? = null,
        endAt: Long? = null,
        priority: Int = 0,
    ): String = buildString {
        append("""{"id":"$id","slot":"$slot","title":"$title","body":"$body","enabled":$enabled""")
        append(""","priority":$priority""")
        image?.let { append(""","image":"$it"""") }
        startAt?.let { append(""","startAt":$it""") }
        endAt?.let { append(""","endAt":$it""") }
        action?.let { append(""","action":$it""") }
        append("}")
    }

    // ── 正常路径 ──

    @Test
    fun `解析一条合法推广`() {
        val cfg = PromoValidator.parse(json(slot()), now)

        assertEquals(3, cfg.version)
        assertEquals(1, cfg.promos.size)
        val p = cfg.promos[0]
        assertEquals("promo_1", p.id)
        assertEquals(PromoSlot.ZHUANSANGBEN, p.slot)
        assertEquals("专升本秋季班", p.title)
    }

    @Test
    fun `缺 enabled 字段视为启用 而不是下架`() {
        // 漏写一个字段就让整份配置静默失效，是很难排查的坑 —— 所以缺省为启用
        val noEnabled = """{"id":"a","slot":"专升本","title":"标题"}"""
        val cfg = PromoValidator.parse(json(noEnabled), now)

        assertEquals("缺 enabled 应视为启用", 1, cfg.promos.size)
    }

    @Test
    fun `显式 enabled false 立即下架`() {
        val cfg = PromoValidator.parse(json(slot(enabled = "false")), now)

        assertTrue("enabled=false 必须不展示", cfg.promos.isEmpty())
    }

    // ── 跳转安全（最重要的一组） ──

    @Test
    fun `白名单内的 https 链接被接受`() {
        val action = """{"kind":"external","url":"https://ehall.yitsd.edu.cn/xxx"}"""
        val cfg = PromoValidator.parse(json(slot(action = action)), now)

        assertEquals(PromoAction.External("https://ehall.yitsd.edu.cn/xxx"), cfg.promos[0].action)
    }

    @Test
    fun `白名单域名的子域名也被接受`() {
        val action = """{"kind":"external","url":"https://lib.yitsd.edu.cn/a/b"}"""
        val cfg = PromoValidator.parse(json(slot(action = action)), now)

        assertTrue(cfg.promos[0].action is PromoAction.External)
    }

    @Test
    fun `非白名单域名被拒绝 且降级为不可点而不是丢弃整条`() {
        val action = """{"kind":"external","url":"https://evil.example.com/phish"}"""
        val cfg = PromoValidator.parse(json(slot(action = action)), now)

        // 文案本身仍有价值，只是不给它一个危险的跳转目标
        assertEquals(1, cfg.promos.size)
        assertNull("非白名单域名必须被拒绝", cfg.promos[0].action)
    }

    @Test
    fun `明文 http 被拒绝`() {
        // 校园网/公共 Wi-Fi 下明文跳转可被篡改，而推广位是"点出去就填报名信息"的场景
        val action = """{"kind":"external","url":"http://ehall.yitsd.edu.cn/x"}"""
        val cfg = PromoValidator.parse(json(slot(action = action)), now)

        assertNull(cfg.promos[0].action)
    }

    @Test
    fun `伪装成白名单的前缀域名被拒绝`() {
        // 经典绕过：把白名单域名拼在自己的域名里
        listOf(
            "https://ehall.yitsd.edu.cn.evil.com/x",
            "https://notyitsd.edu.cn/x",
            "https://evil.com/?q=ehall.yitsd.edu.cn",
        ).forEach { url ->
            val action = """{"kind":"external","url":"$url"}"""
            val cfg = PromoValidator.parse(json(slot(action = action)), now)
            assertNull("「$url」不该被当成白名单", cfg.promos[0].action)
        }
    }

    @Test
    fun `未知 kind 的动作被忽略`() {
        val action = """{"kind":"javascript","url":"https://ehall.yitsd.edu.cn/x"}"""
        val cfg = PromoValidator.parse(json(slot(action = action)), now)

        assertNull(cfg.promos[0].action)
    }

    @Test
    fun `App 内跳转只允许预置路由`() {
        val ok = PromoValidator.parse(json(slot(action = """{"kind":"route","route":"services"}""")), now)
        assertEquals(PromoAction.Route("services"), ok.promos[0].action)

        val bad = PromoValidator.parse(json(slot(action = """{"kind":"route","route":"/admin/secret"}""")), now)
        assertNull("任意内部路由必须被拒绝", bad.promos[0].action)
    }

    // ── 逐条容错：一条坏数据不能毁整页 ──

    @Test
    fun `一条坏数据不影响其余条目`() {
        val cfg = PromoValidator.parse(
            json(
                slot(id = "ok_1"),
                slot(id = ""),                                  // 缺 id
                slot(id = "ok_2", slot = "返乡大巴"),
                slot(id = "bad_slot", slot = "不存在的页面"),      // slot 不在枚举内
                slot(id = "ok_3", slot = "考研", title = "考研冲刺"),
                slot(id = "bad_title", title = ""),              // 缺标题
            ),
            now,
        )

        assertEquals(3, cfg.promos.size)
        assertEquals(listOf("ok_1", "ok_2", "ok_3"), cfg.promos.map { it.id }.sorted())
    }

    @Test
    fun `完全无法解析的 JSON 返回空配置而不是抛异常`() {
        listOf("", "not json", "{", """{"slots": "不是数组"}""", "[]").forEach { bad ->
            val cfg = PromoValidator.parse(bad, now)
            assertTrue("「$bad」应退化为空配置", cfg.promos.isEmpty())
        }
    }

    @Test
    fun `字段类型不符时按缺失处理而不是崩`() {
        // 服务端把数字写成字符串、把布尔写成字符串，都属于常见配置错误
        val messy = """{"id":"a","slot":"专升本","title":"标题","startAt":"123","priority":"high"}"""
        val cfg = PromoValidator.parse(json(messy), now)

        assertEquals(1, cfg.promos.size)
        assertNull("字符串型的 startAt 应被当作缺失", cfg.promos[0].startAt)
        assertEquals("字符串型的 priority 应回退为 0", 0, cfg.promos[0].priority)
    }

    @Test
    fun `重复 id 只保留一条`() {
        val cfg = PromoValidator.parse(json(slot(id = "dup"), slot(id = "dup")), now)

        assertEquals(1, cfg.promos.size)
    }

    // ── 时间窗 ──

    @Test
    fun `未到开始时间不展示`() {
        val cfg = PromoValidator.parse(json(slot(startAt = now + 3600)), now)

        assertTrue(cfg.promos.isEmpty())
    }

    @Test
    fun `已过结束时间不展示`() {
        val cfg = PromoValidator.parse(json(slot(endAt = now - 3600)), now)

        assertTrue(cfg.promos.isEmpty())
    }

    @Test
    fun `时间窗内的展示`() {
        val cfg = PromoValidator.parse(json(slot(startAt = now - 10, endAt = now + 10)), now)

        assertEquals(1, cfg.promos.size)
    }

    @Test
    fun `起止写反的配置被丢弃`() {
        // 配置错误：展示出来会永远不生效，不如当无效处理
        val cfg = PromoValidator.parse(json(slot(startAt = now + 100, endAt = now - 100)), now)

        assertTrue(cfg.promos.isEmpty())
    }

    // ── 排序与配图 ──

    @Test
    fun `按优先级降序 同优先级按 id 稳定排序`() {
        val cfg = PromoValidator.parse(
            json(
                slot(id = "b", priority = 1),
                slot(id = "a", priority = 1),
                slot(id = "c", priority = 9),
            ),
            now,
        )

        // 顺序必须稳定：否则每次拉取顺序可能变，用户看起来像在跳
        assertEquals(listOf("c", "a", "b"), cfg.promos.map { it.id })
    }

    @Test
    fun `非白名单配图被丢弃但文案保留`() {
        val cfg = PromoValidator.parse(json(slot(image = "https://evil.com/a.jpg")), now)

        assertEquals(1, cfg.promos.size)
        assertNull("配图域名同样要过白名单", cfg.promos[0].imageUrl)
    }

    @Test
    fun `超长标题与正文被拒绝或截断`() {
        val longTitle = "标".repeat(200)
        assertTrue("超长标题应被拒绝", PromoValidator.parse(json(slot(title = longTitle)), now).promos.isEmpty())

        val longBody = "文".repeat(1000)
        val ok = PromoValidator.parse(json(slot(body = longBody)), now)
        assertEquals("超长正文应被截断而不是拒绝整条", 600, ok.promos[0].body.length)
    }
}
