package com.pocket.xiaoli.data.card

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Cipher

/**
 * 易校园（小富宝平台）校园卡接口的两块硬骨头：**密码加密**与**响应解析**。
 *
 * 字段名不是编的：照 GitHub 上 `xhboke/yixiaoyuan`（Python 实查消费）逐行读出来的
 * （`getPublicKey` / `doLoginByPwd` / `getCardMoney` 的参数名与 `statusCode`/`data` 结构）。
 */
class YxyCardRepositoryTest {

    @Test
    fun md5MatchesPythonHexdigest() {
        // Python: hashlib.md5("abc".encode()).hexdigest()
        assertEquals("900150983cd24fb0d6963f7d28e17f72", YxyCardRepository.md5Hex("abc"))
        // 空串也要是标准的 d41d8...（大小写与长度都按 Python 的来）
        assertEquals(32, YxyCardRepository.md5Hex("").length)
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", YxyCardRepository.md5Hex(""))
    }

    /**
     * 加密形状对不对：用**临时生成的 RSA 密钥对**当平台公钥，
     * 加密后自己解回来，必须正好等于 MD5 十六进制串。
     *
     * 这条能钉住三件事：`RSA/ECB/PKCS1Padding`（不是 OAEP，也不是"直接拿明文当摘要"）、
     * Base64 输出、以及**先 MD5 再 RSA**的顺序 —— 顺序反了服务端只会回"密码错误"。
     */
    @Test
    fun encryptsMd5DigestWithRsaPkcs1() {
        val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        val encrypted = YxyCardRepository.encryptPassword("Ytlg@123456", publicKeyBase64)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        val decrypted = String(cipher.doFinal(Base64.getDecoder().decode(encrypted)))
        assertEquals(YxyCardRepository.md5Hex("Ytlg@123456"), decrypted)
    }

    @Test
    fun publicKeyWithLineBreaksStillParses() {
        val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val raw = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        // 平台返回的密钥体常带换行；空白必须吃掉，否则 KeyFactory 会报 InvalidKeySpec
        val withBreaks = raw.chunked(64).joinToString("\n")

        assertTrue(YxyCardRepository.encryptPassword("x", withBreaks).isNotBlank())
    }

    // ── 响应结构（照平台真实形状写的样本）──

    private val LOGIN_OK = """
        {"statusCode":0,"message":"成功","data":{
          "id":"1234567","token":"abc.def.ghi","deviceId":"3ED5F00A-A02C-4383-A383-4BEF38BF1604",
          "schoolCode":"ytlg","schoolName":"烟台理工学院","userName":"张三","userType":"student"}}
    """.trimIndent()

    private val LOGIN_FAIL = """
        {"statusCode":1001,"message":"账号或密码错误","data":null}
    """.trimIndent()

    private val MONEY_NUMBER = """{"statusCode":0,"message":"成功","data":128.5}"""
    private val MONEY_STRING = """{"statusCode":0,"message":"成功","data":"128.50"}"""

    @Test
    fun parsesLoginAndBalance() {
        val login = JsonParser.parseString(LOGIN_OK).asJsonObject
        val data = login.getAsJsonObject("data")
        assertEquals("1234567", data.get("id").asString)
        assertEquals("abc.def.ghi", data.get("token").asString)
        assertEquals("烟台理工学院", data.get("schoolName").asString)

        // 余额两侧形态（数字 / 字符串）都要能读成同一个值
        assertEquals(
            128.5,
            JsonParser.parseString(MONEY_NUMBER).asJsonObject.get("data").asDouble,
            0.001,
        )
        assertEquals(
            128.5,
            JsonParser.parseString(MONEY_STRING).asJsonObject.get("data").asDouble,
            0.001,
        )
    }

    @Test
    fun failureResponseKeepsServerMessage() {
        // 平台自己的失败文案要原样带给用户 —— 我们不编"登录失败"
        val json = JsonParser.parseString(LOGIN_FAIL).asJsonObject
        assertEquals(1001, json.get("statusCode").asInt)
        assertEquals("账号或密码错误", json.get("message").asString)
        assertFalse(json.get("statusCode").asInt == 0)
    }
}
