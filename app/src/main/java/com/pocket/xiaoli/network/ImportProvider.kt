package com.pocket.xiaoli.network

import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.entity.CourseEntity

/**
 * 教务导入抽象接口 — 支持 WebView 和原生两种导入方式。
 * 对应优化方案中的 ImportProvider 架构。
 */
interface ImportProvider {

    /** 提供商标识 */
    val jwType: JwSystemType

    /** 提供商标签（用于UI展示） */
    val label: String

    /** 登录教务系统，返回可用于后续请求的 cookie 字符串 */
    suspend fun login(): LoginResult

    /** 抓取课表数据 */
    suspend fun fetchCourses(cookies: String): List<CourseEntity>
}

/** WebView 登录 + OkHttp 抓取课表的混合方案 */
abstract class WebViewImportProvider(
    override val jwType: JwSystemType,
    override val label: String,
    protected val courseTableUrl: String
) : ImportProvider
