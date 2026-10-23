package com.pocket.xiaoli.data.secondclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * [SecondClassEnvelope] 的契约。
 *
 * ⚠️ 本文件里的 `UNAUTHORIZED_BODY` / `SUCCESS_BODY` / `HTML_BODY` 是 **2026-10-10 实测抓取的真实响应**
 * （不是编出来的样本），改动它们之前请先回看架构文档 §11.8 / §11.9 —— 那两张表就是这些用例的依据。
 * 标了"合成"的是为覆盖分支补的，与实际抓取内容无关。
 */
class SecondClassEnvelopeTest {

    /** 实测：未登录时三个端点都返回这一份（`X-Requested-With` 已带）。 */
    private val unauthorizedBody = """{"code":"400","result":"token不能为空","success":false}"""

    /**
     * 实测：公开接口 `GET /yuny/news/newslist` 的真实响应，**截取前两行**。
     * 保留原样的关键特征：所有标量都是字符串、字段可缺省、成功响应里没有 `code`。
     */
    private val successBody = """
        {"rows":[
          {"id":"1318523","contentType":"2","title":"用鸿蒙打开校园新模式","webFlag":"1",
           "webVersionFlag":"0","appFlag":"0","managerFlag":"0","delflag":"0","createby":"243",
           "createdate":"1780046817000","readnum":"0"},
          {"id":"1318400","contentType":"1","title":"“我用 AI 校古籍”启动","url":"https://example.invalid/x",
           "webFlag":"1","delflag":"0","createby":"243","createdate":"1776051951000","readnum":"0"}
        ],"total":"5","records":"88","page":"1"}
    """.trimIndent()

    /** 实测：**漏了** `X-Requested-With` 时，服务端返回的就是这一页 HTML 的开头。 */
    private val htmlBody = "<!DOCTYPE html>\n<html>\n<head>\n\t<title>到梦空间 — 助力大学生实现梦想</title>"

    // ───────────────────── 未登录必须单独成一支 ─────────────────────

    /**
     * 守门人：未登录**不能**落进 `Failed`。
     *
     * 落了会怎样：UI 只显示"加载失败"，而用户真正该做的是回去重新登录 ——
     * 架构文档 §4 把这条例为"本模块最容易做错的一处体验"。
     */
    @Test
    fun `实测的未登录响应必须判成 Unauthorized 而不是失败`() {
        val result = SecondClassEnvelope.validate(unauthorizedBody)

        assertEquals(SecondClassResult.Unauthorized, result)
    }

    // ───────────────────── 成功判据：没有 code ─────────────────────

    @Test
    fun `实测的成功响应没有 code 因此判成功`() {
        val result = SecondClassEnvelope.validate(successBody)

        val root = result.okValue()
        assertTrue("成功响应的根对象应当仍可读字段", root.has("rows"))
    }

    @Test
    fun `分页元数据是字符串也能解析且页数算法与服务端一致`() {
        val page = SecondClassEnvelope.toPage(SecondClassEnvelope.validate(successBody).okValue()) { it }

        assertEquals(88, page.totalRecords)
        assertEquals(1, page.page)
        // 服务端自己算好的 total 是 "5"；我们按 20 条一页自己算也必须是 5 —— 两边互相印证。
        assertEquals(5, page.totalPages)
        assertEquals(5, page.pageCount(pageSize = 20))
        assertEquals(2, page.items.size)
    }

    @Test
    fun `总条数缺失或页大小非法时页数是 null 而不是崩溃`() {
        val noRecords = SecondClassEnvelope.validate("""{"rows":[]}""").okValue()
        val page = SecondClassEnvelope.toPage(noRecords) { it }

        assertNull(page.pageCount(pageSize = 20))
        assertNull(page.pageCount(pageSize = 0))
        assertNull(page.totalRecords)
    }

    // ───────────────────── 那个"看不出错在哪"的坑 ─────────────────────

