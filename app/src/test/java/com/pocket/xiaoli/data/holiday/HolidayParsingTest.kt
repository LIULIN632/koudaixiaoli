package com.pocket.xiaoli.data.holiday

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * holiday-cn 的真实形状必须能被现有模型吃下。
 *
 * 起因是 2026-10-26 的日志导出：两年 × 四个镜像**全部 HTTP 200**，代码却把四个镜像全试完
 * （= 每次都判定"解析后 0 条"）。这份测试把上游 JSON 的形状钉在模型上，
 * 能挡住**字段名/类型漂移**（比如上游改了 key、我们把 `days` 改名、`year` 从数字变字符串）。
 *
 * ⚠️ 它挡不住 release 构建里 R8 把反射用的模型裁掉 —— 那一层由
 * `app/proguard-rules.pro` 里 `-keep class com.pocket.xiaoli.data.holiday.**` 负责，
 * 只能靠 release 包真机复验。
 */
class HolidayParsingTest {

    /** 从真实响应里截的片段（顶层键与字段名一字未改）。 */
    private val upstreamJson = """
        {
          "${'$'}schema": "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/schema.json",
          "${'$'}id": "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/2026.json",
          "year": 2026,
          "papers": ["https://www.gov.cn/zhengce/content/202511/P020251104xxxx.pdf"],
          "days": [
            { "name": "元旦", "date": "2026-01-01", "isOffDay": true },
            { "name": "春节", "date": "2026-02-15", "isOffDay": true },
            { "name": "春节", "date": "2026-02-28", "isOffDay": false }
          ]
        }
    """.trimIndent()

    @Test
    fun `上游那份 JSON 能解析出放假与调休`() {
        val parsed = Gson().fromJson(upstreamJson, HolidayYearResponse::class.java)
        assertEquals(3, parsed.days.size)
        assertTrue("元旦应标成放假", parsed.days.first().isOffDay)
        assertFalse("末尾那条是调休上班", parsed.days.last().isOffDay)
        assertEquals("2026-01-01", parsed.days.first().date)
        assertEquals("元旦", parsed.days.first().name)
    }

    @Test
    fun `日期格式规则只留 yyyy-MM-dd`() {
        val parsed = Gson().fromJson(
            """{"year":2026,"days":[{"name":"脏数据","date":"2026/01/01","isOffDay":true},
               {"name":"正常","date":"2026-01-01","isOffDay":true}]}""",
            HolidayYearResponse::class.java,
        )
        val kept = parsed.days.filter { it.date.length == 10 && it.date[4] == '-' && it.date[7] == '-' }
        assertEquals(listOf("正常"), kept.map { it.name })
    }
}
