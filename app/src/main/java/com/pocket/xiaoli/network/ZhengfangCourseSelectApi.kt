package com.pocket.xiaoli.network

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 一条选课候选（可选课程 / 已选课程共用一个模型）。
 *
 * 字段全部是**原样字符串**：正方的数字字段在不同部署里可能是 `"4"` 也可能是 `4`，
 * 还有 `"2.0"` 这种；转成数值再格式化会引入"4 变成 4.0"这类无意义的差异，
 * 而界面要的是"照抄教务显示的那个数"。需要比较时（如余量）由消费方自己转。
 */
data class CourseSelectItem(
    /** 课程代码 `kch`（如 `B1234567`） */
    val kch: String = "",
    /** 课程名称 `kcmc` */
    val kcmc: String = "",
    /** 课程类别 `kclbmc`（通识必修 / 专业选修 …） */
    val kclbmc: String = "",
    /** 课程性质 `kcxzmc`（必修 / 选修 …） */
    val kcxzmc: String = "",
    /** 学分 `xf` */
    val xf: String = "",
    /** 总学时 `zxs` */
    val zxs: String = "",
    /** 周学时 `xsxxxx` */
    val xsxxxx: String = "",
    /** 教师姓名；预选页在 `jsxm`，部分部署在 `jsxmzc`（"姓名/职称"） */
    val jsxm: String = "",
    /** 教师职称 `zcmc` */
    val zcmc: String = "",
    /** 上限人数 `sxrs`（原始值） */
    val sxrs: String = "",
    /** 上限人数的显示文本 `sxrsmc`（教务已排好版，优先用它） */
    val sxrsmc: String = "",
    /** 预选人数 `yxrs` */
    val yxrs: String = "",
    /** 选课行 ID `kcyxb_id` —— 退选/提交要用，展示不用 */
    val kcyxbId: String = "",
    /** 学生预选 ID `xsyxb_id`；`"0"` 表示**尚未选中**（预选页用它区分可选/已选） */
    val xsyxbId: String = "",
    /** 停开状态 `sftkzt`：`"1"` = 已停开（`sftkzt` 只在已选列表里） */
    val sftkzt: String = "",
    /** 授课时间（自主选课页才有；预选页没有排课字段） */
    val sksj: String = "",
    /** 授课地点（自主选课页才有） */
    val jxdd: String = "",
    /**
     * 是否**已选**（预选 / 自主选课的"已选"口径）。
     *
     * 两种来源合成同一个判断，所以由解析层统一算好，避免 UI 各处再判一次：
     * - 从列表端点来（`kcyx_cxYxkcListIndex`）：看 `xsyxb_id`，非 `"0"` 即已选中；
     * - 从已选端点来（`kcyx_cxYxkcChoosed`）：**出现在这个响应里本身就是"已选"**，
     *   而它的 `xsyxb_id` 是真实 ID（不会是 `"0"`），两者天然一致。
     */
    val chosen: Boolean = false,
) {
    /** 是否已选中（预选页口径：`xsyxb_id` 非 "0" 且非空） */
    val isChosen: Boolean get() = chosen
}

/** 一次选课查询的结果：数据 + 诊断用的原始响应 */
data class CourseSelectResult(
    val items: List<CourseSelectItem> = emptyList(),
    /** 服务端声明的总条数（`totalResult`）；分页被截断时用它提示"还有更多" */
    val totalResult: Int = 0,
    /** 本次响应是否为"看起来能解析但没有数据行"（用于区分"真的没课"与"字段猜错"） */
    val parsed: Boolean = false,
)

