package com.pocket.xiaoli.core.campus


/**
 * 宿舍生活服务适配器。
 *
 * 目前不调用任何外部 API，也不伪造「剩余 18 分钟」这类实时数据；
 * 它只聚合本地已经能确定的信息：绑定宿舍、服务校区、服务商。
 *
 * 以后服务商开放 API 后，可以在这里新增 `loadSummary` 分支去拉实时数据，
 * UI 仍然是同一套 [ServiceSummary]。
 */
object DormLifeServiceAdapter : CampusServiceAdapter {

    override val id: String = "dorm_life"

    override fun supports(resolved: ResolvedCampusService): Boolean =
        DormServiceRouter.isDormService(resolved.spec.id)

    override suspend fun loadSummary(
        resolved: ResolvedCampusService,
        dormInfo: DormInfo?,
    ): ServiceSummary {
        val provider = resolved.service?.provider
            ?: resolved.spec.provider
            ?: resolved.campus.shortName

        // 判"有没有绑定"用 isBound（它本身是 null 安全的），不要直接 `it.building.isNotBlank()`：
        // 宿舍来自 SharedPreferences 的 JSON，老存档里没有的字段在 release 包里是 null，
        // 那一行会 NPE（单测里已经被抓出来过一次）。isBound 同时也和 AppContainer.loadDormInfo
        // 的过滤口径保持一致 —— 这里曾经比它更严，只认"有楼栋"。
        val binding = dormInfo?.takeIf { it.isBound }
        if (binding == null) {
            return ServiceSummary(
                key = resolved.spec.id,
                state = ServiceSummaryState.NEEDS_BINDING,
                title = resolved.spec.name,
                subtitle = "绑定宿舍后自动匹配烟理 / 烟大南校服务",
                badge = resolved.campus.shortName,
                source = "dorm-binding",
            )
        }

        val state = when {
            resolved.service != null -> ServiceSummaryState.READY
            resolved.spec.type == CampusServiceType.API -> ServiceSummaryState.NEEDS_API
            else -> ServiceSummaryState.NEEDS_CONFIG
        }

        return ServiceSummary(
            key = resolved.spec.id,
            state = state,
            title = resolved.spec.name,
            subtitle = binding.label + " · " + provider,
            badge = resolved.campus.shortName,
            metrics = buildList {
                add(ServiceMetric("宿舍", binding.label))
                // 只有电费这一项用得上宿舍编号 —— 它是缴费系统的唯一标识，
                // 摆出来用户才能核对"系统认的到底是不是我这一间"
                // ⚠️ `orEmpty()` 是在这里挡住 v1.0.93 那次崩溃的地方：宿舍是存在 JSON 里的，
                // 老存档没有 dormCode 这一栏，release 包里它会是 null（见 DormInfo.sanitized）。
                // 真正归零在读取边界做，这里是最后一道 —— 这一行崩过一次，就别再裸调了。
                val code = binding.dormCode.orEmpty()
                if (resolved.spec.id == CampusServiceKeys.ELECTRICITY && code.isNotBlank()) {
                    add(ServiceMetric("宿舍编号", code))
                }
                add(ServiceMetric("服务商", provider))
            },
            source = "dorm-binding",
        )
    }
}