    /**
     * 守门人：拿到整页 HTML 时，报错信息必须**指向真正的原因** —— 也就是 **GET**。
     *
     * ⚠️ 这条用例本身**改过一次结论**，值得记住：第一版断言的是"要点名
     * `X-Requested-With`"，因为当时只有一条 GET 的观察，就把它当成了"漏了请求头"。
     * 2026-10-10 的对照实测（§11.12）推翻了这个解释：**不带该头的 POST 照样返回 JSON**，
     * 而**不带该头的 GET 返回整页首页** —— 决定成败的是 method。
     *
     * 若不专门处理，开发者看到的会是一句 Gson 的语法异常，完全指不到"用了 GET"上。
     */
    @Test
    fun `实测的整页 HTML 会给出指向 GET 的原因`() {
        val result = SecondClassEnvelope.validate(htmlBody)

        assertTrue("必须是失败", result is SecondClassResult.Failed)
        val message = (result as SecondClassResult.Failed).message
        assertTrue(
            "原因里要点名 GET/POST（真正的原因），而不是请求头（那不是原因）",
            message.contains("GET") && message.contains("POST"),
        )
        assertTrue(
            "也不该再提 X-Requested-With —— 实测表明它不是成败关键",
            !message.contains("X-Requested-With"),
        )
        assertTrue("要回显一小段原文便于比对", message.contains("DOCTYPE html"))
    }

    @Test
    fun `空响应是失败而不是崩溃`() {
        assertTrue(SecondClassEnvelope.validate(null) is SecondClassResult.Failed)
        assertTrue(SecondClassEnvelope.validate("   ") is SecondClassResult.Failed)
    }

    // ───────────────────── 其余分支（合成用例） ─────────────────────

    /** 合成：覆盖"非 400 的错误码"这条分支，实测样本里没有它。 */
    @Test
    fun `其他错误码走 Failed 并带上服务端原话`() {
        val result = SecondClassEnvelope.validate("""{"code":"500","result":"系统繁忙","success":false}""")

        assertTrue(result is SecondClassResult.Failed)
        val failed = result as SecondClassResult.Failed
        assertEquals(500, failed.code)
        assertEquals("系统繁忙", failed.message)
    }

    /**
     * 实测（§11.12）：`POST token/checkToken` 带一个格式非法的 token 时，服务端回的是
     * **`90000019`「登录失效，请重新刷新扫码」**，而且它的 `result` 是**对象**。
     *
     * 守门人：它**必须**落进 `Unauthorized`。漏了它，用户会话过期时 UI 显示的是"加载失败"，
     * 而真正该说的是"请重新登录" —— 服务端那句文案本身就在叫人重新扫码。
     */
    @Test
    fun `实测的登录失效码 90000019 也是 Unauthorized`() {
        val body = """{"code":"90000019","result":{"msg":"登录失效，请重新刷新扫码"},"success":false}"""

        assertEquals(SecondClassResult.Unauthorized, SecondClassEnvelope.validate(body))
    }

    /** 合成：`result` 是对象时要能取出 `msg` —— 否则排查时唯一有用的信息就丢了。 */
    @Test
    fun `result 是对象时取其中的 msg`() {
        val body = """{"code":"500","result":{"msg":"系统繁忙"}}"""

        val failed = SecondClassEnvelope.validate(body) as SecondClassResult.Failed

        assertEquals("系统繁忙", failed.message)
    }

    /** 合成：错误码不是数字时不能把整条错误丢掉（message 仍要保留）。 */
    @Test
    fun `错误码不是数字时 code 为 null 但原话要留着`() {
        val result = SecondClassEnvelope.validate("""{"code":"E_SYS","result":"未知错误"}""")

        val failed = result as SecondClassResult.Failed
        assertNull(failed.code)
        assertEquals("未知错误", failed.message)
    }

    @Test
    fun `错误响应没给 result 时兜底文案要带上 code`() {
        val failed = SecondClassEnvelope.validate("""{"code":"500"}""") as SecondClassResult.Failed

        assertTrue(failed.message.contains("500"))
    }

    // ───────────────────── rows 的容错 ─────────────────────

    @Test
    fun `rows 缺失或是脏数据时退化成空页而不是失败`() {
        assertTrue(SecondClassEnvelope.toPage(SecondClassEnvelope.validate("""{"page":"1"}""").okValue()) { it }.items.isEmpty())
        assertTrue(SecondClassEnvelope.toPage(SecondClassEnvelope.validate("""{"rows":"oops"}""").okValue()) { it }.items.isEmpty())
    }

    /** 一条脏数据只丢那一行：整页变成"加载失败"是更糟的选择。 */
    @Test
    fun `rows 里的一条坏数据只丢那一行`() {
        val body = """{"rows":[{"id":"1"},{"id":"2"},"oops",null,{"id":"3"}]}"""
        val page = SecondClassEnvelope.toPage(SecondClassEnvelope.validate(body).okValue()) { row ->
            row.get("id")?.asString
        }

        assertEquals(listOf("1", "2", "3"), page.items)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> SecondClassResult<T>.okValue(): T {
        assertTrue("期望 Ok，实际是 $this", this is SecondClassResult.Ok<*>)
        return (this as SecondClassResult.Ok<T>).value
    }
}
