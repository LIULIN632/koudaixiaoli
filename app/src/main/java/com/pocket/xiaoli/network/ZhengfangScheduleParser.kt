package com.pocket.xiaoli.network

import com.google.gson.JsonObject
import com.pocket.xiaoli.core.academic.ParsedCourse
import com.pocket.xiaoli.core.academic.ZhengfangScheduleParser as CoreParser
import com.pocket.xiaoli.data.entity.CourseEntity

/**
 * 正方课表 JSON 解析的 Android 适配器。
 *
 * [addCourseFromJsonObj] 保留「往传入列表里追加」的旧语义：核心解析到中立模型后，
 * 再映射成 [CourseEntity] 追加，调用方（`ZhengfangClient`）无需改动。
 */
object ZhengfangScheduleParser {

    fun parseJsonSchedule(json: String): List<CourseEntity> =
        CoreParser.parseJsonSchedule(json).map { it.toEntity() }

    fun addCourseFromJsonObj(obj: JsonObject, courses: MutableList<CourseEntity>): String {
        val parsed = mutableListOf<ParsedCourse>()
        val result = CoreParser.addCourseFromJsonObj(obj, parsed)
        courses += parsed.map { it.toEntity() }
        return result
    }
}
