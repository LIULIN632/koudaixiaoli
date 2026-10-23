package com.pocket.xiaoli.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.time.LocalDate

/** 学年学期（成绩查询的学期选择项） */
data class ZfTerm(
    val xnm: String,
    val xqm: String,
    val label: String
)

/** 单门课的成绩记录（总评 + 平时/期末/期中分项；学校未下发的分项为空串） */
data class ZfGradeItem(
    val courseName: String,
    val credits: String,
    val totalScore: String,
    val gpa: String,
    val regular: String,
    val final: String,
    val midterm: String,
    val examType: String,
    val termLabel: String
)

/**
 * 正方教务成绩查询封装（基于同一 ZhengfangClient 的登录会话）。
 *
 * 接口约定（与油猴脚本「正方成绩分项显示」同源验证）：
 * - 学期列表：GET cjcx_cxDgXscj.html?gnmkdm=N305005，解析 JS 变量 xnxq；
 *   部分部署没有 xnxq 变量，退化为解析页面 #xnm/#xqm 下拉框；
 *   再失败用当前学期兜底。
 * - 成绩列表：POST cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005，
 *   body 与页面 jqGrid 查询一致（不带 XHR 头——部分部署带 XHR 头才返回异常）。
 * - 字段名按正方不同版本做了别名容错（pscj/pingshicj、kscj/qmcj/mkcj 等）。
 */
class ZhengfangGradeApi(private val client: ZhengfangClient) {

    private fun makeTerm(xnm: String, xqm: String) = ZfTerm(xnm, xqm, "$xnm-${ZhengfangUtils.termLabel(xqm)}")

    /** 获取可选学年学期列表（登录后调用） */
    suspend fun fetchTerms(baseUrl: String): List<ZfTerm> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005"
        val html = try {
            client.get(url).use { it.body?.string() ?: "" }
        } catch (e: Exception) {
            ""
        }
        if (html.isBlank() || ZhengfangUtils.isSessionDead(html)) return@withContext emptyList()

        // 1) JS 变量 xnxq（标准部署）
        parseTermsFromXnXq(html).takeIf { it.isNotEmpty() }?.let { return@withContext it }

        // 2) 页面 #xnm/#xqm 下拉框枚举（改版部署）
        parseTermsFromSelects(html).takeIf { it.isNotEmpty() }?.let { return@withContext it }

        // 3) 兜底：当前学期（8~1 月视为第1学期，2~7 月视为上一学年下学期）
        currentTermFallback()
    }

    /** 查询指定学年学期的成绩列表（登录后调用） */
    suspend fun fetchGrades(baseUrl: String, xnm: String, xqm: String): List<ZfGradeItem> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005"
            val body = "xnm=$xnm&xqm=$xqm&kksj=&kcgs=&xsfs=all&xm=&xh="
            val respText = try {
                client.post(url, body).use { it.body?.string() ?: "" }
            } catch (e: Exception) {
                ""
            }
            if (respText.isBlank()) return@withContext emptyList()
            if (ZhengfangUtils.isSessionDead(respText)) throw IllegalStateException("会话已失效，请重新登录")
            parseGradeItems(respText, makeTerm(xnm, xqm).label)
        }

    /** 解析 JS 变量 var xnxq = [...] */
    internal fun parseTermsFromXnXq(html: String): List<ZfTerm> {
        val m = Regex("""var\s+xnxq\s*=\s*(\[[\s\S]*?\]);""").find(html) ?: return emptyList()
        return try {
            val arr = JsonParser.parseString(m.groupValues[1]).asJsonArray
            arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val xnm = o.get("xnm")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                val xqm = o.get("xqm")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                makeTerm(xnm, xqm)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 解析页面 #xnm / #xqm 下拉框的 option，笛卡尔积生成学期 */
    internal fun parseTermsFromSelects(html: String): List<ZfTerm> {
        return try {
            val doc = Jsoup.parse(html)
            val xnms = doc.select("select#xnm option").mapNotNull { it.attr("value").trim().takeIf(String::isNotEmpty) }
            val xqms = doc.select("select#xqm option").mapNotNull { it.attr("value").trim().takeIf(String::isNotEmpty) }
            if (xnms.isEmpty() || xqms.isEmpty()) emptyList()
            else xnms.flatMap { xnm -> xqms.map { xqm -> makeTerm(xnm, xqm) } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun currentTermFallback(): List<ZfTerm> {
        val now = LocalDate.now()
        return if (now.monthValue >= 8 || now.monthValue <= 1) {
            listOf(makeTerm(now.year.toString(), "3"))
        } else {
            listOf(makeTerm((now.year - 1).toString(), "12"))
        }
    }

    /** 解析成绩列表 JSON items（字段别名容错） */
    internal fun parseGradeItems(jsonText: String, termLabel: String): List<ZfGradeItem> {
        return try {
            val root = JsonParser.parseString(jsonText).asJsonObject
            val items = root.get("items")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                ZfGradeItem(
                    courseName = gradeField(o, "kcmc"),
                    credits = gradeField(o, "xf"),
                    totalScore = gradeField(o, "cj", "zpcj", "zcj"),
                    gpa = gradeField(o, "jd"),
                    regular = gradeField(o, "pscj", "pingshicj"),
                    final = gradeField(o, "kscj", "qmcj", "mkcj"),
                    midterm = gradeField(o, "qzcj", "qizhongcj"),
                    examType = gradeField(o, "ksxzmc", "ksxz"),
                    termLabel = termLabel
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 取值：按名字顺序取第一个非 null/非空字段值；数字原样转字符串 */
    internal fun gradeField(o: JsonObject, vararg names: String): String =
        ZhengfangUtils.jsonField(o, *names)
}
