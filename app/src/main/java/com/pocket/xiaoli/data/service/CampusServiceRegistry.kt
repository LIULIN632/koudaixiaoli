package com.pocket.xiaoli.data.service

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.CampusServiceKeys

/**
 * 校园服务注册表。
 *
 * 这里是「服务如何被打开」的唯一事实来源：
 * 1. 先查校区/全局显式 spec；
 * 2. 没有显式 spec 时，由 resolver 用 `CampusInfo.serviceUrls` 生成默认 WEBVIEW 方案；
 * 3. 以后接 API / 原生页，只在这里补 spec，UI 不需要知道实现方式。
 */
object CampusServiceRegistry {

    const val KEY_TODAY_CAMPUS = "today_campus"
    const val TODAY_CAMPUS_PACKAGE = "com.wisedu.cpdaily"

    /** 校园卡（易校园，云马）App 包名。没装时走门户里的易校园入口，见 CampusCardLauncher。 */
    const val CAMPUS_CARD_PACKAGE = "cn.com.yunma.school.app"

    private const val ALIPAY_PREFIX = "alipays://platformapi/startapp?appId=20000067&url="
    private const val YTU_SOUTH_SHOWER_ALIPAY = ALIPAY_PREFIX + "https://ur.alipay.com/_3RNRCjUEqDwoXrlYv9z3CQ"
    private const val YTU_SOUTH_LAUNDRY_ALIPAY = ALIPAY_PREFIX + "https://ur.alipay.com/_7vb3EeHonidVGuRpPfF9LB"
    private const val YTU_SOUTH_WATER_ALIPAY = ALIPAY_PREFIX + "https://ur.alipay.com/_6ieUaYIW8aFebTkPEcIUw"
    private const val YTU_SOUTH_ELECTRICITY_URL = "https://ydny.ytu.edu.cn/pms/wechat/ytdx/payItemAdd"

    private val COMMON_SPECS: Map<String, CampusServiceSpec> = mapOf(
        KEY_TODAY_CAMPUS to CampusServiceSpec(
            id = KEY_TODAY_CAMPUS,
            name = "今日校园",
            category = "general",
            type = CampusServiceType.EXTERNAL_APP,
            priority = 10,
            provider = "今日校园",
            target = TODAY_CAMPUS_PACKAGE,
        ),
        CampusServiceKeys.TRANSIT_CODE to CampusServiceSpec(
            id = CampusServiceKeys.TRANSIT_CODE,
            name = "烟台乘车码",
            category = "life",
            type = CampusServiceType.EXTERNAL_APP,
            priority = 20,
            provider = "烟台公交",
            target = "com.ixiaoma.yantaibus",
            fallback = "market://search?q=烟台公交",
        ),
    )

    private val CAMPUS_SPECS: Map<String, Map<String, CampusServiceSpec>> = mapOf(
        "ytlg_main" to mapOf(
            CampusServiceKeys.CAMPUS_CARD to CampusServiceSpec(
                id = CampusServiceKeys.CAMPUS_CARD,
                name = "校园卡",
                category = "life",
                type = CampusServiceType.EXTERNAL_APP,
                priority = 10,
                provider = "易校园",
                target = CAMPUS_CARD_PACKAGE,
                // 兜底从「易校园官网」改成门户：官网只是个宣传页，办不了任何事；
                // 门户登录后本来就有易校园入口（而且统一认证是免登的）。
                fallback = "http://ehall.yitsd.edu.cn/new/index.html",
            ),
            CampusServiceKeys.COURSE_SELECT to CampusServiceSpec(
                id = CampusServiceKeys.COURSE_SELECT, name = "选课", category = "study",
                type = CampusServiceType.NATIVE, priority = 10, provider = "教务系统",
                target = "native://course_select",
                // 兜底地址同步更正为正方 v9 自主选课页（旧值 `jiaowu.yitsd.edu.cn/jwxt/xkxt.htm`
                // 是上一代选课系统，已不适用）。理由与取舍见 `CampusConfig` 里同一处的注释。
                fallback = "https://jwxt.yitsd.edu.cn/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default",
                requiresAuth = true,
            ),
        ),
        "ytu_south" to mapOf(
            CampusServiceKeys.SHOWER to CampusServiceSpec(
                id = CampusServiceKeys.SHOWER,
                name = "洗澡",
                category = "life",
                type = CampusServiceType.EXTERNAL_APP,
                priority = 10,
                provider = "趣智校园",
                target = "com.klcxkj.zqxy",
                fallback = YTU_SOUTH_SHOWER_ALIPAY,
                dormZoneAware = true,
            ),
            CampusServiceKeys.HOT_WATER to CampusServiceSpec(
                id = CampusServiceKeys.HOT_WATER,
                name = "饮水",
                category = "life",
                type = CampusServiceType.ALIPAY,
                priority = 10,
                provider = "胖乖",
                target = YTU_SOUTH_WATER_ALIPAY,
                dormZoneAware = true,
            ),
            CampusConfig.SERVICE_LAUNDRY to CampusServiceSpec(
                id = CampusConfig.SERVICE_LAUNDRY,
                name = "洗衣",
                category = "life",
                type = CampusServiceType.ALIPAY,
                priority = 10,
                provider = "U净",
                target = YTU_SOUTH_LAUNDRY_ALIPAY,
                dormZoneAware = true,
            ),
            CampusServiceKeys.ELECTRICITY to CampusServiceSpec(
                id = CampusServiceKeys.ELECTRICITY,
                name = "宿舍电费",
                category = "life",
                type = CampusServiceType.WEBVIEW,
                priority = 10,
                provider = "烟大后勤",
                target = YTU_SOUTH_ELECTRICITY_URL,
                requiresAuth = true,
                dormZoneAware = true,
            ),
        ),
    )

