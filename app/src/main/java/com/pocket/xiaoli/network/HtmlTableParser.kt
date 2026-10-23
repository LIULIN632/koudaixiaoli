package com.pocket.xiaoli.network

import com.pocket.xiaoli.data.entity.CourseEntity
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Shared Jsoup-based HTML table parser for course schedule extraction.
 * Used by both WebImportViewModel (WebView/Jsoup path) and ZhengfangClient (OkHttp path).
 */

/** Split an element's inner HTML on `<br>` tags, extract plain text from each fragment. */
fun parseBrDelimitedElements(element: Element): List<String> {
    return element.html()
        .split(Regex("(?i)<br\\s*/?>"))
        .map { Jsoup.parse(it).text().trim() }
        .filter { it.isNotBlank() }
}

/** Split on `<br>` with `\n` fallback when `<br>` yields too few lines. */
fun parseBrOrNewlineElements(element: Element): List<String> {
    val text = element.text().trim()
    val brLines = parseBrDelimitedElements(element)
    return if (brLines.size >= 2) brLines
    else text.split("\n", "\r\n").map { it.trim() }.filter { it.isNotBlank() }
}

/** Detect vertical layout: first column cells are slot labels in the first 6 rows. */
fun detectVerticalLayout(bodyRows: List<Element>): Boolean {
    var slotCount = 0
    for (i in 0 until minOf(6, bodyRows.size)) {
        val first = bodyRows[i].select("td, th").firstOrNull()?.text()?.trim() ?: continue
        if (parseSlotRange(first) != null) slotCount++
        else if (first.toIntOrNull() in 1..12) slotCount++
        else if (Regex("""节|课""").containsMatchIn(first)) slotCount++
    }
    return slotCount >= 2
}

/** Build day→column mapping from the first few rows of a table.
 *  Falls back to sequential column mapping (col 1=Mon..col 7=Sun) if fewer than 3 days found. */
fun buildDayMap(headerRows: List<Element>): Pair<Map<Int, Int>, Int> {
    val dayMap = mutableMapOf<Int, Int>()
    var headerRowIdx = -1
    for (ri in 0 until minOf(headerRows.size, 4)) {
        val cells = headerRows[ri].select("td, th")
        cells.forEachIndexed { col, cell ->
            val day = parseDayHeader(cell.text().trim())
            if (day != null && day !in dayMap.values) dayMap[col] = day
        }
        if (dayMap.size >= 5) { headerRowIdx = ri; break }
    }
    if (dayMap.size < 3) {
        dayMap.clear()
        for (i in 1..7) dayMap[i] = i
    }
    return dayMap to headerRowIdx
}

/** Filter table rows to only data rows (skip header, empty, month/date rows).
 *  Returns bodyRows and the list of pre-scanned slot ranges for rowSpan lookahead. */
fun filterBodyRows(
    allRows: List<Element>,
    headerRowIdx: Int
): Pair<List<Element>, List<Pair<Int, Int>>> {
    val bodyStart = maxOf(headerRowIdx + 1, 1)
    val rowSlotRanges = mutableListOf<Pair<Int, Int>>()
    var runningSlot = 0

    val bodyRows = allRows.drop(bodyStart).filter { row ->
        val cells = row.select("td, th")
        if (cells.isEmpty()) return@filter false
        val allText = cells.map { it.text().trim() }.filter { it.isNotBlank() }
        if (allText.isEmpty()) return@filter false

        // Check if this is a month/date header row
        val monthDateCount = allText.count { isMonthOrDateCell(it) }
        if (monthDateCount > allText.size / 2 && allText.size >= 3) return@filter false

        // Build slot range for rowSpan lookahead
        val firstText = cells[0].text().trim()
        val parsed = parseSlotRange(firstText)
        if (parsed != null) {
            runningSlot = parsed.first
            rowSlotRanges.add(parsed)
        } else {
            val isSectionHeader = firstText.isNotEmpty() &&
                (firstText.contains("上") || firstText.contains("下") || firstText.contains("晚") ||
                 firstText.contains("早晨") || firstText.contains("中午"))
            if (!isSectionHeader) runningSlot++
            rowSlotRanges.add(if (isSectionHeader) Pair(-1, -1) else Pair(runningSlot, runningSlot))
        }
        true
    }
    return bodyRows to rowSlotRanges
}

/** Parse a horizontal (横排) timetable: rows=slots, columns=days.
 *  Shared by Zhengfang, Qiangzhi, and URP parsers. */
