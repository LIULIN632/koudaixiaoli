package com.pocket.xiaoli.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pocket.xiaoli.core.academic.ZhengfangUtils as Core

// 正方公共工具已搬进共享核心（`:core` 的 jvmAndroidMain），Android 侧保留同名对象，
// `ZhengfangAcademicApi` / `ZhengfangCourseSelectApi` / 成绩页与测试调用点零改动。

object ZhengfangUtils {

    fun isSessionDead(text: String): Boolean = Core.isSessionDead(text)

    fun termLabel(xqm: String): String = Core.termLabel(xqm)

    fun jsonField(o: JsonObject, vararg names: String): String = Core.jsonField(o, *names)

    fun flattenRow(o: JsonObject): JsonObject = Core.flattenRow(o)

    fun findRowArray(text: String): JsonArray? = Core.findRowArray(text)
}