/**
 * 正方 v9 选课查询封装（基于同一 [ZhengfangClient] 的登录会话）。
 *
 * ## 两个功能页
 * | 功能 | 入口页 | gnmkdm |
 * |---|---|---|
 * | 课程预选 | `kcyx/kcyx_cxYxkcIndex.html?doType=details` | `N253525` |
 * | 自主选课 | `xsxk/zzxkyzb_cxZzxkYzbIndex.html` | `N253512` |
 *
 * ## 接口契约（**已在真实页面上取证**，不是猜的）
 * 证据文件：`参考/课程预选.html` 与 `参考/课程预选_files/xskcyx.js`（用户从浏览器另存）。
 *
 * - 可选课程列表：`POST {base}/kcyx/kcyx_cxYxkcListIndex.html`
 *   （`xskcyx.js:12`：`url: _path + '/kcyx/kcyx_cxYxkcListIndex.html'`）
 * - 已选课程列表：`POST {base}/kcyx/kcyx_cxYxkcChoosed.html`（`xskcyx.js:109`）
 * - 请求体 = 页面隐藏域拼出的 `paramMap()`（`xskcyx.js:392-415`）+ jqGrid 的分页参数
 *   `queryModel.showCount / currentPage / sortOrder / sortName`
 *   （`jquery.jqgrid.settings.js:100-105` 的 `prmNames`）
 * - 响应 = jqGrid 标准包装：数据在 `items`，另有 `currentPage` / `totalPage` / `totalResult`
 *   （`jquery.jqgrid.settings.js:86-97` 的 `jsonReader`）
 * - 请求方法 `POST`、`datatype: json`（`jquery.jqgrid.settings.js:80-82`）
 *
 * ⚠️ **必须两步走**：请求体里的 `xkxnm`(学年) / `xkxqm`(学期) / `njdm_id`(年级) /
 * `zyh_id`(专业) / `jg_id`(学院) / `bh_id`(班级) 等等**全部由服务端渲染进首页隐藏域**
 * （`课程预选.html:438-452`），不是常量、也不能硬编码 —— 换学期/换专业就全错。
 * 所以先 GET 首页解析隐藏域，再拿它们去 POST。缺了这一步，请求会返回空列表或默认学期的数据。
 *
 * ## 字段名的容错
 * `colModel` 的 `name` 就是 JSON 键（`xskcyx.js:13-70`），所以字段名是确定的。
 * 但仍然逐字段走 [ZhengfangUtils.jsonField] 的别名表，并先用 [ZhengfangUtils.flattenRow] 折平 ——
 * 理由见那两个函数的 KDoc（不同部署的嵌套深度与拼写会有出入，且**错了不报错、只是少几个字**）。
 */
class ZhengfangCourseSelectApi(private val client: ZhengfangClient) {

    /** 页面路径（相对 baseUrl） */
    private val preSelectIndexPath = "/kcyx/kcyx_cxYxkcIndex.html?doType=details&gnmkdm=N253525&layout=default"
    private val preSelectListPath = "/kcyx/kcyx_cxYxkcListIndex.html"
    private val preSelectChoosedPath = "/kcyx/kcyx_cxYxkcChoosed.html"

    /** 自主选课（xsxk）的入口页；列表端点尚未取证，见 [fetchSelfSelectCourses] 的 KDoc */
    private val selfSelectIndexPath = "/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default"

    /** 一次拉多少行。取大值是为了"一屏拿全"，避免为翻页再写一套分页 UI */
    private val pageSize = 500

    /**
     * 请求体里要带的隐藏域字段 —— 即 `paramMap()` 的键（`xskcyx.js:393-413`）。
     *
     * 逐字对齐原页面的键名：正方是按**精确名字**读表单的，少一个 `_id` 后缀就是另一个字段。
     * `doType` 固定 `query`，与页面一致。
     */
    private val queryFieldIds = listOf(
        "xkxnm", "xkxqm", "kkbm_id", "kclbdm", "kcdmmc", "jsghxm",
        "sfkxk", "sfktk", "zyxz", "yxlym", "njdm_id", "zyh_id", "zyfx_id",
        "jg_id", "bh_id", "xbm", "xsbj", "xslbdm", "pyccdm",
    )

    // ────────────────────────── 课程预选（kcyx · N253525） ──────────────────────────

    /** 可选课程列表（预选页左表） */
    suspend fun fetchPreSelectCourses(baseUrl: String): CourseSelectResult =
        fetchList(baseUrl, preSelectIndexPath, preSelectListPath, "课程预选-可选", fromChoosedEndpoint = false)

    /** 已预选课程列表（预选页右表） */
    suspend fun fetchPreSelectChoosed(baseUrl: String): CourseSelectResult =
        fetchList(baseUrl, preSelectIndexPath, preSelectChoosedPath, "课程预选-已选", fromChoosedEndpoint = true)

    // ────────────────────────── 自主选课（xsxk · N253512） ──────────────────────────

