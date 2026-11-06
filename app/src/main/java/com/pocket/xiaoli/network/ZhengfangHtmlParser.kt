package com.pocket.xiaoli.network

import com.pocket.xiaoli.core.academic.ZhengfangHtmlParser as CoreParser
import com.pocket.xiaoli.data.entity.CourseEntity

/**
 * 正方课表 HTML 解析的 Android 适配器。
 *
 * 解析逻辑已搬进共享核心（`:core` 的 jvmAndroidMain），桌面端与 Android 跑同一份；
 * 这里只把中立的 [com.pocket.xiaoli.core.academic.ParsedCourse] 映射回 Room 实体。
 */
object ZhengfangHtmlParser {

    fun parseScheduleHtmlV8Grid(html: String): List<CourseEntity> =
        CoreParser.parseScheduleHtmlV8Grid(html).map { it.toEntity() }

    fun parseScheduleHtmlV8List(html: String): List<CourseEntity> =
        CoreParser.parseScheduleHtmlV8List(html).map { it.toEntity() }

    fun parseHtmlScheduleTable(html: String): List<CourseEntity> =
        CoreParser.parseHtmlScheduleTable(html).map { it.toEntity() }
}
