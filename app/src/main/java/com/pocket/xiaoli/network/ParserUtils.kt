package com.pocket.xiaoli.network

/**
 * Shared parser utilities for HTML course schedule extraction.
 * Used by both WebImportViewModel (WebView/Jsoup path) and ZhengfangClient (OkHttp path).
 */

/** Extracted course fields from a table cell's text lines. */
data class CourseFields(val name: String, val teacher: String, val room: String, val weeks: String)

/** Parse day-of-week from header text. Returns 1-7 (Mon-Sun) or null. */
fun parseDayHeader(text: String): Int? {
    return when {
        text.matches(Regex("""星期\s*一|周一|^一$|^\s*一\s*$""")) -> 1
        text.matches(Regex("""星期\s*二|周二|^二$|^\s*二\s*$""")) -> 2
        text.matches(Regex("""星期\s*三|周三|^三$|^\s*三\s*$""")) -> 3
        text.matches(Regex("""星期\s*四|周四|^四$|^\s*四\s*$""")) -> 4
        text.matches(Regex("""星期\s*五|周五|^五$|^\s*五\s*$""")) -> 5
        text.matches(Regex("""星期\s*六|周六|^六$|^\s*六\s*$""")) -> 6
        text.matches(Regex("""星期\s*日|周日|星期\s*天|周天|^日$|^\s*日\s*$""")) -> 7
        else -> null
    }
}

/** Parse slot range from label text. Returns (start, end) or null.
 *  Handles: "第X-Y节", "第X节", "X-Y节", bare digits 1-12, "第N大节". */
