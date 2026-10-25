package com.pocket.xiaoli.data.qzxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「登录失效」的客户端判据。
 *
 * 趣智没有稳定的错误码文档，只能按文案识别（见 `docs/QzxyShower.md` 的挤号一节）。
 * 这里钉住：常见失效文案要命中，普通业务失败**不能**被误判成失登录 ——
 * 误判会让用户被踢回登录页，而他其实只是没签约。
 */
class QzxyErrorsTest {

    @Test
    fun `登录失效类文案命中`() {
        assertTrue(QzxyErrors.isAuthExpired("登录已失效，请重新登录"))
        assertTrue(QzxyErrors.isAuthExpired("token expired"))
        assertTrue(QzxyErrors.isAuthExpired("Token 已失效"))
        assertTrue(QzxyErrors.isAuthExpired("未授权访问"))
        assertTrue(QzxyErrors.isAuthExpired("Unauthorized"))
    }

    @Test
    fun `普通业务失败不误判为登录失效`() {
        assertFalse(QzxyErrors.isAuthExpired("还未签约免密支付"))
        assertFalse(QzxyErrors.isAuthExpired("设备不在线"))
        assertFalse(QzxyErrors.isAuthExpired("余额查询失败"))
        assertFalse(QzxyErrors.isAuthExpired(""))
        assertFalse(QzxyErrors.isAuthExpired(null))
    }

    @Test
    fun `服务端要求预约时点名预约`() {
        assertTrue(QzxyErrors.needsBooking("该设备需要预约后使用"))
        assertTrue(QzxyErrors.needsBooking("请先预约"))
    }

    @Test
    fun `其他失败不要说成需要预约`() {
        assertFalse(QzxyErrors.needsBooking("设备不在线"))
        assertFalse(QzxyErrors.needsBooking("还未签约免密支付"))
        assertFalse(QzxyErrors.needsBooking(""))
        assertFalse(QzxyErrors.needsBooking(null))
    }
}
