package com.pocket.xiaoli.network

import com.pocket.xiaoli.core.academic.CourseFields as CoreCourseFields
import com.pocket.xiaoli.core.academic.computeWeekType as coreComputeWeekType
import com.pocket.xiaoli.core.academic.extractCourseFields as coreExtractCourseFields
import com.pocket.xiaoli.core.academic.isMonthOrDateCell as coreIsMonthOrDateCell
import com.pocket.xiaoli.core.academic.isSlotLabel as coreIsSlotLabel
import com.pocket.xiaoli.core.academic.isValidCourseName as coreIsValidCourseName
import com.pocket.xiaoli.core.academic.parseDayHeader as coreParseDayHeader
import com.pocket.xiaoli.core.academic.parseSlotRange as coreParseSlotRange
import com.pocket.xiaoli.core.academic.parseWeeks as coreParseWeeks
import com.pocket.xiaoli.core.academic.parseWeeksToString as coreParseWeeksToString
import com.pocket.xiaoli.core.academic.splitCourseBlocks as coreSplitCourseBlocks

// 这些正则/周次工具已搬进 `:core` 的 jvmAndroidMain（桌面端解析教务页面时复用同一份）。
// 这里保留原包名与签名，`WebImportViewModel`、`QiangZhiClient` 等调用点无需改动。

typealias CourseFields = CoreCourseFields

fun parseDayHeader(text: String): Int? = coreParseDayHeader(text)

fun parseSlotRange(text: String): Pair<Int, Int>? = coreParseSlotRange(text)

fun isSlotLabel(text: String): Boolean = coreIsSlotLabel(text)

fun isMonthOrDateCell(text: String): Boolean = coreIsMonthOrDateCell(text)

fun isValidCourseName(name: String): Boolean = coreIsValidCourseName(name)

fun extractCourseFields(lines: List<String>): CourseFields = coreExtractCourseFields(lines)

fun splitCourseBlocks(lines: List<String>): List<List<String>> = coreSplitCourseBlocks(lines)

fun parseWeeks(raw: String): List<Int> = coreParseWeeks(raw)

fun parseWeeksToString(raw: String): String = coreParseWeeksToString(raw)

fun computeWeekType(weeks: List<Int>, raw: String = ""): String = coreComputeWeekType(weeks, raw)