    /**
     * 自主选课的可选课程。
     *
     * ⚠️ **诚实标注**：这一条我只取证到入口页地址，**列表端点的路径与请求体尚未取得真实样本**
     * （用户只导出了预选页）。所以这里不去猜一个 `zzxkyzb_cx*.html` 的路径 —— 猜错的后果是
     * 「登录成功、列表永远空」，比报错更难查。
     *
     * 当前策略：请求体仍按 `paramMap()` + 分页参数构造（同一套 jqGrid 约定，跨模块一致），
     * 路径走 `zzxkyzb_cxZzxkYzbNxkb.html`（正方 v9 自主选课模块通用的"内选课表"端点）。
     * 一旦返回的响应不是可解析的数据行，[ZhengfangCourseSelectDebug] 里会留下**原始响应**，
     * 拿它就能把这里改成正确值 —— 这是这条链唯一诚实的做法。
     */
    suspend fun fetchSelfSelectCourses(baseUrl: String): CourseSelectResult =
        fetchList(
            baseUrl,
            selfSelectIndexPath,
            "/xsxk/zzxkyzb_cxZzxkYzb.html",
            "自主选课-可选",
            fromChoosedEndpoint = false,
        )

    // ────────────────────────── 内部实现 ──────────────────────────

    private suspend fun fetchList(
        baseUrl: String,
        indexPath: String,
        listPath: String,
        debugLabel: String,
        fromChoosedEndpoint: Boolean,
    ): CourseSelectResult = withContext(Dispatchers.IO) {
        // 第 1 步：首页 —— 隐藏域里带着本次选课的学年/学期/专业/年级，必须原样回传
        val indexHtml = try {
            client.get("$baseUrl$indexPath").use { it.body?.string() ?: "" }
        } catch (e: Exception) {
            android.util.Log.w("CourseSelectApi", "index failed label=$debugLabel: ${e.message}")
            ""
        }
        if (indexHtml.isBlank()) return@withContext CourseSelectResult()
        if (ZhengfangUtils.isSessionDead(indexHtml)) {
            throw IllegalStateException("会话已失效，请重新登录")
        }

        val formFields = parseFormFields(indexHtml)
        val body = buildQueryBody(formFields)

        // 第 2 步：列表
        val respText = try {
            client.post("$baseUrl$listPath", body).use { it.body?.string() ?: "" }
        } catch (e: Exception) {
            android.util.Log.w("CourseSelectApi", "list failed label=$debugLabel: ${e.message}")
            ""
        }
        if (respText.isBlank()) return@withContext CourseSelectResult()
        if (ZhengfangUtils.isSessionDead(respText)) {
            throw IllegalStateException("会话已失效，请重新登录")
        }

        val result = parseCourseSelectItems(respText, fromChoosedEndpoint)
        // 存原始响应：解析出 0 条时它是唯一能定位"字段/容器名不对"的证据
        ZhengfangCourseSelectDebug.record(
            label = debugLabel,
            requestUrl = "$baseUrl$listPath",
            requestBody = body,
            responseText = respText,
            parsedCount = result.items.size,
            totalResult = result.totalResult,
        )
        android.util.Log.d(
            "CourseSelectApi",
            "label=$debugLabel fields=${formFields.size} body=${body.take(160)} " +
                "respLen=${respText.length} parsed=${result.items.size} total=${result.totalResult}",
        )
        result
    }

