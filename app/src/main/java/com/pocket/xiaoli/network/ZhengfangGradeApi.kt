package com.pocket.xiaoli.network

import com.google.gson.JsonObject
import com.pocket.xiaoli.core.academic.ZhengfangGradeApi as CoreGradeApi
import com.pocket.xiaoli.core.academic.ZfGradeItem as CoreZfGradeItem
import com.pocket.xiaoli.core.academic.ZfTerm as CoreZfTerm

typealias ZfTerm = CoreZfTerm
typealias ZfGradeItem = CoreZfGradeItem

/**
 * 正方成绩查询的 Android 适配器。
 *
 * 解析与请求全部在共享核心（桌面端成绩页也用它），这里只把 Android 的
 * `ZhengfangClient` 适配成核心客户端，并保留原来给单测看的解析入口。
 */
class ZhengfangGradeApi(private val client: ZhengfangClient) {

    private val core = CoreGradeApi(client.core)

    suspend fun fetchTerms(baseUrl: String): List<ZfTerm> = core.fetchTerms(baseUrl)

    suspend fun fetchGrades(baseUrl: String, xnm: String, xqm: String): List<ZfGradeItem> =
        core.fetchGrades(baseUrl, xnm, xqm)

    fun parseTermsFromXnXq(html: String): List<ZfTerm> = core.parseTermsFromXnXq(html)

    fun parseTermsFromSelects(html: String): List<ZfTerm> = core.parseTermsFromSelects(html)

    fun parseGradeItems(jsonText: String, termLabel: String): List<ZfGradeItem> =
        core.parseGradeItems(jsonText, termLabel)

    fun gradeField(o: JsonObject, vararg names: String): String = core.gradeField(o, *names)
}
