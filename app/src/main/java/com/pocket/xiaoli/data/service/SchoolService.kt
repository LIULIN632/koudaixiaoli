package com.pocket.xiaoli.data.service

/**
 * 服务跳转渠道 —— 移植自「聚在工大」SchoolServiceLauncher 的四渠道分发模型。
 */
enum class ServiceChannel {
    /** 支付宝小程序：alipays:// 一步拉起（体验最好） */
    ALIPAY,

    /** 内置 WebView（可注入 cookie 免登）—— 口袋小鲤默认渠道 */
    WEBVIEW,

    /** 微信小程序：微信禁止第三方直接拉起，只能「复制链接 + 跳微信」两步 */
    WECHAT_MP,

    /** 第三方 App：按包名 Intent 拉起，未安装则走兜底 */
    APP,
}

/**
 * 一项校园服务 —— 移植自「聚在工大」SchoolService，去掉 HFUT 专属常量依赖。
 *
 * @param title    功能名，如「洗澡」
 * @param provider 服务商，如「易校园」
 * @param channel  主渠道
 * @param target   主渠道目标：APP 为包名；其余为链接
 * @param fallback 主渠道不可用时的兜底，通常是支付宝小程序链接
 */
data class SchoolService(
    val title: String,
    val provider: String,
    val channel: ServiceChannel,
    val target: String,
    val fallback: String = "",
)