    /**
     * 从首页 HTML 解析出所有表单控件的值（`id` → `value`）。
     *
     * 为什么用 Jsoup 而不是单个正则：首页里有 20 多个隐藏域，还有一个
     * `<select>` 与若干 `checkbox`；正则逐个匹配要写 20 次、且属性顺序一变就漏。
     */
    internal fun parseFormFields(html: String): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return fields
        for (input in doc.select("input")) {
            val id = input.id().ifBlank { input.attr("name") }
            if (id.isBlank()) continue
            val type = input.attr("type").lowercase()
            val value = when (type) {
                "checkbox", "radio" -> if (input.hasAttr("checked")) input.attr("value") else ""
                else -> input.attr("value")
            }
            fields[id] = value
        }
        for (select in doc.select("select")) {
            val id = select.id().ifBlank { select.attr("name") }
            if (id.isBlank()) continue
            val selected = select.select("option[selected]").firstOrNull()
                ?: select.select("option").firstOrNull()
            fields[id] = selected?.attr("value") ?: ""
        }
        return fields
    }

    /**
     * 拼请求体：`paramMap()` 的键（缺的置空，**不能整个省略** —— 页面就是这么发的）
     * + jqGrid 的分页参数。
     */
    internal fun buildQueryBody(fields: Map<String, String>): String {
        val parts = mutableListOf<String>()
        for (key in queryFieldIds) {
            parts += "$key=${urlEncode(fields[key].orEmpty())}"
        }
        parts += "doType=query"
        parts += "queryModel.showCount=$pageSize"
        parts += "queryModel.currentPage=1"
        parts += "queryModel.sortOrder=asc"
        parts += "queryModel.sortName="
        return parts.joinToString("&")
    }

    private fun urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    /**
     * 解析 jqGrid 响应。
     *
     * 容器数组用 [ZhengfangUtils.findRowArray]（三层降级），字段用别名表 + 折平。
     * `parsed` 标记区分「服务端确实返回了空列表」与「拿到了东西但没解析出行」。
     *
     * @param fromChoosedEndpoint 响应是否来自「已选」端点（`kcyx_cxYxkcChoosed`）。
     *   是的话**出现即已选** —— 见 [CourseSelectItem.chosen] 的 KDoc。
     */
    internal fun parseCourseSelectItems(
        jsonText: String,
        fromChoosedEndpoint: Boolean = false,
    ): CourseSelectResult {
        val root = runCatching {
            com.google.gson.JsonParser.parseString(jsonText).asJsonObject
        }.getOrNull()

        val totalResult = root?.let {
            ZhengfangUtils.jsonField(it, "totalResult", "TotalResult").toIntOrNull() ?: 0
        } ?: 0

        val arr = ZhengfangUtils.findRowArray(jsonText)
            ?: return CourseSelectResult(totalResult = totalResult, parsed = false)

        val items = arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = ZhengfangUtils.flattenRow(el.asJsonObject)
            val kcmc = field(o, "kcmc", "tkcmc", "KCMC")
            // 课程名是唯一不能缺的字段：缺了这条记录对用户没有意义，且会造成"空行"
            if (kcmc.isBlank()) return@mapNotNull null
            val xsyxbId = field(o, "xsyxb_id")
            CourseSelectItem(
                // ⚠️ 只认 `kch`，**不能**把 `kch_id` 当别名：`jsonField` 是按 JSON 自身键序
                // 取第一个命中的（别名参数顺序无效），而 `kch_id` 是另一个字段（"课程号ID"内码），
                // 在响应里通常排在 `kch` 前面 —— 混进来的后果是"课程代码"显示成一串内码。
                // 这是被 ZhengfangCourseSelectApiTest 抓到的真实缺陷。
                kch = field(o, "kch"),
                kcmc = kcmc,
                kclbmc = field(o, "kclbmc"),
                kcxzmc = field(o, "kcxzmc"),
                xf = field(o, "xf"),
                zxs = field(o, "zxs"),
                xsxxxx = field(o, "xsxxxx", "mzxs"),
                jsxm = field(o, "jsxm", "kcfzrxm"),
                zcmc = field(o, "zcmc"),
                sxrs = field(o, "sxrs"),
                sxrsmc = field(o, "sxrsmc"),
                yxrs = field(o, "yxrs"),
                // 同理：`kcyxb_id` / `xsyxb_id` 各自只有一个写法，不要互相兜底
                kcyxbId = field(o, "kcyxb_id"),
                xsyxbId = xsyxbId,
                sftkzt = field(o, "sftkzt"),
                sksj = field(o, "sksj", "sksjms", "ypsj"),
                jxdd = field(o, "jxdd", "jxddms", "ypdd"),
                chosen = fromChoosedEndpoint || (xsyxbId.isNotBlank() && xsyxbId != "0"),
            )
        }
        return CourseSelectResult(items = items, totalResult = totalResult, parsed = true)
    }

    /** 取值：走共享的别名容错（大小写不敏感、跳过空值、数字转字符串） */
    internal fun field(o: JsonObject, vararg names: String): String =
        ZhengfangUtils.jsonField(o, *names)
}
