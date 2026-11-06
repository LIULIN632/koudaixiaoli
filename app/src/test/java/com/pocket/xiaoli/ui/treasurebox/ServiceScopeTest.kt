package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.ui.graphics.Color
import com.pocket.xiaoli.data.ANCHORED_SCHOOL_IDS
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.HOME_CITY
import com.pocket.xiaoli.core.weather.weatherAnchorForCity
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务格子的**可见性**判据 —— 判据在「学校」这一层，不在「校区」。
 *
 * ## 这份测试守的是什么
 *
 * `AppContainer.selectedCampus` 对没收录的学校会静默回退到烟台理工本校区
 *（`CampusConfig.byId` 的兜底），所以能力档位那套判据永远说"能用"：
 * 用户看到一张「宿舍电费」，点下去才吃到一句"这所学校的校园服务还没收录"。
 * 先画一张假格子、再用 Toast 收回它 —— 这是本次要修的病。
 *
 * 修法是按学校把格子**过滤掉**，而过滤最容易出的两种事故正好对应下面两组断言：
 *
 * 1. **误杀**：把不需要学校数据的格子（番茄钟、成绩查询、学习通）也藏了 ——
 *    用户少了一堆能用的东西，且看不出为什么；
 * 2. **漏杀**：需要本校数据的格子还留着 —— 等于没修。
 *
 * 所以核心断言是**不变量**（哪一档跟着哪条事实、任何事实全假时只剩 ANY_SCHOOL），
 * 而不是"某一共有 17 格被藏起来"这种数字快照 —— 后者改一个字就红，且不说明任何事。
 */
class ServiceScopeTest {

    private fun entry(scope: ServiceScope) = ServiceEntry(
        title = "占位格子",
        subtitle = "",
        icon = Icons.Outlined.FlashOn,
        tint = Color.Unspecified,
        background = Color.Unspecified,
        scope = scope,
        onClick = {},
    )

    /** 八种事实组合（三条布尔全排列），用来把"误杀/漏杀"两种事故一次跑尽。 */
    private val allFacts: List<ServiceScopeFacts> = listOf(
        ServiceScopeFacts.ALL,
    ) + listOf(true, false).flatMap { campus ->
        listOf(true, false).flatMap { city ->
            listOf(true, false).map { anchor ->
                ServiceScopeFacts(
                    campusConfigReady = campus,
                    cityWeatherReady = city,
                    anchoredSchoolReady = anchor,
                )
            }
        }
    }

    @Test
    fun 烟台理工口径下任何格子都不消失() {
        for (scope in ServiceScope.entries) {
            assertTrue(
                "$scope 在事实齐备的学校不该被藏起来（否则服务页凭空少格子）",
                serviceEntryVisible(entry(scope), ServiceScopeFacts.ALL),
            )
        }
    }

    @Test
    fun 每一档只跟着自己那条事实() {
        for (facts in allFacts) {
            assertTrue(serviceEntryVisible(entry(ServiceScope.ANY_SCHOOL), facts))
            assertEquals(facts.campusConfigReady, serviceEntryVisible(entry(ServiceScope.CAMPUS_CONFIG), facts))
            assertEquals(facts.cityWeatherReady, serviceEntryVisible(entry(ServiceScope.CITY_DATA), facts))
            assertEquals(facts.anchoredSchoolReady, serviceEntryVisible(entry(ServiceScope.ANCHORED_SCHOOL), facts))
        }
    }

    /**
     * 三条事实全假（一台设备接入了一所**什么数据都没配**的学校）时，
     * 服务页只剩下不依赖学校数据的格子 —— 这一条同时钉住"误杀"与"漏杀"两个方向。
     */
    @Test
    fun 什么都没配时只剩本机与全国性入口() {
        val nothing = ServiceScopeFacts(
            campusConfigReady = false,
            cityWeatherReady = false,
            anchoredSchoolReady = false,
        )
        val visible = ServiceScope.entries.filter { serviceEntryVisible(entry(it), nothing) }
        assertEquals(listOf(ServiceScope.ANY_SCHOOL), visible)
    }

    // ── 事实本身：从学校算出三条布尔 ──

