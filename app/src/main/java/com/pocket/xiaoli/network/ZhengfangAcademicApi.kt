package com.pocket.xiaoli.network

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URLEncoder

/** 学业情况统计（页面 div#alertBox 解析，字段缺失为空串） */
data class AcademicSummary(
    val gpa: String = "",          // 平均学分绩点 GPA
    val plannedTotal: String = "", // 计划内总课程数
    val plannedPassed: String = "",// 计划内已通过
    val plannedFailed: String = "",// 计划内未通过
    val plannedMissed: String = "",// 计划内未修
    val plannedIn: String = "",    // 计划内在读
    val unplannedPassed: String = "", // 计划外已通过
    val unplannedFailed: String = "", // 计划外未通过
    val studentId: String = ""     // 学号
)

/** 培养方案课程类型（如通识必修/专业必修），含学分要求与已获学分 */
data class AcademicTypeInfo(
    val name: String,
    val id: String,
    val requiredCredit: String = "", // 要求学分
    val earnedCredit: String = "",   // 获得学分
    val missedCredit: String = "",   // 未获得学分
    // 页面的第二行（有些类型没有这一段，那就是空串）：
    // 「要求门次:14 到达要求:12 未达到要求:2 共（14）门 通过（12）门」
    val requiredCount: String = "",  // 要求门次
    val reachedCount: String = "",   // 到达要求（门次）
    val missedCount: String = "",    // 未达到要求（门次）
    val totalCourses: String = "",   // 共（N）门
    val passedCourses: String = "",  // 通过（N）门
    /**
     * 父节点 id（培养方案是**两级树**：平台/模块 → 子模块）。
     * 烟理这个部署的父子关系写在 JS 里：`appendTo($("#li{父GUID}"))`。
     * 空串 = 顶层。
     */
    val parentId: String = "",
    /** 层级（0 = 顶层「主修」）。由 [parentId] 链推出，界面据此缩进 */
    val depth: Int = 0
)

/** 单门课程修读情况（按培养方案类型分组返回） */
data class AcademicCourseItem(
    val typeName: String = "",   // 所属课程类型
    /**
     * 所属类型的 id。⚠️ 分组要用它，不要用名字：培养方案里**真的存在同名节点**
     * （通识必修和通识选修下各有一个「身心健康类课程」），按名字分组会让两组课程串在一起。
     */
    val typeId: String = "",
    val courseName: String = "", // 课程名称 KCMC
    val courseId: String = "",   // 课程号 KCH
    val credit: String = "",     // 学分 XF
    val nature: String = "",     // 课程性质 KCXZMC（必修/选修）
    val category: String = "",   // 课程类别 KCLBMC
    val maxGrade: String = "",   // 最大成绩 MAXCJ（补考/重修后取最高）
    val score: String = "",      // 成绩 CJ（原始成绩；与 maxGrade 不同才显示 —— 挂科补考的场景）
    val gradePoint: String = "", // 绩点 JD
    val hours: String = "",      // 学时 XSXXXX：教务页写的是「理论(2.0)-实践(4.0)」这种按教学环节拼的串
    val makeupScore: String = "",// 补考 BKCJ
    val retakeScore: String = "",// 重修 CXCJ
    val status: String = "",     // 修读状态 XDZT（⚠️ 这个部署是数字码，见 cleanStatus）
    val term: String = "",       // **实际**修读学年学期 XNMC + XQMMC（成绩学年/学期那一对）
    /**
     * **建议**修读学年学期 `JYXDXNMC` + `JYXDXQMC`。
     *
     * ⚠️ 千万别把这两个混起来：教务页面上是**两对不同的列**，
     * 「成绩学年/学期」= 我实际什么时候修的，「建议修读学年/学期」= 学校建议我什么时候修。
     * 2026-10 实测回过一次：`JYXDXNM=2024`（建议，还是编码）与 `XNMC=2024-2025`（实际）**不是一回事**，
     * 老代码读的是前者 ⇒ 把"建议"当成"实际"显示 —— 而这种错误**看起来完全正常**，最难发现。
     */
    val suggestedTerm: String = ""
)

/**
 * 类型解析的**自检**：解析数量明显不对时，界面直接把这一串显示出来。
 *
 * 为什么让它上界面：2026-10 连着两版解析事故（先整页空白、再"只出来一个类型"），
 * 而远程只能靠用户读 logcat —— 那一步没人会做。四个数字放在屏幕上，
 * 用户截一张图就能定位是"锚点没找到""句柄没找到""配对失败"还是"取名撞车被去重"。
 */
data class AcademicParseDiag(
    val anchors: Int = 0,   // 页面里「要求学分」锚点数
    val handles: Int = 0,   // showKc 句柄数
    val pairs: Int = 0,     // 单调配对成功的对数
    val named: Int = 0,     // 取到类型名的个数
    val kept: Int = 0,      // 最后留下的类型数
    val renamed: Int = 0,   // 名字撞车、只能用 id 区分的个数（"被去重吞成 1 个"的现场证据）
    val sample: String = "", // 首个类型名（判断"名字是否全被取成同一个"）
    /** 这份结果是从哪一路来的：正文 / 内嵌 / 正则（远程排查时一眼看出走的是哪条路） */
    val source: String = "",
) {
    /**
     * 解析结果可疑吗（界面据此提示）。
     *
     * ⚠️ 判据里**不能**用「留下的比句柄少」：培养方案的根节点（`2023级软件工程`）有句柄，
     * 但它不是课程类型、是**故意跳过**的 —— 那样会把正常页面报成异常。
     * 真正说明"丢类型了"的是：
     * - 一个都没留下；
     * - 配上的对数比句柄少（有句柄没配上学分栏）；
     * - 有类型连名字都取不到（`命名 < 留下`）；
     * - **大量重名**（≥3：12 个类型被去重吞成 1 个是那个事故的现场）。
     *   ⚠️ 一两个重名是**正常的**：培养方案里真的存在同名节点（通识必修与通识选修下
     *   各有一个「身心健康类课程」）—— 把它报成"解析异常"就是狼来了（v0.1.15 实测误报过）。
     */
    val suspicious: Boolean
        get() = kept == 0 || named < kept || renamed >= 3 ||
            (handles > 0 && pairs < handles)

    fun summary(): String = buildString {
        append("自检")
        if (source.isNotBlank()) append("[").append(source).append("]")
        append("：锚点 ").append(anchors)
        append(" · 句柄 ").append(handles)
        append(" · 配对 ").append(pairs)
        append(" · 命名 ").append(named)
        append(" · 留下 ").append(kept)
        if (renamed > 0) append(" · 重名 ").append(renamed)
        if (sample.isNotBlank()) append("（首个「").append(sample).append("」）")
    }
}

/** fetchIndex 返回：统计信息 + 课程类型列表 + 解析自检 + 取证文本 */
data class AcademicIndexResult(
    val summary: AcademicSummary,
    val types: List<AcademicTypeInfo>,
    val parseDiag: AcademicParseDiag = AcademicParseDiag(),
    /** 解析不对时给用户一键复制用（内含原始响应里几个关键位置的原文） */
    val probe: String = "",
    /**
     * 培养方案的根节点（「主修」，其学分就是**总要求/总已修**）。
     *
     * 为什么单独拎出来：这个页面是**两级树**（平台/模块 → 子模块），
     * 把所有节点加起来算"总进度"会**重复计算**（父节点的值约等于子节点之和）。
     * 根节点那两档数字才是总进度，所以汇总环用它。
     */
    val rootType: AcademicTypeInfo? = null,
    /**
     * 索引页里那些**隐藏表单字段**（`xh_id` / `cjlrxn` / `cjlrxq` / …）的原值。
     *
     * ⚠️ 它们是明细接口的**必要入参**，不是可选装饰 —— 见 [detailFormBody] 的 KDoc：
     * 少了 `xh_id`，学校那边就没办法把"**还没保存过**的节点"从教学计划里现拉出来，
     * 那几个类型在 App 里于是一直显示「（该类型未取到课程明细）」，
     * 而同一页面上"已保存过"的类型却是好的 —— 这就是用户报的
     * 「有的课程课程明细怎么查不到」（2026-10）。
     */
    val formFields: Map<String, String> = emptyMap()
)

/**
 * 类型句柄 id 的合法形状：ASCII 字母/数字/下划线/连字符。
 *
 * ⚠️ **不能要求"纯数字"**：烟理这个部署的句柄是 32 位 GUID（`showKc1CB4DBE3…`），
 * 要求数字 ⇒ 一个句柄都留不下 ⇒ 类型列表整页空白。
 * ⚠️ 也**不能放开小数点**：老代码把「未获得学分」当成 id 传出去，值就是 `0.0`，
 * 放开小数点等于把那个线上事故的门重新打开。
 * ⚠️ 也不能用 `Char.isDigit()`：它连全角「２０２３」都认（本项目在绑定校验上踩过同一个坑）。
 */
internal fun isAcademicTypeId(id: String): Boolean =
    id.isNotEmpty() && id.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '_' || it == '-' }

/**
 * 明细接口需要的隐藏表单字段 —— 与学校自己的 JS 逐个对齐（`cxXsxyqkIndex.js` 的 `loadKc()`）。
 *
 * 学校那三个分支分别带：
 * - `xfyqjd_id == 'qtkcxfyq'`（其他课程）/ `'cxcyqkxfyq'`（创新创业）：
 *   `fromXh_id` + `xfyqjd_id` + `xh_id` + 八个成绩可见性字段（`cjlrxn` … `cjzt`）；
 * - 其余节点 `jdkcsx == '1'`（**该节点未保存过，从教学计划加载课程**）：只带 `fromXh_id`、`xfyqjd_id`
 *   与 **`xh_id`** 三个；
 * - 其余节点：`fromXh_id`/`xfyqjd_id`/`xh_id`，但走的是"非课程信息"那个接口。
 *
 * ⚠️ `fromXh_id` **刻意不收**：它来自入口 URL 的查询串（`$.getWindowHrefMap()`），
 * 语义是"老师从某个学生页跳过来看"，学生自己那条链路上它是空的。
 */
private val DETAIL_FORM_IDS = listOf(
    "xh_id", "cjlrxn", "cjlrxq", "bkcjlrxn", "bkcjlrxq", "xscjcxkz", "cjcxkzzt", "cjztkz", "cjzt"
)

/** 明细主接口：从教学计划取"该节点未保存过"的课程（`jdkcsx == '1'` 那条路） */
private const val DETAIL_PATH_COURSE = "/xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html"

