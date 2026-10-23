package com.pocket.xiaoli.data.exam

import com.pocket.xiaoli.data.agent.AssistantLlmClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LLM 兜底解析的 JSON 处理测试。
 *
 * ## 为什么重点测这一段
 *
 * 模型输出是**不可控输入**：它可能加 markdown 围栏、加前后说明文字、
 * 少给字段、给空字段、甚至返回一段散文。任何一处没兜住，
 * 用户就会看到一条**字段错位的考试** —— 那比解析失败更糟。
 *
 * 网络调用本身难单测，但这段纯字符串处理可以，且它正是最易错的地方。
 */
class ExamLlmExtractorTest {

    private val extractor = ExamLlmExtractor(AssistantLlmClient())

    @Test
    fun `parses a plain json array`() {
        val r = extractor.parseJson(
            """[{"courseName":"高等数学A","date":"2026-01-15","time":"14:00-16:00","location":"教三楼305","seat":"12"}]"""
        )
        assertNotNull(r)
        assertEquals(1, r!!.size)
        assertEquals("高等数学A", r[0].courseName)
        assertEquals("教三楼305", r[0].location)
        assertEquals("12", r[0].seat)
    }

    @Test
    fun `strips markdown code fence`() {
        // 模型几乎总会加围栏，这不是可选优化而是必需步骤
        val r = extractor.parseJson(
            """
            ```json
            [{"courseName":"大学英语","date":"2026-01-16"}]
            ```
            """.trimIndent()
        )
        assertNotNull(r)
        assertEquals("大学英语", r!![0].courseName)
    }

    @Test
    fun `tolerates prose before and after the array`() {
        val r = extractor.parseJson(
            """好的，我从通知中提取到以下考试：
            [{"courseName":"线性代数","date":"2026-01-18"}]
            以上是全部考试安排。"""
        )
        assertNotNull(r)
        assertEquals("线性代数", r!![0].courseName)
    }

    @Test
    fun `missing fields become empty strings not crashes`() {
        val r = extractor.parseJson("""[{"courseName":"高数"}]""")
        assertNotNull(r)
        assertEquals("", r!![0].date)
        assertEquals("", r[0].time)
        assertEquals("", r[0].location)
        assertEquals("", r[0].seat)
    }

    @Test
    fun `entries without a course name are dropped`() {
        // 没名字的考试对用户毫无用处，宁可少一条
        val r = extractor.parseJson(
            """[{"date":"2026-01-15"},{"courseName":"   "},{"courseName":"物理","date":"2026-01-20"}]"""
        )
        assertNotNull(r)
        assertEquals(1, r!!.size)
        assertEquals("物理", r[0].courseName)
    }

    @Test
    fun `empty array yields null so caller falls back gracefully`() {
        // 返回 null 而不是空列表：调用方据此判断"AI 也没认出来"
        assertNull(extractor.parseJson("[]"))
    }

    @Test
    fun `non json output yields null instead of throwing`() {
        assertNull(extractor.parseJson("这段通知里没有考试安排。"))
        assertNull(extractor.parseJson(""))
        assertNull(extractor.parseJson("null"))
    }

    @Test
    fun `malformed json yields null instead of throwing`() {
        assertNull(extractor.parseJson("""[{"courseName":}]"""))
        assertNull(extractor.parseJson("""{"courseName":"高数"}"""))  // 对象不是数组
    }

    @Test
    fun `unclosed fence still parses the body`() {
        // 模型偶尔忘了闭合围栏；此时应取围栏之后的内容而不是整段丢弃
        val r = extractor.parseJson("```json\n[{\"courseName\":\"化学\"}]")
        assertNotNull(r)
        assertEquals("化学", r!![0].courseName)
    }

    @Test
    fun `multiple entries keep their order`() {
        val r = extractor.parseJson(
            """[{"courseName":"A","date":"2026-01-11"},{"courseName":"B","date":"2026-01-12"},{"courseName":"C","date":"2026-01-13"}]"""
        )
        assertNotNull(r)
        assertEquals(listOf("A", "B", "C"), r!!.map { it.courseName })
    }
}
