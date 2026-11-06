package com.pocket.xiaoli.network

import com.pocket.xiaoli.core.academic.ZhengfangClient as CoreClient
import com.pocket.xiaoli.core.academic.ZhengfangSchool as CoreSchool
import com.pocket.xiaoli.core.academic.LoginResult as CoreLoginResult
import com.pocket.xiaoli.core.academic.CaptchaResult as CoreCaptchaResult
import com.pocket.xiaoli.core.academic.CaptchaProbe as CoreCaptchaProbe
import com.pocket.xiaoli.core.academic.captchaProbe as coreCaptchaProbe
import com.pocket.xiaoli.data.entity.CourseEntity
import okhttp3.Response

data class ZhengfangSchool(
    val name: String,
    val url: String,
    val isV8: Boolean = true,
)

data class LoginResult(
    val success: Boolean,
    val error: String? = null,
    val cookies: String? = null,
    val courses: List<CourseEntity>? = null,
    val needsCaptcha: Boolean = false,
    val captchaBase64: String? = null,
    val semesterStart: String? = null,
    val semesterWeeks: Int? = null,
)

data class CaptchaResult(
    val base64: String,
    val refresh: suspend () -> CaptchaResult,
)

// 验证码探测规则已搬进共享核心；保留原包名给 CaptchaProbeTest 使用。
typealias CaptchaProbe = CoreCaptchaProbe

fun captchaProbe(statusCode: Int, looksLikeImage: Boolean): CaptchaProbe =
    coreCaptchaProbe(statusCode, looksLikeImage)

/**
 * 正方教务客户端的 Android 适配器。
 *
 * 登录、验证码、会话、抓课表全部在共享核心（`:core` 的 jvmAndroidMain），
 * 桌面端直接消费同一份；这里只把中立的 [com.pocket.xiaoli.core.academic.ParsedCourse]
 * 和结果类型映射回 Android 的 Room 实体模型。
 *
 * `get/post` 仍暴露给同包的 `ZhengfangAcademicApi` / `GradeApi` / `CourseSelectApi`：
 * 它们依赖同一个实例里的 Cookie 会话，不能各建一个客户端。
 */
class ZhengfangClient {

    internal val core = CoreClient()

    suspend fun login(
        school: ZhengfangSchool,
        username: String,
        password: String,
        onProgress: (String) -> Unit = {},
        onCaptcha: (suspend (CaptchaResult) -> String)? = null,
    ): LoginResult = core.login(
        school = CoreSchool(school.name, school.url, school.isV8),
        username = username,
        password = password,
        onProgress = onProgress,
        onCaptcha = onCaptcha?.let { callback -> { cap -> callback(mapCaptcha(cap)) } },
    ).toAndroid()

    suspend fun loginOnly(
        school: ZhengfangSchool,
        username: String,
        password: String,
        onProgress: (String) -> Unit = {},
        onCaptcha: (suspend (CaptchaResult) -> String)? = null,
    ): LoginResult = core.loginOnly(
        school = CoreSchool(school.name, school.url, school.isV8),
        username = username,
        password = password,
        onProgress = onProgress,
        onCaptcha = onCaptcha?.let { callback -> { cap -> callback(mapCaptcha(cap)) } },
    ).toAndroid()

    fun getCookiesForUrl(baseUrl: String): String = core.getCookiesForUrl(baseUrl)

    /** 供 `ZhengfangClientTest` 覆盖的学期推导（实现已搬进共享核心）。 */
    fun parseZhengfangSemester(body: String): CoreClient.SemesterInfo? = core.parseZhengfangSemester(body)

    fun get(url: String): Response = core.get(url)

    fun post(url: String, body: String): Response = core.post(url, body)

    private fun mapCaptcha(core: CoreCaptchaResult): CaptchaResult =
        CaptchaResult(core.base64) { mapCaptcha(core.refresh()) }

    private fun CoreLoginResult.toAndroid(): LoginResult = LoginResult(
        success = success,
        error = error,
        cookies = cookies,
        courses = courses?.map { it.toEntity() },
        needsCaptcha = needsCaptcha,
        captchaBase64 = captchaBase64,
        semesterStart = semesterStart,
        semesterWeeks = semesterWeeks,
    )
}
