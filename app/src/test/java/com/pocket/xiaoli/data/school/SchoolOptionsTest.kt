package com.pocket.xiaoli.data.school

import com.pocket.xiaoli.core.campus.CampusConfig
import com.pocket.xiaoli.core.campus.CampusInfo
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「可接入学校」列表的判据测试 —— 引导第一步与「绑定信息 → 学校」都吃这一份。
 *
 * ## 这批断言为什么值得钉
 *
 * 2026-11-05 之前的状态是：名单里有 302 所、白名单里有 16 所、
 * 但引导和绑定页只列 `CampusConfig` 的两所校区 —— 其余学校**数据里可选、入口里选不到**。
 * 用户点名接入的山东理工与燕京理工就是这样躲过了所有测试：
 * `SchoolPickerPriorityTest` 钉的是白名单判据，而白名单在 UI 上根本走不到。
 * 所以这里既钉判据（交集、排序、待验证标注），也钉**真实资产里的结果数量**，
 * 两者少一个就会红。
 */
class SchoolOptionsTest {

    private fun school(
        id: String,
        name: String = "某学校",
        city: String = "烟台",
        type: JwSystemType = JwSystemType.ZHENGFANG,
        baseUrl: String = "https://jw.example.edu.cn/jwglxt",
    ) = School(id = id, name = name, city = city, jwType = type, baseUrl = baseUrl, isV8 = true)

    private val campuses = listOf(
        CampusInfo(id = "c1", name = "有校区", shortName = "一", underConstruction = false, schoolId = "S1"),
        CampusInfo(id = "c2", name = "在建", shortName = "二", underConstruction = true, schoolId = "S2"),
    )

    @Test
    fun `白名单 ∩ 教务系统可用 —— 超星与青果不进列表`() {
        val schools = listOf(
            school("S1", "正方本校"),
            school("SQ", "强智烟台", type = JwSystemType.QIANGZHI),
            school("SC", "超星烟台", type = JwSystemType.CHAOXING),
            school("SG", "青果烟台", type = JwSystemType.QINGGUO),
        )
        val ids = schoolOptions(schools, campuses).map { it.id }
        assertEquals(listOf("S1", "SQ"), ids)
    }

    @Test
    fun `外地学校只有点名的两所能进来`() {
        val schools = listOf(
            school("YJIT", "燕京理工学院", city = "廊坊", type = JwSystemType.QIANGZHI),
            school("SDUT", "山东理工大学", city = "淄博"),
            school("OTHER", "某外地正方高校", city = "济南"),
        )
        assertEquals(listOf("SDUT", "YJIT"), schoolOptions(schools, campuses).map { it.id })
    }

    @Test
    fun `用户自建学校不受白名单限制`() {
        val custom = school("custom_abc", "自己加的学院", city = "")
        val options = schoolOptions(listOf(custom), campuses)
        assertEquals(1, options.size)
        assertEquals("自己加的学院", options.first().name)
    }

    @Test
    fun `教务地址为空的学校不进列表`() {
        // 内置名单里有一批"知道有这套系统、但没抓到地址"的条目，
        // 列出来等于给一个点进去必然失败的入口
        val options = schoolOptions(listOf(school("S1", baseUrl = "  ")), campuses)
        assertTrue(options.isEmpty())
    }

    @Test
    fun `有校区的排前面并标出校园服务已收录`() {
        val options = schoolOptions(
            listOf(
                school("S9", "没校区的烟台学校"),
                school("S1", "有校区的烟台学校"),
            ),
            campuses,
        )
        assertEquals(listOf("S1", "S9"), options.map { it.id })
        assertTrue(options.first().hasCampusConfig)
        assertFalse(options.last().hasCampusConfig)
    }

    @Test
    fun `强智那条链路标待验证`() {
        val options = schoolOptions(
            listOf(
                school("S1", "正方学校"),
                school("SQ", "强智学校", type = JwSystemType.QIANGZHI),
            ),
            campuses,
        )
        val zf = options.first { it.id == "S1" }
        val qz = options.first { it.id == "SQ" }
        assertTrue(zf.protocolVerified)
        assertFalse("强智协议从未用真账号验证过，不能显示成已验证", qz.protocolVerified)
        assertTrue(qz.subtitle.endsWith("待验证"))
    }

    @Test
    fun `在建校区的学校不算已收录`() {
        // S2 挂在 underConstruction 的校区上：校园服务与地图都还没配齐
        val options = schoolOptions(
            listOf(school("S2", "在建校区对应的学校")),
            campuses,
        )
        assertFalse(options.first().hasCampusConfig)
    }

    // ── 真实资产：钉住"现在到底能接入几所" ──

    private fun realSchools(): List<School> {
        val out = mutableListOf<School>()
        listOf("zhengfang", "qiangzhi", "qingguo", "chaoxing", "urp").forEach { system ->
            val file = File("src/main/assets/schools_$system.json")
            assertTrue("找不到 $system 名单资产（测试工作目录是 app/）", file.isFile)
            val items = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                out += School(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    city = o.optString("city"),
                    jwType = when (o.optString("jwType").uppercase()) {
                        "QIANGZHI" -> JwSystemType.QIANGZHI
                        "QINGGUO" -> JwSystemType.QINGGUO
                        "CHAOXING" -> JwSystemType.CHAOXING
                        "URP" -> JwSystemType.URP
                        else -> JwSystemType.ZHENGFANG
                    },
                    baseUrl = o.optString("baseUrl"),
                    isV8 = o.optBoolean("isV8", true),
                )
            }
        }
        return out
    }

    @Test
    fun `真实名单里可接入的就是烟台可用院校加两所点名学校`() {
        val options = schoolOptions(realSchools(), CampusConfig.campuses)
        val ids = options.map { it.id }
        // 烟台 14 所里只有 10 所是正方/强智；加 SDUT + YJIT
        assertEquals(12, options.size)
        assertTrue("烟台理工必须在列表里", "YIT" in ids)
        assertTrue("烟大必须在列表里", "YTU" in ids)
        assertTrue("点名接入的两所必须在", listOf("SDUT", "YJIT").all { it in ids })
        // 4 所超星/青果的烟台院校仍然进不来（没有原生适配器）
        assertFalse("超星烟台院校不该被列成可接入", "SDTCM" in ids)
        assertFalse("青果烟台院校不该被列成可接入", "NSXY" in ids)
    }

    @Test
    fun `已收录校园服务的两所排在最前`() {
        val options = schoolOptions(realSchools(), CampusConfig.campuses)
        assertEquals(listOf("YIT", "YTU"), options.take(2).map { it.id })
        assertEquals(2, options.count { it.hasCampusConfig })
    }

    @Test
    fun `未收录校园服务的学校副标题不承诺服务`() {
        val options = schoolOptions(realSchools(), CampusConfig.campuses)
        options.filter { !it.hasCampusConfig }.forEach { option ->
            // 副标题只说城市与教务系统（外加待验证标记），不许出现"电费/洗衣"这类承诺
            assertFalse(option.subtitle, listOf("电费", "洗衣", "校园卡").any { it in option.subtitle })
        }
    }
}
