package com.pocket.xiaoli.core.academic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class ZhengfangGradeMockTest {

    @Test
    fun `学期列表与成绩走同一份共享解析`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val termsHtml = """
                <html><body>
                <script>var xnxq = [{"xnm":"2026","xqm":"3"},{"xnm":"2025","xqm":"12"}];</script>
                </body></html>
            """.trimIndent()
            val gradesJson = """
                {"items":[
                  {"kcmc":"高等数学","xf":"4","cj":"92","jd":"4.0","pscj":"90","kscj":"94","qzcj":"91","ksxzmc":"正常考试"},
                  {"kcmc":"大学英语","xf":3,"zpcj":"78","pingshicj":"80","qmcj":"75","ksxz":"补考"}
                ]}
            """.trimIndent()

            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val isGrades = request.method == "POST"
                    return if (isGrades) {
                        MockResponse().setHeader("Content-Type", "application/json").setBody(gradesJson)
                    } else {
                        MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(termsHtml)
                    }
                }
            }

            val baseUrl = server.url("/jwglxt").toString().trimEnd('/')
            val api = ZhengfangGradeApi(ZhengfangClient())

            val terms = api.fetchTerms(baseUrl)
            assertEquals(listOf("2026-第1学期", "2025-第2学期"), terms.map { it.label })

            val grades = api.fetchGrades(baseUrl, "2026", "3")
            assertEquals(2, grades.size)
            assertEquals("高等数学", grades[0].courseName)
            assertEquals("92", grades[0].totalScore)
            assertEquals("4", grades[0].credits)
            assertEquals("90", grades[0].regular)
            assertEquals("94", grades[0].final)
            assertEquals("91", grades[0].midterm)
            // 别名兜底：zpcj / pingshicj / qmcj / ksxz
            assertEquals("78", grades[1].totalScore)
            assertEquals("80", grades[1].regular)
            assertEquals("75", grades[1].final)
            assertEquals("补考", grades[1].examType)
        } finally {
            server.shutdown()
        }
    }
}
