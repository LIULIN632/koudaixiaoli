package com.pocket.xiaoli.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolPickerPriorityTest {

    private fun school(id: String, city: String) = School(
        id = id,
        name = id,
        city = city,
        jwType = JwSystemType.ZHENGFANG,
        baseUrl = "https://example.edu.cn",
    )

    @Test
    fun `只在选择器里保留烟台院校与自定义学校`() {
        assertTrue(school("YIT", "烟台").isPickerSchool())
        assertTrue(school("YTU", "烟台").isPickerSchool())
        assertTrue(school("custom_1", "外地").isPickerSchool())
        assertFalse(school("PKU", "北京").isPickerSchool())
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
}
