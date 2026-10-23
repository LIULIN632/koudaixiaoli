package com.pocket.xiaoli.data.service

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.model.DormInfo
import com.pocket.xiaoli.data.model.DormServiceRouter
import com.pocket.xiaoli.ui.treasurebox.sortRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务入口**能力档位**的判据。
 *
 * ## 这份测试守的是什么
 *
 * 服务页有 43 个入口，界面此前不区分"接通了没有"，于是点「校园网」才知道
 * 它只是个说明页 —— 学生对另外 42 个也不再信任。修法是把能力档位显式化，
 * 而**显式化最怕的就是它与真实行为漂移**（界面标"能用"、点下去弹预留）。
 *
 * 所以核心断言不是"校园网是 INFO"这种数据快照（改配置就会红、且不说明任何事），
 * 而是两条**不变量**：
 *
 * 1. **能力与现实一致**：判为 WEB/NATIVE 的入口必须真能被打开；
 *    判为 INFO/RESERVED 的必须真的打不开（否则就是"骗用户去点"或"藏着能用的"）。
 * 2. **档位序**：能用得上的排在"仅介绍 / 未接入"前面 —— 这条决定服务页的座次。
 *
 * 数据快照断言只保留在**语义稳定**的地方（宿舍生活服务走哪个校区），
 * 那是 `DormServiceRouter` 的既有契约，不是本次新增的判断。
 */
class CampusServiceCapabilityTest {

    private val main = CampusConfig.byId("ytlg_main")
    private val south = CampusConfig.byId("ytu_south")

    /** 与 `TreasureBoxScreen` 的入口列表同源的 key 抽样：注释写明每一类的代表性。 */
    private val sampleKeys = listOf(
        CampusServiceKeys.SHOWER,          // 宿舍生活服务，受宿舍楼影响
        CampusServiceKeys.ELECTRICITY,     // 同上，且有原生页（绑了宿舍才走）
        CampusServiceKeys.HOT_WATER,
        CampusConfig.SERVICE_LAUNDRY,
        CampusServiceKeys.CAMPUS_CARD,     // 有显式 spec（EXTERNAL_APP）
        CampusServiceKeys.TRANSIT_CODE,    // 同上
        CampusServiceKeys.COURSE_SELECT,   // 显式 spec 且 type = NATIVE（需 adapter）
        CampusServiceKeys.NETWORK,         // 有原生"说明页"的典型：不该被说成能用
        CampusServiceKeys.REPAIR,
        CampusServiceKeys.JOB,
        // 故意用一个两边都没有的 key：判不出来时必须是 INFO（低估可用性，
        // 只会让人不去点；高估会让人白点一次并失去信任）
        "definitely_not_registered_key",
    )

    private val campuses = listOf(main, south)

    private val dormContexts = listOf(
        null,
        DormInfo("7号宿舍楼", "305"),
        DormInfo("3号宿舍楼", "101"),
    )

    /**
     * **能力判定与"真有没有去处"必须一致** —— 这是本次改动的核心不变量。
     *
     * 注意 `WEB` 的语义是"**有真实去处**"，而不是"一定能解析出一个 UrlService"：
     * 去处有三种，任何一种都算数 ——
     * ① 解析出可拉起的渠道（WebView / 小程序 / 支付宝 / 第三方 App）；
     * ② spec 首选原生实现但**登记了兜底 URL**（如选课：原生页优先、网页兜底）；
     * ③ 界面层有原生页（由 `UI_NATIVE_SERVICE_KEYS` 声明，在 UI 测试里守）。
     *
     * 反过来，判成 `INFO`/`RESERVED` 的必须**真的哪儿都去不了** ——
     * 否则角标就从"提前告知"退化成"骗用户别点"。
     */
    @Test
    fun webCapabilityImpliesARealDestination() {
        for (campus in campuses) {
            for (dorm in dormContexts) {
                for (key in sampleKeys) {
                    if (CampusServiceResolver.capabilityOf(key, campus, dorm) != CampusServiceCapability.WEB) {
                        continue
                    }
                    val resolved = CampusServiceResolver.resolve(key, key, campus, dorm)
                    val launchable = resolved?.service != null
                    val hasFallback = resolved?.spec?.fallback?.isNotBlank() == true
                    assertTrue(
                        "判为 WEB 却既拉不起也没兜底：key=$key campus=${campus.id}",
                        launchable || hasFallback,
                    )
                }
            }
        }
    }

    @Test
    fun infoAndReservedCapabilityImpliesNoDestinationAtAll() {
        for (campus in campuses) {
            for (dorm in dormContexts) {
                for (key in sampleKeys) {
                    val capability = CampusServiceResolver.capabilityOf(key, campus, dorm)
                    if (capability != CampusServiceCapability.INFO &&
                        capability != CampusServiceCapability.RESERVED
                    ) {
                        continue
                    }
                    assertNull(
                        "判成不可用，却其实能拉起：key=$key campus=${campus.id} cap=$capability",
                        CampusServiceResolver.resolve(key, key, campus, dorm)?.service,
                    )
                }
            }
        }
    }