    /** 显式登记的 spec；没有则返回 null，由 resolver 用 serviceUrls 生成默认方案。 */
    fun specFor(campusId: String, key: String): CampusServiceSpec? =
        CAMPUS_SPECS[campusId]?.get(key) ?: COMMON_SPECS[key]

    private val SERVICE_TITLES: Map<String, String> = mapOf(
        CampusServiceKeys.SMART_CAMPUS to "智慧校园",
        CampusServiceKeys.CAMPUS_CARD to "校园卡",
        CampusServiceKeys.ELECTRICITY to "宿舍电费",
        CampusServiceKeys.NETWORK to "校园网",
        CampusServiceKeys.SHOWER to "洗澡",
        CampusServiceKeys.HOT_WATER to "热水",
        CampusServiceKeys.EXPRESS to "快递",
        CampusServiceKeys.REPAIR to "报修",
        CampusServiceKeys.NOTICE to "通知公告",
        CampusServiceKeys.JOB to "就业",
        CampusServiceKeys.LIBRARY to "图书馆",
        CampusServiceKeys.CLASSROOM to "空教室查询",
        CampusServiceKeys.COURSE_SELECT to "选课",
        CampusServiceKeys.EVALUATION to "评教",
        CampusServiceKeys.TRANSFER_MAJOR to "转专业",
        CampusServiceKeys.UPGRADE to "专升本",
        CampusServiceKeys.EXAM to "考试",
        CampusServiceKeys.SECOND_CLASS to "到梦空间",
        CampusServiceKeys.TRANSIT_CODE to "烟台乘车码",
        CampusServiceKeys.GYM to "健身房",
        CampusServiceKeys.DRIVING_SCHOOL to "驾校",
    )

    /** 某校区的完整服务目录：显式 spec + serviceUrls 默认 web 方案。 */
    fun catalogFor(campus: CampusInfo): List<CampusServiceSpec> {
        val explicit = specsFor(campus.id).associateBy { it.id }
        val defaults = campus.serviceUrls.mapNotNull { (key, url) ->
            if (url.isBlank() || key in explicit) return@mapNotNull null
            CampusServiceSpec(
                id = key,
                name = SERVICE_TITLES[key] ?: key,
                category = "general",
                type = CampusServiceType.WEBVIEW,
                priority = 100,
                provider = campus.services[key] ?: campus.shortName,
                target = url,
            )
        }
        return (explicit.values + defaults).sortedWith(compareBy({ it.priority }, { it.name }))
    }
    /** 是否在任意校区里有显式登记（UI 角标可用）。 */
    fun hasAnyExplicitSpec(key: String): Boolean =
        COMMON_SPECS.containsKey(key) || CAMPUS_SPECS.values.any { it.containsKey(key) }

    fun specsFor(campusId: String): List<CampusServiceSpec> {
        val explicit = CAMPUS_SPECS[campusId].orEmpty()
        return (COMMON_SPECS + explicit).values.sortedWith(compareBy({ it.priority }, { it.name }))
    }
}
