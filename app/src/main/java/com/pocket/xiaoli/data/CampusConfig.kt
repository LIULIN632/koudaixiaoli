package com.pocket.xiaoli.data

data class CampusInfo(
    val id: String,
    val name: String,
    val shortName: String,
    val underConstruction: Boolean,
    val schoolId: String? = null,
    val schoolName: String? = null,
    val jwType: String? = null,
    val jwBaseUrl: String? = null,
    val portalUrl: String? = null,
    val services: Map<String, String> = emptyMap(),
    val serviceUrls: Map<String, String> = emptyMap()
) {
    val laundryProvider: String?
        get() = services[CampusConfig.SERVICE_LAUNDRY]
}

val CampusInfo.jwTypeLabel: String
    get() = when (jwType) {
        "ZHENGFANG" -> "正方教务"
        "QIANGZHI" -> "强智教务"
        "QINGGUO" -> "青果教务"
        "CHAOXING" -> "超星教务"
        else -> "待配置"
    }

object CampusServiceKeys {
    /** 智慧校园 / 统一认证门户（今日校园网页版入口，与 7 项门户服务同源） */
    const val SMART_CAMPUS = "smart_campus"
    const val CAMPUS_CARD = "campus_card"
    const val ELECTRICITY = "electricity"
    const val NETWORK = "network"
    const val SHOWER = "shower"
    const val HOT_WATER = "hot_water"
    const val EXPRESS = "express"
    const val REPAIR = "repair"
    const val NOTICE = "notice"
    const val JOB = "job"
    const val LIBRARY = "library"
    const val CLASSROOM = "classroom"
    const val COURSE_SELECT = "course_select"
    const val EVALUATION = "evaluation"
    const val TRANSFER_MAJOR = "transfer_major"
    const val UPGRADE = "upgrade"
    const val EXAM = "exam"
    const val SECOND_CLASS = "second_class"
    /** 校园跑 / 阳光跑：支付宝小程序直达（见 `CampusServiceRegistry.SCHOOL_RUN_ALIPAY`） */
    const val SCHOOL_RUN = "school_run"
    const val TRANSIT_CODE = "transit_code"
    const val GYM = "gym"
    const val DRIVING_SCHOOL = "driving_school"
}

object CampusConfig {
    const val SERVICE_LAUNDRY = "laundry"

