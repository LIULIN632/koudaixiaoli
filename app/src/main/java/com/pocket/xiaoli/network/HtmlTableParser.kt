package com.pocket.xiaoli.network

import com.pocket.xiaoli.core.academic.buildDayMap as coreBuildDayMap
import com.pocket.xiaoli.core.academic.detectVerticalLayout as coreDetectVerticalLayout
import com.pocket.xiaoli.core.academic.filterBodyRows as coreFilterBodyRows
import com.pocket.xiaoli.core.academic.parseBrDelimitedElements as coreParseBr
import com.pocket.xiaoli.core.academic.parseBrOrNewlineElements as coreParseBrOrNewline
import com.pocket.xiaoli.core.academic.parseHorizontalTable as coreParseHorizontalTable
import com.pocket.xiaoli.core.academic.parseVerticalTable as coreParseVerticalTable
import com.pocket.xiaoli.data.entity.CourseEntity
import org.jsoup.nodes.Element

// 共享解析器的 Android 适配层：保持 `ui/import_/WebImportViewModel` 原有的调用签名不变，
// 只把返回的中立模型映射回 Room 实体。几何/容错逻辑全部在 `:core`。

fun parseBrDelimitedElements(element: Element): List<String> = coreParseBr(element)

fun parseBrOrNewlineElements(element: Element): List<String> = coreParseBrOrNewline(element)

fun detectVerticalLayout(bodyRows: List<Element>): Boolean = coreDetectVerticalLayout(bodyRows)

fun buildDayMap(headerRows: List<Element>): Pair<Map<Int, Int>, Int> = coreBuildDayMap(headerRows)

fun filterBodyRows(
    allRows: List<Element>,
    headerRowIdx: Int,
): Pair<List<Element>, List<Pair<Int, Int>>> = coreFilterBodyRows(allRows, headerRowIdx)

fun parseHorizontalTable(
    bodyRows: List<Element>,
    dayMap: Map<Int, Int>,
    colOffset: Int,
    colors: List<Int>,
    rowSlotRanges: List<Pair<Int, Int>>,
): List<CourseEntity> =
    coreParseHorizontalTable(bodyRows, dayMap, colOffset, colors, rowSlotRanges).map { it.toEntity() }

fun parseVerticalTable(
    bodyRows: List<Element>,
    dayMap: Map<Int, Int>,
    colors: List<Int>,
): List<CourseEntity> =
    coreParseVerticalTable(bodyRows, dayMap, colors).map { it.toEntity() }
