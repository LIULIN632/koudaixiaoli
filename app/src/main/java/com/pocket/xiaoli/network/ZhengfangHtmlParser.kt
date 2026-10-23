package com.pocket.xiaoli.network

import com.pocket.xiaoli.data.entity.CourseEntity

/** HTML table schedule parsers — extracted from ZhengfangClient to keep file sizes manageable. */
object ZhengfangHtmlParser {

    // ── V8 HTML grid parser ──

    fun parseScheduleHtmlV8Grid(html: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        val tdRegex = Regex("""<td[^>]*\bid\s*=\s*["']?(\d+)-(\d+)["']?[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
        val blockSplitter = Regex("""<div\s+class=["']?timetable_con""", RegexOption.IGNORE_CASE)

        for (tdMatch in tdRegex.findAll(html)) {
            val day = tdMatch.groupValues[1].toIntOrNull() ?: continue
            if (day !in 1..7) continue
            val cellContent = tdMatch.groupValues[3]

            val blocks = cellContent.split(blockSplitter)
            for (i in 1 until blocks.size) {
                val blockHtml = "<div class=\"timetable_con" + blocks[i]

                var name = extractNameFromBlock(blockHtml)

                var teacher = extractTextByTitle(blockHtml, "教师")
                if (teacher.isEmpty()) teacher = extractTextByTitle(blockHtml, "老师")
                if (teacher.isNotEmpty()) teacher = cleanTeacherNameRegex(teacher)

                var location = extractTextByTitle(blockHtml, "上课地点")
                if (location.isEmpty()) location = extractTextByTitle(blockHtml, "教室")
                if (location.isEmpty()) location = extractTextByTitle(blockHtml, "校区/上课地点")

                var weeksStr = ""
                var sectionsStr = ""
                val timeText = extractTextByTitle(blockHtml, "节/周")
                if (timeText.isNotEmpty()) {
                    sectionsStr = extractSectionsStr(timeText)
                    weeksStr = extractWeeksStr(timeText)
                }

                val timeMatch = Regex("""[\(（](\d+(?:-\d+)?节)[\)）]\s*([^<]*周[^<]*)""").find(blockHtml)
                if (timeMatch != null) {
                    sectionsStr = timeMatch.groupValues[1]
                    weeksStr = timeMatch.groupValues[2]
                }

                if (teacher.isEmpty() || location.isEmpty() || weeksStr.isEmpty() || sectionsStr.isEmpty()) {
                    val text = normalizeText(blockHtml)
                    if (teacher.isEmpty()) {
                        val tm = Regex("""教师\s*[:：]?\s*([^\s/，,;；]+)""").find(text)
                        if (tm != null) teacher = cleanTeacherNameRegex(tm.groupValues[1].trim())
                    }
                    if (location.isEmpty()) {
                        val lm = Regex("""上课地点\s*[:：]?\s*([^教师周数节次校区]+)""").find(text)
                        if (lm != null) location = lm.groupValues[1].trim()
                    }
                    if (weeksStr.isEmpty()) weeksStr = extractWeeksStr(text)
                    if (sectionsStr.isEmpty()) sectionsStr = extractSectionsStr(text)
                }

                // 变体 B: 拾光 p/font 结构（无 title span，p 顺序: 0=信息串 1=地点 2=教师）
                if (teacher.isEmpty() || location.isEmpty()) {
                    val pv = extractPFontVariant(blockHtml)
                    if (teacher.isEmpty()) teacher = pv.teacher
                    if (location.isEmpty()) location = pv.location
                }

                if (name.isEmpty() || weeksStr.isEmpty() || sectionsStr.isEmpty()) continue

                val weeks = parseWeeks(weeksStr)
                val sections = parseSectionList(sectionsStr)
                if (weeks.isEmpty() || sections.isEmpty()) continue

                courses.add(CourseEntity(
                    name = name, teacher = teacher, room = location,
                    dayOfWeek = day,
                    startSlot = sections.first(), endSlot = sections.last(),
                    weekType = computeWeekType(weeks),
                    weeks = weeks.joinToString(",") { it.toString() },
                    colorIndex = (day * 3 + sections.first()) % 15
                ))
            }
        }

        return courses.distinctBy { "${it.name}_${it.dayOfWeek}_${it.startSlot}" }
    }

    // ── V8 HTML list parser ──

    fun parseScheduleHtmlV8List(html: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        val rowRegex = Regex(
            """<tr[^>]*>\s*<td[^>]*id=["']?jc_(\d+)-(\d+)-(\d+)["']?[^>]*>\s*</td>\s*<td[^>]*>([\s\S]*?)</td>\s*</tr>""",
            RegexOption.IGNORE_CASE
        )

        for (match in rowRegex.findAll(html)) {
            val day = match.groupValues[1].toIntOrNull() ?: continue
            val sectionStart = match.groupValues[2].toIntOrNull() ?: continue
            val sectionEnd = match.groupValues[3].toIntOrNull() ?: sectionStart
            val blockHtml = match.groupValues[4]

            var name = extractNameFromBlock(blockHtml)
            val text = normalizeText(blockHtml)

            var teacher = extractTextByTitle(blockHtml, "教师")
            if (teacher.isEmpty()) teacher = extractTextByTitle(blockHtml, "老师")
            if (teacher.isNotEmpty()) teacher = cleanTeacherNameRegex(teacher)
            if (teacher.isEmpty()) {
                val tm = Regex("""教师\s*[:：]?\s*([^\s/，,;；]+)""").find(text)
                if (tm != null) teacher = cleanTeacherNameRegex(tm.groupValues[1].trim())
            }

            var location = extractTextByTitle(blockHtml, "上课地点")
            if (location.isEmpty()) location = extractTextByTitle(blockHtml, "教室")
            if (location.isEmpty()) location = extractTextByTitle(blockHtml, "校区/上课地点")

            var weeksStr = extractWeeksStr(text)
            val sectionsStr = "$sectionStart-${sectionEnd}节"

            val timeText = extractTextByTitle(blockHtml, "节/周")
            if (timeText.isNotEmpty() && !weeksStr.isNotEmpty()) {
                weeksStr = extractWeeksStr(timeText)
            }

            if (name.isEmpty() || weeksStr.isEmpty()) continue

            val weeks = parseWeeks(weeksStr)
            if (weeks.isEmpty()) continue

            courses.add(CourseEntity(
                name = name, teacher = teacher, room = location,
                dayOfWeek = day,
                startSlot = sectionStart, endSlot = sectionEnd,
                weekType = computeWeekType(weeks),
                weeks = weeks.joinToString(",") { it.toString() },
                colorIndex = (day * 3 + sectionStart) % 15
            ))
        }

        return courses.distinctBy { "${it.name}_${it.dayOfWeek}_${it.startSlot}" }
            .ifEmpty { parseScheduleHtmlV8ListTbody(html) }
    }

    // ── V8 HTML list parser (tbody variant, 拾光 style: #kblist_table with per-day tbodies) ──

    private fun parseScheduleHtmlV8ListTbody(html: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        val tableRegex = Regex(
            """<table[^>]*id=["']?kblist_table["']?[^>]*>([\s\S]*?)</table>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val tableMatch = tableRegex.find(html) ?: return courses
        val tableHtml = tableMatch.groupValues[1]

        val tbodyRegex = Regex("""<tbody[^>]*>([\s\S]*?)</tbody>""", RegexOption.IGNORE_CASE)
        val tbodies = tbodyRegex.findAll(tableHtml).map { it.groupValues[1] }.toList()

        for ((tbodyIndex, tbodyHtml) in tbodies.withIndex()) {
            if (tbodyIndex !in 1..7) continue
            val day = tbodyIndex

            val trRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val trs = trRegex.findAll(tbodyHtml).map { it.groupValues[1] }.toList()
            if (trs.size < 2) continue

            var lastSectionStart = 0
            var lastSectionEnd = 0
            for (tr in trs.drop(1)) {
                val tdRegex = Regex("""<td[^>]*>([\s\S]*?)</td>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                val tds = tdRegex.findAll(tr).map { it.groupValues[1] }.toList()
                if (tds.isEmpty()) continue

                var contentHtml: String
                if (tds.size > 1) {
                    val sectionRange = parseSectionPair(stripTags(tds[0]).trim())
                    if (sectionRange != null) {
                        lastSectionStart = sectionRange.first
                        lastSectionEnd = sectionRange.second
                    }
                    contentHtml = tds[1]
                } else {
                    contentHtml = tds[0]
                }
                if (lastSectionStart < 1) continue

                val name = extractNameFromBlock(contentHtml)
                if (name.isEmpty()) continue

                val pFonts = Regex("""<p[^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
                    .findAll(contentHtml)
                    .flatMap { match ->
                        Regex("""<font[^>]*>([\s\S]*?)</font>""", RegexOption.IGNORE_CASE)
                            .findAll(match.groupValues[1])
                    }
                    .map { stripTags(it.groupValues[1]).trim() }
                    .filter { it.isNotEmpty() }
                    .toList()

                var weeksRaw = ""
                var room = ""
                var teacher = ""
                for (fontText in pFonts) {
                    if (weeksRaw.isEmpty() && parseWeeks(fontText).isNotEmpty()) {
                        weeksRaw = fontText
                        continue
                    }
                    val cleaned = fontText.replace(Regex("""(周数|上课地点|教师)\s*[:：]?"""), "").trim()
                    if (room.isEmpty() && !cleaned.contains("周") &&
                        !cleaned.matches(Regex("""^[\d\s,\-~至]+$""")) &&
                        (Regex("""\d""").containsMatchIn(cleaned) || Regex("""[楼教室区馆栋]""").containsMatchIn(cleaned))) {
                        room = cleaned.split(Regex("\\s+")).lastOrNull() ?: cleaned
                        continue
                    }
                    if (teacher.isEmpty() && !Regex("""[\d周节]""").containsMatchIn(cleaned) &&
                        cleaned.length in 2..12 && Regex("""^[一-鿿A-Za-z·]+$""").matches(cleaned)) {
                        teacher = cleaned
                    }
                }
                if (weeksRaw.isEmpty()) weeksRaw = extractWeeksStr(normalizeText(contentHtml))

                val weeks = parseWeeks(weeksRaw)
                if (weeks.isEmpty()) continue

                courses.add(CourseEntity(
                    name = name, teacher = teacher, room = room,
                    dayOfWeek = day,
                    startSlot = lastSectionStart, endSlot = lastSectionEnd,
                    weekType = computeWeekType(weeks),
                    weeks = weeks.joinToString(",") { it.toString() },
                    colorIndex = (day * 3 + lastSectionStart) % 15
                ))
            }
        }

        return courses.distinctBy { "${it.name}_${it.dayOfWeek}_${it.startSlot}" }
    }

    private fun parseSectionPair(text: String): Pair<Int, Int>? {
        val rangeMatch = Regex("""(\d+)\s*[-至~～—－]\s*(\d+)""").find(text)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull() ?: return null
            val end = rangeMatch.groupValues[2].toIntOrNull() ?: start
            return Pair(start, end)
        }
        val num = text.toIntOrNull()
        return if (num != null && num in 1..12) Pair(num, num) else null
    }

    // ── Classic HTML table parser (handles both horizontal and vertical layouts) ──

    fun parseHtmlScheduleTable(html: String): List<CourseEntity> {
        val v8Grid = parseScheduleHtmlV8Grid(html)
        if (v8Grid.isNotEmpty()) return v8Grid

        val v8List = parseScheduleHtmlV8List(html)
        if (v8List.isNotEmpty()) return v8List

        val tableRegex = Regex("""<table[^>]*id=["'](?:Table1|kbgrid_table_0)["'][^>]*>(.*?)</table>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tableMatch = tableRegex.find(html)
        val tableHtml = tableMatch?.groupValues?.get(1) ?: html

        val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val rows = rowRegex.findAll(tableHtml).toList()
        if (rows.size < 3) return emptyList()

        val isVertical = detectVerticalTable(rows, html)
        if (isVertical) {
            return parseVerticalTable(rows, html)
        }

        return parseHorizontalTable(rows, html)
    }

    private fun detectVerticalTable(rows: List<MatchResult>, fullHtml: String): Boolean {
        if (Regex("""yitsd\.edu\.cn""", RegexOption.IGNORE_CASE).containsMatchIn(fullHtml)) return true

        var slotLabelCount = 0
        val totalRows = minOf(rows.size, 8)
        for (i in 0 until totalRows) {
            val cells = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(rows[i].value).toList()
            if (cells.isEmpty()) continue
            val firstText = cells[0].groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            if (isSlotLabel(firstText)) slotLabelCount++
        }
        return slotLabelCount >= 2
    }

    private fun parseVerticalTable(rows: List<MatchResult>, fullHtml: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        val allRows = rows.map { row ->
            Regex("""<t[dh][^>]*>(.*?)</t[dh]>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(row.value).toList()
        }.filter { it.isNotEmpty() }

        if (allRows.isEmpty()) return courses

        val colors = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

        val dayMap = mutableMapOf<Int, Int>()
        for (ri in 0 until minOf(allRows.size, 4)) {
            allRows[ri].forEachIndexed { col, cell ->
                val text = cell.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                val day = parseDayHeader(text)
                if (day != null && day !in dayMap.values) dayMap[col] = day
            }
            if (dayMap.size >= 5) break
        }
        if (dayMap.size < 3) {
            dayMap.clear()
            val maxCols = allRows.maxOfOrNull { it.size } ?: 0
            for (c in 1 until maxCols) dayMap[c] = c.coerceIn(1, 7)
        }

        android.util.Log.d("ZhengfangHtml", "Vertical: dayMap=$dayMap, rows=${allRows.size}")

        val rowSlotRanges = mutableListOf<Pair<Int, Int>>()
        for (row in allRows) {
            val firstText = row.getOrNull(0)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
            rowSlotRanges.add(parseSlotRange(firstText) ?: Pair(-1, -1))
        }

        for (rowIdx in allRows.indices) {
            val row = allRows[rowIdx]
            val (startSlot, endSlot) = rowSlotRanges.getOrNull(rowIdx) ?: Pair(-1, -1)
            if (startSlot < 1) continue

            for (colIdx in row.indices) {
                if (colIdx == 0) continue
                val day = dayMap[colIdx] ?: continue
                if (day !in 1..7) continue

                val cellHtml = row[colIdx].groupValues[1]
                val text = cellHtml.replace(Regex("<br[^>]*>", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("<[^>]*>"), "").trim()
                if (text.isBlank() || text.length < 2) continue
                if (Regex("""^[\d\s.\-/]+$""").matches(text)) continue

                val rowSpan = Regex("""row[sS]pan\s*=\s*["'](\d+)["']""").find(cellHtml)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val cellEndSlot = if (rowSpan > 1) {
                    val lookAhead = rowIdx + rowSpan - 1
                    if (lookAhead < rowSlotRanges.size && rowSlotRanges[lookAhead].first > 0)
                        rowSlotRanges[lookAhead].second
                    else endSlot + rowSpan - 1
                } else endSlot

                val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isEmpty()) continue

                val blocks = splitCourseBlocks(lines)
                for (block in blocks) {
                    val (name, teacher, room, weeksRaw) = extractCourseFields(block)
                    if (name.isEmpty() || name.length < 2) continue
                    if (!isValidCourseName(name)) continue

                    val weeks = parseWeeks(weeksRaw)
                    if (weeks.isEmpty()) continue

                    courses.add(CourseEntity(
                        name = name, teacher = teacher, room = room,
                        dayOfWeek = day, startSlot = startSlot, endSlot = cellEndSlot,
                        weeks = weeks.joinToString(",") { it.toString() },
                        weekType = computeWeekType(weeks),
                        colorIndex = colors[courses.size % colors.size]
                    ))
                }
            }
        }

        android.util.Log.d("ZhengfangHtml", "Vertical: parsed ${courses.size} courses")
        return courses.distinctBy { "${it.name}_${it.dayOfWeek}_${it.startSlot}" }
    }

    private fun parseHorizontalTable(rows: List<MatchResult>, html: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()

        val headerCells = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(rows[0].value).toList()

        val dayColumnMap = mutableMapOf<Int, Int>()
        for ((colIdx, cell) in headerCells.withIndex()) {
            val text = cell.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            when {
                text.contains("一") || text.contains("周一") -> dayColumnMap[colIdx] = 1
                text.contains("二") || text.contains("周二") -> dayColumnMap[colIdx] = 2
                text.contains("三") || text.contains("周三") -> dayColumnMap[colIdx] = 3
                text.contains("四") || text.contains("周四") -> dayColumnMap[colIdx] = 4
                text.contains("五") || text.contains("周五") -> dayColumnMap[colIdx] = 5
                text.contains("六") || text.contains("周六") -> dayColumnMap[colIdx] = 6
                text.contains("日") || text.contains("周日") -> dayColumnMap[colIdx] = 7
            }
        }

        var currentSlot = 0
        for (row in rows.drop(1)) {
            val cells = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(row.value).toList()
            if (cells.isEmpty()) continue

            val firstCellText = cells.getOrNull(0)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
            val slotMatch = Regex("""第\s*(\d+)\s*节""").find(firstCellText)
            if (slotMatch != null) {
                currentSlot = slotMatch.groupValues[1].toIntOrNull() ?: (currentSlot + 1)
            } else if (firstCellText.isNotEmpty() &&
                !firstCellText.contains("上午") && !firstCellText.contains("下午") && !firstCellText.contains("晚上")) {
                currentSlot++
            }

            for ((colIdx, cell) in cells.withIndex()) {
                val day = dayColumnMap[colIdx] ?: continue
                val cellHtml = cell.groupValues[1]
                val cellText = cellHtml.replace(Regex("<br[^>]*>", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("<[^>]*>"), "").trim()
                if (cellText.isBlank() || cellText.length < 2) continue
                if (Regex("""^[\d\s.]*$""").matches(cellText)) continue

                val lines = cellText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isEmpty()) continue

                val name = lines.getOrElse(0) { "" }.replace("&nbsp;", "").trim()
                val teacher = lines.getOrElse(1) { "" }.replace("&nbsp;", "").trim()
                val room = lines.getOrElse(2) { "" }.replace("&nbsp;", "").trim()
                val weeksRaw = lines.getOrElse(3) { "" }.replace("&nbsp;", "").trim()
                if (name.isEmpty()) continue

                val weeks = parseWeeks(weeksRaw.ifEmpty {
                    val weekMatch = Regex("""(\d+)\s*[-–]\s*(\d+)\s*周""").find(cellText)
                    if (weekMatch != null) {
                        val start = weekMatch.groupValues[1].toIntOrNull()
                        if (start != null) {
                            val end = weekMatch.groupValues[2].toIntOrNull() ?: start
                            (start..end).joinToString(",") { it.toString() }
                        } else ""
                    } else ""
                })
                val weekType = computeWeekType(weeks)

                val rowSpanMatch = Regex("""row[sS]pan\s*=\s*["'](\d+)["']""").find(cellHtml)
                val rowSpan = rowSpanMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val endSlot = currentSlot + rowSpan - 1

                courses.add(CourseEntity(
                    name = name,
                    teacher = if (teacher.length < 10 && !teacher.contains("周") && !teacher.contains("第")) teacher else "",
                    room = if (room.length < 10 && (room.contains("楼") || room.contains("教") || room.contains("室") || room.contains("区") || room.matches(Regex(".*\\d+.*")))) room else "",
                    dayOfWeek = day,
                    startSlot = currentSlot,
                    endSlot = endSlot,
                    weekType = weekType,
                    weeks = weeks.joinToString(",") { it.toString() },
                    colorIndex = (day * 3 + currentSlot) % 15
                ))
            }
        }

        // Method 2: fallback regex extraction
        if (courses.isEmpty()) {
            val allCellContent = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .map { it.groupValues[1].replace(Regex("<[^>]*>"), "").trim() }
                .filter { it.length > 5 && Regex("""[一-鿿]""").containsMatchIn(it) }
                .toList()

            var i = 0
            var inferredDay = 1
            var inferredSlot = 1
            while (i + 2 < allCellContent.size) {
                val potentialName = allCellContent[i]
                val potentialTeacher = allCellContent.getOrElse(i + 1) { "" }
                val potentialRoom = allCellContent.getOrElse(i + 2) { "" }
                if (potentialName.length in 2..30 && Regex("""[一-鿿]""").containsMatchIn(potentialName)) {
                    val weeks = parseWeeks(allCellContent.getOrElse(i + 3) { "" })
                    courses.add(CourseEntity(
                        name = potentialName,
                        teacher = if (potentialTeacher.length < 15) potentialTeacher else "",
                        room = if (potentialRoom.length < 15) potentialRoom else "",
                        dayOfWeek = inferredDay, startSlot = inferredSlot, endSlot = inferredSlot + 1,
                        weekType = computeWeekType(weeks),
                        weeks = weeks.joinToString(",") { it.toString() },
                        colorIndex = (inferredDay * 3 + inferredSlot) % 15
                    ))
                    inferredSlot++
                    if (inferredSlot > 12) { inferredSlot = 1; inferredDay++ }
                    if (inferredDay > 7) break
                    i += 4
                } else { i++ }
            }
        }

        return courses.distinctBy { "${it.name}_${it.dayOfWeek}_${it.startSlot}" }
    }

    // ── HTML helper functions ──

    private fun stripTags(html: String): String {
        var result = html
        var previous: String
        do {
            previous = result
            result = result.replace(Regex("<[^>]*>"), "")
        } while (result != previous)
        return result.replace(Regex("[<>]"), "")
    }

    private fun normalizeText(html: String): String {
        return stripTags(html).replace(Regex("\\s+"), " ").replace("：", ":").trim()
    }

    private fun extractNameFromBlock(blockHtml: String): String {
        val titleMatch = Regex("""<([a-zA-Z]+)[^>]*class=["']?title[^>]*>([\s\S]*?)</\1>""", RegexOption.IGNORE_CASE)
            .find(blockHtml)
        if (titleMatch != null) {
            return stripTags(titleMatch.groupValues[2]).trim().replace(Regex("""[●★○]"""), "")
        }
        val altMatch = Regex("""<u[^>]*class=["']?title[^>]*>([\s\S]*?)</u>""", RegexOption.IGNORE_CASE)
            .find(blockHtml)
        if (altMatch != null) {
            return stripTags(altMatch.groupValues[1]).trim().replace(Regex("""[●★○]"""), "")
        }
        return ""
    }

    /** 变体 B（拾光 p/font 结构）: p0.font1 为 "(1-2节)1-16周" 信息串, p1 地点, p2 教师. */
    private data class PFontVariant(val teacher: String, val location: String)

    private fun extractPFontVariant(blockHtml: String): PFontVariant {
        val pRegex = Regex("""<p[^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
        val pFonts = pRegex.findAll(blockHtml)
            .map { match ->
                Regex("""<font[^>]*>([\s\S]*?)</font>""", RegexOption.IGNORE_CASE)
                    .findAll(match.groupValues[1])
                    .map { stripTags(it.groupValues[1]).trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }
            .filter { it.isNotEmpty() }
            .toList()
        if (pFonts.size < 3) return PFontVariant("", "")

        val infoFont = pFonts[0].getOrNull(1) ?: ""
        val isInfoFormat = Regex("""[\(（]?\d+\s*[-至~～—－]\s*\d+\s*节""").containsMatchIn(infoFont)
        if (!isInfoFormat) return PFontVariant("", "")

        var location = ""
        val locText = pFonts[1].firstOrNull() ?: ""
        val locToken = locText.split(Regex("\\s+")).lastOrNull() ?: ""
        if (locToken.isNotEmpty() &&
            (Regex("""\d""").containsMatchIn(locToken) || Regex("""[楼教室区馆栋]""").containsMatchIn(locToken))) {
            location = locToken
        }

        var teacher = ""
        val teacherText = pFonts[2].firstOrNull() ?: ""
        if (teacherText.isNotEmpty() && !Regex("""[\d周节]""").containsMatchIn(teacherText) &&
            teacherText.length in 2..12) {
            teacher = teacherText
        }
        return PFontVariant(teacher, location)
    }

    private fun extractTextByTitle(blockHtml: String, titleText: String): String {
        val patternInside = Regex(
            """<span[^>]*title\s*=\s*["']?\s*${Regex.escape(titleText)}\s*["']?[^>]*>([\s\S]*?)</span>""",
            RegexOption.IGNORE_CASE
        )
        val matchInside = patternInside.find(blockHtml)
        if (matchInside != null) {
            val content = stripTags(matchInside.groupValues[1]).trim()
            if (content.isNotEmpty()) return content
        }

        val patternAfter = Regex(
            """title\s*=\s*["']?\s*${Regex.escape(titleText)}\s*["']?[^>]*>[\s\S]*?</span>\s*<font[^>]*>([\s\S]*?)</font>""",
            RegexOption.IGNORE_CASE
        )
        val matchAfter = patternAfter.find(blockHtml)
        if (matchAfter != null) {
            return stripTags(matchAfter.groupValues[1]).trim()
        }
        return ""
    }

    private fun cleanTeacherNameRegex(raw: String): String {
        var text = stripTags(raw)
        text = text.replace(Regex("""教师\s*[:：]?\s*"""), "").trim()
        val keywordRegex = Regex("""(教学班组成|教学班|选课备注|考核方式|课程学时组成|总学时|学时|学分|班级|课程性质|课程类别)\s*[:：]?""")
        val match = keywordRegex.find(text)
        if (match != null) {
            text = text.substring(0, match.range.first).trim()
        }
        text = text.replace(Regex("""[，,;；]\s*$"""), "").trim()
        return text
    }

    private fun extractWeeksStr(text: String): String {
        val weeksMatch = Regex("""周数\s*[:：]?\s*([^教师节次校区]+?周[^教师节次校区]*)""").find(text)
        if (weeksMatch != null) return weeksMatch.groupValues[1].trim()
        val rangeMatch = Regex("""(\d+\s*[-至~～—－]\s*\d+\s*周[^\s]*)""").find(text)
        if (rangeMatch != null) return rangeMatch.groupValues[1].trim()
        val singleMatch = Regex("""(\d+\s*周[^\s]*)""").find(text)
        if (singleMatch != null) return singleMatch.groupValues[1].trim()
        return ""
    }

    private fun extractSectionsStr(text: String): String {
        val sectionMatch = Regex("""节次\s*[:：]?\s*(\d+)\s*[-至~～—－]\s*(\d+)""").find(text)
        if (sectionMatch != null) return sectionMatch.groupValues[1] + "-" + sectionMatch.groupValues[2] + "节"
        val rangeMatch = Regex("""第?\s*(\d+)\s*[-至~～—－]\s*(\d+)\s*节""").find(text)
        if (rangeMatch != null) return rangeMatch.groupValues[1] + "-" + rangeMatch.groupValues[2] + "节"
        val singleMatch = Regex("""第?\s*(\d+)\s*节""").find(text)
        if (singleMatch != null) return singleMatch.groupValues[1] + "节"
        return ""
    }

    private fun parseSectionList(sectionsString: String): List<Int> {
        val sections = mutableListOf<Int>()
        var str = sectionsString
            .replace("第", "").replace("节次:", "").replace("节次：", "")
            .replace("节", "").replace("(", "").replace(")", "")
            .replace("（", "").replace("）", "")
            .replace(Regex("[至~～—－]"), "-")
        val parts = str.split("-")
        val start = parts[0].trim().toIntOrNull()
        val end = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: start
        if (start != null && end != null) {
            for (s in start..end) sections.add(s)
        }
        return sections
    }
}
