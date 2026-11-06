package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.academic.CaptchaResult
import com.pocket.xiaoli.core.academic.ParsedCourse
import com.pocket.xiaoli.core.academic.ZfGradeItem
import com.pocket.xiaoli.core.academic.ZfTerm
import com.pocket.xiaoli.core.academic.ZhengfangClient
import com.pocket.xiaoli.core.academic.ZhengfangGradeApi
import com.pocket.xiaoli.core.academic.ZhengfangSchool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一次教务登录的结果（桌面模型，不把共享层的类型漏进 UI 之外）。 */
data class AcademicLoginOutcome(
    val success: Boolean,
    val error: String? = null,
    val courses: List<ParsedCourse> = emptyList(),
    val semesterStart: String? = null,
    val semesterWeeks: Int? = null,
    /** 登录成功时保留的会话；成绩查询等复用同一个客户端（Cookie 还在）。 */
    val session: AcademicSession? = null,
)

/**
 * 已登录的教务会话。
 *
 * 只登录一次（`loginOnly`）就能查成绩；导入课表时走 `fetchSchedule = true`。
 * 会话只活在本次运行里，密码不落盘。
 */
class AcademicSession(
    val baseUrl: String,
    private val client: ZhengfangClient = ZhengfangClient(),
) {
    private val gradeApi by lazy { ZhengfangGradeApi(client) }

    suspend fun login(
        username: String,
        password: String,
        fetchSchedule: Boolean,
        onProgress: (String) -> Unit = {},
        onCaptcha: (suspend (CaptchaResult) -> String)? = null,
    ): AcademicLoginOutcome = withContext(Dispatchers.IO) {
        val school = ZhengfangSchool(name = "教务", url = baseUrl.trimEnd('/'), isV8 = true)
        val result = if (fetchSchedule) {
            client.login(school, username, password, onProgress, onCaptcha)
        } else {
            client.loginOnly(school, username, password, onProgress, onCaptcha)
        }
        AcademicLoginOutcome(
            success = result.success,
            error = result.error,
            courses = result.courses.orEmpty(),
            semesterStart = result.semesterStart,
            semesterWeeks = result.semesterWeeks,
            session = if (result.success) this@AcademicSession else null,
        )
    }

    suspend fun fetchTerms(): List<ZfTerm> = withContext(Dispatchers.IO) {
        gradeApi.fetchTerms(baseUrl)
    }

    suspend fun fetchGrades(xnm: String, xqm: String): List<ZfGradeItem> = withContext(Dispatchers.IO) {
        gradeApi.fetchGrades(baseUrl, xnm, xqm)
    }
}

/**
 * 桌面端登录教务。
 *
 * 协议、验证码、RSA、会话、抓取全部在 `:core` 的 `ZhengfangClient`（与 Android 同一份）；
 * 这里只负责切 IO 线程并保留会话。真实学校环境未验证，Mock 链路由
 * `ZhengfangClientMockTest` / `ZhengfangGradeMockTest` 覆盖。
 */
object AcademicLogin {

    suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
        fetchSchedule: Boolean = true,
        onProgress: (String) -> Unit = {},
        onCaptcha: (suspend (CaptchaResult) -> String)? = null,
    ): AcademicLoginOutcome = AcademicSession(baseUrl).login(
        username = username,
        password = password,
        fetchSchedule = fetchSchedule,
        onProgress = onProgress,
        onCaptcha = onCaptcha,
    )
}
