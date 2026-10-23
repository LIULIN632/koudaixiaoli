package com.pocket.xiaoli.network

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ZhengfangUtils] 的解析工具测试。
 *
 * ⚠️ 这个文件补的是一个**此前不存在的测试面**：`extractJsonArray` 原先藏在
 * `ZhengfangAcademicApi` 里且是 `private`，既不能被别的 API 复用，也**没有任何单测**
 * （全仓 `grep ZhengfangAcademicApi app/src/test` = 0 命中）。
 * 上移到 [ZhengfangUtils] 之后它才有了测试。
 */
class ZhengfangUtilsTest {

    // ────────────────────── findRowArray：容器键名 ──────────────────────

    @Test
    fun `标准 items 容器`() {
        val arr = ZhengfangUtils.findRowArray("""{"items":[{"kcmc":"高等数学"}],"totalResult":1}""")
        assertNotNull(arr)
        assertEquals(1, arr!!.size())
        assertEquals("高等数学", arr[0].asJsonObject.get("kcmc").asString)
    }

    @Test
    fun `容器键名大小写不敏感`() {
        for (key in listOf("items", "Items", "ITEMS", "ItEmS")) {
            val arr = ZhengfangUtils.findRowArray("""{"$key":[{"kcmc":"x"}]}""")
            assertNotNull("键名 $key 应被识别", arr)
            assertEquals(1, arr!!.size())
        }
    }

    @Test
    fun `其余常见容器键名都能命中`() {
        for (key in listOf("data", "rows", "list", "result", "xx", "kbList", "jxbList")) {
            val arr = ZhengfangUtils.findRowArray("""{"$key":[{"kcmc":"x"}]}""")
            assertNotNull("键名 $key 应被识别", arr)
            assertEquals(1, arr!!.size())
        }
    }

    @Test
    fun `裸数组响应`() {
        val arr = ZhengfangUtils.findRowArray("""[{"kcmc":"大学英语"}]""")
        assertNotNull(arr)
        assertEquals(1, arr!!.size())
    }

    @Test
    fun `一层嵌套 data_items`() {
        val arr = ZhengfangUtils.findRowArray("""{"data":{"items":[{"kcmc":"x"}]}}""")
        assertNotNull(arr)
        assertEquals(1, arr!!.size())
    }

    // ────────────────────── findRowArray：深度兜底 ──────────────────────

    @Test
    fun `没见过的容器名由深度搜索兜住`() {
        // 这是刻意保留的第三层降级：容器名猜错时，功能从"不可用"降级为"可用但字段可能不全"
        val arr = ZhengfangUtils.findRowArray("""{"someUnknownWrapper":{"anotherLevel":[{"kcmc":"x"}]}}""")
        assertNotNull("深度搜索应找到元素为对象的数组", arr)
        assertEquals(1, arr!!.size())
    }

    @Test
    fun `元素不是对象的数组不算数据行`() {
        // {"ids":[1,2,3]} 不是数据行；没有对象数组时应返回 null，而不是把 [1,2,3] 当行
        assertNull(ZhengfangUtils.findRowArray("""{"ids":[1,2,3]}"""))
        assertNull(ZhengfangUtils.findRowArray("""{"tags":["a","b"]}"""))
    }

    @Test
    fun `空数组是合法的数据行容器`() {
        // 教务"本学期没有可选课程"就是 {"items":[]} —— 这是**解析成功但零条**，不是解析失败
        val arr = ZhengfangUtils.findRowArray("""{"items":[],"totalResult":0}""")
        assertNotNull("空数组仍应被认定为找到了容器", arr)
        assertEquals(0, arr!!.size())
    }

    // ────────────────────── findRowArray：无效输入 ──────────────────────

    @Test
    fun `无效输入一律返回 null 而不抛异常`() {
        assertNull(ZhengfangUtils.findRowArray(""))
        assertNull(ZhengfangUtils.findRowArray("   "))
        assertNull(ZhengfangUtils.findRowArray("""{"items":"oops"}"""))
        assertNull(ZhengfangUtils.findRowArray("""{"noItems":[]}"""))
        // 登录页（会话失效）—— 不能当成 JSON 解析
        assertNull(ZhengfangUtils.findRowArray("<html><body>用户登录</body></html>"))
        assertNull(ZhengfangUtils.findRowArray("{ broken json"))
    }