fun parseHorizontalTable(
    bodyRows: List<Element>,
    dayMap: Map<Int, Int>,
    colOffset: Int,
    colors: List<Int>,
    rowSlotRanges: List<Pair<Int, Int>>
): List<CourseEntity> {
    val courses = mutableListOf<CourseEntity>()
    var slotIndex = 0
    var dataRowIdx = 0

    for (row in bodyRows) {
        val cells = row.select("td, th")
        if (cells.isEmpty()) { dataRowIdx++; continue }

        val firstText = cells[0].text().trim()
        val slotFromLabel = parseSlotRange(firstText)
        val isSectionHeader = firstText.isNotEmpty() &&
            (firstText.contains("上") || firstText.contains("下") || firstText.contains("晚") ||
             firstText.contains("早晨") || firstText.contains("中午"))

        if (slotFromLabel != null) {
            slotIndex = slotFromLabel.first
        } else if (!isSectionHeader) {
            slotIndex++
        }

        cells.forEachIndexed { ci, cell ->
            if (ci == 0) return@forEachIndexed
            if (isSectionHeader) return@forEachIndexed
            val day = dayMap[ci - colOffset]
                ?: (ci - colOffset).takeIf { it in 1..7 }
                ?: return@forEachIndexed
            val text = cell.text().trim()
            if (text.isBlank() || text.length < 2) return@forEachIndexed
            if (text.matches(Regex("""^\d{1,2}\s*月$""")) || text.matches(Regex("""^\d{1,2}/\d{1,2}$""")))
                return@forEachIndexed

            val rowSpan = cell.attr("rowspan").toIntOrNull() ?: 1
            val labelEnd = slotFromLabel?.second ?: slotIndex
            val endSlot = if (rowSpan > 1) {
                val lookAheadIdx = dataRowIdx + rowSpan - 1
                if (lookAheadIdx < rowSlotRanges.size) {
                    val lastRange = rowSlotRanges[lookAheadIdx]
                    if (lastRange.first > 0) lastRange.second else labelEnd + rowSpan - 1
                } else labelEnd + rowSpan - 1
            } else {
                maxOf(labelEnd, slotIndex)
            }

            val lines = parseBrOrNewlineElements(cell)
            if (lines.isEmpty()) return@forEachIndexed

            val (name, teacher, room, weeksRaw) = extractCourseFields(lines)
            if (name.isEmpty()) return@forEachIndexed

            val weeks = parseWeeksToString(weeksRaw)
            val weekType = computeWeekType(parseWeeks(weeksRaw), weeksRaw)

            courses.add(CourseEntity(
                name = name, teacher = teacher, room = room,
                dayOfWeek = day, startSlot = slotIndex, endSlot = endSlot,
                weeks = weeks, colorIndex = colors[courses.size % colors.size],
                weekType = weekType
            ))
        }
        dataRowIdx++
    }
    return courses
}

/** Parse a vertical (竖排) timetable: rows=slots, columns=days. */
fun parseVerticalTable(
    bodyRows: List<Element>,
    dayMap: Map<Int, Int>,
    colors: List<Int>
): List<CourseEntity> {
    val courses = mutableListOf<CourseEntity>()

    val maxCols = bodyRows.maxOfOrNull { row -> row.select("td, th").size } ?: return courses
    val colToDay = mutableMapOf<Int, Int>()
    for (col in 1 until maxCols) {
        colToDay[col] = dayMap[col] ?: col.coerceIn(1, 7)
    }

    // Pre-scan slot ranges for rowSpan lookahead
    val rowSlotRanges = mutableListOf<Pair<Int, Int>>()
    for (row in bodyRows) {
        val firstText = row.select("td, th").firstOrNull()?.text()?.trim() ?: ""
        rowSlotRanges.add(parseSlotRange(firstText) ?: Pair(-1, -1))
    }

    for ((dayCol, day) in colToDay) {
        if (day !in 1..7) continue
        for (rowIdx in bodyRows.indices) {
            val row = bodyRows[rowIdx]
            val cells = row.select("td, th")
            if (dayCol >= cells.size) continue

            val slotRange = rowSlotRanges.getOrNull(rowIdx)
            if (slotRange == null || slotRange.first < 0) continue
            val (startSlot, endSlot) = slotRange

            val cell = cells[dayCol]
            val text = cell.text().trim()
            if (text.isBlank() || text.length < 2) continue

            val contentLines = parseBrDelimitedElements(cell)
            if (contentLines.isEmpty()) continue

            // Handle rowSpan
            val rowSpan = cell.attr("rowspan").toIntOrNull() ?: 1
            val cellEndSlot = if (rowSpan > 1) {
                val lookAheadIdx = rowIdx + rowSpan - 1
                if (lookAheadIdx < rowSlotRanges.size) {
                    val lastRange = rowSlotRanges[lookAheadIdx]
                    if (lastRange.first > 0) lastRange.second else endSlot + rowSpan - 1
                } else endSlot + rowSpan - 1
            } else endSlot

            val blocks = splitCourseBlocks(contentLines)
            for (block in blocks) {
                if (block.isEmpty()) continue
                val (name, teacher, room, weeksRaw) = extractCourseFields(block)
                if (name.isEmpty() || name.length < 2) continue

                val weeks = parseWeeksToString(weeksRaw)
                val weekType = computeWeekType(parseWeeks(weeksRaw), weeksRaw)

                courses.add(CourseEntity(
                    name = name, teacher = teacher, room = room,
                    dayOfWeek = day, startSlot = startSlot, endSlot = cellEndSlot,
                    weeks = weeks, colorIndex = colors[courses.size % colors.size],
                    weekType = weekType
                ))
            }
        }
    }
    return courses
}
