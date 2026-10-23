package com.pocket.xiaoli.data.service

import com.pocket.xiaoli.data.CampusInfo

/**
 * 服务的「推荐实现类型」。
 *
 * 和 [ServiceChannel] 的区别：
 * - [CampusServiceType] 描述产品层怎么实现，如原生页、网页、小程序、API；
 * - [ServiceChannel] 描述执行层具体怎么拉起，如 WebView、支付宝、第三方 App。
 *
 * 未来接入 API 时，优先把 type 标为 [API]，resolver 会先返回 spec，
 * 等有 adapter 后再把 summary / 原生页面补上，不需要改服务页 UI。
 */
enum class CampusServiceType {
    NATIVE,
    WEBVIEW,
    MINIPROGRAM,
    ALIPAY,
    EXTERNAL_APP,
    API,
}

/**
 * 一个校园服务的完整描述。
 *
 * @param priority 越小越优先；同类型下 resolver 可按这个排序产出打开方案。
 * @param target 主渠道目标：网页 URL、App 包名、小程序链接、alipays:// 等。
 * @param fallback 主渠道不可用时的兜底，通常是支付宝小程序链接或官方网页。
 * @param requiresAuth 是否涉及登录态；后续 UI 可据此给提示或走 WebView。
 * @param dormZoneAware 是否受宿舍楼影响（洗澡/洗衣/电费等）。
 */
data class CampusServiceSpec(
    val id: String,
    val name: String,
    val category: String = "general",
    val type: CampusServiceType,
    val priority: Int = 100,
    val provider: String? = null,
    val target: String = "",
    val fallback: String = "",
    val requiresAuth: Boolean = false,
    val dormZoneAware: Boolean = false,
)

/**
 * resolver 的解析结果。
 *
 * [service] 可能为 null：例如服务已经登记为 [CampusServiceType.NATIVE] 或
 * [CampusServiceType.API]，但当前版本还没有对应 adapter / 原生页面。
 * 调用方可以据此显示「敬请期待」或降级到 spec 里的 fallback。
 */
data class ResolvedCampusService(
    val spec: CampusServiceSpec,
    val campus: CampusInfo,
    val service: SchoolService?,
)