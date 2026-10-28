package com.pocket.xiaoli.core.campus


/**
 * 一个服务入口**当前到底能做到什么**。
 *
 * ## 为什么需要它
 *
 * 服务页有 40 多个入口，但「点下去会发生什么」分四种，而**界面上四种长得一模一样**：
 * 用户点了「校园网」才知道它只是个说明页，于是对另外几十个也不再信任。
 * 这个枚举把"能做什么"变成**可显示的事实**，而不是点开后的意外。
 *
 * ## 与 [ServiceSummaryState] 的分工（不要混用）
 *
 * - [CampusServiceCapability] 回答「**这个入口本身接通了吗**」——
 *   由校区配置与注册表决定，**不依赖网络、不依赖登录**，永远可判定；
 * - `ServiceSummaryState` 回答「**接通之后现在是什么状态**」——
 *   要跑 adapter、要联网（余额/待绑定/待登录），只有少数服务支持。
 *
 * 所以两者是**两层角标**：能力是底座（每个入口都有），摘要是覆盖（少数才有）。
 * 覆盖优先，见 `ServiceEntry.withSummary`。
 */
enum class CampusServiceCapability {
    /** 有原生页面：点下去直接在本 App 里用。 */
    NATIVE,

    /** 有真实去处：WebView / 小程序 / 支付宝 / 第三方 App（详见 spec 的 type）。 */
    WEB,

    /** 只有站内说明文字，没有可跳转的系统。 */
    INFO,

    /** 已在注册表登记，但这一版还没有对应实现（原生页或 adapter）。 */
    RESERVED,
}

/**
 * 服务解析器：
 * - 根据当前校区 +（可选）宿舍楼，决定生活服务走哪个校区；
 * - 优先使用 [CampusServiceRegistry] 的显式 spec；
 * - 没有显式 spec 时，把 `CampusInfo.serviceUrls` 包装成默认 WEBVIEW 方案，保持老行为；
 * - 输出 [ResolvedCampusService]，包含产品类型、目标校区和给 launcher 用的 [SchoolService]。
 *
 * ⚠️ [capabilityOf] 与 [resolve] 必须走**同一套** spec 查找与宿舍路由，
 * 否则会出现"界面标着能用、点下去弹预留"这种自相矛盾 —— 那正是本次要修的病。
 * 两处共用 [specFor] / [targetCampusOf]，不要在任一处另写一套判断。
 */
object CampusServiceResolver {

    fun resolve(
        key: String,
        title: String,
        campus: CampusInfo,
        dormInfo: DormInfo? = null,
    ): ResolvedCampusService? {
        val targetCampus = targetCampusOf(key, campus, dormInfo)

        val spec = specFor(targetCampus, key, title) ?: return null

        return ResolvedCampusService(
            spec = spec,
            campus = targetCampus,
            service = spec.toSchoolService(title, targetCampus),
        )
    }

    fun resolveService(
        key: String,
        title: String,
        campus: CampusInfo,
        dormInfo: DormInfo? = null,
    ): SchoolService? = resolve(key, title, campus, dormInfo)?.service

    /**
     * 判定一个入口当前的能力档位。
     *
     * ## 判据（顺序即优先级）
     *
     * 1. 没有 spec、且校区也没有配 URL ⇒ [CampusServiceCapability.INFO]
     *    （"只有站内说明文字"）；
     * 2. `type` 是 [CampusServiceType.NATIVE] / [CampusServiceType.API] ⇒
     *    还要 adapter 或原生页。但**是不是真的不能用，取决于有没有兜底去处**：
     *    显式登记了 `fallback` 的（如选课的网页选课页）仍然能打开 ⇒ [CampusServiceCapability.WEB]；
     *    既没有 adapter 也没有 fallback 的才是 [CampusServiceCapability.RESERVED]；
     * 3. 其余类型（WEBVIEW / MINIPROGRAM / ALIPAY / EXTERNAL_APP）⇒ [CampusServiceCapability.WEB]。
     *
     * ## 为什么第 2 条要分叉
     *
     * `type` 描述的是**首选**实现方式，不是**唯一**出路。把 NATIVE 一律判成"未接入"
     * 会冤枉那些主页走原生、但兜底网页确实可用的服务（实测 `course_select`、`library`、
     * `classroom` 都属于这一类）—— 那是另一种形式的假话，方向相反而已。
     *
     * @param key 服务注册表 key。
     * @return 见 [CampusServiceCapability]；本函数不返回 null —— 判不出来时的
     *   兜底是 [CampusServiceCapability.INFO]（"只有说明文字"），
     *   因为**低估可用性**只会让人不去点，**高估**会让人白点一次并失去信任。
     */
    fun capabilityOf(
        key: String,
        campus: CampusInfo,
        dormInfo: DormInfo? = null,
    ): CampusServiceCapability {
        val targetCampus = targetCampusOf(key, campus, dormInfo)
        val spec = specFor(targetCampus, key, key) ?: return CampusServiceCapability.INFO
        return capabilityOfSpec(spec)
    }