fun parseSlotRange(text: String): Pair<Int, Int>? {
    Regex("""第\s*(\d+)\s*[-–~至]\s*(\d+)\s*节""").find(text)?.let {
        return Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    Regex("""第\s*(\d+)\s*节""").find(text)?.let {
        val s = it.groupValues[1].toInt()
        return Pair(s, s)
    }
    Regex("""^(\d+)\s*[-–~]\s*(\d+)\s*节?$""").find(text.trim())?.let {
        return Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    val num = text.trim().toIntOrNull()
    if (num != null && num in 1..12) return Pair(num, num)
    val bigSlots = mapOf(
        "一" to (1 to 2), "二" to (3 to 4), "三" to (5 to 6),
        "四" to (7 to 8), "五" to (9 to 10), "六" to (11 to 12)
    )
    Regex("""第([一二三四五六])大节""").find(text)?.let {
        return bigSlots[it.groupValues[1]]
    }
    return null
}

/** Check if text looks like a time-slot label (not necessarily a parseable one). */
fun isSlotLabel(text: String): Boolean {
    if (text.isBlank()) return false
    if (Regex("""第\s*\d+""").containsMatchIn(text)) return true
    val num = text.trim().toIntOrNull()
    if (num != null && num in 1..12) return true
    if (Regex("""第[一二三四五六]大节""").containsMatchIn(text)) return true
    if (Regex("""^\d+\s*[-–~]\s*\d+\s*节?$""").matches(text.trim())) return true
    return false
}

/** Check if text looks like a month/date/header cell (not course data). */
fun isMonthOrDateCell(text: String): Boolean {
    if (text.isBlank()) return true
    if (Regex("""^\d{1,2}\s*月$""").matches(text)) return true
    if (Regex("""^\d{1,2}/\d{1,2}$""").matches(text)) return true
    if (Regex("""^第\s*\d+\s*周$""").matches(text)) return true
    if (Regex("""^\d{4}$""").matches(text)) return true
    if (text in listOf("上午", "下午", "晚上", "早晨", "中午")) return true
    return false
}

/** Validate course name isn't a header/metadata/empty line. */
fun isValidCourseName(name: String): Boolean {
    if (name.length < 2 || name.length > 25) return false
    if (Regex("""^[\d\s.\-/月日周星期]+$""").matches(name)) return false
    if (name in listOf("无", "备注", "节次", "时间", "课程", "教师", "教室")) return false
    return Regex("""[一-龥A-Za-z]""").containsMatchIn(name)
}

/** Extract (name, teacher, room, weeksRaw) from course text lines. */
fun extractCourseFields(lines: List<String>): CourseFields {
    if (lines.isEmpty()) return CourseFields("", "", "", "")
    val name = lines[0]
    var teacher = ""
    var room = ""
    var weeksRaw = ""

    for (i in 1 until lines.size) {
        val line = lines[i].trim()
        if (line.isEmpty()) continue
        if (Regex("""学分|绩点|考核|学时""").containsMatchIn(line)) continue
        if (Regex("""^\d+\.?\d*\s*(学分)?$""").matches(line)) continue
        if (Regex("""周""").containsMatchIn(line)) {
            if (weeksRaw.isEmpty()) weeksRaw = line
            continue
        }
        if (Regex("""\d""").containsMatchIn(line) && room.isEmpty() &&
            (Regex("""[楼教室区馆厅栋号]""").containsMatchIn(line) || line.length <= 10)) {
            room = line.removePrefix("@")
            continue
        }
        if (Regex("""^[一-鿿A-Za-z·]{2,10}$""").matches(line) && teacher.isEmpty()) {
            teacher = line
        }
    }
    return CourseFields(name, teacher, room, weeksRaw)
}

/** Split multi-course blocks within a single cell (e.g. 单双周 different courses). */
fun splitCourseBlocks(lines: List<String>): List<List<String>> {
    if (lines.isEmpty()) return emptyList()
    if (lines.size <= 6) return listOf(lines)
    val blocks = mutableListOf<List<String>>()
    val current = mutableListOf<String>()
    for (line in lines) {
        val looksLikeCourseName = line.length in 2..18 &&
            !line.contains("周") &&
            !Regex("""^\d|^@|^第|^星期|^[\d\s.\-/]+$""").containsMatchIn(line) &&
            Regex("""[一-龥A-Za-z]""").containsMatchIn(line)
        if (looksLikeCourseName && current.isNotEmpty() && current.size >= 2) {
            blocks.add(current.toList())
            current.clear()
        }
        current.add(line)
    }
    if (current.isNotEmpty()) blocks.add(current.toList())
    return if (blocks.size > 1) blocks else listOf(lines)
}

/** Parse week numbers from raw text. Returns sorted list of week numbers.
 *  Handles: "1-16周", "1-8,10-16周", "1-16(单)", "1,3,5,7", etc. */
fun parseWeeks(raw: String): List<Int> {
    if (raw.isBlank()) return emptyList()
    val isOddOnly = raw.contains("单周") || raw.contains("(单)") || raw.contains("（单）")
    val isEvenOnly = raw.contains("双周") || raw.contains("(双)") || raw.contains("（双）")

    val cleaned = raw.replace(Regex("""[第周]"""), "").trim()
    val weeks = mutableSetOf<Int>()

    // Try segments split by comma
    val segments = cleaned.split(",", "，")
    for (seg in segments) {
        val trimmed = seg.trim()
        // "1-16(单)" or "1-16"
        val rangeMatch = Regex("""(\d+)\s*[-–~]\s*(\d+)\s*[\(（]?([单雙双]?)[\)）]?""").find(trimmed)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull() ?: continue
            val end = rangeMatch.groupValues[2].toIntOrNull() ?: start
            val flag = rangeMatch.groupValues[3]
            for (w in start..end) {
                if (flag == "单" && w % 2 != 1) continue
                if (flag == "双" && w % 2 != 0) continue
                weeks.add(w)
            }
        } else {
            // Single number
            val num = trimmed.toIntOrNull()
            if (num != null && num in 1..30) weeks.add(num)
        }
    }

    // Apply global 单/双 filters if not already handled per-segment
    val filtered = weeks.filter {
        if (isOddOnly) it % 2 == 1
        else if (isEvenOnly) it % 2 == 0
        else true
    }
    return filtered.sorted()
}

/** Convenience: parse weeks to comma-separated string (for CourseEntity.weeks). */
fun parseWeeksToString(raw: String): String =
    parseWeeks(raw).joinToString(",") { it.toString() }

/** Detect week type from week numbers and raw text markers. */
fun computeWeekType(weeks: List<Int>, raw: String = ""): String {
    if (raw.contains("单周") || raw.contains("(单)") || raw.contains("（单）")) return "odd"
    if (raw.contains("双周") || raw.contains("(双)") || raw.contains("（双）")) return "even"
    if (weeks.isEmpty()) return "all"
    return when {
        weeks.all { it % 2 == 1 } -> "odd"
        weeks.all { it % 2 == 0 } -> "even"
        else -> "all"
    }
}
