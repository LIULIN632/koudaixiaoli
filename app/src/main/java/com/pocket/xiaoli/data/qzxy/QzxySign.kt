package com.pocket.xiaoli.data.qzxy

import java.security.MessageDigest

/**
 * 趣智校园的本地签名工具。
 *
 * ## 这是整个接入的关键发现
 *
 * 官方 App 发短信验证码时带的 `secret` **不是随机值、也不需要抓包** ——
 * 它由手机号本地推导：
 *
 * ```
 * raw    = 手机号前 3 位 + 手机号后 4 位 + "klcx"
 * secret = MD5(raw)   （32 位小写十六进制）
 * ```
 *
 * 例：`13800138000` → `"138" + "8000" + "klcx"` = `"1388000klcx"`
 * → MD5 = `f000554d9cb90c44bd0ae2ded9a847aa`
 *
 * 算法逆向自官方 APK，**任何学校、任何手机号都能在本地算出自己的 secret**，
 * 所以「短信验证码登录」这条路对所有人可用，不依赖密码、不依赖宿主 App。
 *
 * ⚠️ 上面的例子是**回归测试的锚点**（见 `QzxySignTest`）：这三个数值对不上，
 * 说明算法被改过或我抄错了 —— 那时登录会全部失败，且服务端只回一句
 * 含糊的错，很难查。所以必须锁死。
 *
 * 出处：[`yehu-imei/linyu`](https://github.com/yehu-imei/linyu) 的 `utils/SignUtils.kt`（MIT）。
 */
object QzxySign {

    /** README 里给出的样例，用作单测锚点。 */
    const val SAMPLE_PHONE = "13800138000"
    const val SAMPLE_SECRET = "f000554d9cb90c44bd0ae2ded9a847aa"

    /**
     * 按手机号算短信验证码接口所需的 `secret`。
     *
     * @throws IllegalArgumentException 手机号不是 11 位 —— 官方接口也只接受 11 位，
     *   提前拦下比等服务端回一句「参数错误」更好查。
     */
    fun smsSecret(telephone: String): String {
        val tel = telephone.trim()
        require(tel.length == 11) { "手机号必须是 11 位" }
        return md5(tel.take(3) + tel.takeLast(4) + "klcx")
    }

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
