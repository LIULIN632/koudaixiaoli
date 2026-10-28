package com.pocket.xiaoli.core.campus


/**
 * 校园服务目录（兼容门面）。
 *
 * 旧 UI 继续调用 [resolve] / [isOverridden] 即可；真正的事实来源已经迁移到：
 * - [CampusServiceRegistry]：服务 spec 注册表
 * - [CampusServiceResolver]：按校区 + 宿舍上下文解析
 *
 * 新代码请优先使用 resolver，不要继续往这个 object 里堆硬编码。
 */
object CampusServiceCatalog {

    /** 今日校园独立 App，不属于校内门户 key，因此单独保留。 */
    val KEY_TODAY_CAMPUS: String = CampusServiceRegistry.KEY_TODAY_CAMPUS

    /** 今日校园官方 App 包名（UI 也用它做「是否已安装」判断）。 */
    val TODAY_CAMPUS_PACKAGE: String = CampusServiceRegistry.TODAY_CAMPUS_PACKAGE

    /** 该服务是否已有明确渠道覆盖（供 UI 判断，如显示「App」角标）。 */
    fun isOverridden(key: String): Boolean =
        CampusServiceRegistry.hasAnyExplicitSpec(key)

    /**
     * 兼容旧调用的解析入口。
     * @return null 表示该校区尚未配置这个服务。
     */
    fun resolve(key: String, title: String, campus: CampusInfo): SchoolService? =
        CampusServiceResolver.resolveService(key, title, campus)

    /** 带宿舍上下文的解析入口：会自动判断洗澡/洗衣/电费该走哪个校区。 */
    fun resolve(
        key: String,
        title: String,
        campus: CampusInfo,
        dormInfo: DormInfo?,
    ): SchoolService? = CampusServiceResolver.resolveService(key, title, campus, dormInfo)

    /** 需要读取 spec / type / priority 时使用。 */
    fun resolveDetailed(
        key: String,
        title: String,
        campus: CampusInfo,
        dormInfo: DormInfo? = null,
    ): ResolvedCampusService? =
        CampusServiceResolver.resolve(key, title, campus, dormInfo)
}