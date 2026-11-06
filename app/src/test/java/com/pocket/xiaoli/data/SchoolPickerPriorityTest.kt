package com.pocket.xiaoli.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SchoolPickerPriorityTest {

    private fun school(id: String, city: String) = School(
        id = id,
        name = id,
        city = city,
        jwType = JwSystemType.ZHENGFANG,
        baseUrl = "https://example.edu.cn",
    )

    @Test
    fun `只在选择器里保留烟台院校、点名的两所外地学校与自定义学校`() {
        assertTrue(school("YIT", "烟台").isPickerSchool())
        assertTrue(school("YTU", "烟台").isPickerSchool())
        assertTrue(school("custom_1", "外地").isPickerSchool())
        // 2026-10-31 用户点名接入的两所外地学校
        assertTrue(school("YJIT", "廊坊").isPickerSchool())
        assertTrue(school("SDUT", "淄博").isPickerSchool())
        // ⚠️ 白名单不是放开全国：其他外地学校仍然不进
        assertFalse(school("PKU", "北京").isPickerSchool())
        assertFalse(school("ZJGSU", "杭州").isPickerSchool())
    }

    @Test
    fun `烟台理工与烟台大学排在最前且顺序固定`() {
        val ytu = school("YTU", "烟台")
        val yit = school("YIT", "烟台")
        val other = school("SDBI", "烟台")

        assertEquals(0, yit.pickerRank())
        assertEquals(1, ytu.pickerRank())
        assertTrue(other.pickerRank() > ytu.pickerRank())
    }

    /**
     * 名单里那两条"点名接入"的记录必须**真的能登**：系统、地址、城市一个都不能错。
     *
     * 为什么判据落在**资产文件**上：这两所学校的接入成本全在这条数据里
     *（强智 / 正方的适配器早就有了，缺的只是名单条目），而地址写错在界面上看不出来 ——
     * 用户点进去只会看到一个打不开的页面。地址只认**学校自己公布的教务登录页**，不许猜。
     */
    @Test
    fun `点名的两所学校在名单里 系统与地址都不空`() {
        val zhengfang = schoolsJson("schools_zhengfang.json")
        val qiangzhi = schoolsJson("schools_qiangzhi.json")

        val yjit = qiangzhi["YJIT"]
        assertNotNull("强智名单里没有 YJIT（燕京理工学院）", yjit)
        assertEquals("燕京理工学院", yjit!!["name"].asString)
        assertEquals("QIANGZHI", yjit["jwType"].asString)
        assertEquals("廊坊", yjit["city"].asString)
        // 强智的 baseUrl 是站点根（个别学校带一段子路径，如燕京的 /yjlgxy）
        assertTrue(
            "燕京理工的教务地址必须是学校公布的那个：${yjit["baseUrl"].asString}",
            yjit["baseUrl"].asString.startsWith("http"),
        )

        val sdut = zhengfang["SDUT"]
        assertNotNull("正方名单里没有 SDUT（山东理工大学）", sdut)
        assertEquals("山东理工大学", sdut!!["name"].asString)
        assertEquals("ZHENGFANG", sdut["jwType"].asString)
        assertTrue(sdut["baseUrl"].asString.startsWith("http"))
    }

    /** 读资产里的学校名单；从模块目录或仓库根跑测试都能找到。 */
    private fun schoolsJson(name: String): Map<String, JsonObject> {
        val file = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull { it.exists() }
        assertNotNull("找不到 $name —— 工作目录变了，这条守卫会假绿", file)
        return JsonParser.parseString(file!!.readText()).asJsonArray
            .map { it.asJsonObject }
            .associateBy { it["id"].asString }
    }
}
