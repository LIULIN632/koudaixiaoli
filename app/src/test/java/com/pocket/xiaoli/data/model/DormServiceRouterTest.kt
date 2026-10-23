package com.pocket.xiaoli.data.model

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.service.CampusServiceCatalog
import com.pocket.xiaoli.data.service.ServiceChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DormServiceRouterTest {

    @Test
    fun ytuSouthBuildingsResolveToSouthServices() {
        assertEquals("ytu_south", DormBuildingCatalog.serviceCampusId("7号宿舍楼"))
        assertEquals("ytu_south", DormBuildingCatalog.serviceCampusId("12号楼"))
        assertEquals("ytu_south", DormBuildingCatalog.serviceCampusId("南校9号"))
    }

    @Test
    fun yitBuildingsResolveToMainServices() {
        assertEquals("ytlg_main", DormBuildingCatalog.serviceCampusId("3号楼"))
        assertEquals("ytlg_main", DormBuildingCatalog.serviceCampusId("13号宿舍楼"))
    }

    @Test
    fun unknownBuildingFallsBackToSelectedCampus() {
        assertNull(DormBuildingCatalog.serviceCampusId("神秘宿舍"))
        val fallback = CampusConfig.byId("ytlg_main")
        val resolved = DormServiceRouter.serviceCampus(DormInfo("神秘宿舍", "101"), fallback)
        assertEquals("ytlg_main", resolved.id)
    }

    @Test
    fun dormServiceCampusFollowsBuildingNotSelectedCampus() {
        val selected = CampusConfig.byId("ytlg_main")
        val resolved = DormServiceRouter.serviceCampus(DormInfo("7号宿舍楼", "305"), selected)
        assertEquals("ytu_south", resolved.id)
    }

    @Test
    fun southShowerAndLaundryUseSouthChannels() {
        val south = CampusConfig.byId("ytu_south")
        val shower = CampusServiceCatalog.resolve(CampusServiceKeys.SHOWER, "洗澡", south)
        assertEquals("趣智校园", shower?.provider)
        assertEquals(ServiceChannel.APP, shower?.channel)
        assertEquals("com.klcxkj.zqxy", shower?.target)

        val laundry = CampusServiceCatalog.resolve(CampusConfig.SERVICE_LAUNDRY, "洗衣", south)
        assertEquals("U净", laundry?.provider)
        assertEquals(ServiceChannel.ALIPAY, laundry?.channel)
    }

    @Test
    fun mainCampusStillUsesMainServices() {
        val main = CampusConfig.byId("ytlg_main")
        val shower = CampusServiceCatalog.resolve(CampusServiceKeys.SHOWER, "洗澡", main)
        assertEquals("易校园", shower?.provider)
        assertEquals(ServiceChannel.WEBVIEW, shower?.channel)

        assertNull(CampusServiceCatalog.resolve(CampusConfig.SERVICE_LAUNDRY, "洗衣", main))
    }

    @Test
    fun userOverrideWinsOverDefaultBuildingMapping() {
        val selected = CampusConfig.byId("ytlg_main")
        val resolved = DormServiceRouter.serviceCampus(
            DormInfo("7号宿舍楼", "305", serviceCampusId = "ytlg_main"),
            selected,
        )
        assertEquals("ytlg_main", resolved.id)
    }
}
