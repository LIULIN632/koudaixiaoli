package com.pocket.xiaoli.core.academic

import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/**
 * 登录链路的 Mock 验收：登录页 → 加密公钥 → 验证码 → POST 登录 → 抓课表。
 *
 * 真实学校环境未验证（用户选择方案 B）；这条测试保证共享客户端的协议理解没退步。
 */
class ZhengfangClientMockTest {

    @Test
    fun `登录 验证码 与课表抓取走同一份客户端`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val publicKey = KeyPairGenerator.getInstance("RSA")
                .apply { initialize(1024) }
                .generateKeyPair().public as RSAPublicKey
            val modulusBytes = publicKey.modulus.toByteArray().let {
                if (it.isNotEmpty() && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
            }
            val modulus = Base64.getEncoder().encodeToString(modulusBytes)

            val loginPage = """
                <html><head><title>统一身份认证</title></head><body>
                <form id="loginForm" action="/xtgl/login_slogin.html" method="post">
                  <input type="hidden" id="csrftoken" name="csrftoken" value="token-abc">
                  <input type="text" id="yhm" name="yhm">
                  <input type="password" id="mm" name="mm">
                  <input type="text" id="yzm" name="yzm">
                </form>
                </body></html>
            """.trimIndent()

            val scheduleJson = """
                {"zc":9,"totalweek":20,"kbList":[{
                  "kcmc":"高等数学","xm":"张三","cdmc":"东九楼D101",
                  "xqj":"3","jcs":"1-2节","zcd":"1-16周"
                }]}
            """.trimIndent()

            val recorded = mutableListOf<RecordedRequest>()

            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    recorded += request
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("login_getPublicKey") -> MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"modulus":"$modulus","exponent":"10001"}""")

                        path.contains("login_getCaptcha") -> MockResponse()
                            .setHeader("Content-Type", "image/png")
                            .setBody(Buffer().write(ByteArray(120) { 7 }))

                        path.contains("login_slogin.html") && request.method == "POST" -> MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"result":"success"}""")

                        path.contains("login_slogin.html") -> MockResponse()
                            .setHeader("Content-Type", "text/html; charset=utf-8")
                            .setBody(loginPage)

                        path.contains("xskbcx_cxXsgrkb") -> MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody(scheduleJson)

                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }

            val client = ZhengfangClient()
            var captchaShown = false
            val result = client.login(
                school = ZhengfangSchool(
                    name = "Mock 理工",
                    url = server.url("/jwglxt").toString().trimEnd('/'),
                    isV8 = true,
                ),
                username = "20260001",
                password = "secret",
                onProgress = {},
                onCaptcha = { captcha ->
                    captchaShown = captcha.base64.isNotBlank()
                    "abcd"
                },
            )

            assertTrue(result.success, "登录失败：${result.error}")
            assertEquals("高等数学", result.courses?.single()?.name)
            assertEquals("张三", result.courses?.single()?.teacher)
            assertEquals(20, result.semesterWeeks)
            assertTrue(captchaShown, "应该弹出过验证码")

            val loginPost = recorded.last { it.method == "POST" && it.path.orEmpty().contains("login_slogin") }
            val loginBody = loginPost.body.readUtf8()
            assertTrue(loginBody.contains("yhm=20260001"), "登录表单缺学号：$loginBody")
            assertTrue(loginBody.contains("yzm=abcd"), "登录表单缺验证码：$loginBody")
            assertTrue(loginBody.contains("csrftoken=token-abc"), "登录表单缺 csrftoken：$loginBody")

            val schedulePost = recorded.last { it.method == "POST" && it.path.orEmpty().contains("xskbcx_cxXsgrkb") }
            assertTrue(schedulePost.body.readUtf8().contains("xnm="), "课表请求缺学期参数")
        } finally {
            server.shutdown()
        }
    }
}
