package com.pocket.xiaoli.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiangZhiClientTest {

    private val client = QiangZhiClient()

    @Test
    fun normalizeBaseUrlBareDomain() {
        assertEquals("https://jw.fosu.edu.cn/app.do", client.normalizeBaseUrl("jw.fosu.edu.cn"))
        assertEquals("https://jw.fosu.edu.cn/app.do", client.normalizeBaseUrl("https://jw.fosu.edu.cn"))
        assertEquals("https://jw.ynufe.edu.cn/app.do", client.normalizeBaseUrl("https://jw.ynufe.edu.cn"))
        assertEquals("https://jw.fosu.edu.cn/app.do", client.normalizeBaseUrl("https://jw.fosu.edu.cn/"))
        assertEquals("https://jw.fosu.edu.cn/app.do", client.normalizeBaseUrl("https://jw.fosu.edu.cn/app.do"))
        assertEquals("https://10.0.0.5:8080/app.do", client.normalizeBaseUrl("10.0.0.5:8080"))
        assertEquals("", client.normalizeBaseUrl("  "))
    }

    @Test
    fun parseApiCourseFullFields() {
        val item = JSONObject("""
            {"kcmc":"高等数学A","jsxm":"张三","jsmc":"教一楼101",
             "kkzc":"1-16周","sjbz":"","jcs":"1-2节","xqj":1,"kcsj":""}
        """.trimIndent())
        val raw = client.parseApiCourse(item)
        assertTrue(raw != null)
        assertEquals("高等数学A", raw!!.name)
        assertEquals("张三", raw.teacher)
        assertEquals("教一楼101", raw.place)
        assertEquals(1, raw.day)
        assertTrue(raw.weeks == (1..16).toList())
        assertTrue(raw.sections == listOf(1, 2))
    }

    @Test
    fun parseApiCourseOddWeeks() {
        // sjbz==1 → 单周
        val item = JSONObject("""
            {"kcmc":"大学英语","jsxm":"李四","jsmc":"教五楼201",
             "kkzc":"1-16周","sjbz":"1","jcs":"3-4节","xqj":2,"kcsj":""}
        """.trimIndent())
        val raw = client.parseApiCourse(item)
        assertTrue(raw != null)
        assertTrue(raw!!.weeks.all { it % 2 == 1 })
        assertTrue(raw.weeks == (1..16).filter { it % 2 == 1 })
        assertTrue(raw.sections == listOf(3, 4))
    }

    @Test
    fun parseApiCourseKcsjFallback() {
        // xqj 缺失，用 kcsj "50102" → 星期5, 1-2节
        val item = JSONObject("""
            {"kcmc":"大学物理","jsxm":"王五","jsmc":"教二楼301",
             "kkzc":"1-8,11-16周","sjbz":"","jcs":"","xqj":0,"kcsj":"50102"}
        """.trimIndent())
        val raw = client.parseApiCourse(item)
        assertTrue(raw != null)
        assertEquals(5, raw!!.day)
        assertTrue(raw.sections == listOf(1, 2))
        assertTrue(raw.weeks == listOf(
            1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 13, 14, 15, 16
        ))
    }

    @Test
    fun parseApiCourseRejectsEmpty() {
        val noName = JSONObject("""{"kcmc":"","jsxm":"","jsmc":"","kkzc":"","jcs":"","xqj":0,"kcsj":""}""")
        val noDay = JSONObject("""{"kcmc":"计算机","jsxm":"","jsmc":"","kkzc":"1-16周","jcs":"1-2节","xqj":0,"kcsj":""}""")
        val noWeek = JSONObject("""{"kcmc":"体育","jsxm":"","jsmc":"","kkzc":"","jcs":"1-2节","xqj":3,"kcsj":""}""")
        assertTrue(client.parseApiCourse(noName) == null)
        assertTrue(client.parseApiCourse(noDay) == null)
        assertTrue(client.parseApiCourse(noWeek) == null)
    }

    @Test
    fun parseHtmlKbContentStandard() {
        val html = """
        <table>
          <tr>
            <td class="kbcontent" id="td12">高等数学A<br>
              <font title="老师">张三</font><br>
              <font title="教室">教一楼101</font><br>
              <font title="周次(节次)">1-16周(1-2节)</font>
            </td>
            <td class="kbcontent" id="td23">线性代数<br>
              <font title="老师">李四</font><br>
              <font title="教室">教五楼202</font><br>
              <font title="周次(节次)">1-8,10-16周(3-4节)</font>
            </td>
          </tr>
        </table>
        """.trimIndent()
        val courses = client.parseHtmlCourses(html)
        assertEquals(2, courses.size)
        val first = courses.first { it.name == "高等数学A" }
        assertEquals(1, first.dayOfWeek)
        assertEquals(1, first.startSlot)
        assertEquals(2, first.endSlot)
        assertEquals("all", first.weekType)
        assertEquals("张三", first.teacher)

        val second = courses.first { it.name == "线性代数" }
        assertEquals(2, second.dayOfWeek)
        assertEquals(3, second.startSlot)
        assertTrue(second.weeks.split(",").map { it.toInt() } == listOf(
            1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16
        ))
    }

    @Test
    fun parseHtmlKbContentNoTitleFontOrder() {
        val html = """
        <table>
          <tr>
            <td class="kbcontent" id="td34">概率论<br>
              <font>张三</font><br>
              <font>1-16周(5-6节)</font><br>
              <font>教三楼101</font>
            </td>
          </tr>
        </table>
        """.trimIndent()
        val courses = client.parseHtmlCourses(html)
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals(3, c.dayOfWeek)
        assertEquals(5, c.startSlot)
        assertEquals(6, c.endSlot)
        assertEquals("张三", c.teacher)
        assertEquals("教三楼101", c.room)
    }

    @Test
    fun parseHtmlKbContentOddEvenMarkers() {
        val html = """
        <table>
          <tr>
            <td class="kbcontent" id="td61">高等数学B<br>
              <font title="老师">张三</font><br>
              <font title="教室">教一楼102</font><br>
              <font title="周次(节次)">1-16(单)周(7-8节)</font>
            </td>
          </tr>
        </table>
        """.trimIndent()
        val courses = client.parseHtmlCourses(html)
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals("odd", c.weekType)
        assertTrue(c.weeks.split(",").map { it.toInt() }.all { it % 2 == 1 })
        assertEquals(6, c.dayOfWeek)
        assertEquals(7, c.startSlot)
    }

    @Test
    fun mergeAndConvertCombinesWeeks() {
        val raw = listOf(
            QiangZhiClient.RawCourse(
                name = "C1", teacher = "T1", place = "R1", day = 1,
                weeks = listOf(1, 2, 3), sections = listOf(1, 2)
            ),
            QiangZhiClient.RawCourse(
                name = "C1", teacher = "T1", place = "R1", day = 1,
                weeks = listOf(4, 5), sections = listOf(1, 2)
            ),
            QiangZhiClient.RawCourse(
                name = "C2", teacher = "T1", place = "R1", day = 1,
                weeks = listOf(1, 2), sections = listOf(3, 4)
            )
        )
        val courses = client.mergeAndConvert(raw)
        assertEquals(2, courses.size)
        val mergedC1 = courses.first { it.name == "C1" }
        assertTrue(mergedC1.weeks.split(",").map { it.toInt() } == listOf(1, 2, 3, 4, 5))
        val c2 = courses.first { it.name == "C2" }
        assertTrue(c2.weeks.split(",").map { it.toInt() } == listOf(1, 2))
    }

    @Test
    fun deriveSemesterStartNestedData() {
        // 第3周 + 2026-03-02 → 开学日 = 2026-02-16（与 tryLogin 一致，先扁平化 data 再传）
        val json = JSONObject("""
            {"status":1,"data":{"currentTime":"2026-03-02 08:00:00","zc":3,"totalweek":20,"xnxqid":"2025-2026-2"}}
        """.trimIndent())
        val data = json.optJSONObject("data")
        assertEquals("2026-02-16", client.deriveSemesterStart(data))
    }

    @Test
    fun deriveSemesterStartFlatFallback() {
        // 扁平结构 + dqsj 字段
        val json = JSONObject("""
            {"currentTime":"2026-09-15 10:00:00","zc":1,"totalweek":18}
        """.trimIndent())
        assertEquals("2026-09-15", client.deriveSemesterStart(json))
    }

    @Test
    fun deriveSemesterStartInvalidWeekReturnsNull() {
        assertEquals(null, client.deriveSemesterStart(JSONObject("{\"zc\":0,\"currentTime\":\"2026-03-02\"}")))
        assertEquals(null, client.deriveSemesterStart(JSONObject("{\"zc\":99,\"currentTime\":\"2026-03-02\"}")))
        assertEquals(null, client.deriveSemesterStart(JSONObject("{\"zc\":3,\"currentTime\":\"not-a-date\"}")))
        assertEquals(null, client.deriveSemesterStart(null))
    }
}