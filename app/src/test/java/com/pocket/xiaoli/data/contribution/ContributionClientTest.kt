package com.pocket.xiaoli.data.contribution

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共建榜 HTTP 客户端里那几个**纯函数**。
 *
 * 请求体会被服务端逐字段校验，所以这里钉住两件事：
 *
 * 1. **请求里没有 `points`** —— 分值只能由服务端查表，客户端多带一个数就是刷榜入口；
 * 2. 响应解析对坏行**只丢那一条**，不能让一条脏数据把整页变成空态。
 */
class ContributionClientTest {

    private val client = ContributionClient(
        baseUrl = "https://example.invalid",
        appKey = "test-key",
    )

    @Test
    fun `未配置地址或 key 时不算可配置`() {
        assertTrue(client.configured)
        assertFalse(ContributionClient("", "k").configured)
        assertFalse(ContributionClient("https://x", "").configured)
        assertFalse(ContributionClient(" ", "  ").configured)
    }

    @Test
    fun `上报体只有动作 没有分数字段`() {
        val json = JsonParser.parseString(
            client.reportBody(
                cid = "c-1",
                nickname = "鲤鱼王",
                action = ContributionAction.COURSE_IMPORT,
                eid = "e-1",
            ),
        ).asJsonObject

        assertEquals(1, json.get("v").asInt)
        assertEquals("c-1", json.get("cid").asString)
        assertEquals("鲤鱼王", json.get("nickname").asString)
        assertEquals("e-1", json.get("eid").asString)
        assertEquals("course_import", json.get("action").asString)
        // 这是安全边界：客户端不许自己报分
        assertFalse(json.has("points"))
        assertFalse(json.has("score"))
    }

    @Test
    fun `榜单路径带上分类 周期 上限与可选 cid`() {
        assertEquals(
            "/v1/contribution/leaderboard?category=notes&period=all&limit=50&cid=c-1",
            client.leaderboardPath(ContributionCategory.NOTES, ContributionPeriod.ALL, "c-1"),
        )
        // 没加入榜单时不该带上任何身份
        assertEquals(
            "/v1/contribution/leaderboard?category=all&period=week&limit=50",
            client.leaderboardPath(ContributionCategory.ALL, ContributionPeriod.WEEK, null),
        )
        assertEquals(
            "/v1/contribution/leaderboard?category=campus&period=week&limit=50",
            client.leaderboardPath(ContributionCategory.CAMPUS, ContributionPeriod.WEEK, "  "),
        )
    }

    @Test
    fun `正常响应按昵称排序 且忽略分数与名次`() {
        val board = client.parseBoard(
            raw = """
                {"category":"all","period":"week","rows":[
                  {"rank":1,"nickname":"摸鱼佬","points":80,"mine":true},
                  {"rank":2,"nickname":"鲤鱼王","points":120,"mine":false}
                ],"me":{"rank":2,"nickname":"摸鱼佬","points":80},"total":2}
            """.trimIndent(),
            category = ContributionCategory.ALL,
            period = ContributionPeriod.WEEK,
            nowMs = 123L,
        )

        assertNotNull(board)
        board!!
        // 服务端即使还带着 rank / points，也统统不看：顺序按昵称（li < mo）重排。
        assertEquals(
            listOf(
                ContributionEntry(1, "鲤鱼王", mine = false),
                ContributionEntry(2, "摸鱼佬", mine = true),
            ),
            board.entries,
        )
        assertEquals(ContributionMe("摸鱼佬"), board.me)
        assertEquals(2, board.total)
        assertEquals(123L, board.fetchedAt)
        assertFalse(board.fromCache)
    }

    @Test
    fun `空昵称的行只丢自己 其它照常上榜`() {
        val board = client.parseBoard(
            raw = """
                {"rows":[
                  {"rank":0,"nickname":"早八人"},
                  {"rank":1,"nickname":"   "},
                  {"rank":2,"nickname":"笔记搬运工"}
                ]}
            """.trimIndent(),
            category = ContributionCategory.ALL,
            period = ContributionPeriod.WEEK,
            nowMs = 0L,
        )

        // 笔记搬运工 < 早八人（拼音 b < z），rank 按排序重编号。
        assertEquals(
            listOf(
                ContributionEntry(1, "笔记搬运工"),
                ContributionEntry(2, "早八人"),
            ),
            board!!.entries,
        )
        assertNull(board.me)
        // 旧服务端不带 total：至少不能比看得见的列表还小。
        assertEquals(2, board.total)
    }

    @Test
    fun `我的昵称为空时当没上榜`() {
        val noName = client.parseBoard(
            """{"rows":[],"me":{"nickname":" "}}""",
            ContributionCategory.ALL,
            ContributionPeriod.WEEK,
            0L,
        )
        assertNull(noName!!.me)

        val ok = client.parseBoard(
            """{"rows":[],"me":{"nickname":"我"}}""",
            ContributionCategory.ALL,
            ContributionPeriod.WEEK,
            0L,
        )
        assertEquals(ContributionMe("我"), ok!!.me)
    }

    @Test
    fun `坏 JSON 与空响应返回 null`() {
        assertNull(
            client.parseBoard("{ 半个 JSON", ContributionCategory.ALL, ContributionPeriod.WEEK, 0L),
        )
        assertNull(
            client.parseBoard("", ContributionCategory.ALL, ContributionPeriod.WEEK, 0L),
        )
        // 结构对但没有 rows：是一份空榜，而不是解析失败
        val empty = client.parseBoard("{}", ContributionCategory.ALL, ContributionPeriod.WEEK, 0L)
        assertNotNull(empty)
        assertTrue(empty!!.entries.isEmpty())
    }
}