    val campuses: List<CampusInfo> = listOf(
        CampusInfo(
            id = "ytlg_main",
            name = "烟台理工本校区",
            shortName = "本校区",
            underConstruction = false,
            schoolId = "YIT",
            schoolName = "烟台理工学院",
            jwType = "ZHENGFANG",
            jwBaseUrl = "https://jwxt.yitsd.edu.cn/jwglxt",
            portalUrl = "https://www.yitsd.edu.cn",
            services = mapOf(
                SERVICE_LAUNDRY to "浣熊先生",
                CampusServiceKeys.SHOWER to "易校园",
                CampusServiceKeys.CAMPUS_CARD to "易校园",
                CampusServiceKeys.HOT_WATER to "热水",
                CampusServiceKeys.ELECTRICITY to "校园电费"
            ),
            // 2026-09-22 实测：http://ehall.yitsd.edu.cn 会 302 到 /new/index.html（200）；
            // 7 项校园服务的深层入口需要登录态，暂统一进智慧校园门户，不在公开页伪造 serviceId。
            serviceUrls = mapOf(
                CampusServiceKeys.SMART_CAMPUS to "http://ehall.yitsd.edu.cn/new/index.html",
                CampusServiceKeys.CAMPUS_CARD to "http://ehall.yitsd.edu.cn/new/index.html",
                CampusServiceKeys.ELECTRICITY to "http://ehall.yitsd.edu.cn/new/index.html",
                CampusServiceKeys.NETWORK to "http://ehall.yitsd.edu.cn/new/index.html",
                CampusServiceKeys.SHOWER to "http://ehall.yitsd.edu.cn/new/index.html",
                CampusServiceKeys.HOT_WATER to "http://ehall.yitsd.edu.cn/new/index.html",
                CampusServiceKeys.EXPRESS to "http://ehall.yitsd.edu.cn/new/index.html",
                CampusServiceKeys.REPAIR to "http://ehall.yitsd.edu.cn/new/index.html",
                CampusServiceKeys.NOTICE to "https://www.yitsd.edu.cn/",
                CampusServiceKeys.JOB to "http://jy.yitsd.edu.cn",
                CampusServiceKeys.LIBRARY to "https://lib.yitsd.edu.cn",
                // 到梦空间是**App 为主**的平台：学生在 App 内用「学号 + 姓名」自助激活账号，
                // **不经学校统一认证**。这里是它的官方站（多所高校的院级操作指南都把它列为官网）。
                // ⚠️ 但匿名访问**只到静态介绍页**（2026-10 实测：只有 banner /「到梦服务」/ 备案信息），
                // 活动列表与成绩单都在 App 内；web 端的登录入口**尚未证实**。
                // 所以别假设这个入口带登录态、也别假设能免登 —— 统一认证**不**覆盖到梦空间
                //（`ServiceAudit.md` 里那条 `second_class_token` 是从兄弟项目抄的 HFUT 口径）。
                // 证据与出处：docs/superpowers/specs/2026-10-05-secondclass-architecture-design.md §11。
                CampusServiceKeys.SECOND_CLASS to "https://www.5idream.net/",
                CampusServiceKeys.CLASSROOM to "https://jwxt.yitsd.edu.cn/jwglxt",
                // ⚠️ 2026-10 更正：这里原先是 `jiaowu.yitsd.edu.cn/jwxt/xkxt.htm` ——
                // 那是**旧版选课系统**的地址，与现在在用的正方 v9（`jwxt.yitsd.edu.cn/jwglxt`）
                // 不是同一套。选课现在有两个独立入口页（预选 N253525 / 自主选课 N253512），
                // 没有单一"选课页"地址，所以这里指向**自主选课**（学期中正选/补退选用得最多），
                // 预选入口由 App 内的原生选课页给出（`ui/courseselect/CourseSelectScreen`）。
                CampusServiceKeys.COURSE_SELECT to "https://jwxt.yitsd.edu.cn/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default",
                CampusServiceKeys.EVALUATION to "https://jwxt.yitsd.edu.cn/jwglxt",
                CampusServiceKeys.TRANSFER_MAJOR to "http://jiaowu.yitsd.edu.cn",
                CampusServiceKeys.UPGRADE to "http://jiaowu.yitsd.edu.cn",
                CampusServiceKeys.EXAM to "https://jwxt.yitsd.edu.cn/jwglxt"
            )
        ),
        CampusInfo(
            id = "ytu_south",
            name = "烟大南校区",
            shortName = "南校区",
            underConstruction = false,
            schoolId = "YTU",
            schoolName = "烟台大学",
            jwType = "ZHENGFANG",
            // ⚠️ 烟大教务是**旧版正方**（学校索引 `schools_zhengfang.json` 里 isV8=false）：
            // 登录页在 `/default2.aspx` 一带，不能按 V8 的 `/xtgl/login_slogin.html` 硬拼。
            // 地址保留根路径，由 `ZhengfangClient` 依次探测；明文 IP 已在
            // `network_security_config.xml` 里单独放行，别改成 https（该主机未提供）。
            jwBaseUrl = "http://210.47.245.4/",
            services = mapOf(
                SERVICE_LAUNDRY to "U净",
                CampusServiceKeys.SHOWER to "趣智校园",
                CampusServiceKeys.HOT_WATER to "胖乖",
                CampusServiceKeys.ELECTRICITY to "烟大后勤"
            ),
            // 2026-09-22 实测：http://ehall.yitsd.edu.cn 会 302 到 /new/index.html（200）；
            // 7 项校园服务的深层入口需要登录态，暂统一进智慧校园门户，不在公开页伪造 serviceId。
            serviceUrls = mapOf(
                // 后勤 / 能源缴费系统（PMS）：宿舍电费等缴费入口
                CampusServiceKeys.ELECTRICITY to "https://ydny.ytu.edu.cn/pms/wechat/ytdx/payItemAdd",
            )
        ),
                CampusInfo(
            id = "ytlg_new",
            name = "烟台理工新校区",
            shortName = "新校区",
            underConstruction = true,
            services = emptyMap()
        )
    )

    val defaultCampus: CampusInfo = campuses.first()

    fun byId(id: String?): CampusInfo =
        campuses.firstOrNull { it.id == id } ?: defaultCampus
}