    /**
     * `INFO` 与 `RESERVED` 的分界是"**系统里到底有没有这个东西**"：
     * 注册表没登记 ⇒ INFO（站内说明文字）；登记了却没有可用实现 ⇒ RESERVED（未接入）。
     */
    @Test
    fun infoMeansUnregisteredWhileReservedMeansRegisteredButUnimplemented() {
        assertNull(
            "抽样里应当存在完全没登记的 key",
            CampusServiceRegistry.specFor(main.id, "definitely_not_registered_key"),
        )
        assertEquals(
            CampusServiceCapability.INFO,
            CampusServiceResolver.capabilityOf("definitely_not_registered_key", main),
        )
    }

    /**
     * **登记为 NATIVE/API 的 spec，命运由 `fallback` 决定** —— 这是本文档最容易判错的一档。
     *
     * - 有 `fallback`：首选是实现原生页，但兜底网页确实能打开 ⇒ 必须报 **WEB**
     *   （实测 `course_select` 就属此类。把它判成"未接入"是另一种假话，方向相反）；
     * - 没有 `fallback`：既没 adapter 也没网页 ⇒ 报 **RESERVED**（要靠角标提前告知）。
     *
     * ## 为什么用**合成 spec** 而不是现网数据来测 RESERVED 那一支
     *
     * 现网注册表里目前**没有**"NATIVE 且 fallback 为空"的条目（三个 NATIVE spec
     * 都带兜底），若只断言现网数据，RESERVED 这一支就永远跑不到 —— 测试会绿，
     * 但绿得毫无意义（假绿）。所以这里直接构造一个这样的 spec 把规则钉死，
     * 现网数据则用上面的不变量测试覆盖。
     */
    @Test
    fun nativeSpecWithFallbackIsWebAndWithoutFallbackIsReserved() {
        val withFallback = CampusServiceSpec(
            id = "tmp_native_with_fallback",
            name = "合成·有兜底",
            type = CampusServiceType.NATIVE,
            target = "native://tmp",
            fallback = "https://example.invalid/fb",
        )
        val withoutFallback = CampusServiceSpec(
            id = "tmp_native_without_fallback",
            name = "合成·无兜底",
            type = CampusServiceType.NATIVE,
            target = "native://tmp",
        )
        assertEquals(
            "有 fallback 的原生 spec 仍然能打开，必须报 WEB",
            CampusServiceCapability.WEB,
            CampusServiceResolver.capabilityOfSpec(withFallback),
        )
        assertEquals(
            "既无 adapter 也无 fallback，必须报 RESERVED",
            CampusServiceCapability.RESERVED,
            CampusServiceResolver.capabilityOfSpec(withoutFallback),
        )
        // API 与 NATIVE 同规则（都靠 adapter），别让两档走散
        assertEquals(
            CampusServiceCapability.RESERVED,
            CampusServiceResolver.capabilityOfSpec(withoutFallback.copy(type = CampusServiceType.API)),
        )
    }

    @Test
    fun unregisteredKeyIsInfoNotReserved() {
        val key = "definitely_not_registered_key"
        assertNull(CampusServiceResolver.resolve(key, key, main))
        assertEquals(CampusServiceCapability.INFO, CampusServiceResolver.capabilityOf(key, main))
    }

    /**
     * 宿舍生活服务的档位必须跟着**宿舍路由**走，而不是只看当前校区。
     *
     * 这是"界面标能用、实际不能用"的高发区：用户在本校区、宿舍在南校区时，
     * 洗澡/热水/洗衣要按南校区的 spec 判定。两处若各判一次就会漂移，
     * 所以 `capabilityOf` 与 `resolve` 共用 `targetCampusOf`。
     */
    @Test
    fun dormServicesUseDormRoutedCampus() {
        val dorm = DormInfo("7号宿舍楼", "305")
        val southId = DormServiceRouter.serviceCampus(dorm, main).id
        assertEquals("ytu_south", southId)

        for (key in DormServiceRouter.dormServiceKeys) {
            assertEquals(
                "宿舍服务 $key 的能力必须按宿舍路由后的校区判定",
                CampusServiceResolver.capabilityOf(key, south, dorm),
                CampusServiceResolver.capabilityOf(key, main, dorm),
            )
        }
    }

    @Test
    fun sortRankPutsUsableBeforeUnusable() {
        // 显式列出四个档位（不依赖 `enumValues`/`entries` 的推断），
        // 断言的是"序"而不是"有哪些" —— 新增档位时这里会因 `when` 的穷尽性先编译失败。
        val ranks: List<Int> = listOf(
            CampusServiceCapability.NATIVE,
            CampusServiceCapability.WEB,
            CampusServiceCapability.INFO,
            CampusServiceCapability.RESERVED,
        ).map { it.sortRank }
        assertEquals(ranks.sorted(), ranks)
        assertEquals(ranks.distinct().size, ranks.size)
    }
}