    // ────────────────────── flattenRow ──────────────────────

    @Test
    fun `折平后内层标量字段可读`() {
        val o = JsonParser.parseString(
            """{"kcyxb_id":"1","kcmc":"高等数学","detail":{"xf":"4","zxs":"64"}}"""
        ).asJsonObject
        val flat = ZhengfangUtils.flattenRow(o)
        // 外层字段保持
        assertEquals("1", ZhengfangUtils.jsonField(flat, "kcyxb_id"))
        // 内层字段折上来 —— 不做这一步，第二种部署的学分永远是空的
        assertEquals("4", ZhengfangUtils.jsonField(flat, "xf"))
        assertEquals("64", ZhengfangUtils.jsonField(flat, "zxs"))
    }

    @Test
    fun `折平时内层不覆盖外层同名字段`() {
        val o = JsonParser.parseString(
            """{"xf":"3","detail":{"xf":"9"}}"""
        ).asJsonObject
        val flat = ZhengfangUtils.flattenRow(o)
        assertEquals("外层值更接近数据源，应胜出", "3", ZhengfangUtils.jsonField(flat, "xf"))
    }

    @Test
    fun `折平时跳过内层数组避免造出假字段`() {
        val o = JsonParser.parseString(
            """{"kcmc":"x","list":[{"xf":"9"}]}"""
        ).asJsonObject
        val flat = ZhengfangUtils.flattenRow(o)
        assertEquals("x", ZhengfangUtils.jsonField(flat, "kcmc"))
        assertEquals("数组元素的字段不属于这一行，不应被折平进来", "", ZhengfangUtils.jsonField(flat, "xf"))
    }

    // ────────────────────── jsonField：既有语义回归 ──────────────────────

    @Test
    fun `jsonField 跳过空值与 null 并继续找下一个别名`() {
        val o = JsonParser.parseString(
            """{"a":null,"b":"","c":"ok"}"""
        ).asJsonObject
        assertEquals("ok", ZhengfangUtils.jsonField(o, "a", "b", "c"))
        assertEquals("", ZhengfangUtils.jsonField(o, "a", "b", "missing"))
    }

    @Test
    fun `jsonField 把数字原样转字符串`() {
        val o = JsonParser.parseString("""{"xf":4,"zxs":64.5}""").asJsonObject
        assertEquals("4", ZhengfangUtils.jsonField(o, "xf"))
        assertEquals("64.5", ZhengfangUtils.jsonField(o, "zxs"))
    }

    // ────────────────────── termLabel：幂等性回归 ──────────────────────

    @Test
    fun `termLabel 吃编码 序号 与已是名称三种写法且幂等`() {
        assertEquals("第1学期", ZhengfangUtils.termLabel("3"))
        assertEquals("第2学期", ZhengfangUtils.termLabel("12"))
        // 烟理这个部署的 XQMMC 是 1 / 2
        assertEquals("第1学期", ZhengfangUtils.termLabel("1"))
        assertEquals("第2学期", ZhengfangUtils.termLabel("2"))
        // ⚠️ 已经是名称时必须原样返回：否则会拼出「第第一学期学期」
        assertEquals("第一学期", ZhengfangUtils.termLabel("第一学期"))
        assertEquals("2025-2026学年第一学期", ZhengfangUtils.termLabel("2025-2026学年第一学期"))
        assertEquals("", ZhengfangUtils.termLabel(""))
        assertEquals("", ZhengfangUtils.termLabel("   "))
    }

    // ────────────────────── isSessionDead ──────────────────────

    @Test
    fun `会话失效判据覆盖各版本登录页特征`() {
        assertTrue(ZhengfangUtils.isSessionDead("""<form action="/xtgl/login_slogin.html">"""))
        assertTrue(ZhengfangUtils.isSessionDead("""<input id="userLogin" />"""))
        assertTrue(ZhengfangUtils.isSessionDead("""<input id="userPassword" />"""))
        assertTrue(ZhengfangUtils.isSessionDead("<div>用户登录</div>"))
        assertFalse(ZhengfangUtils.isSessionDead("""{"items":[{"kcmc":"高等数学"}]}"""))
    }
}