/** 明细备用接口（学校叫"非课程信息"）：`jdkcsx != '1'` 的节点走这条，行结构完全相同 */
private const val DETAIL_PATH_NON_COURSE = "/xsxy/xsxyqk_cxJxzxjhxfyqFKcxx.html"

private val INPUT_TAG = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
private val ATTR_ID = Regex("""\bid\s*=\s*["']?([^"'\s>/]+)""", RegexOption.IGNORE_CASE)
// 学校页面里是 `value="202408501222" id="xh_id"`（带引号、id 在后）；
// 裸值分支（`value=2024`）是 HTML 允许而页面里没出现的形态，顺手认掉 —— 认不出就是"少补一个参数"，
// 不值得为它冒"页面一改版就静默失效"的风险。
private val ATTR_VALUE =
    Regex("""\bvalue\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

/**
 * 从索引页里摘出明细接口要用的隐藏字段。
 *
 * ⚠️ **空白值丢弃**（不是"照抄学校"）：学校那边 `xh_id` 一定是有值的，
 * 而我们把一个空串显式发过去，和"不带这个参数"在服务端很可能不是一回事 ——
 * 现在这种"只补上拿到的"做法是**单调增加信息**的：拿不到就跟改之前完全一样。
 */
internal fun parseHiddenFields(html: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    INPUT_TAG.findAll(html).forEach { tag ->
        val id = ATTR_ID.find(tag.value)?.groupValues?.get(1)?.lowercase() ?: return@forEach
        if (id !in DETAIL_FORM_IDS || id in out) return@forEach
        val value = ATTR_VALUE.find(tag.value)
            ?.let { it.groupValues[2].ifEmpty { it.groupValues[3] }.ifEmpty { it.groupValues[4] } }
            ?.trim()
            .orEmpty()
        if (value.isNotEmpty()) out[id] = value
    }
    return out
}

/**
 * 明细接口的 POST body。
 *
 * ⚠️ 这里曾经只发 `xfyqjd_id=…`，于是**没保存过的节点**（学校那段 JS 里
 * `jdkcsx == '1'` 注释原话：「该节点未保存过，从教学计划加载课程」）在服务端查不出东西 ——
 * 而它们要靠 `xh_id` 才能现从教学计划里拉出来。表现就是用户报的
 * 「有的课程课程明细怎么查不到」：同一页面上有的类型有课、有的永远空着。
 *
 * 字段顺序排一遍（`toSortedMap`）只为**可测**：同一份输入必须产出同一份 body。
 */
internal fun detailFormBody(typeId: String, fields: Map<String, String>): String = buildString {
    append("xfyqjd_id=").append(URLEncoder.encode(typeId, "UTF-8"))
    fields.toSortedMap().forEach { (k, v) ->
        append('&').append(URLEncoder.encode(k, "UTF-8")).append('=').append(URLEncoder.encode(v, "UTF-8"))
    }
}

/**
 * 正方教务 v9「学生学业情况」查询封装（基于同一 ZhengfangClient 的登录会话）。
 *
 * 接口约定（与开源 zfn_api 的 get_academia 同源验证）：
 * - 页面：GET xsxy/xsxyqk_cxXsxyqkIndex.html?gnmkdm=N105515&layout=default
 *   - input#xh_id → 学号；div#alertBox 文本 → 课程/学分统计；页面正则 → 各类型要求/获得/未获得学分
 * - 明细：POST xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html?gnmkdm=N105515，body xfyqjd_id={类型id}
 *   - 返回 JSON 数组，字段名大小写不定，解析做别名容错
 */
class ZhengfangAcademicApi(private val client: ZhengfangClient) {

    /**
     * 最近一次**有课程数据的明细响应**的可读样例（键=值，字段最全的那一条），诊断用。
     *
     * 为什么要留它：明细表那几列（学时 / 补考 / 重修 / 建议修读学年学期）的**字段名**
     * 只能从真实响应里读出来 —— 教务页面上的表头是 i18n key（`kcxs`/`bk`/`msg_cx`），
     * 不等于 JSON 里的键名。猜错别名会显示**错的数据**，比空着更糟。
     *
     * ⚠️ 只在**真有课程行**的响应上取样：培养方案里「主修 / 平台 / 模块」这些容器节点的
     * 明细就是 `[]`，而它们恰好排在列表最前面 —— 抓"第一条非空响应"会抓到 `[]`
     * （v0.1.19 用户实测回来就是 `【明细样例】[]`）。
     */
    internal var lastCoursesSample: String = ""
        private set

    /** 一条课程行都没有时的兜底原文（`[]` / 报错信息），也是有用的诊断线索 */
    internal var lastEmptyCoursesSample: String = ""
        private set

    /** 拉取学业情况页面：统计信息 + 课程类型列表（登录后调用） */
    suspend fun fetchIndex(baseUrl: String): AcademicIndexResult = withContext(Dispatchers.IO) {
        val url = "$baseUrl/xsxy/xsxyqk_cxXsxyqkIndex.html?gnmkdm=N105515&layout=default"
        val html = try {
            client.get(url).use { it.body?.string() ?: "" }
        } catch (e: Exception) {
            android.util.Log.w("AcademicApi", "fetchIndex failed: ${e.message}")
            ""
        }
        android.util.Log.d("AcademicApi", "fetchIndex url=$url len=${html.length} isLogin=${ZhengfangUtils.isSessionDead(html)}")
        if (html.isBlank()) return@withContext AcademicIndexResult(AcademicSummary(), emptyList())
        if (ZhengfangUtils.isSessionDead(html)) throw IllegalStateException("会话已失效，请重新登录")
        val (types, diag) = parseTypesDetailed(html)
        val result = AcademicIndexResult(
            parseSummary(html),
            types,
            diag,
            // 取证文本是**按需**生成的：它内部会把四条解析路径重跑一遍（1.2 MB 的页面 ≈ 白干一遍活），
            // 而正常页面根本用不到它 —— 只在"可疑"或"只剩一个类型"时才值得付这笔钱。
            if (diag.suspicious || types.size <= 1) probeOf(html, diag) else "",
            // 根节点 = 「主修」这类（它的学分就是总要求/总已修，汇总环只能用它，不能把两级加起来）
            rootTypeOf(types),
            // 明细接口的必要入参就藏在这个页面里（`xh_id` 等），取不到就给空表 —— 行为退回改之前
            parseHiddenFields(html)
        )
        // 排查用：页面结构异常时，这一行 + 界面上的自检串是唯一能远程定位的依据
        android.util.Log.d(
            "AcademicApi",
            "fetchIndex studentId=${result.summary.studentId} gpa=${result.summary.gpa} " +
                "types=${result.types.map { it.name to it.id }} ${diag.summary()}"
        )
        result
    }

    /**
     * 解析不对时的**取证文本**：直接从原始响应里剪几个关键位置的原文出来。
     *
     * 为什么要它：远程修这个页面已经耗了三轮「看现象 → 猜结构 → 再发版」。
     * 页面结构这种东西，**看一眼原文就定死了**，而要让用户去开 DevTools 拿源码，
     * 三轮里一次都没拿到。于是把这件事做进 App：用户点一下「复制诊断」，
     * 把这段贴进聊天窗口就行。
     */
    internal fun probeOf(html: String, diag: AcademicParseDiag): String = buildString {
        append("—— 修课情况解析诊断 ——\n")
        append(diag.summary()).append('\n')
        append("响应长度 ").append(html.length)
        append(" · 要求学分×").append(Regex("要求学分").findAll(html).count())
        append(" · showKc×").append(Regex("showKc").findAll(html).count())
        append(" · 未获得学分×").append(Regex("未获得学分").findAll(html).count())
        append(" · script×").append(Regex("<script").findAll(html).count())
        append('\n')
        // 每条路各自认出了几个类型 —— 一眼看出是哪条路挂了、挂在哪一步
        append("各路径：正文 ").append(parseTypesByBlock(html, "正文").types.size)
        append(" · 内嵌 ").append(parseTypesFromEmbedded(html).types.size)
        append(" · JS模板 ").append(parseTypesFromJsTemplate(html).types.size)
        append(" · 正则 ").append(parseTypesByRegex(html).size)
        append('\n')
        append(jsTemplateProbe(html))
        append(snippetAround(html, "showKc"))
        append(snippetAround(html, "要求学分"))
    }

    /**
     * JS 模板那条路的取证：块数、句柄数、**首个节点的原文与切出来的字面量**。
     * 这一段是为了"名字取不出来"这种情况看一眼就能定死 —— 猜页面结构已经猜了四轮。
     */
    private fun jsTemplateProbe(html: String): String = buildString {
        val tags = JS_NODE_TAG.findAll(html).toList()
        val handles = JS_HANDLE_TAG.findAll(html).map { it.groupValues[1] }.distinct().count()
        append("JS模板：p块 ").append(tags.size).append(" · 句柄 ").append(handles).append('\n')
        val first = tags.firstOrNull()
        if (first == null) {
            val h = html.indexOf("showKc")
            if (h > 0) {
                append("【没匹配到 p 块 · 句柄前 220 字】\n…")
                append(html.substring(maxOf(0, h - 220), h).replace(Regex("""\s+"""), " "))
                append("…\n")
            }
            return@buildString
        }
        val guid = first.groupValues[1]
        val segStart = first.range.last + 1
        val handleStart = html.indexOf("showKc$guid", segStart)
        val seg = if (handleStart > segStart) html.substring(segStart, minOf(handleStart, segStart + 300)) else ""
        append("【首个节点 · 段】\n…").append(seg.replace(Regex("""\s+"""), " ")).append("…\n")
        append("【首个节点 · 切出的字面量】").append(jsLiterals(seg).map { it.trim() }).append('\n')
        append("【首个节点 · 取名结果】").append(jsNameOf(seg) ?: "（空）").append('\n')
    }

    /** 剪 [needle] 前后各一段原文（压掉换行，便于粘贴） */
    private fun snippetAround(html: String, needle: String, window: Int = 260): String {
        val idx = html.indexOf(needle)
        if (idx < 0) return "【没有「$needle」】\n"
        val from = maxOf(0, idx - window / 2)
        val to = minOf(html.length, idx + window / 2)
        val snippet = html.substring(from, to).replace(Regex("""\s+"""), " ")
        return "【「$needle」附近】\n…$snippet…\n"
    }

    /**
     * 查询某类型下的全部课程修读明细（登录后调用）。
     *
     * ⚠️ 学校那个页面**同一件事有两个接口**，按节点属性 `jdkcsx` 二选一（`cxXsxyqkIndex.js` 的 `loadKc()`）：
     * - `jdkcsx == '1'`（注释原话：「该节点未保存过，从教学计划加载课程」）→
     *   `…Kcxx.html`（本方法）；
     * - 其余（`jdkcsx` 为空串或 `'4'` 之类）→ `…FKcxx.html`（"非课程信息"）。
     *
     * 两个接口返回的是**同一种课程行 JSON**（`setTbodyHtml_Fkc` 用的字段与 `setTbodyHtml` 完全一致），
     * 所以解析不用分叉。这里做成"Kcxx 空了就再试 FKcxx"的兜底，而不是去读 `jdkcsx`：
     * 属性没解析出来时兜底仍然有效，而多打的这一次请求只发生在**本来就查不到东西**的类型上。
     */
    suspend fun fetchCourses(
        baseUrl: String,
        typeId: String,
        typeName: String,
        fields: Map<String, String> = emptyMap()
    ): List<AcademicCourseItem> {
        val primary = postDetail(baseUrl, DETAIL_PATH_COURSE, typeId, typeName, fields)
        if (primary.isNotEmpty()) return primary
        // 空响应才走第二条路：`jdkcsx != '1'` 的节点（美育类课程、其他选修课程、各平台/模块）在这条路上
        val fallback = postDetail(baseUrl, DETAIL_PATH_NON_COURSE, typeId, typeName, fields)
        if (fallback.isNotEmpty()) nonCourseFallbackCount++
        return fallback
    }

    /** 走“非课程信息”接口取回明细则记一笔（诊断串里会显示，用来区分两种完全不同的空） */
    private var nonCourseFallbackCount = 0

    /** 诊断用：有几组明细是**靠 FKcxx 兜底**取回来的 */
    internal fun nonCourseFallbackGroups(): Int = nonCourseFallbackCount

    private suspend fun postDetail(
        baseUrl: String,
        path: String,
        typeId: String,
        typeName: String,
        fields: Map<String, String>
    ): List<AcademicCourseItem> = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path?gnmkdm=N105515"
        // ⚠️ body 必须带上索引页里那些隐藏字段（尤其 `xh_id`），见 `detailFormBody` 的 KDoc
        val body = detailFormBody(typeId, fields)
        val respText = try {
            client.post(url, body).use { it.body?.string() ?: "" }
        } catch (e: Exception) {
            android.util.Log.w("AcademicApi", "fetchCourses($typeId, $path) failed: ${e.message}")
            ""
        }
        android.util.Log.d(
            "AcademicApi",
            "fetchCourses type=$typeName id=$typeId path=$path fields=${fields.keys} resp=${respText.take(400)}"
        )
        if (respText.isBlank()) return@withContext emptyList()
        if (ZhengfangUtils.isSessionDead(respText)) throw IllegalStateException("会话已失效，请重新登录")
        val items = parseCourseItems(respText, typeName, typeId)
        // 取样分两档：有课程行的（真正有用的）优先；全是空行的留一份兜底
        if (items.isNotEmpty()) {
            if (lastCoursesSample.isBlank()) lastCoursesSample = courseSampleOf(respText)
        } else if (lastEmptyCoursesSample.isBlank()) {
            lastEmptyCoursesSample = courseSampleOf(respText)
        }
        items
    }

    /**
     * 从明细响应里摘**字段最全的那条**记录，摊成 `键=值` 的一行 —— 诊断里就靠它定死字段名。
     *
     * 摊平（`flattenRow`）是必要的：这套接口的字段可能嵌在子对象里。
     * 挑最全的一条而不是第一条：首条常常是空课（各列都空），看不到我们要找的那几个键。
     */
    internal fun courseSampleOf(raw: String, limit: Int = 700): String = try {
        val rows = ZhengfangUtils.findRowArray(raw)
            ?.filter { it.isJsonObject }
            ?.map { ZhengfangUtils.flattenRow(it.asJsonObject) }
            .orEmpty()
        val best = rows.maxByOrNull { it.entrySet().size }
        if (best == null) {
            raw.take(limit)
        } else {
            best.entrySet()
                .joinToString(" ") { (k, v) -> "$k=${v.asString.take(28)}" }
                .take(limit)
        }
    } catch (_: Exception) {
        raw.take(limit)
    }

    // ── 页面解析 ──

    internal fun parseSummary(html: String): AcademicSummary {
        if (html.isBlank()) return AcademicSummary()
        return try {
            val doc = Jsoup.parse(html)
            val studentId = doc.select("input#xh_id").attr("value").trim()
                .ifBlank { Regex("""(?:id|name)=["']xh_id["'][^>]*value=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim() ?: "" }
            val alertText = doc.select("div#alertBox").text()
                .ifBlank { Regex("""<div[^>]*id=["']alertBox["'][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) ?: "" }
                .replace(Regex("""<[^>]*>"""), "").replace("&nbsp;", " ").replace(" ", "")
                .trim()
            parseAlertStatistics(alertText, studentId)
        } catch (_: Exception) {
            AcademicSummary()
        }
    }

    /**
     * 平均学分绩点：**先认标签**（「平均学分绩点：3.52」），认不到才退回「第一个小数」。
     *
     * 为什么先认标签：alertBox 那段文本里同时躺着学分、门数、绩点三类数字，
     * 「取第一个小数」在别的学校部署上会把**学分**当绩点显示 —— 而界面看起来完全正常
     * （一个像模像样的小数），用户只会以为学校算错了。
     */
    private fun gpaOf(alertText: String): String {
        Regex("""平均学分绩点\s*[:：]?\s*([0-9]{1,}(?:\.[0-9]+)?)""").find(alertText)
            ?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return Regex("""[0-9]{1,}[.][0-9]+""").find(alertText)?.value ?: ""
    }

    /** 解析 alertBox 文本中的统计数字（对齐 zfn_api 正则） */
    internal fun parseAlertStatistics(alertText: String, studentId: String = ""): AcademicSummary {
        if (alertText.isBlank()) return AcademicSummary(studentId = studentId)
        val summary = AcademicSummary(studentId = studentId)
        return try {
            val gpa = gpaOf(alertText)
            val plan = Regex(
                """计划总课程(\d+)门通过(\d+)门?.*?未通过(\d+)门?.*?未修(\d+)?.*?在读(\d+)门?.*?计划外?.*?通过(\d+)门?.*?未通过(\d+)门"""
            ).find(alertText)
            if (plan == null) {
                summary.copy(gpa = gpa)
            } else {
                val g = plan.groupValues
                summary.copy(
                    gpa = gpa,
                    plannedTotal = g.getOrNull(1) ?: "",
                    plannedPassed = g.getOrNull(2) ?: "",
                    plannedFailed = g.getOrNull(3) ?: "",
                    plannedMissed = g.getOrNull(4) ?: "",
                    plannedIn = g.getOrNull(5) ?: "",
                    unplannedPassed = g.getOrNull(6) ?: "",
                    unplannedFailed = g.getOrNull(7) ?: ""
                )
            }
        } catch (_: Exception) {
            summary
        }
    }

    /**
     * 解析页面里的课程类型学分块：类型名 / 要求学分 / 获得学分 / 未获得学分 / `showKc{id}`。
     *
     * ## 页面长什么样（与 zfn_api 同源验证）
     *
     * ```html
     * <div class="panel-heading">"通识必修"&nbsp;要求学分：10.0&nbsp;获得学分：9.0&nbsp;未获得学分：1.0
     *   <span id='showKc101'>点击查看</span>
     * </div>
     * ```
     *
     * 三个学分是**非捕获交替**，所以正则里的组号固定为
     * `1=名称 2=要求 3=获得 4=未获得 5=showKc id`。
     *
     * ## 两个线上踩过的坑（改这段之前先读完）
     *
     * 1. **id 与「未获得学分」错位**：老代码按「1=名称 2=要求 3=获得 4=id」取组号，
     *    于是 `type.id` 拿到了未获得学分（`0` / `1.0`），而 `missedCredit` 拿到了 `showKc` 的数字后缀。
     *    后果有两层，而且都不报错：
     *    - 每个类型都显示「还差 {类型id} 学分」（`还差 1 学分` 看着完全像个正常数字，
     *      而进度条用的是组 2/3，所以**进度条是对的** —— 用户就是这么报的）；
     *    - `xfyqjd_id` 传成 `0` / `0.0`，明细接口返回的东西和本类型毫无关系 ⇒
     *      **课程明细整片取不到字段**。
     * 2. **不能整块取文本**：有的部署把所有类型塞进同一个容器，
     *    整块 `text()` 会让每个类型都读到**第一个**「未获得学分」（所有类型同一个数）。
     *    这里改成「从容器开头一路读到本类型的 `showKc` span 为止，再取**最后**一个
     *    『要求学分』」，于是共用容器与各自成块两种形状用同一套逻辑。
     *
     * 解析顺序：**结构优先**（Jsoup 按 `showKc` 定位），认得出来就用它；
     * 认不出来才退回正则（[parseTypesByRegex]）。
     */
    internal fun parseTypeStatistics(html: String): List<AcademicTypeInfo> = parseTypesDetailed(html).first

    /**
     * 解析 + 自检（[parseTypeStatistics] 只是取前半）。
     *
     * ## 兜底策略：三路都跑，**谁列得多用谁**
     *
     * 一条路走不通时"看起来都很正常"是这里最大的陷阱 —— 2026-10 线上第二次事故就是：
     * 页面正文里**只有当前选中的那一个类型块**（其余是 JS 渲染的 / 塞在 `<script>` 或注释里），
     * 于是"正文结构解析"干净地拿出了 1 个类型，自检**不报警**，兜底正则压根没被触发。
     *
     * 所以现在不再"可疑才兜底"，而是每次都算三条：
     *
     * | 来源 | 捞的是 |
     * |---|---|
     * | 正文 | `body` 里的可见文本（最准，字段最全） |
     * | 内嵌 | `<script>` / HTML 注释里的 HTML 片段（JS 渲染前的模板） |
     * | 正则 | 整个原始响应的字符串（连脚本里的转义字符串一起扫） |
     *
     * 取类型最多的那一路；并列时优先"正文 > 内嵌 > 正则"（字段更全）。
     * 代价不对称：少一个类型 = 一整组课程静默消失；多一行 = 用户一眼能看见。
     */
    internal fun parseTypesDetailed(html: String): Pair<List<AcademicTypeInfo>, AcademicParseDiag> {
        if (html.isBlank()) return emptyList<AcademicTypeInfo>() to AcademicParseDiag()
        val direct = parseTypesByBlock(html, source = "正文")
        val embedded = parseTypesFromEmbedded(html)
        val jsTemplate = parseTypesFromJsTemplate(html)
        val regexTypes = parseTypesByRegex(html)
        val byRegex = BlockResult(
            regexTypes,
            AcademicParseDiag(
                kept = regexTypes.size,
                named = regexTypes.size,
                sample = regexTypes.firstOrNull()?.name.orEmpty(),
                source = "正则"
            )
        )
        val best = listOf(direct, embedded, jsTemplate, byRegex)
            .reduce { acc, cur -> if (cur.types.size > acc.types.size) cur else acc }
        val (finalTypes, renamed) = finalizeTypes(best.types)
        return finalTypes to best.diag.copy(renamed = renamed)
    }

    /**
     * 有的部署把类型块**塞在脚本或 HTML 注释里**（页面上再由 JS 渲染出来）——
     * 那些内容不在正文文本里，光扫正文只会看到"当前选中的那一个类型"。
     * 这里把它们掏出来还原成 HTML 再解析一遍。
     */
    private fun parseTypesFromEmbedded(html: String): BlockResult {
        return try {
            val doc = Jsoup.parse(html)
            val payloads = mutableListOf<String>()
            doc.select("script").forEach { if (it.data().contains("要求学分")) payloads.add(it.data()) }
            collectEmbedded(doc, payloads)
            if (payloads.isEmpty()) return BlockResult(emptyList(), AcademicParseDiag())
            val rebuilt = "<body>" + payloads.joinToString("\n") {
                // JS 字符串里的转义引号还原出来，Jsoup 才认得出标签
                it.replace("\\\"", "\"").replace("\\'", "'")
            } + "</body>"
            parseTypesByBlock(rebuilt, source = "内嵌")
        } catch (_: Exception) {
            BlockResult(emptyList(), AcademicParseDiag())
        }
    }

    private fun collectEmbedded(node: Node, out: MutableList<String>) {
        if (node is Comment) {
            if (node.data.contains("要求学分")) out.add(node.data)
            return
        }
        for (child in node.childNodes()) collectEmbedded(child, out)
    }

    // ── 第四路：这个部署把培养方案树拼成 JS 字符串下发 ──
    //
    // 2026-10 从用户贴回的原始响应里看清的真实形状（烟理 zftal-ui 版）：
    //
    // ```js
    // "<p id='p<GUID>' yxxf='95.0' yqzdxf='170' sftg='0'>" + "主修&nbsp;"
    //   + $.i18n.get('yqxf')  /* 要求学分 */ + ":170.0&nbsp;"
    //   + $.i18n.get('hdxf')  /* 获得学分 */ + ":95.0&nbsp;&nbsp;"
    //   + $.i18n.get('whdxf') /* 未获得学分 */ + ":75.0&nbsp;"
    //   + "<span id='showKc<GUID>'></span>"
    //   + "<span id='thzt<GUID>' value='0,0,0'></span>"
    // ```
    //
    // 于是这个页面有三条"常识"全都不成立：
    // 1. **中文标签不在响应里** —— 它们是 `$.i18n.get('yqxf')`，中文只写在 **JS 注释**里，
    //    运行期才替换。所以"按『要求学分』找锚点"在这个部署上必然 0 命中；
    // 2. **句柄 id 是 32 位 GUID**（`showKc1CB4DBE3…`）而不是数字 —— 老代码要求"id 全是数字"，
    //    于是连句柄都认不出来（自检里的 `句柄 0`）；
    // 3. **学分值在元素属性里**（`yqzdxf` 要求 / `yxxf` 已修），不在正文文本里。
    //
    // 好消息是 **GUID 把 `<p>`、`showKc`、`thzt` 串在一起**，所以按 GUID 配对是稳的：
    // 节点 = `<p id='p{GUID}'>` 里的属性 + 它到 `showKc{GUID}` 之间的那段 JS 文本。

    /** `<p id='p{GUID}' …>`：类型节点的数据块（属性里就是学分） */
    private val JS_NODE_TAG = Regex("""<p[^>]*\bid\s*=\s*['"]p([0-9A-Za-z_-]+)['"][^>]*>""")

    /** 句柄：`showKc{GUID}`（⚠️ 别改回 `[0-9]+` —— 这个部署是 GUID） */
    private val JS_HANDLE_TAG = Regex("""showKc([0-9A-Za-z_-]{4,})""")

    private val JS_ATTR = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*['"]([^'"]*)['"]""")

    private fun parseTypesFromJsTemplate(html: String): BlockResult {
        return try {
            val source = "JS模板"
            // ⚠️ 同一个 GUID 在响应里会出现**十几次**（页面按学校代码分了一堆 JS 分支，
            // 每个分支都把同一棵树重写一遍），所以不能只留一次：
            // - 属性要**跨所有出现合并**（某个分支少写 yxxf，另一个分支有）；
            // - 名字要**逐个出现试**（有的分支把名字写在标签前面，有的写在后面）。
            // 只取 `associateBy` 留下的那一次 ⇒ 取到的是最后一次出现，
            // 而那一次恰好取不出名字 ⇒ **整条路 0 个类型**（2026-10 用真实响应复现过）。
            val tagsByGuid = LinkedHashMap<String, MutableList<MatchResult>>()
            for (m in JS_NODE_TAG.findAll(html)) {
                tagsByGuid.getOrPut(m.groupValues[1].trim()) { mutableListOf() }.add(m)
            }
            val handlePosByGuid = LinkedHashMap<String, MutableList<Int>>()
            for (m in JS_HANDLE_TAG.findAll(html)) {
                handlePosByGuid.getOrPut(m.groupValues[1].trim()) { mutableListOf() }.add(m.range.first)
            }
            if (handlePosByGuid.isEmpty()) return BlockResult(emptyList(), AcademicParseDiag(source = source))
            // 有 `<p id='p{GUID}'>` 的部署只认这些 —— 课程行/其它控件没有这个属性块，
            // 认进来就会把类型列表撑爆。
            val guids = if (tagsByGuid.isEmpty()) {
                handlePosByGuid.keys.toList()
            } else {
                handlePosByGuid.keys.filter { it in tagsByGuid }
            }
            if (guids.isEmpty()) {
                return BlockResult(
                    emptyList(),
                    AcademicParseDiag(handles = handlePosByGuid.size, source = source)
                )
            }

            val types = mutableListOf<AcademicTypeInfo>()
            var named = 0
            var renamed = 0
            for (guid in guids) {
                val tagRanges = tagsByGuid[guid].orEmpty()
                val handlePositions = handlePosByGuid.getValue(guid)
                val attrs = LinkedHashMap<String, String>()
                for (tag in tagRanges) {
                    JS_ATTR.findAll(tag.value).forEach { a ->
                        val k = a.groupValues[1].lowercase()
                        val v = a.groupValues[2].trim()
                        if (v.isNotEmpty() && attrs[k].isNullOrEmpty()) attrs[k] = v
                    }
                }
                // 名字与小字都在"数据块结束 → 句柄"之间那段（顺序：名字、要求、获得、未获得、门次…）
                val picked = pickJsSegment(html, tagRanges, handlePositions) ?: continue
                // 滤掉「创新创业情况 / 其他课程」这类占位分支（见 [isRealJsNode]）
                if (!isRealJsNode(guid, attrs)) continue
                named++
                // ⚠️ **学分与门次在互斥的两个分支里**，必须跨所有出现分别找：
                // 学校为同一节点渲染了四种分支（见 `real-page` 参考文件 1150/1156/1162/1169 行）：
                //   ① 只有学分（`yqzdxf` 有值、要求门次为空）
                //   ② 只有门次（学分空、要求门次有值）
                //   ③ 两者都有（学分行 + `<br/>` + 门次行）
                //   ④ 都没有（只有名字 —— 如「其他课程」）
                // 只挑一段 ⇒ **学分与门次永远不会同时拿到**（2026-10 用渲染后的真实页面当标准答案验出来的：
                // 标准答案里「通识教育平台 要求门次 35 / 达到要求 29 / 未达到要求 6」，而我们三个全空）。
                val segments = jsSegments(html, tagRanges, handlePositions)
                fun fromAnySegment(label: String): String? =
                    segments.firstNotNullOfOrNull { commentValue(it, label) }
                val segment = picked.text
                val required = normalizeCredit(attrs["yqzdxf"] ?: fromAnySegment("要求学分"))
                val earned = normalizeCredit(attrs["yxxf"] ?: fromAnySegment("获得学分"))
                // 门次三件套：**要求门次为空时整组作废**。
                // 理由：根节点（「主修」）的模板里 `达到要求:0` 是**硬编码**的 0（见参考文件 817 行），
                // 而学校页面上「主修」那一行根本没有任何门次文字 —— 留着会渲染出孤零零一句「到达 0」。
                val requiredCount = normalizeCount(fromAnySegment("要求门次"))
                val reachedRaw = fromAnySegment("到达要求") ?: fromAnySegment("达到要求")
                val missedRaw = fromAnySegment("未达到要求")
                val hasGate = requiredCount.isNotBlank()
                types.add(
                    AcademicTypeInfo(
                        name = picked.name,
                        id = guid,
                        requiredCredit = required,
                        earnedCredit = earned,
                        missedCredit = normalizeCredit(
                            fromAnySegment("未获得学分") ?: creditDelta(required, earned)
                        ),
                        requiredCount = requiredCount,
                        // ⚠️ 这一项在真实响应里有**两种用字**：JS 注释写「达到要求」，
                        // i18n 表里的值是「到达要求」（页面上显示的是后者）。只认一种 ⇒ 这格永远空。
                        reachedCount = if (hasGate) normalizeCount(reachedRaw) else "",
                        missedCount = if (hasGate) normalizeCount(missedRaw) else "",
                        parentId = parentIdOf(html, guid)
                    )
                )
            }
            BlockResult(
                types,
                AcademicParseDiag(
                    anchors = Regex("要求学分").findAll(html).count(),
                    handles = handlePosByGuid.size,
                    pairs = guids.size,
                    named = named,
                    kept = types.size,
                    renamed = renamed,
                    sample = types.firstOrNull()?.name.orEmpty(),
                    source = source
                )
            )
        } catch (_: Exception) {
            BlockResult(emptyList(), AcademicParseDiag(source = "JS模板"))
        }
    }

    /**
     * 统一的收尾：**层级**与**同名**。
     *
     * - `depth`：按 [AcademicTypeInfo.parentId] 链算出来（界面据此缩进，25 行平铺没人看得懂）；
     * - 同名节点：培养方案里**真的存在同名**（通识必修与通识选修下各有一个「身心健康类课程」）。
     *   这时用**父节点名**限定（`通识选修课程模块 · 身心健康类课程`），拿不到父名才退回 id 后缀。
     *   ⚠️ 名字必须保持唯一 —— 界面按类型分组列课程，名字一撞两组课程就串在一起
     *   （现在分组改用 id 了，但"两行一模一样"仍然没人看得懂）。
     */
    private fun finalizeTypes(types: List<AcademicTypeInfo>): Pair<List<AcademicTypeInfo>, Int> {
        if (types.isEmpty()) return types to 0
        val byId = types.associateBy { it.id }
        fun depthOf(t: AcademicTypeInfo, guard: Int = 0): Int {
            if (guard > 8) return 0
            val parent = t.parentId.takeIf { it.isNotBlank() }?.let { byId[it] } ?: return 0
            return 1 + depthOf(parent, guard + 1)
        }
        val out = mutableListOf<AcademicTypeInfo>()
        var renamed = 0
        for (t in types) {
            val parentName = byId[t.parentId]?.name.orEmpty()
            val name = if (out.any { it.name == t.name }) {
                renamed++
                if (parentName.isNotBlank()) "$parentName · ${t.name}" else "${t.name}（${t.id}）"
            } else {
                t.name
            }
            out += t.copy(name = name, depth = depthOf(t))
        }
        return out to renamed
    }

    /** 取到名字的那一段 JS 文本（名字 + 小字都在里面） */
    private class JsNodeSegment(val name: String, val text: String)

    /**
     * 这个节点算不算"真有数据的课程类型"。
     *
     * ⚠️ 判据是**显式的黑名单**，不是"id 像不像 GUID / 有没有学分"那类形状启发式
     * （2026-10 拿渲染后的真实页面当标准答案时抓到的）：
     * 「其他课程」这个**合法**节点，id 是固定的 `qtkcxfyq`、`yqzdxf` 与 `yxxf` **都是空串**
     * （它只渲染一句「共（4）门 通过（4）门」）—— 形状启发式会把它当占位分支丢掉，
     * 于是 App 里比学校页面**少一个类型**（23 vs 24），而"其他课程"恰恰是学生要看的"计划外的课"。
     *
     * 真正要丢的占位分支只有两个，它们的名字本身就是在说"没数据"：
     * - `cxcyqkxfyq` → 「创新创业情况尚未数据信息」
     * - `kcxfyq`（旧部署的同位分支）
     */
    private fun isRealJsNode(guid: String, attrs: Map<String, String>): Boolean {
        if (guid in PLACEHOLDER_NODE_IDS) return false
        // 走到这里的节点都带着 `showKc{id}` 句柄 —— 服务器只给"真类型"发这个句柄
        //（真实响应：125 个「要求学分」锚点 / 25 个句柄 / 25 个 `<p id='p{GUID}'>`，
        // 正好对应渲染后页面的 25 个节点）。所以**不再用形状判据**：
        // 它唯一的作用是"顺便"丢掉那两个占位分支，而那件事黑名单已经做了，
        // 代价却是把「其他课程」这种没有学分的真节点一起误杀（见上面 KDoc 的实测）。
        return true
    }

    /** GUID 形状（≥16 位十六进制） */
    private fun looksLikeGuid(id: String): Boolean =
        id.length >= 16 && id.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /**
     * 服务器把这两个分支渲染成"没有数据"的占位节点（名字就是在说没数据），
     * 其余短 id（如 `qtkcxfyq` = 其他课程）都是**真节点**。见 [isRealJsNode]。
     */
    private val PLACEHOLDER_NODE_IDS = setOf("cxcyqkxfyq", "kcxfyq")

    /**
     * 父节点 id：这个部署的父子关系写在 JS 里 —— 子节点的 HTML 最后 `.appendTo($("#li{父GUID}"))`。
     *
     * 取值窗口 = 「本节点的数据块」到「下一个 `<li id='li…`」之间：一个节点的语句可能因为
     * 按学校代码分了一堆分支而很长，但绝不会跨到下一个节点去；而顶层节点 appendTo 的是
     * `$("#ul…")`，窗口里根本没有 `#li` ⇒ 自然没有父节点。
     */
    private fun parentIdOf(html: String, guid: String): String {
        val from = html.indexOf("id='p$guid'").let { if (it >= 0) it else html.indexOf("id=\"p$guid\"") }
        if (from < 0) return ""
        val to = html.indexOf("<li id='li", from).let { if (it >= 0) it else html.length }
        val window = html.substring(from, minOf(to, from + 20_000))
        return Regex("""appendTo\(\$\("#li([0-9A-Za-z_-]+)"\)\)""").find(window)?.groupValues?.get(1).orEmpty()
    }

    /**
     * **所有**出现对应的那段文本（供"逐字段跨分支合并"用）。
     *
     * 为什么需要它（2026-10 用渲染后的真实页面对照出来的）：学校为**同一个节点**渲染了
     * 四个**互斥**分支 ——「只有学分」「只有门次」「学分+门次」「都没有」。
     * 只取一段（[pickJsSegment] 的做法，为了取名字）⇒ 学分与门次**永远不可能同时拿到**：
     * 标准答案里「通识教育平台 要求门次 35 / 达到要求 29 / 未达到要求 6」，而我们三个全空。
     */
    private fun jsSegments(
        html: String,
        tags: List<MatchResult>,
        handlePositions: List<Int>,
    ): List<String> = tags.map { tag ->
        val tagEnd = tag.range.last + 1
        val nextHandle = handlePositions.filter { it > tagEnd }.minOrNull()
        val hardEnd = minOf(html.length, tagEnd + 2_000)
        // ⚠️ 段落的右边界必须卡在**本节点自己的那一次 jQuery 调用**里：
        // 一个节点的语句以 `…appendTo($("#li…"))` 收尾，越过它就会读到**隔壁节点**的小字
        // ——2026-10 实测串到了「其他选修课程」身上（标准答案里它没有门次行，我们却读出「到达 2」）。
        val boundary = listOfNotNull(
            nextHandle,
            html.indexOf("\").appendTo(", tagEnd).takeIf { it > tagEnd },
            html.indexOf("').appendTo(", tagEnd).takeIf { it > tagEnd },
        ).filter { it in (tagEnd + 1)..hardEnd }.minOrNull() ?: hardEnd
        html.substring(tagEnd, boundary)
    }

    /**
     * 在同一个 GUID 的所有出现里挑出"能取出名字"的那一次，并返回那一段文本。
     *
     * 顺序：① 标签结束 → **紧随其后的**那个句柄之间；② 标签往后的 400 字；
     * ③ 标签之前的 300 字（有的分支把名字写在标签前面）。
     * 为什么要"逐个出现试"：同一个 GUID 会被十几个分支各写一遍，其中有些分支
     * 那一截里根本没有名字 —— 只挑一次就是在赌运气（线上就是这么 0 命中的）。
     */
    private fun pickJsSegment(
        html: String,
        tags: List<MatchResult>,
        handlePositions: List<Int>,
    ): JsNodeSegment? {
        for (tag in tags) {
            val tagEnd = tag.range.last + 1
            val nextHandle = handlePositions.filter { it > tagEnd }.minOrNull()
            val forwards = nextHandle?.let { html.substring(tagEnd, it) }
                ?: html.substring(tagEnd, minOf(html.length, tagEnd + 400))
            jsNameOf(forwards)?.let { return JsNodeSegment(it, forwards) }
            val backwards = html.substring(maxOf(0, tag.range.first - 300), tag.range.first)
            jsNameOf(backwards)?.let { return JsNodeSegment(it, backwards) }
        }
        return null
    }

    /**
     * 从一段 JS 文本里取类型名。真实页面有**三种写法**（同一份响应里都出现过，别只按一种写）：
     *
     * ```js
     * `…sftg='0'>主修" +`              // ① 名字和标签在同一个 JS 字符串里
     * `…sftg='0'>主修" + "&nbsp;…"`    // ② 同上，后面再拼一段
     * `…sftg='0'>" + "主修&nbsp;" +`   // ③ 名字在紧随其后的那个字面量里
     * ```
     *
     * ① ② 里名字在**段首那个引号之前**，③ 里它在**下一个**字面量里 ——
     * 只按 ③ 写就会一个类型都取不到名字（2026-10 拿真实响应复现过：整页只剩正则兜底那一个）。
     *
     * 另外只认**含汉字**的候选：不这么挡，名字会被取成 `yqxf` 或 ` + `，
     * 而且**所有类型都取成同一个**（去重再吞掉，又只剩一个）。
     */
    private fun jsNameOf(segment: String): String? {
        // ① / ②：段首到第一个引号之间的文字
        jsNameCandidate(segment.substringBefore('"').substringBefore('\''))?.let { return it }
        // ③：段首那个引号是"上一个字符串的收尾引号"，所以先按跳过一个引号配对，再按常规配对
        for (startQuote in listOf(1, 0)) {
            for (raw in jsLiterals(segment, startQuote)) {
                jsNameCandidate(raw)?.let { return it }
            }
        }
        return null
    }

    /**
     * 顺序切出 JS 字符串字面量（[startQuote] = 从第几个引号开始当"开引号"）。
     *
     * ⚠️ 两个坑，都踩过：
     * 1. **不能用正则 `findAll`**：它匹配互不重叠的区间，而这里的名字恰好夹在两段被引号
     *    "吃掉"的文本中间（`" + "主修&nbsp;" + "$.i18n.get('yqxf')`），于是名字永远当不上
     *    匹配起点，取出来的是 ` + `、`$.i18n.get(` 这些渣；
     * 2. **段首那个引号是收尾引号**（`<p …>` 就在某个 JS 字符串里），照常规配对会整体错一位，
     *    于是又取到 ` + ` 而不是名字。
     */
    private fun jsLiterals(segment: String, startQuote: Int = 1, limit: Int = 6): List<String> {
        val quotes = mutableListOf<Int>()
        segment.forEachIndexed { i, c -> if (c == '"' || c == '\'') quotes.add(i) }
        val out = mutableListOf<String>()
        var k = startQuote
        while (k + 1 < quotes.size && out.size < limit) {
            out.add(segment.substring(quotes[k] + 1, quotes[k + 1]))
            k += 2
        }
        return out
    }

    /** JS 模板里的类型名：必须是中文名（这个部署的类型名都是中文，见 [jsNameOf]） */
    private fun jsNameCandidate(raw: String): String? {
        // ⚠️ 先切掉行内的 HTML：名字后面常常紧跟标签，例如「其他课程」这一支是
        // `其他课程<span id='showKcqtkcxfyq'>…`（和那些后面接 `&nbsp;` + i18n 调用的节点不同），
        // 不切就会取到 `其他课程<span id=` 这种带标签的名字 —— 单测抓到的。
        val head = raw.substringBefore('<').replace("&nbsp;", " ")
        val s = cleanNameToken(head)
        if (s.length < 2 || s.length > 20) return null
        if (s.none { it in '\u4e00'..'\u9fff' }) return null
        if (s.contains("学分") || s.contains("门次") || s.contains("要求") || s.contains("成绩")) return null
        if (s in JUNK_NAMES) return null
        return s
    }

    /**
     * 取 JS 注释标签后面的值。
     *
     * 原文形态是「i18n 调用 + 注释 + 字面值」：`$.i18n.get('yqxf')` 紧跟一个写着「要求学分」的
     * 块注释，再紧跟 `":170.0"` —— 于是这个部署的**标签就是注释里的中文**。
     *
     * ⚠️ 这段说明里刻意不写块注释的完整符号：KDoc 里出现那对符号会**提前结束注释**
     * （这次就踩了，编译器报的却是一串"Expecting member declaration"）。
     */
    private fun commentValue(segment: String, label: String): String? =
        Regex(
            """/\*\s*""" + Regex.escape(label) +
                """\s*\*/\s*(?:\+\s*)?["'`]?\s*[:：]?\s*([0-9]+(?:\.[0-9]+)?)"""
        ).find(segment)?.groupValues?.getOrNull(1)

    /** 未获得学分没写时用「要求 − 已修」补（这个部署两者都有，仅作兜底） */
    private fun creditDelta(required: String, earned: String): String? {
        val a = required.toDoubleOrNull() ?: return null
        val b = earned.toDoubleOrNull() ?: return null
        val d = a - b
        if (d <= 0.0) return null
        return if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    }

    private class BlockResult(val types: List<AcademicTypeInfo>, val diag: AcademicParseDiag)

    /**
     * 结构解析：把每个 `showKc{id}` 与「离它最近的一处学分栏」配对（保持文档顺序）。
     *
     * ## 为什么不能按"前后顺序"硬认
     *
     * 2026-10 连着两版都栽在这里：
     * - 第一版要求「学分文字排在 `showKc` 之前」⇒ 线上一个类型都解析不出来（整页空白）；
     * - 第二版各挑"最近的"⇒ 下一个类型的学分常常比自己的还近，于是串号。
     *
     * 现在按**两个序列的对齐**做（[matchAnchors]）：保持顺序、尽量多配、总距离最小。
     *
     * ## 取名：先 DOM，后退回文本
     *
     * 类型名通常是**兄弟节点的文字**（`<td>思想政治类课程</td>`），而句柄文字（`查看`）
     * 可能就插在名字和学分之间 —— 只按"学分前面那串文字"取名会把每个类型都取成「查看」，
     * 然后**去重把 11 个类型吞成 1 个**（这就是"只剩下一个类型"的来历）。
     * 所以先按 DOM 往前找兄弟元素，取不到再用文本回退。
     *
     * ## 还有一个底线：**名字撞车不许丢类型**
     *
     * 撞车时用 id 区分（`课程（12）`）。名字可疑是**看得见**的，比静默少 11 个类型强得多。
     */
    private fun parseTypesByBlock(html: String, source: String): BlockResult {
        return try {
            val doc = Jsoup.parse(html)
            val page = flattenPage(doc)
            if (page.text.isBlank() || page.spans.isEmpty()) {
                return BlockResult(
                    emptyList(),
                    AcademicParseDiag(handles = page.spans.size, source = source)
                )
            }
            val anchors = anchorsOf(page.text)
            // 句柄 id：⚠️ 不能要求"纯数字" —— 烟理这个部署是 32 位 GUID（`showKc1CB4DBE3…`），
            // 要求数字就会一个句柄都留不下（自检里的"句柄 0"就是这么来的）。
            val usable = page.spans.withIndex().filter { (_, s) -> isIdLike(s.id) }
            val pairs = matchAnchors(anchors, usable.map { it.value })

            val types = mutableListOf<AcademicTypeInfo>()
            val usedIds = HashSet<String>()
            var named = 0
            var renamed = 0
            for ((anchor, usableIndex) in pairs) {
                val mark = usable[usableIndex].value
                if (!usedIds.add(mark.id)) continue
                val next = anchors.firstOrNull { it > anchor }
                val segment = page.text.substring(anchor, minOf(next ?: page.text.length, anchor + 1200))
                val base = pickName(page, anchor)
                // 培养方案的根节点（「2023级软件工程」这类）不是课程类型：它有自己的学分栏，
                // 但不是能点开看课程的类型，列进来就是一行噪音。
                if (base.skip) continue
                if (base.name != null) named++
                val unique = uniqueName(base.name, mark.id, types)
                if (base.name != null && unique != base.name) renamed++
                types.add(
                    AcademicTypeInfo(
                        name = unique,
                        id = mark.id,
                        requiredCredit = normalizeCredit(creditAfter(segment, REQ_LABEL)),
                        earnedCredit = normalizeCredit(creditAfter(segment, EARN_LABEL)),
                        missedCredit = normalizeCredit(creditAfter(segment, MISS_LABEL)),
                        // 门次那一行排在本类型学分下面（也可能排在"查看"链接下面），两种都在这一段内：
                        // 段的终点是**下一个类型**的「要求学分」，所以不会串到下一类型。
                        requiredCount = normalizeCount(creditAfter(segment, GATE_REQ_LABEL)),
                        reachedCount = normalizeCount(creditAfter(segment, GATE_REACHED_LABEL)),
                        missedCount = normalizeCount(creditAfter(segment, GATE_MISSED_LABEL)),
                        totalCourses = normalizeCount(parenCount(segment, "共")),
                        passedCourses = normalizeCount(parenCount(segment, "通过"))
                    )
                )
            }
            BlockResult(
                types,
                AcademicParseDiag(
                    anchors = anchors.size,
                    handles = page.spans.size,
                    pairs = pairs.size,
                    named = named,
                    kept = types.size,
                    renamed = renamed,
                    sample = types.firstOrNull()?.name.orEmpty(),
                    source = source
                )
            )
        } catch (_: Exception) {
            BlockResult(emptyList(), AcademicParseDiag(source = source))
        }
    }

    /**
     * 「要求学分」里哪些是**真的学分栏**。
     *
     * 判据：后面 200 字符内还有一档「未获得学分」，且它后面跟着数字或「无」。
     * 用它当判据的理由：它一定排在最后（要求 / 获得 / 未获得），而页面里若有
     * "要求学分 / 获得学分 / 未获得学分"这种**列标题**，它后面没有任何值 ⇒ 天然被排除
     * （多一个锚点会把后面所有类型顶偏一位）。
     */
    private fun anchorsOf(text: String): List<Int> =
        Regex("要求学分").findAll(text)
            .map { it.range.first }
            .filter { idx ->
                creditAfter(text.substring(idx, minOf(text.length, idx + 200)), MISS_LABEL) != null
            }
            .toList()

    /** 培养方案根节点 = 汇总环用的"总计"（两级树求和会重复计算，见 [AcademicIndexResult.rootType]） */
    internal fun rootTypeOf(types: List<AcademicTypeInfo>): AcademicTypeInfo? =
        types.firstOrNull { it.name in ROOT_TYPE_NAMES }

    /** 类型句柄 id 的合法形状：ASCII 字母/数字/下划线 —— 见顶层 [isAcademicTypeId] 的说明 */
    internal fun isIdLike(id: String): Boolean = isAcademicTypeId(id)

    /**
     * 名字撞车时用 id 区分 —— **绝不因为重名丢掉一个类型**。
     * 名字难看是看得见的（用户会报），少一个类型是看不见的（用户只会觉得"课少了"）。
     */
    private fun uniqueName(base: String?, id: String, existing: List<AcademicTypeInfo>): String {
        val candidate = base ?: "类型 $id"
        if (existing.none { it.name == candidate }) return candidate
        return "$candidate（$id）"
    }

    /** 取名的结论：拿到名字 / 这是培养方案根节点（跳过）/ 取不到名字（保留，用兜底名） */
    private class NamePick(val name: String? = null, val skip: Boolean = false)

    /** 先按 DOM 往前找兄弟元素，再退回文本扫描 */
    private fun pickName(page: PageText, anchor: Int): NamePick {
        val dom = nameFromDom(page.ownerOf(anchor))
        if (dom != null) return if (isRootType(dom)) NamePick(skip = true) else NamePick(name = dom)
        return typeNameOf(page.text.substring(maxOf(0, anchor - 160), anchor))
    }

    private fun isRootType(s: String): Boolean =
        Regex("""^\d{4}\s*级""").containsMatchIn(s.trim())

    /**
     * 从锚点所在元素**往文档前面**找类型名：名字通常是前一个兄弟元素的文字
     * （`<td>思想政治类课程</td>`），这样就不受"句柄文字插在名字和学分之间"的影响。
     */
    private fun nameFromDom(owner: Element?): String? {
        var node = owner
        var hops = 0
        while (node != null && hops < 12) {
            var sibling = node.previousElementSibling()
            while (sibling != null && hops < 12) {
                nameCandidate(sibling.ownText())?.let { return it }
                sibling = sibling.previousElementSibling()
                hops++
            }
            node = node.parent()
            hops++
        }
        return null
    }

    /** 一段文字够不够格当类型名（挡掉「查看」这类按钮文字、数字与括号） */
    private fun nameCandidate(raw: String): String? {
        val s = cleanNameToken(raw)
        if (s.length < 2 || s.length > 20) return null
        if (!s.any { it.isLetter() }) return null
        if (s.contains("学分") || s.contains("门次") || s.contains("要求") || s.contains("成绩")) return null
        if (s in JUNK_NAMES) return null
        return s
    }

    /** 去掉引号/括号/冒号这类包裹字符（引号往往属于相邻的另一个类型名，只剥整串会剥错） */
    private fun cleanNameToken(raw: String): String =
        raw.trim().trim('"', '\'', '：', ':', '（', '）', '(', ')', '【', '】', '·', '、', '-', '|')

    private val JUNK_NAMES = setOf("查看", "点击查看", "查看详情", "详情", "点击", "展开", "收起", "更多")

    /** 培养方案根节点的名字：这些不是"课程类型"，而是整棵树的总计（学分 = 总要求/总已修） */
    private val ROOT_TYPE_NAMES = setOf("主修", "辅修", "全部", "总计", "总学分")

    /** 平铺后的页面文本 + 每个 `showKc` 在其中的位置（配对要算距离、切段） */
    private class SpanMark(val id: String, val start: Int, val end: Int)

    private class PageText(
        val text: String,
        val spans: List<SpanMark>,
        /** 每个文本片段起始偏移 → 它所属的元素（用来从锚点回到 DOM 找类型名） */
        val owners: List<Pair<Int, Element>>,
    ) {
        fun ownerOf(offset: Int): Element? = owners.lastOrNull { it.first <= offset }?.second
    }

    /**
     * 按文档顺序摊平 body 的文本，同时记下每个 `showKc` span 的字符区间与
     * **每个文本片段的宿主元素**（取名要用）。
     * `<script>` / `<style>` 的内容是 `DataNode` 而非 `TextNode`，天然不会混进来。
     */
    private fun flattenPage(doc: org.jsoup.nodes.Document): PageText {
        // 从 body 走起：`<script>` 在 head/body 里都可能出现，但它们是 DataNode 不是 TextNode，
        // 所以哪怕从 body 摊平也不会把 JS 源码读成页面文字。
        val root: Element = doc.body()
        val sb = StringBuilder()
        val spans = mutableListOf<SpanMark>()
        val owners = mutableListOf<Pair<Int, Element>>()
        fun walk(node: Node) {
            if (node is TextNode) {
                (node.parent() as? Element)?.let { owners.add(sb.length to it) }
                sb.append(node.wholeText).append(' ')
                return
            }
            if (node is Element && node.id().startsWith("showKc")) {
                val start = sb.length
                for (child in node.childNodes()) walk(child)
                spans.add(SpanMark(node.id().removePrefix("showKc").trim(), start, sb.length))
                return
            }
            for (child in node.childNodes()) walk(child)
        }
        for (child in root.childNodes()) walk(child)
        return PageText(sb.toString(), spans, owners)
    }

    /** 字符距离：落在 span 区间之前/之后都算到最近的一端 */
    private fun distanceTo(index: Int, mark: SpanMark): Int = when {
        index < mark.start -> mark.start - index
        index > mark.end -> index - mark.end
        else -> 0
    }

    /**
     * 把锚点（「要求学分」出现的位置）与 span 配对：**先尽量多配，再让总距离最小**，
     * 且配对必须**保持文档顺序**（单调）—— 这就是两个序列的对齐问题。
     *
     * ## 为什么不能各自挑"最近的那个"
     *
     * 单看距离会挑错：学分排在 span **后面**的部署里，上一个类型的学分离下一个 span 也很近，
     * 于是"最近"会把下一类型的数据挂到本类型上。而**单调**约束（`a1<a2 ⇒ s1<s2`）
     * 正好符合页面事实 —— 类型在文档里的先后就是它们的先后。
     *
     * ## 为什么成本要带一个负数奖励
     *
     * 只求"总距离最小"时，**少配一对**（成本 0）比多配一对（成本 = 距离 > 0）更划算，
     * 于是解析器会为了省几十个字符丢掉一整个类型。所以每配一对先给一笔 `MATCH_BONUS` 奖励，
     * 距离只用来在"配对数相同"的方案里挑最整齐的那个。
     *
     * 距离超过 [MAX_ANCHOR_DISTANCE] 的配对**直接不允许**：宁可漏一个类型（还有正则兜底），
     * 也不要把别的类型的数据挂上来。
     *
     * ⚠️ 上限刻意给得很宽（整页长度量级）：真实页面里句柄与学分栏可能隔着整张课程表，
     * 上限设小了会出现"只配上第一个类型"这种**看起来像解析成功**的故障。
     */
    private fun matchAnchors(anchors: List<Int>, spans: List<SpanMark>): List<Pair<Int, Int>> {
        val n = anchors.size
        val m = spans.size
        if (n == 0 || m == 0) return emptyList()
        val inf = Int.MAX_VALUE / 4
        val dp = Array(n + 1) { IntArray(m + 1) { inf } }
        dp[0][0] = 0
        for (i in 0..n) {
            for (j in 0..m) {
                val cur = dp[i][j]
                if (cur >= inf) continue
                if (i < n) dp[i + 1][j] = minOf(dp[i + 1][j], cur)
                if (j < m) dp[i][j + 1] = minOf(dp[i][j + 1], cur)
                if (i < n && j < m) {
                    val d = distanceTo(anchors[i], spans[j])
                    if (d <= MAX_ANCHOR_DISTANCE) {
                        val cost = cur + d - MATCH_BONUS
                        if (cost < dp[i + 1][j + 1]) dp[i + 1][j + 1] = cost
                    }
                }
            }
        }
        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            val d = distanceTo(anchors[i - 1], spans[j - 1])
            val matched = if (d <= MAX_ANCHOR_DISTANCE && dp[i - 1][j - 1] < inf) {
                dp[i - 1][j - 1] + d - MATCH_BONUS
            } else {
                inf
            }
            when {
                matched < inf && matched == dp[i][j] -> {
                    pairs.add(anchors[i - 1] to (j - 1))
                    i--
                    j--
                }
                dp[i - 1][j] == dp[i][j] -> i--
                else -> j--
            }
        }
        pairs.reverse()
        return pairs
    }

    /** 锚点与自己的 span 之间的距离上限（正常只有"名称 + 学分"这么几十个字符） */
    private val MAX_ANCHOR_DISTANCE = 20_000

    /** 每配上一对的奖励，远大于任何距离，保证"先尽量多配" */
    private val MATCH_BONUS = 100_000

    /**
     * 正则兜底（整页匹配）。⚠️ **两个方向的组号不一样**，所以组号是显式传进去的，
     * 不靠"记得住" —— 老代码就是把 `4=未获得学分` 当成了 `4=id`（见 [parseTypeStatistics]）。
     *
     * `internal` 是为了让单测能直接喂 HTML 验证组号不再错位。
     */
    internal fun parseTypesByRegex(html: String): List<AcademicTypeInfo> {
        if (html.isBlank()) return emptyList()
        // 方向一：学分在前、showKc 在后（老代码认的形状）
        val forward = typesFromRegex(
            html,
            Regex(
                """["']?([^"'<>&]{1,24})["']?(?:\s|&nbsp;)*要求学分.*?[:：]([0-9]+[.][0-9]+|0|&nbsp;|无).*?获得学分.*?[:：]([0-9]+[.][0-9]+|0|&nbsp;|无).*?未获得学分.*?[:：]([0-9]+[.][0-9]+|0|&nbsp;|无)[\s\S]*?showKc([0-9A-Za-z_]+)"""
            ),
            nameGroup = 1, requiredGroup = 2, earnedGroup = 3, missedGroup = 4, idGroup = 5
        )
        if (forward.isNotEmpty()) return forward
        // 方向二：showKc 在前、学分在后（空 span 在标题前面的部署）
        return typesFromRegex(
            html,
            Regex(
                """showKc([0-9A-Za-z_]+)[\s\S]{0,600}?["']?([^"'<>&]{1,24})["']?(?:\s|&nbsp;)*要求学分.*?[:：]([0-9]+[.][0-9]+|0|&nbsp;|无).*?获得学分.*?[:：]([0-9]+[.][0-9]+|0|&nbsp;|无).*?未获得学分.*?[:：]([0-9]+[.][0-9]+|0|&nbsp;|无)"""
            ),
            nameGroup = 2, requiredGroup = 3, earnedGroup = 4, missedGroup = 5, idGroup = 1
        )
    }

    private fun typesFromRegex(
        html: String,
        pattern: Regex,
        nameGroup: Int,
        requiredGroup: Int,
        earnedGroup: Int,
        missedGroup: Int,
        idGroup: Int,
    ): List<AcademicTypeInfo> {
        val seen = LinkedHashMap<String, AcademicTypeInfo>()
        for (m in pattern.findAll(html)) {
            val g = m.groupValues
            val name = g.getOrNull(nameGroup).orEmpty().trim().trim('"', '\'')
            val id = g.getOrNull(idGroup).orEmpty().trim()
            if (name.isBlank() || name.length > 20 || name.contains("学分")) continue
            // 兜底路径也要跳培养方案根节点，否则「结构解析漏了类型」时它会跟着混进来
            if (isRootType(name)) continue
            if (!isAcademicTypeId(id)) continue
            if (seen.containsKey(name)) continue
            seen[name] = AcademicTypeInfo(
                name = name,
                id = id,
                requiredCredit = normalizeCredit(g.getOrNull(requiredGroup)),
                earnedCredit = normalizeCredit(g.getOrNull(earnedGroup)),
                missedCredit = normalizeCredit(g.getOrNull(missedGroup))
            )
        }
        return seen.values.toList()
    }

    /**
     * 文本回退取名：从「要求学分」前面那一小段文字里**从后往前**找第一段像名字的。
     *
     * 它只是 [nameFromDom] 的备胎：句柄文字（`查看`）会混进这段文字里，
     * 只靠它取名会把所有类型取成同一个词（然后被去重吞掉）。
     */
    private fun typeNameOf(head: String): NamePick {
        val tokens = head.replace(Regex("""[\s\u00a0]+"""), " ").trim().split(' ').asReversed()
        for (raw in tokens) {
            // ⚠️ 根节点判据要排在 nameCandidate **前面**：它本身"像个名字"（8 个字、有汉字），
            // 放到后面就永远轮不到 —— 于是「2023级软件工程」会被当成一个课程类型列出来。
            if (isRootType(cleanNameToken(raw))) return NamePick(skip = true)
            nameCandidate(raw)?.let { return NamePick(name = it) }
        }
        return NamePick()
    }

    /** 三档学分的标签。⚠️ 「未获得学分」里含「获得学分」，所以后者要排除前面带「未」的情形 */
    private val REQ_LABEL = Regex("""要求学分""")
    private val EARN_LABEL = Regex("""(?<!未)获得学分""")
    private val MISS_LABEL = Regex("""未获得学分""")

    // 门次那一行（同样是「未达到要求」里含「达到要求」，所以到达要求要排除前面带「未」的情形）
    private val GATE_REQ_LABEL = Regex("""要求门次""")
    private val GATE_REACHED_LABEL = Regex("""到达要求""")
    private val GATE_MISSED_LABEL = Regex("""未达到要求""")

    /** 取标签后紧跟的第一个数字；认不出返回 null（`无` 也认，交给 [normalizeCount] 丢掉） */
    private fun creditAfter(segment: String, label: Regex): String? {
        val m = label.find(segment) ?: return null
        val tail = segment.substring(m.range.last + 1)
        return Regex("""^\D{0,4}?([0-9]+(?:\.[0-9]+)?|无)""").find(tail)?.groupValues?.getOrNull(1)
    }

    /** `共（14）门` / `通过（12）门` —— 全角半角括号都认 */
    private fun parenCount(text: String, prefix: String): String? =
        Regex("""$prefix\s*[（(]\s*([0-9]+)\s*[)）]\s*门""").find(text)?.groupValues?.getOrNull(1)

    /**
     * 学分归一：只把**脏值**（缺失 / `无` / `&nbsp;` / 破折号）给空串，**`0.0` 要原样保留**。
     *
     * ⚠️ 2026-10 用户报「为零分的课程类型没有进度条」，根因就在这里：
     * 本函数原先顺手把 `<= 0` 也丢掉（那条规则的**真实目的**只是不想显示「还差 0.0 学分」），
     * 可「获得学分:0.0」是**真值** —— 学校页面上就写着它（`获得学分:0.0`，一门没过的模块）。
     * 丢掉之后分子成了 null，界面算不出比例 ⇒ **整条进度条不画**，
     * 于是"一门都没过的模块"看起来像"学校没给数据"，而这恰恰是学生最该看到的模块。
     *
     * 「还差」那一行由界面按**数值**判断（`> 0` 才显示），不再依赖这里丢 0。
     */
    private fun normalizeCredit(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ""
        if (v == "&nbsp;" || v == "无" || v == "—" || v == "-" || v == "--") return ""
        return v
    }

    /**
     * 门次/门数归一：⚠️ 这里**不能**照 [normalizeCredit] 把 0 丢掉 ——
     * 「未达到要求:0」是"这门课的门次已经够了"这个**结论**，丢掉就变成"学校没给数据"，
     * 两种情况的界面文案不一样（前者不显示"还差"，后者整段不显示）。
     */
    private fun normalizeCount(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ""
        if (v == "&nbsp;" || v == "无" || v == "—" || v == "-" || v == "--") return ""
        return v
    }

    // ── 明细 JSON 解析 ──

    internal fun parseCourseItems(
        jsonText: String,
        typeName: String = "",
        typeId: String = "",
    ): List<AcademicCourseItem> {
        return try {
            // 容器名与嵌套深度都交给公共工具 —— 见 ZhengfangUtils.findRowArray / flattenRow 的说明
            val arr = ZhengfangUtils.findRowArray(jsonText) ?: return emptyList()
            arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = ZhengfangUtils.flattenRow(el.asJsonObject)
                val courseName = ZhengfangUtils.jsonField(o, "KCMC", "kcmc").trim()
                if (courseName.isBlank()) return@mapNotNull null
                AcademicCourseItem(
                    typeName = typeName,
                    typeId = typeId,
                    courseName = courseName,
                    courseId = cleanText(ZhengfangUtils.jsonField(o, "KCH", "kch")),
                    credit = normalizeCourseNumber(ZhengfangUtils.jsonField(o, "XF", "xf")),
                    nature = cleanText(ZhengfangUtils.jsonField(o, "KCXZMC", "kcxzmc")),
                    category = cleanText(ZhengfangUtils.jsonField(o, "KCLBMC", "kclbmc")),
                    maxGrade = normalizeCourseNumber(
                        ZhengfangUtils.jsonField(o, "MAXCJ", "maxcj"),
                        keepWords = true
                    ),
                    // 「成绩」与「最大成绩」是同一行的两列（挂科补考时两列不同：成绩 51 / 最大成绩 55），
                    // 所以别名要分开写，不能拿 CJ 当 MAXCJ 的兜底。
                    score = normalizeCourseNumber(ZhengfangUtils.jsonField(o, "CJ", "cj"), keepWords = true),
                    gradePoint = normalizeCourseNumber(ZhengfangUtils.jsonField(o, "JD", "jd")),
                    // ⚠️ 别名表刻意取窄：`XS` 这种两字母的通用名在别的字段上也可能出现，
                    // 猜错就会显示错数据 —— 而"错误数据"比"空着"更糟（用户会以为系统算错了）。
                    // `XSXXXX` 是 2026-10 从真实响应里读出来的（教务页写「理论(2.0)-实践(4.0)」）。
                    hours = cleanText(
                        ZhengfangUtils.jsonField(o, "XSXXXX", "xsxxxx", "KCXS", "kcxs", "ZXS", "zxs")
                    ),
                    makeupScore = normalizeCourseNumber(
                        // ⚠️ 补考列 = `MBKCJ`（不是想当然的 `BKCJ`）—— 抄自学校自己的填表代码
                        ZhengfangUtils.jsonField(o, "MBKCJ", "mbkcj", "BKCJ", "bkcj", "BK", "bk"),
                        keepWords = true
                    ),
                    retakeScore = retakeOf(o),
                    status = statusLabel(
                        ZhengfangUtils.jsonField(o, "XDZT", "xdzt"),
                        ZhengfangUtils.jsonField(o, "MAXCJ", "maxcj")
                    ),
                    term = buildTerm(o),
                    suggestedTerm = buildSuggestedTerm(o)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cleanText(raw: String): String = raw.trim().takeUnless { it.isBlank() || it == "&nbsp;" } ?: ""

    /**
     * 修读状态码 → 文字。⚠️ **这张表抄自学校自己的渲染代码**，不是猜的：
     * `/jwglxt/js/comp/jwglxt/xjgl/xyqk/cxXsxyqkIndex.js` 的 `getXdzt()` + i18n 文件
     * `N105515_zh_CN.js`（`zx`=在修 / `wg`=未过 / `wx`=未修 / `yx`=已修）。
     *
     * 顺带解开一个谜：**教务页面上这一列看着是空的** —— 它渲染成彩色小图标 + tooltip
     * （`<div class="png_ico_tjxk tjxk4" title="已修">`），页面文本里没有字，
     * 所以从浏览器复制页面时那一列自然是空的（用户贴回来的页面正是如此）。
     *
     * 认不出的码（将来新增的 `11` 之类）一律不显示 —— 猜就是编数据。
     */
    private fun statusLabel(raw: String, maxGrade: String): String {
        val v = cleanText(raw)
        if (v.isEmpty()) return ""
        // 已经是文字（别的部署）→ 直接用，别套码表
        if (v.any { it !in '0'..'9' }) return v
        // 学校代码里这条在最前面：最大成绩"未开放"也算在修
        if (v == "1" || maxGrade.trim() == "未开放") return "在修"
        return when (v) {
            "2" -> "未过"
            "3" -> "未修"
            "4", "21" -> "已修"
            "5" -> "校内被替代课程"
            "6" -> "校内课程替代"
            "7" -> "校内课程替代节点"
            "8" -> "校外课程替换节点"
            "9" -> "校内被认定课程"
            "10" -> "学业预警不审核课程"
            else -> ""
        }
    }

    /**
     * 重修成绩。这个部署用 `MCXCJ`；另一些部署（`xxdm==11483`）把重修拆成两列 `CXCJ1` / `CXCJ2`
     * —— 两种都认，两列都有时用 ` / ` 连起来（抄自学校的 `setTbodyHtml`）。
     */
    private fun retakeOf(o: JsonObject): String {
        val single = normalizeCourseNumber(
            ZhengfangUtils.jsonField(o, "MCXCJ", "mcxcj", "CXCJ", "cxcj", "ZXCJ", "zxcj", "CX", "cx"),
            keepWords = true
        )
        if (single.isNotBlank()) return single
        // ⚠️ 两种拼写要写在**同一次** jsonField 调用里：分成两次调用会把同一个键取两遍，
        // 结果变成「55 / 55 / 62 / 62」（单测抓到的）
        return listOf(
            ZhengfangUtils.jsonField(o, "CXCJ1", "cxcj1"),
            ZhengfangUtils.jsonField(o, "CXCJ2", "cxcj2"),
        ).map { normalizeCourseNumber(it, keepWords = true) }
            .filter { it.isNotBlank() }
            .joinToString(" / ")
    }

    /**
     * 明细里的数字字段归一。正方在这些列上会写 `无` / `-` / `&nbsp;`（zfn_api 专门有个
     * `align_floats` 就是在处理这个），不归一的话界面就会出现「学分 无」「绩点 -」这种字样；
     * 而**空列与「无」在用户眼里是两件事** —— 前者是没取到，后者是学校写明了没有。
     *
     * [keepWords] 给成绩列用：`缺考` / `缓考` / `免修` / `优秀` 是真信息，要留着。
     */
    private fun normalizeCourseNumber(raw: String, keepWords: Boolean = false): String {
        val v = cleanText(raw)
        if (v.isEmpty()) return ""
        if (v == "无" || v == "—" || v == "-" || v == "--") return ""
        if (keepWords && v.toDoubleOrNull() == null) return v
        return if (v.toDoubleOrNull() != null) v else ""
    }

    /**
     * **实际**修读学年学期 = 「成绩学年 / 学期」那一对：`XNMC`(2024-2025) + `XQMMC`(1)。
     * 见 [AcademicCourseItem.suggestedTerm] 的说明：另有一对 `JYXD*` 是**建议**修读，别混。
     */
    private fun buildTerm(o: JsonObject): String = buildTermFrom(
        o,
        yearNames = arrayOf("XNMC", "xnmc"),
        yearCodes = arrayOf("XNM", "xnm"),
        termNames = arrayOf("XQMMC", "xqmmc"),
        termCodes = arrayOf("XQM", "xqm"),
    )

    /** **建议**修读学年学期 = `JYXDXNMC` + `JYXDXQMC`（没有名称字段时退回编码 `JYXDXNM` / `JYXDXQM`） */
    private fun buildSuggestedTerm(o: JsonObject): String = buildTermFrom(
        o,
        yearNames = arrayOf("JYXDXNMC", "jyxdxnmc"),
        yearCodes = arrayOf("JYXDXNM", "jyxdxnm"),
        termNames = arrayOf("JYXDXQMC", "jyxdxqmc"),
        termCodes = arrayOf("JYXDXQM", "jyxdxqm"),
    )

    /**
     * 「学年 + 学期」→ `2024-2025 第1学期`。
     *
     * 优先取**名称**字段（`XNMC`），拿不到才用编码（`XNM`=2024）—— 名称就是教务页面上显示的那一列，
     * 而编码在页面上是"2024"这种半截年份。
     */
    private fun buildTermFrom(
        o: JsonObject,
        yearNames: Array<String>,
        yearCodes: Array<String>,
        termNames: Array<String>,
        termCodes: Array<String>,
    ): String {
        val year = ZhengfangUtils.jsonField(o, *yearNames).ifBlank { ZhengfangUtils.jsonField(o, *yearCodes) }
        val rawTerm = ZhengfangUtils.jsonField(o, *termNames).ifBlank { ZhengfangUtils.jsonField(o, *termCodes) }
        if (year.isBlank() && rawTerm.isBlank()) return ""
        val label = if (rawTerm.isBlank()) "" else ZhengfangUtils.termLabel(rawTerm)
        return listOf(year, label).filter { it.isNotBlank() }.joinToString(" ")
    }
}
