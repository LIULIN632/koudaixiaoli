package com.pocket.xiaoli.data.service

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 服务目录解析测试：锁定今日校园 / 智慧校园门户的渠道定义，避免以后误改回死链。 */
class CampusServiceCatalogTest {

    private val campus = CampusConfig.defaultCampus

    @Test
    fun todayCampusUsesOfficialAppPackage() {
        val service = CampusServiceCatalog.resolve(
            CampusServiceCatalog.KEY_TODAY_CAMPUS, "今日校园", campus
        )
        assertEquals(ServiceChannel.APP, service?.channel)
        assertEquals(CampusServiceCatalog.TODAY_CAMPUS_PACKAGE, service?.target)
        assertTrue(CampusServiceCatalog.isOverridden(CampusServiceCatalog.KEY_TODAY_CAMPUS))
    }

    @Test
    fun smartCampusUsesBuiltInWebViewPortal() {
        val service = CampusServiceCatalog.resolve(CampusServiceKeys.SMART_CAMPUS, "智慧校园", campus)
        assertEquals(ServiceChannel.WEBVIEW, service?.channel)
        assertEquals("http://ehall.yitsd.edu.cn/new/index.html", service?.target)
        assertFalse(CampusServiceCatalog.isOverridden(CampusServiceKeys.SMART_CAMPUS))
    }

    @Test
    fun unknownServiceReturnsNull() {
        assertNull(CampusServiceCatalog.resolve("not_configured", "未知服务", campus))
    }
}