    private val yit = School("YIT", "烟台理工学院", HOME_CITY, JwSystemType.ZHENGFANG, "https://jwxt.yitsd.edu.cn/jwglxt")
    private val ytu = School("YTU", "烟台大学", HOME_CITY, JwSystemType.ZHENGFANG, "https://jw.ytu.edu.cn")
    private val sdut = School("SDUT", "山东理工大学", "淄博", JwSystemType.ZHENGFANG, "https://jwxt.sdut.edu.cn")
    private val yjit = School("YJIT", "燕京理工学院", "廊坊", JwSystemType.QIANGZHI, "https://jw.yjit.edu.cn")

    /** 判据要求调用方传**当前学校的校区**，未收录学校必须是 null —— 传默认校区等于永远为真。 */
    private fun campusOf(school: School) = CampusConfig.forSchoolId(school.id)

    @Test
    fun 烟台理工三条全真() {
        val facts = serviceScopeFacts(yit, campusOf(yit))
        assertEquals(ServiceScopeFacts.ALL, facts)
    }

    /**
     * 烟台大学：校园服务配了（`ytu_south` 有自己的校区条目），
     * 但公交站锚点与图书馆 OPAC 仍写死在烟台理工 —— 那两格对它不显示。
     * 这一条是 `ANCHORED_SCHOOL` 与 `CAMPUS_CONFIG` 必须**分成两档**的理由。
     */
    @Test
    fun 烟台大学有校园服务但没有公交与馆藏() {
        val facts = serviceScopeFacts(ytu, campusOf(ytu))
        assertTrue(facts.campusConfigReady)
        assertTrue(facts.cityWeatherReady)
        assertFalse(facts.anchoredSchoolReady)
    }

    /**
     * 外地学校（山东理工=淄博、燕京理工=廊坊）：**有自己城市的天气，没有校园服务**。
     *
     * 改造前这里断言的是"三条全假"，因为天气那档写死了主场城市 —— 于是这两所学校
     * 连天气都看不见。城市表补上之后，那一条就变成了谎话（它们明明能看自己城市的天气），
     * 所以这条断言跟着改：**只有天气为真**。这正是把判据从"是不是烟台"换成
     * "这座城市配了坐标没有"要换来的结果。
     */
    @Test
    fun 外地学校只有天气没有校园服务() {
        for (school in listOf(sdut, yjit)) {
            val facts = serviceScopeFacts(school, campusOf(school))
            assertFalse("${school.name} 不该有校区配置", facts.campusConfigReady)
            assertTrue("${school.name} 应拿到自己城市的天气", facts.cityWeatherReady)
            assertFalse("${school.name} 没有站点锚点", facts.anchoredSchoolReady)
        }
    }

    /** 自建学校（`custom_` 前缀）按它填的城市走，与内置名单同一套判据。 */
    @Test
    fun 自建学校按城市判定() {
        val local = School("custom_1", "自定义烟台学校", HOME_CITY, JwSystemType.ZHENGFANG, "https://example.edu.cn")
        val elsewhere = School("custom_2", "自定义外地学校", "昆明", JwSystemType.ZHENGFANG, "https://example.edu.cn")
        assertTrue("自建学校填烟台 → 天气可用", serviceScopeFacts(local, null).cityWeatherReady)
        assertFalse("昆明确实还没配坐标", serviceScopeFacts(elsewhere, null).cityWeatherReady)
    }

    @Test
    fun 没有学校时三条全假() {
        // 一台设备理论上不会走到这里（校区配置会兜出烟台理工），
        // 但判据必须把 null 当成"什么都不知道"，而不是猜一所学校。
        assertEquals(
            ServiceScopeFacts(false, false, false),
            serviceScopeFacts(null, null),
        )
    }

    // ── 学校层配置本身（本次判据的地基）──

    @Test
    fun 校区按学校取_未收录返回空() {
        assertEquals("ytlg_main", campusOf(yit)?.id)
        assertEquals("ytu_south", campusOf(ytu)?.id)
        assertNull("山东理工没有校区配置：借用默认校区就等于把烟台理工的电费当成它的", campusOf(sdut))
        assertNull(campusOf(yjit))
        assertFalse(CampusConfig.hasCampusFor(sdut.id))
        assertTrue(CampusConfig.hasCampusFor(yit.id))
    }

    @Test
    fun 默认校区仍然兜底但可查空() {
        // `byId` 的静默回退是历史调用点的地基，`byIdOrNull` 才是新判据该用的那个。
        assertEquals(CampusConfig.defaultCampus, CampusConfig.byId("no_such_campus"))
        assertNull(CampusConfig.byIdOrNull("no_such_campus"))
    }

