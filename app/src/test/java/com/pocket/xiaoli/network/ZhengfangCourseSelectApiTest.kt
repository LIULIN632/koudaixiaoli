package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ZhengfangCourseSelectApi] 的**纯解析**测试（不联网）。
 *
 * 字段名与容器名不是猜的，来自用户从浏览器另存的真实页面：
 * - `参考/课程预选_files/xskcyx.js:12` → 列表端点 `kcyx_cxYxkcListIndex.html`
 * - `参考/课程预选_files/xskcyx.js:13-70` → `colModel` 的 `name` 即 JSON 键
 * - `参考/课程预选_files/jquery.jqgrid.settings.js:88` → `root: "items"`
 * - `参考/课程预选.html:438-452` → 请求体各隐藏域的真实形态
 *
 * ⚠️ 这些用例**证明不了教务明天不会改字段名**。改名的后果是"列表空但没报错"，
 * 所以另有 [ZhengfangCourseSelectDebug] 留原始响应兜底 —— 测试与诊断是两条互补的防线。
 */
class ZhengfangCourseSelectApiTest {

    private val api = ZhengfangCourseSelectApi(ZhengfangClient())

    /** 与 `colModel` 逐字对应的真实响应形态（jqGrid 包装 + items 数组） */
    private val realWorldJson = """{
        "currentPage": 1,
        "totalPage": 1,
        "totalResult": 2,
        "items": [
            {
                "kcyxb_id": "0A1B2C3D",
                "xsyxb_id": "0",
                "kch_id": "KCHID001",
                "kch": "B1234567",
                "tkcmc": "高等数学A",
                "kcmc": "高等数学A",
                "xfjdmc": "通识必修",
                "kclbmc": "通识必修课",
                "kcxzmc": "必修",
                "jsxm": "张三",
                "zcmc": "教授",
                "zxs": "64",
                "xsxxxx": "4",
                "xf": "4",
                "sxrsmc": "120/60",
                "sxrs": "120",
                "yxrs": "60"
            },
            {
                "kcyxb_id": "0A1B2C3E",
                "xsyxb_id": "9F8E7D6C",
                "kch": "B7654321",
                "kcmc": "大学英语（二）",
                "kclbmc": "通识必修课",
                "kcxzmc": "必修",
                "jsxm": "李四",
                "zcmc": "副教授",
                "zxs": "48",
                "xsxxxx": "3",
                "xf": "3",
                "sxrsmc": "100/98",
                "sxrs": "100",
                "yxrs": "98"
            }
        ]
    }"""

    // ────────────────────── 正常解析 ──────────────────────

    @Test
    fun `解析真实 jqGrid 响应并读出全部字段`() {
        val result = api.parseCourseSelectItems(realWorldJson)
        assertTrue("应标记为解析成功", result.parsed)
        assertEquals(2, result.totalResult)
        assertEquals(2, result.items.size)

        val first = result.items[0]
        assertEquals(
            "课程代码必须取 kch，不能取到 kch_id（内码）",
            "B1234567", first.kch,
        )
        assertEquals("高等数学A", first.kcmc)
        assertEquals("通识必修课", first.kclbmc)
        assertEquals("必修", first.kcxzmc)
        assertEquals("4", first.xf)
        assertEquals("64", first.zxs)
        assertEquals("4", first.xsxxxx)
        assertEquals("张三", first.jsxm)
        assertEquals("教授", first.zcmc)
        assertEquals("120", first.sxrs)
        assertEquals("120/60", first.sxrsmc)
        assertEquals("60", first.yxrs)
        assertEquals("0A1B2C3D", first.kcyxbId)
        assertEquals("0", first.xsyxbId)
    }

    @Test
    fun `xsyxb_id 为 0 表示未选中 非 0 表示已选中`() {
        val items = api.parseCourseSelectItems(realWorldJson).items
        // 预选页的原生口径：操作列用 xsyxb_id === "0" 判断该显示「预选」还是「已选」
        // （xskcyx.js:58-60）。这条判据直接影响按钮语义，必须有测试钉住。
        assertFalse("xsyxb_id=0 → 尚未选中", items[0].isChosen)
        assertTrue("xsyxb_id 非 0 → 已选中", items[1].isChosen)
    }

    @Test
    fun `空列表是解析成功而不是失败`() {
        val result = api.parseCourseSelectItems("""{"items":[],"totalResult":0}""")
        assertTrue("空容器仍算解析成功", result.parsed)
        assertEquals(0, result.items.size)
        assertEquals(0, result.totalResult)
    }

