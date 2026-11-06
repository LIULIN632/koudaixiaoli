package com.pocket.xiaoli.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 学号脱敏的边界表。
 *
 * 这些用例与 Android `BoundLoginTest` / 桌面原有单测同口径；谁改了规则，这里先红。
 */
class StudentIdMaskTest {

    @Test
    fun `正常学号只留末四位`() {
        assertEquals("*********6789", maskStudentId("2023123456789"))
        assertEquals("****1234", maskStudentId("20231234"))
    }

    @Test
    fun `null 与空白返回空串`() {
        assertEquals("", maskStudentId(null))
        assertEquals("", maskStudentId(""))
        assertEquals("", maskStudentId("   "))
    }

    @Test
    fun `长度不超过四位一律全遮`() {
        assertEquals("****", maskStudentId("6789"))
        assertEquals("****", maskStudentId("12"))
    }

    @Test
    fun `前后空格不影响长度判定`() {
        assertEquals("****1234", maskStudentId("  20231234  "))
    }

    @Test
    fun `已经遮过的串再遮一次仍然只留末四位`() {
        assertEquals("*********6789", maskStudentId("*********6789"))
    }
}