    /**
     * `ANCHORED_SCHOOL` 是**临时口径**（见 SchoolRegistry 的 KDoc）：
     * 数据源写死在一所学校，早晚要搬进校区配置。这条断言不是欢迎它，是给删除留个哨兵 ——
     * 想往里面加第二个 id 时必须先改这段断言，也就是逼那一次"该不该搬进配置"的讨论。
     */
    @Test
    fun 写死数据源的学校名单目前只有烟台理工() {
        assertEquals(setOf("YIT"), ANCHORED_SCHOOL_IDS)
    }

    /**
     * 天气那一档跟着**城市表**，不再跟着「是不是主场城市」那句字符串比较。
     * 这张表必须覆盖选校列表里会出现的所有城市 —— 少一座，那所学校的天气格就凭空消失。
     */
    @Test
    fun 选校列表的城市都有天气坐标() {
        for (city in listOf(HOME_CITY, "淄博", "廊坊")) {
            assertNotNull("城市「$city」没配天气坐标", weatherAnchorForCity(city))
        }
        assertNull("没配的城市必须返回 null，由可见性判据把格子藏起来", weatherAnchorForCity("昆明"))
        assertNull(weatherAnchorForCity(null))
    }

    // ── 整条前处理管道（过滤 → 去死格子 → 排座次）──

    private fun realEntry(
        title: String,
        scope: ServiceScope,
        serviceKey: String? = null,
    ) = ServiceEntry(
        title = title,
        subtitle = "",
        icon = Icons.Outlined.FlashOn,
        tint = Color.Unspecified,
        background = Color.Unspecified,
        serviceKey = serviceKey,
        scope = scope,
        onClick = {},
    )

    /** 与服务页真实形状同构的一节：本机格 / 学校格 / 城市格 / 写死数据源的格各一，再加「校园跑」那颗死格子。 */
    private val sampleSections = listOf(
        ServiceSection(
            title = "生活服务",
            entries = listOf(
                realEntry("专注计时", ServiceScope.ANY_SCHOOL),
                realEntry("校园卡", ServiceScope.CAMPUS_CONFIG, CampusServiceKeys.CAMPUS_CARD),
                realEntry("天气", ServiceScope.CITY_DATA),
                realEntry("公交到站", ServiceScope.ANCHORED_SCHOOL),
                realEntry("校园跑", ServiceScope.CAMPUS_CONFIG, CampusServiceKeys.SCHOOL_RUN),
            ),
        ),
    )

    private fun titlesOf(facts: ServiceScopeFacts, campusId: String) =
        visibleSections(sampleSections, facts, CampusConfig.byId(campusId), null)
            .first().entries.map { it.title }

    /** 烟台理工口径下，座次与"死格子"过滤都要与改造前逐字一致 —— 这条挡住顺手改坏老行为。 */
    @Test
    fun 本校口径下管道行为不变() {
        val titles = titlesOf(ServiceScopeFacts.ALL, "ytlg_main")
        assertEquals(
            "「校园跑」在本校没配直达链接时是死格子，仍然不该出现；其余四格按档位排座次",
            listOf("专注计时", "天气", "公交到站", "校园卡"),
            titles,
        )
        assertFalse("死格子不该被排回列表", "校园跑" in titles)
    }

    /**
     * 未收录学校：**只剩不需要本校数据的格子**，且死格子那条规则不会被绕过
     *（这一格已经因为学校判据先没了，两条规则各管各的，谁也不代替谁）。
     */
    @Test
    fun 未收录学校只剩本机格子() {
        val nothing = ServiceScopeFacts(false, false, false)
        assertEquals(listOf("专注计时"), titlesOf(nothing, "ytlg_main"))
    }

    /** 烟台大学：校园服务与天气在，公交与馆藏不在 —— 两档判据分开才有这个结果。 */
    @Test
    fun 烟台大学少两格() {
        val ytu = School("YTU", "烟台大学", HOME_CITY, JwSystemType.ZHENGFANG, "https://jw.ytu.edu.cn")
        val facts = serviceScopeFacts(ytu, CampusConfig.forSchoolId(ytu.id))
        assertEquals(
            listOf("专注计时", "天气", "校园卡").sorted(),
            titlesOf(facts, "ytu_south").sorted(),
        )
    }
}
