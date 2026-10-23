package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「修课情况」解析的回归门禁（2026-10 用户报障后补）。
 *
 * ## 报障原话与它对应的两个 bug
 *
 * > 「所有课程类型都显示差一学分，但是进度条显示正常」+「课程明细字段整列为空」
 *
 * 两条症状同源：`parseTypeStatistics` 的老正则把**组号取错了**。
 * 三个学分值是**非捕获**交替，组号其实是
 * `1=名称 2=要求 3=获得 4=未获得 5=showKc id`，而老代码按
 * `4=id`、`5=未获得学分` 取值，于是：
 *
 * | 界面/请求 | 老代码实际拿到的 |
 * |---|---|
 * | 「还差 X 学分」 | `showKc` 的数字后缀（`1` —— 看着像个正常学分数） |
 * | `xfyqjd_id`（查明细用） | 未获得学分（`0` / `0.0`） |
 *
 * 进度条用的是组 2/3，所以它一直是对的 —— **这正是"进度条正常但还差学分不对"这个组合的来历**。
 *
 * ⚠️ 这两条**都不会让代码报错**，所以下面的断言必须落在**字段值**上：
 * 只断 `types.size` 或"能解析出类型"是抓不到的（老代码照样返回 2 个类型，
 * 只是 id 与"还差"都错了）。
 *
 * ## 另外两组守卫
 *
 * - 明细行：正方同一份数据在不同部署里嵌套深度不同（`{"kcmc":…,"detail":{"xf":…}}`），
 *   只按顶层取 ⇒ **学分/绩点/学期整列为空**，而界面只是"少几个字"。
 *   现在走 `ZhengfangUtils.findRowArray` + `flattenRow`，用这里的用例钉住；
 * - 脏值：正方会写 `无` / `-`，不归一就会显示「学分 无」（zfn_api 的 `align_floats` 同因）。
 */
class ZhengfangAcademicApiTest {

    private val api = ZhengfangAcademicApi(ZhengfangClient())

    // ── 类型学分块：组号错位（线上事故） ──

    @Test
    fun `类型学分块不吃错组号 —— id 是 showKc 后缀、还差是未获得学分`() {
        val html = """
            <div class="panel">
              <div class="panel-heading">"通识必修"&nbsp;要求学分：10.0&nbsp;&nbsp;获得学分：10.0&nbsp;&nbsp;未获得学分：0.0
                <span id='showKc101'>点击查看</span>
              </div>
              <div class="panel-heading">"专业必修"&nbsp;要求学分：30.0&nbsp;&nbsp;获得学分：24.0&nbsp;&nbsp;未获得学分：6.0
                <span id='showKc102'>点击查看</span>
              </div>
            </div>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(listOf("通识必修", "专业必修"), types.map { it.name })
        // 老代码这里会是 ["0.0", "6.0"]（未获得学分）
        assertEquals(listOf("101", "102"), types.map { it.id })
        assertEquals(listOf("10.0", "30.0"), types.map { it.requiredCredit })
        assertEquals(listOf("10.0", "24.0"), types.map { it.earnedCredit })
        // 老代码这里会是 ["101", "102"]（showKc 后缀）⇒ 界面显示「还差 101 学分」
        assertEquals(listOf("0.0", "6.0"), types.map { it.missedCredit }) // 第一个类型未获得学分是 0（保留），第二个是 6.0
    }

    @Test
    fun `类型学分块 0 是保留值 无才是缺失`() {
        val html = """
            <div>
              <p>"通识选修"&nbsp;要求学分：8.0&nbsp;获得学分：8.0&nbsp;未获得学分：0<span id='showKc7'></span></p>
              <p>"素拓"&nbsp;要求学分：无&nbsp;获得学分：无&nbsp;未获得学分：无<span id='showKc8'></span></p>
            </div>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(2, types.size)
        // `0` 是**真值**（保留），`无` 才是"学校没给"（丢空）——两者在界面上文案不同
        assertEquals("0", types[0].missedCredit)
        assertEquals("", types[1].missedCredit)
        assertEquals("", types[1].requiredCredit)
        // 进度条要的还是「要求/获得」这两档
        assertEquals("8.0", types[0].requiredCredit)
    }

    // ── 共用一个大容器的部署：每个类型各读各的 ──

    @Test
    fun `类型学分块 共用容器时不串到上一个类型`() {
        val html = """
            <div id="tabContent">
              "通识必修"&nbsp;要求学分：10.0&nbsp;获得学分：8.0&nbsp;未获得学分：2.0
              <span id='showKc201'>查看</span>
              "专业必修"&nbsp;要求学分：40.0&nbsp;获得学分：40.0&nbsp;未获得学分：0.0
              <span id='showKc202'>查看</span>
            </div>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(2, types.size)
        assertEquals("2.0", types[0].missedCredit)
        assertEquals("0.0", types[1].missedCredit) // 0 保留（见 normalizeCredit）；这条测的是"不串门"
        // 若整块取文本，两个类型会同时读到第一个「未获得学分：2.0」
        assertEquals(listOf("10.0", "40.0"), types.map { it.requiredCredit })
        assertEquals(listOf("8.0", "40.0"), types.map { it.earnedCredit })
    }

    @Test
    fun `类型学分块 培养方案根节点不当成课程类型`() {
        val html = """
            <div>
              "2023级软件工程"&nbsp;要求学分：160.0&nbsp;获得学分：90.0&nbsp;未获得学分：70.0
              <span id='showKc1'></span>
              "专业必修"&nbsp;要求学分：40.0&nbsp;获得学分：40.0&nbsp;未获得学分：0.0
              <span id='showKc2'></span>
            </div>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(listOf("专业必修"), types.map { it.name })
    }

    // ── 正则兜底：同一份 HTML 的两条通路都要不吃错组号 ──

    @Test
    fun `正则兜底同样不吃错组号`() {
        val html = """<div>"通识必修"&nbsp;要求学分：10.0&nbsp;获得学分：9.0&nbsp;未获得学分：1.0
            <span id='showKc9'></span></div>"""

        val types = api.parseTypesByRegex(html)

        assertEquals(1, types.size)
        assertEquals("通识必修", types[0].name)
        assertEquals("9", types[0].id)
        assertEquals("10.0", types[0].requiredCredit)
        assertEquals("9.0", types[0].earnedCredit)
        assertEquals("1.0", types[0].missedCredit)
    }

    @Test
    fun `解析不出内容时返回空列表而不是抛异常`() {
        assertTrue(api.parseTypeStatistics("").isEmpty())
        assertTrue(api.parseTypeStatistics("<html><body>用户登录</body></html>").isEmpty())
    }

    // ── 第二行：门次 / 课程门数（2026-10 用户贴出的真实页面形状） ──

    @Test
    fun `门次与课程门数按真实页面解析`() {
        // 摘自用户学校的实际页面（思想政治类课程整块）
        val html = """
            <div class="panel">
              <div class="panel-heading">"思想政治类课程"&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0
                <span id='showKc11'>查看</span>
              </div>
              <div class="panel-body">
                要求门次:14 到达要求:12  未达到要求:2 共（14）门 通过（12）门
              </div>
            </div>
        """.trimIndent()

        val type = api.parseTypeStatistics(html).single()

        assertEquals("思想政治类课程", type.name)
        assertEquals("11", type.id)
        assertEquals("18.0", type.requiredCredit)
        assertEquals("1.0", type.missedCredit)
        assertEquals("14", type.requiredCount)
        assertEquals("12", type.reachedCount)
        assertEquals("2", type.missedCount)
        assertEquals("14", type.totalCourses)
        assertEquals("12", type.passedCourses)
    }

