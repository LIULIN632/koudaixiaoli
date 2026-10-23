package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证码探测的判定（2026-10-23）。
 *
 * ## 为什么要有这条
 *
 * 用户反馈：「每次用教务都会看到那个三次的验证码重试」。原因不是网络差，而是
 * **他们的学校根本不用验证码**：`login_getCaptcha.html` 回的是一段 HTML/404，
 * 代码把「不是图片」当成「加载失败」，于是重试满 4 次（3 次 800ms，约 2.4 秒），
 * 最后才在没有验证码的情况下登录成功。
 *
 * 判定规则现在收敛到 [captchaProbe] 一个纯函数里：
 * 拿到图片 = IMAGE；服务端明确答复但不是图 = ABSENT（**不重试**）；5xx/无答复 = TRANSIENT。
 */
class CaptchaProbeTest {

    @Test
    fun `拿到图片就是需要验证码`() {
        assertEquals(CaptchaProbe.IMAGE, captchaProbe(200, looksLikeImage = true))
        // 有些学校图片接口回 206/302，只要正文像图片就算数
        assertEquals(CaptchaProbe.IMAGE, captchaProbe(206, looksLikeImage = true))
        assertEquals(CaptchaProbe.IMAGE, captchaProbe(0, looksLikeImage = true))
    }

    @Test
    fun `服务端明确答复但不是图片，说明这学校不用验证码`() {
        // 404：接口根本不存在（无验证码学校的典型表现）
        assertEquals(CaptchaProbe.ABSENT, captchaProbe(404, looksLikeImage = false))
        // 200 + HTML：接口在，但回的是登录页/提示，不是验证码
        assertEquals(CaptchaProbe.ABSENT, captchaProbe(200, looksLikeImage = false))
        // 403：被拦了，同样不该重试
        assertEquals(CaptchaProbe.ABSENT, captchaProbe(403, looksLikeImage = false))
        // 204 无内容
        assertEquals(CaptchaProbe.ABSENT, captchaProbe(204, looksLikeImage = false))
    }

    @Test
    fun `服务器故障或无响应才值得重试`() {
        assertEquals(CaptchaProbe.TRANSIENT, captchaProbe(500, looksLikeImage = false))
        assertEquals(CaptchaProbe.TRANSIENT, captchaProbe(502, looksLikeImage = false))
        assertEquals(CaptchaProbe.TRANSIENT, captchaProbe(503, looksLikeImage = false))
    }
}
