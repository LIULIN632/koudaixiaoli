package com.pocket.xiaoli.data.service

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusTransitCodeRegistryTest {

    @Test
    fun transitCodeIsRegisteredForDefaultCampus() {
        val spec = CampusServiceRegistry.specFor(
            campusId = CampusConfig.defaultCampus.id,
            key = CampusServiceKeys.TRANSIT_CODE,
        )
        assertNotNull(spec)
        assertEquals("烟台乘车码", spec?.name)
        assertEquals(CampusServiceType.EXTERNAL_APP, spec?.type)
        assertEquals("com.ixiaoma.yantaibus", spec?.target)
        assertTrue(spec?.fallback?.startsWith("market://") == true)
    }

    @Test
    fun transitCodeResolvesToOpenableAppService() {
        val service = CampusServiceCatalog.resolve(
            key = CampusServiceKeys.TRANSIT_CODE,
            title = "烟台乘车码",
            campus = CampusConfig.defaultCampus,
        )
        assertNotNull(service)
        assertEquals(ServiceChannel.APP, service?.channel)
        assertEquals("com.ixiaoma.yantaibus", service?.target)
    }
}