    @Test
    fun `门次为 0 时是结论而不是缺数据`() {
        val html = """
            <div>
              语言与工具类课程&nbsp;要求学分:16.0&nbsp;获得学分:16.0&nbsp;未获得学分:0.0
              要求门次:6 到达要求:6  未达到要求:0 共（7）门 通过（6）门
              <span id='showKc12'></span>
            </div>
        """.trimIndent()

        val type = api.parseTypeStatistics(html).single()

        // ⚠️ 学分这一档的 0 **保留为 "0.0"**（2026-10 改）：它是学校给的结论，
        // 而且「获得学分 0.0」要用来算进度条（丢掉 ⇒ 整条不画，用户报过"零分模块没有进度条"）。
        // "要不要显示「还差」"是**界面**的事，按数值判 —— 守门人：
        // `AcademicQueryPrivacyTest.修满的模块不显示还差 0 学分`。
        assertEquals("0.0", type.missedCredit)
        assertEquals("0", type.missedCount)
        assertEquals("6", type.requiredCount)
        assertEquals("6", type.reachedCount)
    }

    @Test
    fun `没有门次行的类型不编造门次`() {
        // 用户页面里的「其他选修课程」：只有 共（2）门 通过（2）门，没有要求门次
        val html = """
            <div>
              其他选修课程&nbsp;要求学分:0.0&nbsp;获得学分:4.0&nbsp;未获得学分:0.0 共（2）门 通过（2）门
              <span id='showKc13'></span>
            </div>
        """.trimIndent()

        val type = api.parseTypeStatistics(html).single()

        assertEquals("", type.requiredCount)
        assertEquals("", type.reachedCount)
        assertEquals("", type.missedCount)
        assertEquals("2", type.totalCourses)
        assertEquals("2", type.passedCourses)
    }

    @Test
    fun `门次行排在查看链接之后也能读到且不吃下一类型`() {
        val html = """
            <div id="tabContent">
              思想政治类课程&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0
              <span id='showKc21'>查看</span>
              要求门次:14 到达要求:12  未达到要求:2 共（14）门 通过（12）门
              语言与工具类课程&nbsp;要求学分:16.0&nbsp;获得学分:16.0&nbsp;未获得学分:0.0
              <span id='showKc22'>查看</span>
              要求门次:6 到达要求:6  未达到要求:0 共（7）门 通过（6）门
            </div>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(listOf("思想政治类课程", "语言与工具类课程"), types.map { it.name })
        assertEquals(listOf("14", "6"), types.map { it.requiredCount })
        assertEquals(listOf("2", "0"), types.map { it.missedCount })
    }

    // ── 两种排布：学分在前 / showKc 在前（后者是 2026-10 线上"整页空白"的元凶） ──

    @Test
    fun `span 排在学分前面也能解析 —— 线上就是这么排的`() {
        // ⚠️ 这一版就是被它打脸的：老实现要求「学分文字在 span 之前」，
        // 而这个页面的点击标记是**空 span 且在标题前面**，于是一个类型都认不出来 ⇒ 整页空白。
        val html = """
            <div class="panel">
              <div class="panel-heading">
                <span id='showKc11'></span>思想政治类课程&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0
                要求门次:14 到达要求:12  未达到要求:2 共（14）门 通过（12）门
              </div>
            </div>
            <div class="panel">
              <div class="panel-heading">
                <span id='showKc12'></span>语言与工具类课程&nbsp;要求学分:16.0&nbsp;获得学分:16.0&nbsp;未获得学分:0.0
                要求门次:6 到达要求:6  未达到要求:0 共（7）门 通过（6）门
              </div>
            </div>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(listOf("思想政治类课程", "语言与工具类课程"), types.map { it.name })
        assertEquals(listOf("11", "12"), types.map { it.id })
        assertEquals(listOf("18.0", "16.0"), types.map { it.requiredCredit })
        assertEquals(listOf("17.0", "16.0"), types.map { it.earnedCredit })
        assertEquals(listOf("1.0", "0.0"), types.map { it.missedCredit }) // 0 现在保留（见 normalizeCredit）
        assertEquals(listOf("14", "6"), types.map { it.requiredCount })
        assertEquals(listOf("2", "0"), types.map { it.missedCount })
        assertEquals(listOf("12", "6"), types.map { it.passedCourses })
    }

    @Test
    fun `学分排在 span 后面时不会把下一个类型的读成自己的`() {
        // 学分在 span 之后：下一个 span 离上一个类型的学分也很近，
        // 所以"各自挑最近的"会挑错 —— 必须保持文档顺序（单调配对）。
        val html = """
            <div id="tabContent">
              <span id='showKc31'></span>
              思想政治类课程&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0
              <span id='showKc32'></span>
              语言与工具类课程&nbsp;要求学分:16.0&nbsp;获得学分:16.0&nbsp;未获得学分:0.0
            </div>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(listOf("思想政治类课程", "语言与工具类课程"), types.map { it.name })
        assertEquals(listOf("31", "32"), types.map { it.id })
        assertEquals(listOf("18.0", "16.0"), types.map { it.requiredCredit })
        assertEquals(listOf("1.0", "0.0"), types.map { it.missedCredit }) // 0 现在保留（见 normalizeCredit）
    }

    @Test
    fun `学分排在 span 前面（老的排布）依然能解析`() {
        val html = """
            <div id="tabContent">
              思想政治类课程&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0
              <span id='showKc41'></span>
              语言与工具类课程&nbsp;要求学分:16.0&nbsp;获得学分:16.0&nbsp;未获得学分:0.0
              <span id='showKc42'></span>
            </div>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(listOf("思想政治类课程", "语言与工具类课程"), types.map { it.name })
        assertEquals(listOf("41", "42"), types.map { it.id })
        assertEquals(listOf("17.0", "16.0"), types.map { it.earnedCredit })
        // 第一个类型真缺 1 学分；第二个是 0.0，归一成空（界面据此不显示"还差"）
        assertEquals(listOf("1.0", "0.0"), types.map { it.missedCredit }) // 0 现在保留（见 normalizeCredit）
    }

    @Test
    fun `正则兜底也认 span 在前的排布`() {
        // 结构解析全挂时（例如 showKc 不在 body 里），兜底正则要能顶上来
        val html = """<div><span id='showKc51'></span>"思想政治类课程"&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0</div>"""

        val types = api.parseTypesByRegex(html)

        assertEquals(1, types.size)
        assertEquals("思想政治类课程", types[0].name)
        assertEquals("51", types[0].id)
        assertEquals("18.0", types[0].requiredCredit)
        assertEquals("17.0", types[0].earnedCredit)
        assertEquals("1.0", types[0].missedCredit)
    }

    // ── 名字取自 DOM 兄弟节点：句柄文字夹在名字与学分之间时也不许重名 ──

