package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhengfangClientTest {

    private val client = ZhengfangClient()

    @Test
    fun parseZhengfangSemesterTopLevel() {
        // 顶层 zc / totalweek，字符串数字
        val body = """{"kblxList":[],"kbcxList":[],"zc":"5","totalweek":"20"}"""
        val info = client.parseZhengfangSemester(body)
        assertTrue(info != null)
        assertEquals(20, info!!.weeks)
        // 开学日 = 今天 - 4 周
        assertEquals(
            java.time.LocalDate.now().minusWeeks(4).toString(),
            info.start
        )
    }

    @Test
    fun parseZhengfangSemesterNestedXqjcxx() {
        // zc/totalweek 只在 xqjcxx 数组里
        val body = """{"kbcxList":[],"xqjcxx":[{"zc":3,"totalweek":18}]}"""
        val info = client.parseZhengfangSemester(body)
        assertTrue(info != null)
        assertEquals(18, info!!.weeks)
        assertEquals(java.time.LocalDate.now().minusWeeks(2).toString(), info.start)
    }

    @Test
    fun parseZhengfangSemesterInvalidInput() {
        assertNull(client.parseZhengfangSemester(""))
        assertNull(client.parseZhengfangSemester("<html>login page</html>"))
        assertNull(client.parseZhengfangSemester("{\"kbcxList\":[]}"))
        // zc=0 → 无法推导开学日
        val noZc = client.parseZhengfangSemester("""{"kbcxList":[],"totalweek":20}""")
        assertTrue(noZc != null)
        assertNull(noZc!!.start)
        assertEquals(20, noZc.weeks)
    }
}