    /**
     * 纯规则：从一个 spec 判定能力档位。
     *
     * 与 [capabilityOf] 拆开是为了让规则**可被直接测到** —— 现网注册表里目前没有
     * "NATIVE 且无 fallback" 的条目，只测 `capabilityOf` 的话 RESERVED 那一支
     * 永远跑不到（测试会绿得毫无意义）。拆出来后测试能构造合成 spec 把规则钉死，
     * 而不是在测试里抄一份 `when` —— 抄一份就等于又造了一处会漂移的真源。
     */
    fun capabilityOfSpec(spec: CampusServiceSpec): CampusServiceCapability = when (spec.type) {
        CampusServiceType.NATIVE,
        CampusServiceType.API ->
            if (spec.fallback.isNotBlank()) {
                CampusServiceCapability.WEB
            } else {
                CampusServiceCapability.RESERVED
            }

        CampusServiceType.WEBVIEW,
        CampusServiceType.MINIPROGRAM,
        CampusServiceType.ALIPAY,
        CampusServiceType.EXTERNAL_APP -> CampusServiceCapability.WEB
    }

    /** 宿舍相关的服务要先决定走哪个校区；与 [resolve] 共用，避免两处判断漂移。 */
    private fun targetCampusOf(
        key: String,
        campus: CampusInfo,
        dormInfo: DormInfo?,
    ): CampusInfo = if (dormInfo != null && DormServiceRouter.isDormService(key)) {
        DormServiceRouter.serviceCampus(dormInfo, campus)
    } else {
        campus
    }

    /** 显式 spec 优先，其次用校区配置的 URL 造默认 WEBVIEW；都没有 → null。 */
    private fun specFor(
        campus: CampusInfo,
        key: String,
        title: String,
    ): CampusServiceSpec? =
        CampusServiceRegistry.specFor(campus.id, key) ?: defaultSpec(campus, key, title)

    private fun defaultSpec(
        campus: CampusInfo,
        key: String,
        title: String,
    ): CampusServiceSpec? {
        val url = campus.serviceUrls[key]?.takeIf { it.isNotBlank() } ?: return null
        return CampusServiceSpec(
            id = key,
            name = title,
            category = "general",
            type = CampusServiceType.WEBVIEW,
            priority = 100,
            provider = campus.services[key] ?: campus.shortName,
            target = url,
            dormZoneAware = DormServiceRouter.isDormService(key),
        )
    }

    private fun CampusServiceSpec.toSchoolService(
        title: String,
        campus: CampusInfo,
    ): SchoolService? {
        val finalTitle = title.ifBlank { name }
        val finalProvider = provider ?: campus.shortName
        return when (type) {
            CampusServiceType.WEBVIEW -> SchoolService(
                title = finalTitle,
                provider = finalProvider,
                channel = ServiceChannel.WEBVIEW,
                target = target.ifBlank { fallback },
            )

            CampusServiceType.MINIPROGRAM -> SchoolService(
                title = finalTitle,
                provider = finalProvider,
                channel = ServiceChannel.WECHAT_MP,
                target = target.ifBlank { fallback },
            )

            CampusServiceType.ALIPAY -> SchoolService(
                title = finalTitle,
                provider = finalProvider,
                channel = ServiceChannel.ALIPAY,
                target = target.ifBlank { fallback },
            )

            CampusServiceType.EXTERNAL_APP -> SchoolService(
                title = finalTitle,
                provider = finalProvider,
                channel = ServiceChannel.APP,
                target = target.ifBlank { fallback },
                fallback = fallback,
            )

            // NATIVE / API 需要 adapter 或原生页面，不能凭一个 URL 直接拉起。
            CampusServiceType.NATIVE,
            CampusServiceType.API -> null
        }
    }
}