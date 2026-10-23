package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhengfangGradeApiTest {

    private val api = ZhengfangGradeApi(ZhengfangClient())

    // ── parseGradeItems：标准字段 ──

    @Test
    fun parseGradeItems_standardFields() {
        val json = """{
            "items": [{
                "kcmc": "高等数学", "xf": "4", "cj": "92", "jd": "4.0",
                "pscj": "90", "kscj": "93", "qzcj": "85", "ksxzmc": "正常考试"
            }]
        }"""
        val items = api.parseGradeItems(json, "2025-2026-第1学期")
        assertEquals(1, items.size)
        val it = items[0]
        assertEquals("高等数学", it.courseName)
        assertEquals("4", it.credits)
        assertEquals("92", it.totalScore)
        assertEquals("4.0", it.gpa)
        assertEquals("90", it.regular)
        assertEquals("93", it.final)
        assertEquals("85", it.midterm)
        assertEquals("正常考试", it.examType)
        assertEquals("2025-2026-第1学期", it.termLabel)
    }

    // ── parseGradeItems：字段别名容错（zf_grade.py 同款） ──

    @Test
    fun parseGradeItems_aliasFields() {
        val json = """{
            "items": [{
                "kcmc": "大学英语", "xf": 3, "zpcj": "78",
                "pingshicj": "80", "qmcj": "75", "qizhongcj": "", "ksxz": "补考"
            }]
        }"""
        val items = api.parseGradeItems(json, "2024-2025-第2学期")
        assertEquals(1, items.size)
        val it = items[0]
        // 别名优先于默认名（zpcj 而非 cj；pingshicj 而非 pscj；qmcj 而非 kscj；ksxz 而非 ksxzmc）
        assertEquals("78", it.totalScore)
        assertEquals("80", it.regular)
        assertEquals("75", it.final)
        assertEquals("", it.midterm) // 空串别名不覆盖
        assertEquals("补考", it.examType)
        assertEquals("3", it.credits) // 数字学分转字符串
    }

    // ── parseGradeItems：无分项字段 → 空串（用户学校实测场景） ──

    @Test
    fun parseGradeItems_noBreakdownFields() {
        val json = """{
            "items": [{ "kcmc": "数据结构", "cj": "78", "jd": "2.6", "bfzcj": "" }]
        }"""
        val items = api.parseGradeItems(json, "2025-2026-第1学期")
        assertEquals(1, items.size)
        val it = items[0]
        assertEquals("78", it.totalScore)
        assertEquals("2.6", it.gpa)
        assertEquals("", it.regular)
        assertEquals("", it.final)
        assertEquals("", it.midterm)
    }

    // ── parseGradeItems：数字成绩 ──

    @Test
    fun parseGradeItems_numericScores() {
        val json = """{ "items": [{ "kcmc": "体育", "cj": 95 }] }"""
        val items = api.parseGradeItems(json, "term")
        assertEquals(1, items.size)
        assertEquals("95", items[0].totalScore)
    }

    // ── parseGradeItems：异常/空输入 → 空列表 ──

    @Test
    fun parseGradeItems_invalidInputReturnsEmpty() {
        assertTrue(api.parseGradeItems("", "term").isEmpty())
        assertTrue(api.parseGradeItems("<html>login</html>", "term").isEmpty())
        assertTrue(api.parseGradeItems("""{"noItems":[]}""", "term").isEmpty())
        assertTrue(api.parseGradeItems("""{"items":"oops"}""", "term").isEmpty())
    }

    // ── parseTermsFromXnXq：JS 变量学期列表 ──

    @Test
    fun parseTermsFromXnXq_standard() {
        val html = """<script>var xnxq = [{"xnm":"2025","xqm":"12"},{"xnm":"2025","xqm":"3"}];</script>"""
        val terms = api.parseTermsFromXnXq(html)
        assertEquals(2, terms.size)
        assertEquals("2025", terms[0].xnm)
        assertEquals("12", terms[0].xqm)
        assertTrue(terms[0].label.contains("2025"))
        assertEquals("3", terms[1].xqm)
    }

    @Test
    fun parseTermsFromXnXq_noVariableReturnsEmpty() {
        assertTrue(api.parseTermsFromXnXq("<html>no variable</html>").isEmpty())
        assertTrue(api.parseTermsFromXnXq("").isEmpty())
    }

    // ── parseTermsFromSelects：下拉框枚举兜底 ──

    @Test
    fun parseTermsFromSelects_cartesian() {
        val html = """
            <html>
              <select id="xnm"><option value="">请选择</option><option value="2025">2025-2026</option></select>
              <select id="xqm"><option value="">请选择</option><option value="12">1</option><option value="3">2</option></select>
            </html>
        """
        val terms = api.parseTermsFromSelects(html)
        // 空 option 被过滤：1 学年 × 2 学期 = 2 项
        assertEquals(2, terms.size)
        assertTrue(terms.any { it.xnm == "2025" && it.xqm == "12" })
        assertTrue(terms.any { it.xnm == "2025" && it.xqm == "3" })
    }

    @Test
    fun parseTermsFromSelects_invalidHtmlReturnsEmpty() {
        assertTrue(api.parseTermsFromSelects("").isEmpty())
        assertTrue(api.parseTermsFromSelects("<html><p>no select</p></html>").isEmpty())
    }

    // ── gradeField：取值优先级 ──

    @Test
    fun gradeField_prefersFirstNonNull() {
        val o = com.google.gson.JsonParser.parseString(
            """{"cj":"90","zpcj":"88","a":null,"b":"","c":"ok"}"""
        ).asJsonObject
        assertEquals("90", api.gradeField(o, "cj", "zpcj"))
        assertEquals("88", api.gradeField(o, "missing", "zpcj"))
        assertEquals("ok", api.gradeField(o, "a", "b", "c"))
        assertEquals("", api.gradeField(o, "a", "b", "missing"))
    }
}
