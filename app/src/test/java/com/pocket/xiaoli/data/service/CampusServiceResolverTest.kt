package com.pocket.xiaoli.data.service

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.model.DormInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampusServiceResolverTest {

    @Test
    fun mainCampusFallbackSpecKeepsOldWebViewBehavior() {
        val main = CampusConfig.byId("ytlg_main")
        val resolved = CampusServiceResolver.resolve(CampusServiceKeys.SHOWER, "洗澡", main)

        assertEquals(CampusServiceType.WEBVIEW, resolved?.spec?.type)
        assertEquals(ServiceChannel.WEBVIEW, resolved?.service?.channel)
        assertEquals("易校园", resolved?.service?.provider)
    }

    @Test
    fun southCampusExplicitSpecWins() {
        val south = CampusConfig.byId("ytu_south")
        val resolved = CampusServiceResolver.resolve(CampusServiceKeys.SHOWER, "洗澡", south)

        assertEquals(CampusServiceType.EXTERNAL_APP, resolved?.spec?.type)
        assertEquals(ServiceChannel.APP, resolved?.service?.channel)
        assertEquals("com.klcxkj.zqxy", resolved?.service?.target)
        assertEquals("趣智校园", resolved?.service?.provider)
    }

    @Test
    fun dormContextRoutesMainCampusToSouthWhenBuildingBelongsToSouth() {
        val main = CampusConfig.byId("ytlg_main")
        val resolved = CampusServiceResolver.resolve(
            key = CampusServiceKeys.SHOWER,
            title = "洗澡",
            campus = main,
            dormInfo = DormInfo("7号宿舍楼", "305"),
        )

        assertEquals("ytu_south", resolved?.campus?.id)
        assertEquals("趣智校园", resolved?.service?.provider)
        assertEquals(ServiceChannel.APP, resolved?.service?.channel)
    }

    @Test
    fun todayCampusIsRegisteredAsExternalApp() {
        val main = CampusConfig.byId("ytlg_main")
        val resolved = CampusServiceResolver.resolve(
            key = CampusServiceCatalog.KEY_TODAY_CAMPUS,
            title = "今日校园",
            campus = main,
        )

        assertEquals(CampusServiceType.EXTERNAL_APP, resolved?.spec?.type)
        assertEquals(CampusServiceCatalog.TODAY_CAMPUS_PACKAGE, resolved?.service?.target)
    }

    @Test
    fun unconfiguredServiceReturnsNull() {
        val main = CampusConfig.byId("ytlg_main")
        assertNull(CampusServiceResolver.resolve("not_configured", "不存在", main))
    }
}