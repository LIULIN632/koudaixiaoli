package com.pocket.xiaoli.data.service

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.model.DormInfo
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DormLifeServiceAdapterTest {

    @Test
    fun southDormLaundryReturnsReadySummary() = runBlocking {
        val main = CampusConfig.byId("ytlg_main")
        val summary = CampusServiceAdapterRegistry.loadSummary(
            key = CampusConfig.SERVICE_LAUNDRY,
            title = "洗衣",
            campus = main,
            dormInfo = DormInfo("7号宿舍楼", "305"),
        )

        assertEquals(ServiceSummaryState.READY, summary?.state)
        assertEquals("洗衣", summary?.title)
        assertEquals("南校区", summary?.badge)
        assertEquals("7号宿舍楼 305", summary?.metrics?.firstOrNull { it.label == "宿舍" }?.value)
        assertEquals("U净", summary?.metrics?.firstOrNull { it.label == "服务商" }?.value)
    }

    @Test
    fun unboundDormServiceReturnsNeedsBinding() = runBlocking {
        val main = CampusConfig.byId("ytlg_main")
        val summary = CampusServiceAdapterRegistry.loadSummary(
            key = CampusServiceKeys.SHOWER,
            title = "洗澡",
            campus = main,
            dormInfo = null,
        )

        assertEquals(ServiceSummaryState.NEEDS_BINDING, summary?.state)
        assertEquals("洗澡", summary?.title)
    }

    @Test
    fun mainDormElectricityUsesMainCampusProvider() = runBlocking {
        val main = CampusConfig.byId("ytlg_main")
        val summary = CampusServiceAdapterRegistry.loadSummary(
            key = CampusServiceKeys.ELECTRICITY,
            title = "宿舍电费",
            campus = main,
            dormInfo = DormInfo("3号楼", "401"),
        )

        assertEquals(ServiceSummaryState.READY, summary?.state)
        assertEquals("本校区", summary?.badge)
        assertEquals("校园电费", summary?.metrics?.firstOrNull { it.label == "服务商" }?.value)
    }

    @Test
    fun nonDormServiceHasNoAdapterSummary() = runBlocking {
        val main = CampusConfig.byId("ytlg_main")
        val summary = CampusServiceAdapterRegistry.loadSummary(
            key = CampusServiceKeys.CAMPUS_CARD,
            title = "校园余额",
            campus = main,
        )

        assertNull(summary)
    }

    @Test
    fun dormLifeAdapterIsRegistered() {
        assertTrue("dorm_life" in CampusServiceAdapterRegistry.availableAdapters())
    }

    @Test
    fun userCampusOverrideChangesSummaryCampus() = runBlocking {
        val main = CampusConfig.byId("ytlg_main")
        val summary = CampusServiceAdapterRegistry.loadSummary(
            key = CampusServiceKeys.SHOWER,
            title = "洗澡",
            campus = main,
            dormInfo = DormInfo("7号宿舍楼", "305", serviceCampusId = "ytlg_main"),
        )

        assertEquals(ServiceSummaryState.READY, summary?.state)
        assertEquals("本校区", summary?.badge)
    }

    /**
     * 崩溃回归：v1.0.93 在服务页停留后闪退，堆栈落到
     * `StringsKt.isBlank` ← `DormLifeServiceAdapter.loadSummary` 第 59 行（按编号加指标那一行）。
     *
     * 原因是老存档里的宿舍 JSON 没有 `dormCode` 这一栏，release 包读出来是 null。
     * 这里直接用「Gson 造出来的 null 字段」当输入，把那条路径钉住。
     */
    @Test
    fun legacyDormWithoutNewFieldsDoesNotCrashSummary() = runBlocking {
        val legacy = Gson().fromJson(
            """{"building":"7号宿舍楼","room":"305","serviceCampusId":null}""",
            DormInfo::class.java,
        )
        val main = CampusConfig.byId("ytlg_main")

        // 电费这一项会读 dormCode —— 以前就是在这里崩的
        val electricity = CampusServiceAdapterRegistry.loadSummary(
            key = CampusServiceKeys.ELECTRICITY,
            title = "宿舍电费",
            campus = main,
            dormInfo = legacy,
        )
        assertEquals(ServiceSummaryState.READY, electricity?.state)
        assertTrue(
            "没有编号时不该多出「宿舍编号」这一项",
            electricity?.metrics?.none { it.label == "宿舍编号" } ?: true,
        )

        // 其余宿舍服务同样不该崩
        assertEquals(
            ServiceSummaryState.READY,
            CampusServiceAdapterRegistry.loadSummary(
                key = CampusConfig.SERVICE_LAUNDRY,
                title = "洗衣",
                campus = main,
                dormInfo = legacy,
            )?.state,
        )
    }

    /** 字段全为 null 的宿舍（手改过的存档）也不能把服务页带崩。 */
    @Test
    fun fullyNullDormDoesNotCrashSummary() = runBlocking {
        val nulls = Gson().fromJson(
            """{"building":null,"room":null,"serviceCampusId":null,"dormCode":null}""",
            DormInfo::class.java,
        )
        val main = CampusConfig.byId("ytlg_main")

        val summary = CampusServiceAdapterRegistry.loadSummary(
            key = CampusServiceKeys.ELECTRICITY,
            title = "宿舍电费",
            campus = main,
            dormInfo = nulls,
        )
        // 没有楼栋等于没绑定 —— 要给 NEEDS_BINDING，而不是崩
        assertEquals(ServiceSummaryState.NEEDS_BINDING, summary?.state)
    }
}