    @Test
    fun `停开状态与时间为空时不报错`() {
        val json = """{"items":[{"kcmc":"微积分","xf":"5"}]}"""
        val item = api.parseCourseSelectItems(json).items.single()
        assertEquals("微积分", item.kcmc)
        assertEquals("5", item.xf)
        assertEquals("", item.sftkzt)
        assertEquals("", item.sksj)
        assertEquals("", item.jxdd)
        assertEquals("", item.kch)
    }

    // ────────────────────── 字段别名与嵌套容错 ──────────────────────

    @Test
    fun `顶层字段名大小写不同也能读到`() {
        val json = """{"items":[{"KCMC":"高等数学","XF":"4","KCH":"B1"}]}"""
        val item = api.parseCourseSelectItems(json).items.single()
        assertEquals("高等数学", item.kcmc)
        assertEquals("4", item.xf)
        assertEquals("B1", item.kch)
    }

    @Test
    fun `嵌套在子对象里的字段由折平读出来`() {
        // 不同部署的嵌套深度不同；不做折平的话学分永远是空的，而界面看不出异常
        val json = """{"items":[{"kcmc":"数据结构","detail":{"xf":"3.5","zxs":"56"}}]}"""
        val item = api.parseCourseSelectItems(json).items.single()
        assertEquals("数据结构", item.kcmc)
        assertEquals("3.5", item.xf)
        assertEquals("56", item.zxs)
    }

    @Test
    fun `课名缺失的行被丢弃`() {
        // 没有课名的记录在界面上就是一行空白，对用户无意义
        val json = """{"items":[{"kch":"B1","xf":"4"},{"kcmc":"有效课程","xf":"2"}]}"""
        val items = api.parseCourseSelectItems(json).items
        assertEquals(1, items.size)
        assertEquals("有效课程", items[0].kcmc)
    }

    @Test
    fun `课名可回退到 tkcmc`() {
        val json = """{"items":[{"tkcmc":"临时课名","xf":"2"}]}"""
        assertEquals("临时课名", api.parseCourseSelectItems(json).items.single().kcmc)
    }

    // ────────────────────── 别名碰撞（真实踩过的坑） ──────────────────────

    @Test
    fun `kch_id 排在 kch 之前时不许污染课程代码`() {
        // ⚠️ 这条是本实现**真实出过一次**的缺陷，必须留着。
        //
        // `ZhengfangUtils.jsonField` 的别名表是**按 JSON 自身的键序**取第一个命中的，
        // 传给它的别名参数顺序**完全无效**（见该函数的 KDoc）。所以 `field(o, "kch", "kch_id")`
        // 并不表示"优先 kch"，而是"kch 和 kch_id 谁在 JSON 里靠前就用谁"。
        // 而真实响应里 `kch_id`（课程号ID 内码）恰好排在 `kch`（课程代码）之前 ——
        // 后果是界面上「课程代码」显示成一串 `KCHID001` 这样的内码。
        //
        // 修法：别名表只放真正的同义拼写，**不同字段绝不互相兜底**。
        val json = """{"items":[{"kch_id":"KCHID001","kch":"B1234567","kcmc":"高等数学"}]}"""
        val item = api.parseCourseSelectItems(json).items.single()
        assertEquals("B1234567", item.kch)
    }

    @Test
    fun `kcyxb_id 与 xsyxb_id 不互相兜底`() {
        // 同一类坑：选课行 ID 与学生预选 ID 是两个不同字段，
        // 混用会让"是否已选中"判错（isChosen），进而让按钮语义反掉。
        val json = """{"items":[{"kcyxb_id":"ROW1","kcmc":"高等数学"}]}"""
        val item = api.parseCourseSelectItems(json).items.single()
        assertEquals("ROW1", item.kcyxbId)
        assertEquals("只有 kcyxb_id 时必须是空 —— 不能拿行 ID 冒充预选 ID", "", item.xsyxbId)
        assertFalse("xsyxb_id 为空不算已选中", item.isChosen)
    }

    // ────────────────────── 已选端点口径 ──────────────────────

