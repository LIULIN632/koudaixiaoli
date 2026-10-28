package com.pocket.xiaoli.core.campus


/**
 * 服务摘要状态。
 *
 * 适配器可以返回 READY，也可以明确告诉 UI「还缺什么」，而不是塞假数据。
 */
enum class ServiceSummaryState {
    READY,
    NEEDS_BINDING,
    NEEDS_AUTH,
    NEEDS_CONFIG,
    NEEDS_API,
    UNAVAILABLE,
}

data class ServiceMetric(
    val label: String,
    val value: String,
)

/**
 * 服务信息层统一输出。
 *
 * 以后不管是 API、本地缓存还是手动信息，UI 都只认这个结构。
 */
data class ServiceSummary(
    val key: String,
    val state: ServiceSummaryState,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val metrics: List<ServiceMetric> = emptyList(),
    val source: String = "local",
)

/**
 * 单个服务的适配器。
 *
 * 现阶段只做本地可确定的信息聚合；等拿到 API 后新增 ApiAdapter 即可，
 * 不需要改 ServiceSummary / UI / 打开方式。
 */
interface CampusServiceAdapter {
    val id: String

    fun supports(resolved: ResolvedCampusService): Boolean

    suspend fun loadSummary(
        resolved: ResolvedCampusService,
        dormInfo: DormInfo?,
    ): ServiceSummary
}

/**
 * 服务适配器注册表。
 *
 * 负责：
 * 1. 调 [CampusServiceResolver] 得到最终 spec / 校区 / launcher service；
 * 2. 找到能处理该服务的 adapter；
 * 3. 返回统一 [ServiceSummary]。
 */
object CampusServiceAdapterRegistry {

    private val adapters: List<CampusServiceAdapter> = listOf(
        DormLifeServiceAdapter,
    )

    fun adapterFor(resolved: ResolvedCampusService): CampusServiceAdapter? =
        adapters.firstOrNull { it.supports(resolved) }

    suspend fun loadSummary(
        key: String,
        title: String,
        campus: CampusInfo,
        dormInfo: DormInfo? = null,
    ): ServiceSummary? {
        val resolved = CampusServiceResolver.resolve(key, title, campus, dormInfo) ?: return null
        val adapter = adapterFor(resolved) ?: return null
        return adapter.loadSummary(resolved, dormInfo)
    }

    fun availableAdapters(): List<String> = adapters.map { it.id }
}