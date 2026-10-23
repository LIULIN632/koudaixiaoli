package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinSortUtilTest {

    @Test
    fun firstLetterCommonChars() {
        assertEquals('B', PinyinSortUtil.firstLetter('北'))
        assertEquals('D', PinyinSortUtil.firstLetter('大'))
        assertEquals('G', PinyinSortUtil.firstLetter('广'))
        assertEquals('S', PinyinSortUtil.firstLetter('苏'))
        assertEquals('T', PinyinSortUtil.firstLetter('同'))
        assertEquals('Q', PinyinSortUtil.firstLetter('庆'))
        assertEquals('H', PinyinSortUtil.firstLetter('华'))
        assertEquals('N', PinyinSortUtil.firstLetter('南'))
        assertEquals('Y', PinyinSortUtil.firstLetter('药'))
        assertEquals('X', PinyinSortUtil.firstLetter('学'))
    }

    @Test
    fun sortKeySchoolNames() {
        assertEquals("GDYKDX", PinyinSortUtil.sortKey("广东药科大学"))
        assertEquals("BJDX", PinyinSortUtil.sortKey("北京大学"))
        assertEquals("HNLGDX", PinyinSortUtil.sortKey("华南理工大学"))
        assertEquals("TJDX", PinyinSortUtil.sortKey("同济大学"))
        assertEquals("SDSFDX", PinyinSortUtil.sortKey("山东师范大学"))
    }

    @Test
    fun sortKeyNonChinese() {
        assertEquals("SWUFE", PinyinSortUtil.sortKey("SWUFE"))
        assertEquals("DX", PinyinSortUtil.sortKey("123大学"))
        assertEquals("", PinyinSortUtil.sortKey(""))
        assertEquals("", PinyinSortUtil.sortKey("123"))
        assertNull(PinyinSortUtil.firstLetter('1'))
    }

    @Test
    fun sortKeyKeepsUnmappableCharsInsteadOfDropping() {
        // 修掉的缺陷：原实现是 `firstLetter(c) ?: continue`，会把 GB2312 覆盖不到的汉字
        // **静默删掉**，导致排序键出现空洞、不同名字可能撞成同一个键，
        // 于是 thenBy 无法区分，相对顺序退化成输入顺序。
        // 现在回退到原字符：宁可让生僻字按码位参与排序，也不能让它凭空消失。
        val withRare = PinyinSortUtil.sortKey("阜阳师范大学")
        val withoutRare = PinyinSortUtil.sortKey("阳师范大学")

        assertTrue(
            "取不到拼音的汉字被丢弃了，导致两个不同名字撞成同一个排序键：[$withRare]",
            withRare != withoutRare,
        )
    }

    @Test
    fun cacheDoesNotChangeResult() {
        val names = listOf("北京大学", "烟台理工学院", "广东药科大学", "SWUFE", "123大学")

        val cold = names.map { PinyinSortUtil.sortKey(it) }
        val warm = names.map { PinyinSortUtil.sortKey(it) }
        PinyinSortUtil.clearCacheForTest()
        val cleared = names.map { PinyinSortUtil.sortKey(it) }

        assertEquals("热缓存结果必须与冷缓存一致", cold, warm)
        assertEquals("清空缓存后结果必须一致（缓存不能改变语义）", cold, cleared)
    }

    @Test
    fun sortOrderIsStableAcrossRepeatedSorts() {
        val schools = listOf("武汉大学", "北京大学", "安徽大学", "浙江大学", "复旦大学", "烟台理工学院")

        val first = schools.sortedBy { PinyinSortUtil.sortKey(it) }
        // 反复排序必须给出同一个顺序：排序键若是不纯的实现（带状态或缓存写错），这里会抖
        repeat(20) {
            assertEquals(first, schools.sortedBy { PinyinSortUtil.sortKey(it) })
        }
        assertEquals(
            listOf("安徽大学", "北京大学", "复旦大学", "武汉大学", "烟台理工学院", "浙江大学"),
            first,
        )
    }

    /**
     * 缓存带来的加速必须**能被量化**，否则"优化"只是说法。
     *
     * 对照组是"原实现每次调用要做的实际工作量"（自己逐字做一次 GB2312 编码），
     * 也就是把缓存拿掉之后的等价成本。阈值取 2 倍这种保守量级：
     * CI 机器上单次测量会抖，但缓存与"每次都编码"之间是数量级的差距，2 倍不可能误判。
     */
    @Test
    fun cacheMakesSortKeyDramaticallyFaster() {
        // 299 个不同名字，对齐学校列表规模
        val names = (0 until 299).map { i -> "测试第${i}职业技术学院" }

        // 预热：先跑一遍把缓存填满（模拟"用户已经停在这个页面上"的状态）
        names.forEach { PinyinSortUtil.sortKey(it) }

        val cachedStart = System.nanoTime()
        repeat(200) { names.forEach { PinyinSortUtil.sortKey(it) } }
        val cachedNanos = System.nanoTime() - cachedStart

        // 对照组：不查缓存，逐字做一次 GB2312 编码（原实现的每次调用成本）
        val rawStart = System.nanoTime()
        repeat(200) {
            names.forEach { name ->
                buildString {
                    for (c in name) {
                        if (c.code in 0x4E00..0x9FA5) {
                            val bytes = c.toString().toByteArray(java.nio.charset.Charset.forName("GB2312"))
                            if (bytes.size == 2) append('X')
                        }
                    }
                }
            }
        }
        val rawNanos = System.nanoTime() - rawStart

        val speedup = rawNanos.toDouble() / cachedNanos.toDouble().coerceAtLeast(1.0)
        println(
            "[PinyinSortUtil] 299 个名字 × 200 轮：带缓存 ${cachedNanos / 1_000_000}ms，" +
                "无缓存 ${rawNanos / 1_000_000}ms，快 ${"%.1f".format(speedup)} 倍"
        )
        assertTrue(
            "缓存没有带来预期加速：带缓存 ${cachedNanos / 1_000_000}ms，" +
                "无缓存 ${rawNanos / 1_000_000}ms，仅快 ${"%.2f".format(speedup)} 倍",
            speedup >= 2.0,
        )
    }
}