    @Test
    fun `已选端点的行一律视为已选`() {
        // `kcyx_cxYxkcChoosed` 的语义是"出现在这里 = 我选了它"，
        // 所以即使某行没带 xsyxb_id（部分部署会省字段），也必须判为已选 ——
        // 否则界面上"我已选的课"会被显示成"还没选"，这是会误导用户决策的错误。
        val json = """{"items":[{"kcmc":"高等数学","kch":"B1","xf":"4"}]}"""
        val fromChoosed = api.parseCourseSelectItems(json, fromChoosedEndpoint = true).items.single()
        assertTrue(fromChoosed.isChosen)

        val fromList = api.parseCourseSelectItems(json, fromChoosedEndpoint = false).items.single()
        assertFalse("同一个响应从列表端点来时应判为未选", fromList.isChosen)
    }

    // ────────────────────── 无效输入 ──────────────────────

    @Test
    fun `无效输入返回空且不抛异常`() {
        for (bad in listOf("", "   ", "<html>用户登录</html>", "{ broken", """{"items":"oops"}""")) {
            val result = api.parseCourseSelectItems(bad)
            assertEquals("输入「$bad」应得到空列表", 0, result.items.size)
            assertFalse("输入「$bad」不应标记为解析成功", result.parsed)
        }
    }

    // ────────────────────── 首页隐藏域解析 ──────────────────────

    /** 与 `课程预选.html` 同构的最小片段：属性顺序刻意不一致 */
    private val indexHtml = """
        <html><body>
        <form id="searchForm">
          <input type="hidden" name="xkxnm" value="2026" id="xkxnm">
          <input type="hidden" name="xkxqm" value="12" id="xkxqm">
          <input type="hidden" id="njdm_id" name="njdm_id" value="2024">
          <input type="hidden" id="zyh_id" value="w110104">
          <input type="hidden" id="jg_id" value="9008">
          <input type="hidden" id="bh_id" value="2024w11010401-2">
          <input type="hidden" id="zdxkrs" value="9999">
          <select id="kclbdm"><option value="">全部</option><option value="01" selected>通识</option></select>
          <input type="text" id="kcdmmc" value="">
          <input type="hidden" id="xxdm" value="13359">
        </form>
        </body></html>
    """.trimIndent()

    @Test
    fun `解析首页隐藏域`() {
        val fields = api.parseFormFields(indexHtml)
        // 属性顺序不同（id 在前 / value 在前）都要读到
        assertEquals("2026", fields["xkxnm"])
        assertEquals("12", fields["xkxqm"])
        assertEquals("2024", fields["njdm_id"])
        assertEquals("w110104", fields["zyh_id"])
        assertEquals("9008", fields["jg_id"])
        assertEquals("2024w11010401-2", fields["bh_id"])
        assertEquals("13359", fields["xxdm"])
        // select 取 selected 的 option
        assertEquals("01", fields["kclbdm"])
    }

    @Test
    fun `解析隐藏域失败时返回空表而不抛异常`() {
        assertTrue(api.parseFormFields("").isEmpty())
        assertTrue(api.parseFormFields("not html at all").isEmpty())
    }

    // ────────────────────── 请求体拼装 ──────────────────────

    @Test
    fun `请求体含 paramMap 的全部键与 jqGrid 分页参数`() {
        val body = api.buildQueryBody(
            mapOf("xkxnm" to "2026", "xkxqm" to "12", "njdm_id" to "2024")
        )
        // 学年学期必须原样带过去（这是"换学期就查不到"的关键）
        assertTrue(body.contains("xkxnm=2026"))
        assertTrue(body.contains("xkxqm=12"))
        assertTrue(body.contains("njdm_id=2024"))
        assertTrue(body.contains("doType=query"))
        // jqGrid 的 prmNames（jquery.jqgrid.settings.js:100-105）
        assertTrue(body.contains("queryModel.showCount="))
        assertTrue(body.contains("queryModel.currentPage=1"))
        assertTrue(body.contains("queryModel.sortOrder=asc"))
        assertTrue(body.contains("queryModel.sortName="))
    }

    @Test
    fun `请求体缺字段时置空而不是整键省略`() {
        // 页面就是这么发的：paramMap() 里每个键都在，值可能是空串。
        // 整键省略会让部分部署把它当成"该条件不存在"，与"该条件为空"语义不同。
        val body = api.buildQueryBody(emptyMap())
        for (key in listOf("xkxnm", "xkxqm", "kkbm_id", "kclbdm", "jsghxm", "sfkxk", "sfktk", "xbm", "xsbj")) {
            assertTrue("键 $key 必须出现（哪怕值为空）", body.contains("$key="))
        }
        // 但不应出现 "xkxnm=2026" 这种残留
        assertFalse(body.contains("xkxnm=2026"))
    }
}