    @Test
    fun `句柄文字夹在名字和学分之间时 12 个类型一个都不能少`() {
        // 2026-10 线上事故：`查看` 夹在名字与学分之间 ⇒ 只按"学分前面那串文字"取名，
        // 每个类型都被取成「查看」，再去重 ⇒ **12 个类型只剩 1 个**。
        val rows = (1..12).joinToString("\n") { i ->
            """
            <tr>
              <td>类型${i}课程</td>
              <td><span id='showKc${100 + i}'>查看</span></td>
              <td>要求学分:${i}.0&nbsp;获得学分:${i}.0&nbsp;未获得学分:0.0</td>
            </tr>
            """.trimIndent()
        }
        val html = "<table>$rows</table>"

        val (types, diag) = api.parseTypesDetailed(html)

        assertEquals(12, types.size)
        assertEquals(listOf("类型1课程", "类型2课程", "类型12课程"), listOf(types[0].name, types[1].name, types[11].name))
        assertEquals("101", types[0].id)
        assertEquals(12, diag.kept)
        // 自检串正常时不该报可疑（这四个数字就是"页面上要不要提示解析异常"的判据）
        assertTrue(!diag.suspicious)
    }

    @Test
    fun `名字撞车时用 id 区分 绝不丢类型`() {
        // 两个类型都取不出名字（前面只有数字），也不能只留一个
        val html = """
            <div>
              <p>1&nbsp;<span id='showKc71'></span>要求学分:10.0&nbsp;获得学分:10.0&nbsp;未获得学分:0.0</p>
              <p>2&nbsp;<span id='showKc72'></span>要求学分:10.0&nbsp;获得学分:10.0&nbsp;未获得学分:0.0</p>
            </div>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(2, types.size)
        assertEquals(listOf("类型 71", "类型 72"), types.map { it.name })
        assertEquals(listOf("71", "72"), types.map { it.id })
    }

    @Test
    fun `类型少列时自检标记可疑`() {
        // 3 个句柄但只有一个学分栏 ⇒ 配对只能成 1 对，自检要报出来（界面据此提示）
        val html = """
            <div>
              思想政治类课程&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0
              <span id='showKc81'></span><span id='showKc82'></span><span id='showKc83'></span>
            </div>
        """.trimIndent()

        val (types, diag) = api.parseTypesDetailed(html)

        assertEquals(1, types.size)
        assertEquals(1, diag.anchors)
        assertEquals(3, diag.handles)
        assertTrue("少列了类型就该标可疑", diag.suspicious)
        assertTrue(diag.summary().contains("锚点 1"))
    }

    // ── 类型块不在正文里（JS 渲染）：正文一个、脚本里十二个 ──

    @Test
    fun `类型块藏在脚本里时 正文只看到一个也要拿全`() {
        // 2026-10 线上第三次事故：页面正文只含"当前选中那个类型"，
        // 其余类型的 HTML 在 <script> 的字符串里 ⇒ 只扫正文就只剩 1 个类型，
        // 而它"看起来完全正常"，所以自检也不报警。
        val html = """
            <html><body>
              <div>思想政治类课程&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0</div>
              <span id='showKc1'></span>
              <script>
                var rows = "<div><span id='showKc11'></span>思想政治类课程&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0</div>"
                  + "<div><span id='showKc12'></span>语言与工具类课程&nbsp;要求学分:16.0&nbsp;获得学分:16.0&nbsp;未获得学分:0.0</div>"
                  + "<div><span id='showKc13'></span>身心健康类课程&nbsp;要求学分:5.5&nbsp;获得学分:1.5&nbsp;未获得学分:4.0</div>";
              </script>
            </body></html>
        """.trimIndent()

        val (types, diag) = api.parseTypesDetailed(html)

        assertEquals(listOf("思想政治类课程", "语言与工具类课程", "身心健康类课程"), types.map { it.name })
        assertEquals(listOf("11", "12", "13"), types.map { it.id })
        assertEquals(listOf("1.0", "0.0", "4.0"), types.map { it.missedCredit })
        assertEquals("内嵌", diag.source)
    }

    @Test
    fun `类型块藏在 HTML 注释里也能拿到`() {
        val html = """
            <html><body>
              <span id='showKc1'></span>
              <!-- <div><span id='showKc21'></span>思想政治类课程&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0</div>
                   <div><span id='showKc22'></span>语言与工具类课程&nbsp;要求学分:16.0&nbsp;获得学分:16.0&nbsp;未获得学分:0.0</div> -->
            </body></html>
        """.trimIndent()

        val (types, diag) = api.parseTypesDetailed(html)

        assertEquals(listOf("思想政治类课程", "语言与工具类课程"), types.map { it.name })
        assertEquals(listOf("21", "22"), types.map { it.id })
        assertEquals("内嵌", diag.source)
    }

    @Test
    fun `正文齐全时优先用正文（字段最全）`() {
        val html = """
            <html><body>
              <div><span id='showKc31'></span>通识必修&nbsp;要求学分:10.0&nbsp;获得学分:9.0&nbsp;未获得学分:1.0
                要求门次:4 到达要求:3 未达到要求:1 共（4）门 通过（3）门</div>
            </body></html>
        """.trimIndent()

        val (types, diag) = api.parseTypesDetailed(html)

        assertEquals("正文", diag.source)
        assertEquals("4", types.single().requiredCount)
        assertEquals("1", types.single().missedCount)
    }

    @Test
    fun `诊断文本带上了原始响应的关键原文`() {
        val html = """
            <html><body>
              <div><span id='showKc11'>查看</span>思想政治类课程&nbsp;要求学分:18.0&nbsp;获得学分:17.0&nbsp;未获得学分:1.0</div>
            </body></html>
        """.trimIndent()

        val (_, diag) = api.parseTypesDetailed(html)
        val probe = api.probeOf(html, diag)

        // 自检 + 原始计数 + 两个关键位置的原文 —— 用户一键复制这段就能远程定位
        assertTrue(probe.contains("留下 1"))
        assertTrue(probe.contains("要求学分×1"))
        assertTrue(probe.contains("showKc×1"))
        assertTrue(probe.contains("【「showKc」附近】"))
        assertTrue(probe.contains("思想政治类课程"))
    }

    // ── 烟理这个部署的真实形状：中文只在 JS 注释里 + 句柄是 GUID + 学分在属性上 ──

    @Test
    fun `JS 模板里的培养方案树能解析出来（线上真实形状）`() {
        // 摘自用户贴回的原始响应（2026-10）：中文标签是 i18n 调用、中文只在块注释里、
        // 句柄 id 是 32 位 GUID、学分在 <p> 的属性上。
        // —— 这个形状下"按中文标签找锚点"与"要求 id 是数字"两条常识同时不成立。
        val dollar = "$"
        val js = """
            var s = "<p id='p1CB4DBE319D21153E0637902A8C0D010' yxxf='95.0' yqzdxf='170' sftg='0'>" + "主修&nbsp;"
              + "$dollar.i18n.get('yqxf')/* 要求学分 */ + ":170.0&nbsp;"
              + "$dollar.i18n.get('hdxf')/* 获得学分 */ + ":95.0&nbsp;&nbsp;"
              + "$dollar.i18n.get('whdxf')/* 未获得学分 */ + ":75.0&nbsp;"
              + "<span id='showKc1CB4DBE319D21153E0637902A8C0D010'></span>"
              + "<span id ='thzt1CB4DBE319D21153E0637902A8C0D010' value='0,0,0'></span>"
              + "<p id='pAAAABBBBCCCCDDDDEEEEFFFF00001111' yxxf='17.0' yqzdxf='18.0' sftg='0'>" + "思想政治类课程&nbsp;"
              + "$dollar.i18n.get('yqxf')/* 要求学分 */ + ":18.0&nbsp;"
              + "$dollar.i18n.get('hdxf')/* 获得学分 */ + ":17.0&nbsp;&nbsp;"
              + "$dollar.i18n.get('whdxf')/* 未获得学分 */ + ":1.0&nbsp;"
              + "$dollar.i18n.get('yqmc')/* 要求门次 */ + ":14"
              + "$dollar.i18n.get('ddyd')/* 到达要求 */ + ":12"
              + "$dollar.i18n.get('wddyq')/* 未达到要求 */ + ":2"
              + "<span id='showKcAAAABBBBCCCCDDDDEEEEFFFF00001111'></span>";
        """.trimIndent()
        val html = "<html><body><script>$js</script></body></html>"

        val (types, diag) = api.parseTypesDetailed(html)

        assertEquals(listOf("主修", "思想政治类课程"), types.map { it.name })
        assertEquals(
            listOf("1CB4DBE319D21153E0637902A8C0D010", "AAAABBBBCCCCDDDDEEEEFFFF00001111"),
            types.map { it.id }
        )
        assertEquals(listOf("170", "18.0"), types.map { it.requiredCredit })
        assertEquals(listOf("95.0", "17.0"), types.map { it.earnedCredit })
        assertEquals(listOf("75.0", "1.0"), types.map { it.missedCredit })
        assertEquals(listOf("", "14"), types.map { it.requiredCount })
        assertEquals(listOf("", "12"), types.map { it.reachedCount })
        assertEquals(listOf("", "2"), types.map { it.missedCount })
        assertEquals("JS模板", diag.source)
    }

    @Test
    fun `GUID 句柄不被当成非法 id`() {
        assertTrue(api.isIdLike("1CB4DBE319D21153E0637902A8C0D010"))
        assertTrue(api.isIdLike("101"))
        assertTrue(api.isIdLike("abc-def_123"))
        assertTrue(!api.isIdLike(""))
        assertTrue(!api.isIdLike("1.0")) // 老事故的现场值：未获得学分被当成 id 传出去
        assertTrue(!api.isIdLike("２０２３")) // 全角数字：Char.isDigit() 会放进来，这里必须挡住
    }

    @Test
    fun `根节点用来算总进度而不是把两级加起来`() {
        val types = listOf(
            AcademicTypeInfo(name = "主修", id = "root", requiredCredit = "170", earnedCredit = "95"),
            AcademicTypeInfo(name = "通识必修", id = "a", requiredCredit = "45.5", earnedCredit = "36.5"),
            AcademicTypeInfo(name = "专业核心", id = "b", requiredCredit = "19", earnedCredit = "0"),
        )

        val root = api.rootTypeOf(types)

        assertEquals("root", root?.id)
        assertEquals("170", root?.requiredCredit)
    }

    @Test
    fun `真实响应的形状：名字贴在标签后、同一 GUID 多分支重复`() {
        // 摘自用户学校真实响应（1.2 MB / 325 个 <p> 块 / 25 个句柄）：
        // ① 名字就在 `<p …>` 标签结束后的同一个 JS 字符串里（`>主修"`）；
        // ② 同一个 GUID 会被多个"按学校代码分支"的 JS 各写一遍，**其中有些分支那一截里没有名字** ——
        //    只取其中一次（associateBy 留最后一次）就会整条路 0 命中。
        val g = "1CB4DBE319D21153E0637902A8C0D010"
        val g2 = "1CB4DBE319E11153E0637902A8C0D011"
        val html = """
            <html><body><script>
            if (xxdm == '11483') {
              $("<li id='li$g' class='first start' fxfyqjd_id=''>" +
                "<div class='title' data-content=''" +
                " xfyqjd_id='$g' jdkcsx='' leaf='' sfmjd='' ><p class='title1' id='p$g' yxxf='95.0' yqzdxf='170' sftg='0'>主修" +
                "</span><span id='showKc$g'></span></p><i id='thzt$g' value='0,0,0'></i></div>").appendTo($("#ul1"));
            } else {
              $("<li id='li$g' class='first start' fxfqjd_id=''>" +
                "<p class='title1' id='p$g'>" +
                "<span id='showKc$g'></span></p></li>").appendTo($("#ul2"));
            }
            $("<li id='li$g2'><p class='title1' id='p$g2' yxxf='17.0' yqzdxf='18' sftg='0'>思想政治类课程" +
              "</span><span id='showKc$g2'></span></p></li>").appendTo($("#li$g"));
            </script></body></html>
        """.trimIndent()

        val (types, diag) = api.parseTypesDetailed(html)

        assertEquals(listOf("主修", "思想政治类课程"), types.map { it.name })
        assertEquals(listOf(g, g2), types.map { it.id })
        assertEquals(listOf("170", "18"), types.map { it.requiredCredit })
        assertEquals(listOf("95.0", "17.0"), types.map { it.earnedCredit })
        assertEquals("JS模板", diag.source)
    }

    @Test
    fun `占位分支不当成课程类型`() {
        // `showKcqkxfyq` 这类短 id + 学分全空 = 给别的学校代码准备的占位分支
        val html = """
            <html><body><script>
            $("<p class='title1' id='pZZZ'>其他课程<span id='showKcqkxfyq'></span></p>");
            $("<p class='title1' id='p1CB4DBE319D21153E0637902A8C0D010' yxxf='95.0' yqzdxf='170'>主修" +
              "<span id='showKc1CB4DBE319D21153E0637902A8C0D010'></span></p>");
            </script></body></html>
        """.trimIndent()

        val types = api.parseTypeStatistics(html)

        assertEquals(listOf("主修"), types.map { it.name })
    }

    @Test
    fun `层级与同名限定按真实响应的 appendTo 关系算`() {
        // 父子关系写在 JS 里：子节点语句最后 `.appendTo($("#li{父GUID}"))`。
        // 同名节点真的存在（通识必修 / 通识选修下各一个「身心健康类课程」）——
        // 这时用父名限定，而不是塞一串 GUID 到名字里。
        val root = "1CB4DBE319D21153E0637902A8C0D010"
        val module = "1CB4DBE319D41153E0637902A8C0D011"
        val leafA = "1CB4DBE319E11153E0637902A8C0D012"
        val leafB = "1CB4DBE319FD1153E0637902A8C0D013"
        fun node(guid: String, name: String, required: String, earned: String, parent: String) = """
            $("<li id='li$guid'><p class='title1' id='p$guid' yxxf='$earned' yqzdxf='$required'>$name" +
              "<span id='showKc$guid'></span></p></li>").appendTo($("$parent"));
        """.trimIndent()
        val html = "<html><body><script>" +
            // 顶层节点 appendTo 的是 `#ul…`（不是 `#li…`）—— 这正是"没有父节点"的判据
            node(root, "主修", "170", "95.0", "#ul202408501222start") +
            node(module, "通识必修课程模块", "45.5", "39.5", "#li$root") +
            node(leafA, "身心健康类课程", "5.5", "1.5", "#li$module") +
            node(leafB, "身心健康类课程", "2", "2.0", "#li$module") +
            "</script></body></html>"

        val (types, diag) = api.parseTypesDetailed(html)

        assertEquals(listOf("主修", "通识必修课程模块", "身心健康类课程", "通识必修课程模块 · 身心健康类课程"), types.map { it.name })
        assertEquals(listOf(0, 1, 2, 2), types.map { it.depth })
        assertEquals(listOf("", root, module, module), types.map { it.parentId })
        assertEquals(1, diag.renamed)
        // 一两个同名是**正常**的（真·同名节点），不该报"解析异常"（v0.1.15 实测误报过）
        assertTrue("一两个重名不算异常", !diag.suspicious)
    }

    @Test
    fun `学时补考重修只认窄别名 取不到就空着`() {
        // 值宁可空着也不许猜错：`XS` 这种两字母通用名在别的字段上也可能出现
        val json = """[{"KCMC":"运筹学A1","XF":"3.0","MAXCJ":"55","CJ":"51",
            "KCXS":"理论(3.0)","BKCJ":"55","CXCJ":"62","JD":"0"}]"""

        val item = api.parseCourseItems(json).single()

        assertEquals("理论(3.0)", item.hours)
        assertEquals("55", item.makeupScore)
        assertEquals("62", item.retakeScore)
    }

    @Test
    fun `明细样例把第一条记录摊成键值对`() {
        // 「学时 / 补考 / 重修 / 建议修读学年学期」的字段名只能从真实响应读出来，
        // 所以诊断里必须带上这一段（键名 + 值），否则只能靠猜别名。
        val raw = """{"items":[{"KCMC":"工程管理导论","KCXS":"理论(2.0)","XF":"1.0",
            "detail":{"BKCJ":"55","JYXDXNM":"2024-2025"}}]}"""

        val sample = api.courseSampleOf(raw)

        assertTrue(sample.contains("KCMC=工程管理导论"))
        assertTrue(sample.contains("KCXS=理论(2.0)"))
        // 嵌套字段也要摊出来（flattenRow）
        assertTrue(sample.contains("BKCJ=55"))
        assertTrue(sample.contains("JYXDXNM=2024-2025"))
    }

    @Test
    fun `明细样例遇到非JSON响应不炸`() {
        assertTrue(api.courseSampleOf("<html>login</html>").contains("login"))
    }

    @Test
    fun `明细样例挑字段最全的一条 空数组不冒充样例`() {
        // 用户实测过的一个坑：容器节点（主修/平台/模块）的明细就是 `[]`，
        // 而它们排在列表最前面 —— 抓"第一条非空响应"就会把 `[]` 当样例发出去。
        assertEquals("[]", api.courseSampleOf("[]"))

        // 同一次响应里，首条是空课（各列都空），要挑字段最全的那条，才看得到要找的键
        val raw = """{"items":[
            {"KCMC":"空课","XF":""},
            {"KCMC":"工程管理导论","KCXS":"理论(2.0)","XF":"1.0","BKCJ":"55","CXCJ":"62",
             "detail":{"JYXDXNM":"2024-2025","JYXDXQMC":"1"}}
        ]}"""

        val sample = api.courseSampleOf(raw)

        assertTrue(sample.contains("KCMC=工程管理导论"))
        assertTrue(sample.contains("KCXS=理论(2.0)"))
        assertTrue(sample.contains("BKCJ=55"))
        assertTrue(sample.contains("CXCJ=62"))
        assertTrue(sample.contains("JYXDXNM=2024-2025"))
    }

    @Test
    fun `真实明细记录 —— 实际修读学年学期与建议修读必须分开`() {
        // 摘自 2026-10 用户「复制诊断」里的【明细样例】（一条真实记录，键名逐字照抄）。
        // ⚠️ 这条钉的是一个"看起来完全正常"的错误：老代码读 JYXDXNM/JYXDXQMC（建议修读），
        // 于是卡片上显示的是「学校建议我什么时候修」，而不是「我实际什么时候修的」。
        val json = """[{
            "KCH_ID":"1ABD451649041325E0637902A8C0","KCMC":"思想道德修养与法律基础",
            "XDZT":"4","XNM":"2024","KCGSMC":"理论课","KCLBDM":"1","JYXDXNM":"2024",
            "ZYZGKCBJ":"是","KCYWMC":"Ideological and Moral Cultiv","XSXXXX":"理论(2.0)",
            "KCXZMC":"必修","XNMC":"2024-2025","CJ":"83","KCLBMC":"本科","XF":"2.0","MAXCJ":"83",
            "KCH":"1211001011","KCZT":"1","XQMMC":"1","JYXDXNMC":"2024-2025","XQM":"3",
            "SFJHKC":"是","JYXDXQMC":"1","JD":"3.3","KCZYXXS":"1","JYXDXQM":"3"
        }]"""

        val item = api.parseCourseItems(json, "思想政治类课程", "GUID").single()

        assertEquals("思想道德修养与法律基础", item.courseName)
        // 学时（教务页那列写的是「理论(2.0)」）
        assertEquals("理论(2.0)", item.hours)
        // 实际修读 = XNMC + XQMMC
        assertEquals("2024-2025 第1学期", item.term)
        // 建议修读 = JYXDXNMC + JYXDXQMC
        assertEquals("2024-2025 第1学期", item.suggestedTerm)
        // 修读状态码 4 → 已修（码表抄自学校自己的 getXdzt()，不是猜的）
        assertEquals("已修", item.status)
        // 分组要用 id
        assertEquals("GUID", item.typeId)
    }

    @Test
    fun `实际与建议不同的记录 两句都要能读出来`() {
        // 未修课程：只有建议，没有实际 —— 这正是"还差哪些课、什么时候该修"的场景
        val json = """[{"KCMC":"工程项目管理","XF":"2.0","JYXDXNMC":"2026-2027","JYXDXQMC":"1",
            "JYXDXNM":"2026","JYXDXQM":"3"}]"""

        val item = api.parseCourseItems(json).single()

        assertEquals("", item.term)
        assertEquals("2026-2027 第1学期", item.suggestedTerm)
    }

    @Test
    fun `学期标签是幂等的 已经是名称就原样返回`() {
        // 老实现无条件套「第${x}学期」，遇到「第1学期」这种已经是名称的值会拼出「第第1学期学期」
        assertEquals("第1学期", ZhengfangUtils.termLabel("3"))
        assertEquals("第1学期", ZhengfangUtils.termLabel("1"))
        assertEquals("第2学期", ZhengfangUtils.termLabel("12"))
        assertEquals("第2学期", ZhengfangUtils.termLabel("2"))
        // 已经是名称：原样返回（不加工、也不二次套壳）
        assertEquals("第1学期", ZhengfangUtils.termLabel("第1学期"))
        assertEquals("第一学期", ZhengfangUtils.termLabel("第一学期"))
        assertEquals("", ZhengfangUtils.termLabel("  "))
    }

    @Test
    fun `修读状态码按学校的码表翻译 认不出就不显示`() {
        // 码表来源：学校自己的 `cxXsxyqkIndex.js` 的 `getXdzt()` + `N105515_zh_CN.js`
        fun status(xdzt: String, maxcj: String = ""): String =
            api.parseCourseItems("""[{"KCMC":"课","XDZT":"$xdzt","MAXCJ":"$maxcj"}]""").single().status

        assertEquals("在修", status("1"))
        assertEquals("未过", status("2"))
        assertEquals("未修", status("3"))
        assertEquals("已修", status("4"))
        assertEquals("已修", status("21"))
        assertEquals("校内被替代课程", status("5"))
        assertEquals("学业预警不审核课程", status("10"))
        // 最大成绩"未开放"按学校代码也算在修
        assertEquals("在修", status("3", "未开放"))
        // 认不出的码：宁可空着也不编
        assertEquals("", status("99"))
        assertEquals("", status(""))
        // 别的部署直接给文字时原样保留
        assertEquals("已修", status("已修"))
    }

    @Test
    fun `补考重修用学校的真实键名 MBKCJ 与 MCXCJ`() {
        // 抄自学校 `setTbodyHtml`：补考列 = MBKCJ，重修列 = MCXCJ
        val json = """[{"KCMC":"运筹学A1","CJ":"51","MAXCJ":"55","MBKCJ":"55","MCXCJ":"62"}]"""

        val item = api.parseCourseItems(json).single()

        assertEquals("55", item.makeupScore)
        assertEquals("62", item.retakeScore)
        assertEquals("55", item.maxGrade)
        assertEquals("51", item.score)
    }

    @Test
    fun `重修拆成两列的部署用斜杠连起来`() {
        val json = """[{"KCMC":"课","CXCJ1":"55","CXCJ2":"62"}]"""
        assertEquals("55 / 62", api.parseCourseItems(json).single().retakeScore)
    }

    // ── 2026-10 用「另存为完整网页」的渲染结果当标准答案，对照出来的四处 ──

    @Test
    fun `学分与门次在互斥分支里 必须跨分支合并`() {
        // 学校为同一个节点渲染**四个互斥分支**（参考文件 1150/1156/1162/1169 行）：
        //   ① 只有学分 ② 只有门次 ③ 学分+门次 ④ 什么都没有
        // 只挑一段 ⇒ 学分与门次**永远不可能同时拿到**。
        // 实测：标准答案里「通识教育平台 要求门次 35 / 达到要求 29 / 未达到要求 6」，我们三格全空。
        val guid = "1CB4DBE319D31153E0637902A8C0D010"
        val html = """
            if ('53.5' != '' && '35' == '') {
              ${'$'}("<li id='li$guid'><p class='title1' id='p$guid' yxxf='49.5' yqzdxf='53.5'>" +
              "通识教育平台&nbsp;" + ${'$'}.i18n.get('yqxf')/* 要求学分 */ + ":53.5&nbsp;" +
              ${'$'}.i18n.get('hdxf')/* 获得学分 */ + ":49.5&nbsp;" +
              "<span id='showKc$guid'></span></p>").appendTo(${'$'}("#li"));
            } else if ('35' != '' && '53.5' != '') {
              ${'$'}("<li id='li$guid'><p class='title1' id='p$guid' yxxf='49.5' yqzdxf='53.5'>" +
              "通识教育平台&nbsp;" + ${'$'}.i18n.get('yqmc')/* 要求门次 */ + ":35&nbsp;" +
              ${'$'}.i18n.get('ddyq')/* 达到要求 */ + ":29&nbsp;&nbsp;" +
              ${'$'}.i18n.get('wddyq')/* 未达到要求 */ + ":6&nbsp;" +
              "<span id='showKc$guid'></span></p>").appendTo(${'$'}("#li"));
            }
        """.trimIndent()

        val type = api.parseTypesDetailed(html).first.single()

        assertEquals("通识教育平台", type.name)
        assertEquals("53.5", type.requiredCredit)
        assertEquals("49.5", type.earnedCredit)
        // 门次来自**另一个分支** —— 这就是本次修的那一处
        assertEquals("35", type.requiredCount)
        assertEquals("29", type.reachedCount)
        assertEquals("6", type.missedCount)
    }

    @Test
    fun `到达要求与达到要求两种用字都要认`() {
        // JS 注释写「达到要求」，而 i18n 表里的值是「到达要求」（页面上显示的也是后者）。
        // 只认一种 ⇒ 「到达」那格永远空（实测如此）。
        val guid = "2CB4DBE319D31153E0637902A8C0D010"
        val html = """
            ${'$'}("<li id='li$guid'><p class='title1' id='p$guid' yqzdxf='18'>" +
            "思想政治类课程&nbsp;" + ${'$'}.i18n.get('yqmc')/* 要求门次 */ + ":14&nbsp;" +
            ${'$'}.i18n.get('ddyq')/* 达到要求 */ + ":12&nbsp;&nbsp;" +
            ${'$'}.i18n.get('wddyq')/* 未达到要求 */ + ":2&nbsp;" +
            "<span id='showKc$guid'></span></p>").appendTo(${'$'}("#li"));
        """.trimIndent()

        val type = api.parseTypesDetailed(html).first.single()

        assertEquals("14", type.requiredCount)
        assertEquals("12", type.reachedCount)
        assertEquals("2", type.missedCount)
    }

    @Test
    fun `要求门次为空时 到达与未达到整组作废`() {
        // 根节点「主修」的模板里 `达到要求:0` 是**硬编码**的 0（参考文件 817 行），
        // 而学校页面上「主修」那行没有任何门次文字 —— 留着会渲染出孤零零一句「到达 0」。
        val guid = "5CB4DBE319D31153E0637902A8C0D010"
        val html = """
            ${'$'}("<li id='li$guid'><p class='title1' id='p$guid' yxxf='95.0' yqzdxf='170'>" +
            "主修&nbsp;" + ${'$'}.i18n.get('yqmc')/* 要求门次 */ + ":&nbsp;" +
            ${'$'}.i18n.get('ddyq')/* 达到要求 */ + ":0&nbsp;&nbsp;" +
            ${'$'}.i18n.get('wddyq')/* 未达到要求 */ + ":&nbsp;" +
            "<span id='showKc$guid'></span></p>").appendTo(${'$'}("#li"));
        """.trimIndent()

        val type = api.parseTypesDetailed(html).first.single()

        assertEquals("", type.requiredCount)
        assertEquals("", type.reachedCount)
        assertEquals("", type.missedCount)
    }

    @Test
    fun `其他课程是合法节点 不能当选位分支丢掉`() {
        // 「其他课程」的 id 是固定短 key（`qtkcxfyq`）、`yqzdxf`/`yxxf` **都是空串** ——
        // 旧实现用"id 像不像 GUID / 有没有学分"的形状判据把它误杀，
        // 于是 App 里比学校页面**少一个类型**（23 vs 24），而它正是学生要看的"计划外课程"。
        val html = """
            ${'$'}("<li id='liqtkcxfyq'><p class='title1' id='pqtkcxfyq' yqzdxf='' sftg=''>" +
            "其他课程<span id='showKcqtkcxfyq'><a href='javascript:;' class='more'>共（" +
            "<font name='zms'>0</font>）门 通过（<font name='tgms'>0</font>）门</a></span></p>" +
            "").appendTo(${'$'}("#li"));
        """.trimIndent()

        assertEquals(listOf("其他课程"), api.parseTypesDetailed(html).first.map { it.name })

        // 真占位分支仍然要丢（名字本身就在说没数据）
        val placeholder = """
            ${'$'}("<li id='licxcyqkxfyq'><p class='title1' id='pcxcyqkxfyq' yqzdxf=''>" +
            "创新创业情况尚未数据信息<span id='showKccxcyqkxfyq'></span></p>").appendTo(${'$'}("#li"));
        """.trimIndent()
        val kept = api.parseTypesDetailed(placeholder).first
        assertTrue("占位分支不该出现：${kept.map { it.name }}", kept.isEmpty())
    }

    @Test
    fun `小字不会串到隔壁节点`() {
        // 段落右边界必须卡在本节点自己那次 jQuery 调用里（`").appendTo(`）。
        // 越过它就会读到隔壁节点的小字 —— 实测「其他选修课程」被串上了「到达 2」，
        // 而标准答案里它只有「共（2）门 通过（2）门」，根本没有门次行。
        val a = "3CB4DBE319D31153E0637902A8C0D010"
        val b = "4CB4DBE319D31153E0637902A8C0D010"
        val html = """
            ${'$'}("<li id='li$a'><p class='title1' id='p$a' yqzdxf='' yxxf='4.0'>" +
            "其他选修课程&nbsp;" + ${'$'}.i18n.get('hdxf')/* 获得学分 */ + ":4.0&nbsp;" +
            "<span id='showKc$a'></span></p>").appendTo(${'$'}("#li"));
            ${'$'}("<li id='li$b'><p class='title1' id='p$b' yqzdxf='2'>" +
            "美育类课程&nbsp;" + ${'$'}.i18n.get('yqmc')/* 要求门次 */ + ":1&nbsp;" +
            ${'$'}.i18n.get('ddyq')/* 达到要求 */ + ":2&nbsp;&nbsp;" +
            ${'$'}.i18n.get('wddyq')/* 未达到要求 */ + ":0&nbsp;" +
            "<span id='showKc$b'></span></p>").appendTo(${'$'}("#li"));
        """.trimIndent()

        val byName = api.parseTypesDetailed(html).first.associateBy { it.name }

        assertEquals("", byName.getValue("其他选修课程").requiredCount)
        assertEquals("", byName.getValue("其他选修课程").reachedCount)
        assertEquals("1", byName.getValue("美育类课程").requiredCount)
        assertEquals("2", byName.getValue("美育类课程").reachedCount)
    }

    @Test
    fun `获得学分 0_0 是真值 不能当缺失丢掉`() {
        // 2026-10 用户报「为零分的课程类型没有进度条」的根因：
        // 学分归一顺手把 <= 0 丢了（真实目的只是不想说「还差 0.0 学分」），
        // 而「获得学分:0.0」是学校页面上的**真值** —— 一门都没过的模块恰恰最该看到进度条。
        val guid = "6CB4DBE319D31153E0637902A8C0D010"
        val html = """
            ${'$'}("<li id='li$guid'><p class='title1' id='p$guid' yxxf='0.0' yqzdxf='19'>" +
            "专业核心课程模块&nbsp;" + ${'$'}.i18n.get('yqxf')/* 要求学分 */ + ":19.0&nbsp;" +
            ${'$'}.i18n.get('hdxf')/* 获得学分 */ + ":0.0&nbsp;" +
            ${'$'}.i18n.get('whdxf')/* 未获得学分 */ + ":19.0&nbsp;" +
            "<span id='showKc$guid'></span></p>").appendTo(${'$'}("#li"));
        """.trimIndent()

        val type = api.parseTypesDetailed(html).first.single()

        assertEquals("19", type.requiredCredit)
        assertEquals("0.0", type.earnedCredit) // ← 会被原样保留（修之前是空串 ⇒ 进度条整条不画）
        assertEquals("19.0", type.missedCredit)
        // 界面据此能算出 0% 的比例（分子不再是 null）
        assertEquals(0f, type.earnedCredit.toFloatOrNull()!! / type.requiredCredit.toFloatOrNull()!!)
    }

    @Test
    fun `修满的模块 未获得学分 0_0 会被保留但界面按数值不显示还差`() {
        val guid = "7CB4DBE319D31153E0637902A8C0D010"
        val html = """
            ${'$'}("<li id='li$guid'><p class='title1' id='p$guid' yxxf='16.0' yqzdxf='16'>" +
            "语言与工具类课程&nbsp;" + ${'$'}.i18n.get('yqxf')/* 要求学分 */ + ":16.0&nbsp;" +
            ${'$'}.i18n.get('hdxf')/* 获得学分 */ + ":16.0&nbsp;" +
            ${'$'}.i18n.get('whdxf')/* 未获得学分 */ + ":0.0&nbsp;" +
            "<span id='showKc$guid'></span></p>").appendTo(${'$'}("#li"));
        """.trimIndent()

        val type = api.parseTypesDetailed(html).first.single()

        // 数据层保留 0.0（它是学校给的结论），判"缺不缺"由界面按数值来
        assertEquals("0.0", type.missedCredit)
        assertEquals(
            "0.0 的数值是 0 ⇒ 不该被当成缺学分",
            0f,
            type.missedCredit.toFloatOrNull()!!,
        )
    }

    // ── 明细行：嵌套 / 容器名 / 脏值 ──

    @Test
    fun `课程明细 嵌套字段也能读到`() {
        val json = """{"data":{"items":[
            {"KCH":"B001","KCMC":"高等数学","XDZT":"已修","MAXCJ":"92","XNMC":"2024-2025","XQMMC":"1",
             "detail":{"XF":"4.0","JD":"4.0","JYXDXNMC":"2026-2027","JYXDXQMC":"3"}}
        ]}}"""

        val items = api.parseCourseItems(json, "通识必修")

        assertEquals(1, items.size)
        assertEquals("通识必修", items[0].typeName)
        // 老代码只读顶层 ⇒ 这几个字段整列为空（用户报的「明细字段整列为空」）
        assertEquals("4.0", items[0].credit)
        assertEquals("4.0", items[0].gradePoint)
        // ⚠️ 「实际修读」与「建议修读」是两对不同的字段，错一个就会把建议当作实际显示
        assertEquals("2024-2025 第1学期", items[0].term)
        assertEquals("2026-2027 第1学期", items[0].suggestedTerm)
        assertEquals("92", items[0].maxGrade)
    }

    @Test
    fun `课程明细 容器名与裸数组都能认`() {
        val rows = """{"rows":[{"kcmc":"大学英语","xf":3,"jd":"3.5","xdzt":"已修",
            "xnmc":"2024-2025","xqmmc":"2"}]}"""
        val bare = """[{"KCMC":"体育","XF":"1.0"}]"""

        assertEquals("3", api.parseCourseItems(rows)[0].credit)
        assertEquals("3.5", api.parseCourseItems(rows)[0].gradePoint)
        assertEquals("2024-2025 第2学期", api.parseCourseItems(rows)[0].term)
        assertEquals("1.0", api.parseCourseItems(bare)[0].credit)
    }

    @Test
    fun `课程明细 无与短横不显示 成绩列保留汉字`() {
        val json = """[{"KCMC":"体育","XF":"无","JD":"-","MAXCJ":"缺考","XDZT":"已修",
            "JYXDXNM":"2024-2025","JYXDXQMC":"12"}]"""

        val item = api.parseCourseItems(json).single()

        assertEquals("", item.credit)
        assertEquals("", item.gradePoint)
        // 「缺考」是学校写明的结果，不是脏值，要留着
        assertEquals("缺考", item.maxGrade)
        assertEquals("已修", item.status)
    }

    @Test
    fun `课程明细 成绩与最大成绩是两列`() {
        // 用户学校的补考场景：成绩 51 / 最大成绩 55 / 补考 55 / 绩点 0
        val json = """[{"KCMC":"运筹学A1","XF":"3.0","MAXCJ":"55","CJ":"51","JD":"0","XDZT":"",
            "JYXDXNM":"2025-2026","JYXDXQMC":"2"}]"""

        val item = api.parseCourseItems(json).single()

        assertEquals("55", item.maxGrade)
        assertEquals("51", item.score)
        assertEquals("0", item.gradePoint)
    }

    @Test
    fun `课程明细 只有成绩列时最大成绩不硬塞`() {
        val json = """[{"KCMC":"体育","CJ":"88"}]"""

        val item = api.parseCourseItems(json).single()

        assertEquals("", item.maxGrade)
        assertEquals("88", item.score)
    }

    @Test
    fun `课程明细 非JSON输入返回空列表`() {
        assertTrue(api.parseCourseItems("").isEmpty())
        assertTrue(api.parseCourseItems("<html><h5>用户登录</h5></html>").isEmpty())
        assertTrue(api.parseCourseItems("""{"items":"oops"}""").isEmpty())
        assertTrue(api.parseCourseItems("""[{"KCMC":""}]""").isEmpty())
    }

    // ── 绩点：先认标签，认不到才退回第一个小数 ──

    @Test
    fun `绩点优先认标签`() {
        val text = "计划总课程50门通过40门未通过2门未修4门在读4门计划外通过0门未通过0门平均学分绩点：3.52"

        assertEquals("3.52", api.parseAlertStatistics(text).gpa)
        // 计划门数照旧要解析出来（同一个正则的通路）
        assertEquals("50", api.parseAlertStatistics(text).plannedTotal)
        assertEquals("40", api.parseAlertStatistics(text).plannedPassed)
    }

    @Test
    fun `绩点没有标签时退回第一个小数`() {
        val summary = api.parseAlertStatistics("学分4.21门数1")

        assertEquals("4.21", summary.gpa)
    }

    // ── 明细接口的入参：`xh_id` 等隐藏字段（用户报「有的课程明细查不到」的根因） ──

    /**
     * ⚠️ 这条盯的是**发出去的 body**，不是"能不能解析出类型"。
     *
     * 症状是"同一页面上有的类型有课、有的永远空着"：学校那段 `loadKc()` 对
     * `jdkcsx == '1'`（注释原话「该节点未保存过，从教学计划加载课程」）的节点会带上 `xh_id` ——
     * 少了它，服务端没法把这类节点现从教学计划里拉出来，于是返回空数组。
     */
    @Test
    fun `明细 body 带上页面里的隐藏字段`() {
        val html = """
            <div class="panel">
              <input type="hidden" id="xh_id" value="202408501222"/>
              <input type="hidden" id="cjlrxn" value="2024-2025"/>
              <input type="hidden" id="cjlrxq" value="1"/>
              <input type="hidden" id="bkcjlrxn" value=""/>
              <input type="text" id="kw" value="语文"/>
            </div>
        """.trimIndent()

        val fields = parseHiddenFields(html)

        assertEquals("202408501222", fields["xh_id"])
        assertEquals("2024-2025", fields["cjlrxn"])
        assertEquals("1", fields["cjlrxq"])
        // 空值不发（见 parseHiddenFields 的 KDoc：拿不到就跟改之前完全一样）
        assertTrue("空值不该发出去", "bkcjlrxn" !in fields)
        // 不在白名单里的字段一概不收（页面上还有很多别的 input）
        assertTrue("无关字段不该收", "kw" !in fields)

        assertEquals(
            "xfyqjd_id=1CB4DBE3&cjlrxn=2024-2025&cjlrxq=1&xh_id=202408501222",
            detailFormBody("1CB4DBE3", fields)
        )
    }

    @Test
    fun `没有隐藏字段时 body 与老行为一致`() {
        assertEquals("xfyqjd_id=abc", detailFormBody("abc", emptyMap()))
        assertEquals("xfyqjd_id=abc", detailFormBody("abc", parseHiddenFields("<html>没有输入框</html>")))
    }

    @Test
    fun `隐藏字段属性顺序与引号形态都要认`() {
        // 学校页面里长这样：`value="202408501222" id="xh_id"`（value 在前、id 在后）
        val html = """<input type="hidden" name="xh_id" value="202408501222" id="xh_id">""" +
            """<input type=hidden value='2024-2025' id=cjlrxn>""" +
            """<input id=cjlrxq value=1>"""

        val fields = parseHiddenFields(html)

        assertEquals("202408501222", fields["xh_id"])
        assertEquals("2024-2025", fields["cjlrxn"])
        assertEquals("1", fields["cjlrxq"])
    }

    /**
     * 「非课程信息」接口（`…FKcxx.html`）的响应**与主接口同构** —— 这一条就是那个判断的守门人。
     *
     * 依据是学校自己的渲染函数：`setTbodyHtml_Fkc(item,…)` 取的字段
     * （`KCMC` / `XF` / `XSXXXX` / `KCXZMC` / `MAXCJ` / `JD` / `CJ` / `MBKCJ` / `MCXCJ` / `XNMC` / `XQMMC`）
     * 与 `setTbodyHtml` 完全一致。也就是说"有的类型查不到明细"只是**问错了接口**，
     * 不需要另写一套解析 —— 谁将来把两个接口分开处理，这条测试会红给他看。
     */
    @Test
    fun `非课程接口的响应与主接口同构`() {
        val nonCourseResp = """
            [{"KCH_ID":"X1","XDZT":"4","XNMC":"2024-2025","XQMMC":"2","KCH":"669200031",
              "KCMC":"影视剪辑","XSXXXX":"理论(2.0)","KCXZMC":"任选","XF":"2.0","KCLBMC":"本科",
              "MAXCJ":"96","JD":"4.6","CJ":"96","MBKCJ":"","MCXCJ":"","KCZYXXS":"1"}]
        """.trimIndent()

        val items = api.parseCourseItems(nonCourseResp, "美育类课程", "1CB4DBE3")

        assertEquals(1, items.size)
        assertEquals("影视剪辑", items[0].courseName)
        assertEquals("2.0", items[0].credit)
        assertEquals("96", items[0].maxGrade)
        assertEquals("2024-2025 第2学期", items[0].term)
        assertEquals("已修", items[0].status)
    }
}
