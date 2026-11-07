package com.pocket.xiaoli.core.academic

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** JSON schedule parsing — extracted from ZhengfangClient to keep file sizes manageable. */
object ZhengfangScheduleParser {

    /**
     * 最近一次 JSON 解析里「星期是从哪个键读出来的」的样本（最多 3 条，形如 xqj="6"）。
     *
     * 存在的原因：教务系统各家把星期放在不同字段，一旦整表挤到周六/周日，
     * 只看「解析到 N 门课」无法判断是字段选错、还是值本身带偏移。界面把这几条打在进度里。
     */
    @Volatile
    var lastDayNotes: List<String> = emptyList()

    /**
     * 教务系统设置的一周起始星期几（正方返回里的 qsxqj，1=周一…7=周日）。
     *
     * 拾光课程表的适配脚本里有同样的处理：qsxqj 缺失时按周一（1）算；学校若设成
     * 周日为首，xqj 的 1 代表的就是周日，不旋转会把整张课表错位一周。
     */
    @Volatile
    var lastWeekStartDay: Int = 1
        private set

    private var weekStartDay: Int = 1
        private set

    /** parseJsonSchedule 期间收集的「星期字段样本」，最多 3 条。 */
    private val dayNotes = LinkedHashSet<String>()

    fun parseJsonSchedule(json: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        dayNotes.clear()
        lastDayNotes = emptyList()
        try {
            val rootEl = JsonParser.parseString(json)

            if (rootEl.isJsonArray) {
                AcademicLog.d("ZhengfangSchedule", "JSON root is array, size=${rootEl.asJsonArray.size()}")
                for (item in rootEl.asJsonArray) {
                    val obj = item.asJsonObject ?: continue
                    addCourseFromJsonObj(obj, courses)
                }
                AcademicLog.d("ZhengfangSchedule", "JSON root array: parsed ${courses.size} courses")
                lastDayNotes = dayNotes.toList()
                return courses
            }

            if (!rootEl.isJsonObject) {
                AcademicLog.w("ZhengfangSchedule", "JSON root is not object or array")
                lastDayNotes = dayNotes.toList()
                return courses
            }
            val root = rootEl.asJsonObject
            weekStartDay = readWeekStartDay(root)
            lastWeekStartDay = weekStartDay
            if (weekStartDay != 1) {
                AcademicLog.d("ZhengfangSchedule", "week starts on `` (qsxqj), days will be rotated")
            }

            val allArrays = mutableListOf<com.google.gson.JsonArray>()
            findJsonArrays(root, allArrays)

            var kbList: com.google.gson.JsonArray? = null
            var bestScore = 0
            for (arr in allArrays) {
                if (arr.size() == 0) continue
                val first = arr.get(0)
                if (!first.isJsonObject) continue
                val score = scoreCourseArray(arr)
                if (score > bestScore) {
                    bestScore = score
                    kbList = arr
                }
            }
            AcademicLog.d("ZhengfangSchedule", "Found ${allArrays.size} arrays, best score=$bestScore, size=${kbList?.size() ?: 0}")

            if (kbList == null || kbList.size() == 0) {
                AcademicLog.w("ZhengfangSchedule", "No course-like array found. Top-level keys: ${root.keySet()}")
                AcademicLog.d("ZhengfangSchedule", "JSON preview: ${json.take(500)}")
                return courses
            }

            if (kbList.size() > 0 && kbList.get(0).isJsonObject) {
                AcademicLog.d("ZhengfangSchedule", "First course keys: ${kbList.get(0).asJsonObject.keySet()}")
            }

            var rejectedName = 0; var rejectedDay = 0; var rejectedSection = 0
            for (item in kbList) {
                val obj = item.asJsonObject ?: continue
                val reason = addCourseFromJsonObj(obj, courses)
                when (reason) {
                    "name" -> rejectedName++
                    "day" -> rejectedDay++
                    "section" -> rejectedSection++
                }
            }
            AcademicLog.d("ZhengfangSchedule",
                "JSON parsed: ${courses.size} courses, rejected: name=$rejectedName day=$rejectedDay section=$rejectedSection")
            mergeAdjacentBlocks(courses)
        } catch (e: Exception) {
            AcademicLog.e("ZhengfangSchedule", "JSON parse error: ${e.message}", e)
        }
        mergeAdjacentBlocks(courses)
        lastDayNotes = dayNotes.toList()
        return courses
    }

    /** 读一周起始日：正方把 qsxqj 放在 root 或 xsxx 里，缺失按周一（1）。 */
    private fun readWeekStartDay(root: com.google.gson.JsonObject): Int {
        val direct = oneOfInt(root, "qsxqj", "xqjStart", "firstDayOfWeek", "weekStartDay")
        if (direct in 1..7) return direct
        val xsxx = root.get("xsxx")
        if (xsxx != null && xsxx.isJsonObject) {
            val nested = oneOfInt(xsxx.asJsonObject, "qsxqj", "xqjStart", "firstDayOfWeek", "weekStartDay")
            if (nested in 1..7) return nested
        }
        return 1
    }

    /**
     * 合并「同一门课、同一教师、同一教室、同一周次」的相邻节次。
     *
     * 拾光课程表在适配脚本里也做同一件事：正方会把一门 1-2 节的课拆成两条（1 节、2 节各一条），
     * 不合并的话课表上会出现上下两个紧贴的块，看起来像“挤在一起”。
     * 只合并严格相邻（next.startSlot <= cur.endSlot + 1）的，中间有别的课就不动。
     */
    private fun mergeAdjacentBlocks(courses: MutableList<ParsedCourse>) {
        if (courses.size < 2) return
        val merged = mutableListOf<ParsedCourse>()
        val used = BooleanArray(courses.size)
        val order = courses.indices.sortedWith(compareBy({ courses[it].dayOfWeek }, { courses[it].startSlot }))
        for (i in order) {
            if (used[i]) continue
            var cur = courses[i]
            used[i] = true
            for (j in order) {
                if (used[j]) continue
                val other = courses[j]
                if (other.dayOfWeek != cur.dayOfWeek) continue
                if (other.name != cur.name || other.teacher != cur.teacher || other.room != cur.room) continue
                if (other.weeks != cur.weeks || other.weekType != cur.weekType) continue
                if (other.startSlot > cur.endSlot + 1) continue
                if (other.endSlot < cur.startSlot) continue
                cur = cur.copy(
                    startSlot = minOf(cur.startSlot, other.startSlot),
                    endSlot = maxOf(cur.endSlot, other.endSlot),
                )
                used[j] = true
            }
            merged.add(cur)
        }
        if (merged.size != courses.size) {
            AcademicLog.d("ZhengfangSchedule", "merged ${courses.size} → ${merged.size} blocks")
        }
        courses.clear()
        courses.addAll(merged.sortedWith(compareBy({ it.dayOfWeek }, { it.startSlot })))
    }

    private fun findJsonArrays(node: JsonElement, result: MutableList<com.google.gson.JsonArray>) {
        when {
            node.isJsonArray -> {
                result.add(node.asJsonArray)
                for (item in node.asJsonArray) findJsonArrays(item, result)
            }
            node.isJsonObject -> {
                for ((_, value) in node.asJsonObject.entrySet()) findJsonArrays(value, result)
            }
        }
    }

    private fun scoreCourseArray(arr: com.google.gson.JsonArray): Int {
        var score = 0
        val sample = minOf(arr.size(), 3)
        for (i in 0 until sample) {
            val obj = arr.get(i)?.asJsonObject ?: continue
            val keys = obj.keySet()
            if (keys.any { it.let { k -> hasNameField(k, safeString(obj.get(k))) } }) score += 3
            if (keys.any { it.let { k -> hasDayField(k, obj.get(k)) } }) score += 2
            if (keys.any { it.let { k -> hasSectionField(k, obj.get(k)) } }) score += 2
            if (keys.any { it.let { k -> hasWeekField(k, safeString(obj.get(k))) } }) score += 1
            if (keys.any { it.let { k -> hasTeacherField(k, safeString(obj.get(k))) } }) score += 1
        }
        return score
    }

    fun addCourseFromJsonObj(obj: JsonObject, courses: MutableList<ParsedCourse>): String {
        val keys = obj.keySet()
        val keySetStr = keys.joinToString(",")

        var name = ""
        var teacher = ""
        var room = ""
        var day = 0
        var dayKey = ""
        var dayRaw = ""
        var startSecInt = 0
        var endSecInt = 0
        var weekStr = ""
        var secStr = ""
        var kcsj = ""

        for (key in keys) {
            val value = obj.get(key) ?: continue
            val strVal = if (value.isJsonPrimitive) value.asString else ""

            if (name.isEmpty() && hasNameField(key, strVal)) {
                name = strVal.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            }
            if (day == 0 && hasDayField(key, value)) {
                day = safeInt(value)
                dayKey = key
                dayRaw = safeString(value) ?: ""
            }
            if (startSecInt == 0 && hasSectionField(key, value)) {
                if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                    startSecInt = value.asInt
                } else if (strVal.isNotEmpty()) {
                    val num = strVal.toIntOrNull()
                    if (num != null) startSecInt = num
                    else secStr = strVal
                }
            }
            if (endSecInt == 0 && hasEndSectionField(key, value)) {
                if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                    endSecInt = value.asInt
                } else {
                    val num = strVal.toIntOrNull()
                    if (num != null) endSecInt = num
                }
            }
            if (weekStr.isEmpty() && hasWeekField(key, strVal)) {
                weekStr = strVal
            }
            if (teacher.isEmpty() && hasTeacherField(key, strVal)) {
                teacher = strVal
            }
            if (room.isEmpty() && hasRoomField(key, strVal)) {
                room = strVal
            }
            if (kcsj.isEmpty() && hasKcsjField(key)) {
                kcsj = strVal
            }
        }

        // Hardcoded fallback for known field names
        if (name.isEmpty()) name = oneOf(obj, "kcmc", "courseName", "name", "kcm", "coursename", "course_name", "kcmcView")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        if (teacher.isEmpty()) teacher = oneOf(obj, "xm", "jsxm", "teacher", "jsxx", "teacherName", "skjs", "teacher_name", "jsm")
        if (room.isEmpty()) room = oneOf(obj, "cdmc", "jsmc", "room", "classroom", "location", "skdd", "place", "classRoom")
        if (day == 0) day = oneOfInt(obj, "xqj", "xq", "day", "weekDay", "dayOfWeek", "xingqi", "xinqi")
        // 星期名称优先：有的教务把 xqj 当「列号」用（表格里多一列节次就会整体右移一位），
        // 而 xqjmc 是文字（"周三"），按文字算不会带偏移。数字仍是兜底。
        val dayNameText = oneOf(obj, "xqjmc", "xqmc", "dayName", "weekName")
        val dayFromName = dayOfWeekFromText(dayNameText)
        if (dayFromName != null) {
            if (day != dayFromName && day != 0) {
                AcademicLog.d("ZhengfangSchedule", "day mismatch: xqj=$day but name='$dayNameText' → use $dayFromName")
            }
            day = dayFromName
            dayKey = if (dayKey.isEmpty()) "xqjmc" else dayKey + "+xqjmc"
            dayRaw = if (dayRaw.isEmpty()) dayNameText else dayRaw + "/" + dayNameText
        }
        // 学校的「一周起始日」不是周一时，xqj 是按该日起算的列号，要旋转成周一=1。
        if (weekStartDay in 2..7 && day in 1..7) {
            val rotated = ((weekStartDay - 1 + (day - 1)) % 7) + 1
            if (rotated != day) {
                AcademicLog.d("ZhengfangSchedule", "day `` → `` (weekStart=``)")
                dayKey = if (dayKey.isEmpty()) "qsxqj" else dayKey + "+qsxqj"
                dayRaw = "``→``"
            }
            day = rotated
        }
        if (startSecInt == 0) startSecInt = oneOfInt(obj, "ksjc", "djj", "startSection", "startSec", "qsz", "qjz")
        if (endSecInt == 0) endSecInt = oneOfInt(obj, "jsjc", "endSection", "endSec", "jsz")
        if (secStr.isEmpty()) secStr = oneOf(obj, "jcs", "jc", "sections", "jcdm", "sectionNo", "pkjc", "section", "jcsj")
        if (kcsj.isEmpty()) kcsj = oneOf(obj, "kcsj", "timeCode")
        if (weekStr.isEmpty()) weekStr = oneOf(obj, "zcd", "zhous", "weeks", "week", "weekRange", "skzc", "zcz", "kkzc")

        if (kcsj.length >= 5 && day !in 1..7) {
            day = kcsj.substring(0, 1).toIntOrNull() ?: 0
        }
        if (kcsj.length >= 5 && startSecInt == 0) {
            startSecInt = kcsj.substring(1, 3).toIntOrNull() ?: 0
            endSecInt = kcsj.substring(3, 5).toIntOrNull() ?: 0
            if (endSecInt == 0) endSecInt = startSecInt
        }

        if (name.isEmpty() || name.length < 2) {
            if (name.isNotEmpty()) AcademicLog.d("ZhengfangSchedule", "Rejected (name<2): '$name' keys=$keySetStr")
            return "name"
        }
        if (day !in 1..7) {
            // 诊断：把「哪个键给了什么值」写下来——同一门课挤到周六/周日时，这一行能直接指出字段
            AcademicLog.d("ZhengfangSchedule", "day unresolved (day=$day key=$dayKey raw=$dayRaw): name='$name'")
            // 日期解析失败直接拒绝该课程，绝不 coerce 到周一（错误数据比无数据更伤信任）
            AcademicLog.d("ZhengfangSchedule", "Rejected (day=$day not in 1..7): name='$name' keys=$keySetStr")
            return "day"
        }
        if (secStr.isEmpty() && startSecInt == 0 && endSecInt == 0) {
            AcademicLog.d("ZhengfangSchedule", "Rejected (no section): name='$name' keys=$keySetStr")
            return "section"
        }

        val (rawStart, rawEnd) = if (startSecInt > 0) {
            Pair(startSecInt, if (endSecInt >= startSecInt) endSecInt else startSecInt)
        } else {
            parseSections(secStr)
        }
        if (rawStart !in 1..12) {
            AcademicLog.d("ZhengfangSchedule", "Rejected (bad section=$rawStart): name='$name' keys=$keySetStr")
            return "section"
        }
        val startSlot = rawStart.coerceIn(1, 12)
        val endSlot = rawEnd.coerceIn(startSlot, 12)

        if (dayNotes.size < 3 && dayKey.isNotEmpty()) dayNotes.add(dayKey + "=" + dayRaw)
        AcademicLog.d("ZhengfangSchedule", "day=$day (key=$dayKey raw=$dayRaw) name='$name'")
        val weeks = parseWeeks(weekStr)
        val weekType = computeWeekType(weeks)

        courses.add(ParsedCourse(
            name = name, teacher = teacher, room = room,
            dayOfWeek = day, startSlot = startSlot, endSlot = endSlot,
            weekType = weekType, weeks = weeks.joinToString(",") { it.toString() },
            colorIndex = (day * 3 + startSlot) % 15
        ))
        return "ok"
    }

    // ── JSON field matchers ──

    private fun hasNameField(key: String, value: String?): Boolean {
        if (value.isNullOrBlank() || value.length < 2) return false
        val kl = key.lowercase()
        return kl.contains("kcmc") || kl.contains("coursename") || kl == "name" ||
            kl.contains("course") && kl.contains("name") ||
            kl == "kcm" || kl.contains("kcmcview") || kl.contains("title") ||
            kl == "cn" || kl.contains("classname")
    }

    /** 把「周三」「星期三」「周三/周五」「3」这类文本解析成 1..7；认不出返回 null。 */
    private fun dayOfWeekFromText(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return when {
            t.contains("一") -> 1
            t.contains("二") -> 2
            t.contains("三") -> 3
            t.contains("四") -> 4
            t.contains("五") -> 5
            t.contains("六") -> 6
            t.contains("日") || t.contains("天") -> 7
            else -> t.firstOrNull { it in '1'..'7' }?.let { it - '0' }
        }
    }

    private fun hasDayField(key: String, value: JsonElement?): Boolean {
        if (value == null) return false
        val kl = key.lowercase()
        if (kl in listOf("xqj", "xq", "day", "weekday", "dayofweek", "xingqi", "xinqi", "week")) {
            if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                val n = value.asInt; return n in 1..7
            }
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                val s = value.asString
                return s.toIntOrNull()?.let { it in 1..7 } == true ||
                    s.contains("一") || s.contains("二") || s.contains("三") || s.contains("四") ||
                    s.contains("五") || s.contains("六") || s.contains("日")
            }
        }
        return false
    }

    private fun hasSectionField(key: String, value: JsonElement?): Boolean {
        if (value == null) return false
        val kl = key.lowercase()
        if (kl in listOf("jcs", "jc", "sections", "jcdm", "sectionno", "pkjc", "section", "jcsj",
                "ksjc", "djj", "startsection", "startsec", "qsz", "qjz", "start", "begin")) {
            if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                val n = value.asInt; return n in 1..12
            }
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                val s = value.asString
                return s.toIntOrNull()?.let { it in 1..12 } == true || Regex("""\d+-\d+""").matches(s)
            }
        }
        return false
    }

    private fun hasEndSectionField(key: String, value: JsonElement?): Boolean {
        if (value == null) return false
        val kl = key.lowercase()
        if (kl in listOf("jsjc", "endsection", "endsec", "jsz", "end")) {
            if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                val n = value.asInt; return n in 1..12
            }
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                val s = value.asString
                return s.toIntOrNull()?.let { it in 1..12 } == true
            }
        }
        return false
    }

    private fun hasWeekField(key: String, value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val kl = key.lowercase()
        return kl.contains("zcd") || kl.contains("zhous") || kl == "weeks" || kl == "week" ||
            kl.contains("weekrange") || kl.contains("skzc") || kl.contains("zcz") ||
            kl.contains("kkzc") || kl.contains("weekstr") ||
            (kl.contains("zc") && value.contains("周"))
    }

    private fun hasTeacherField(key: String, value: String?): Boolean {
        if (value.isNullOrBlank() || value.length < 2) return false
        val kl = key.lowercase()
        return kl == "xm" || kl.contains("jsxm") || kl == "teacher" || kl.contains("jsxx") ||
            kl.contains("teachername") || kl.contains("skjs") || kl.contains("teacher_name") ||
            kl == "jsm" || kl.contains("instructor")
    }

    private fun hasRoomField(key: String, value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val kl = key.lowercase()
        return kl.contains("cdmc") || kl.contains("jsmc") || kl == "room" || kl.contains("classroom") ||
            kl == "location" || kl.contains("skdd") || kl.contains("place") ||
            kl.contains("classroom") || kl.contains("address")
    }

    private fun hasKcsjField(key: String): Boolean {
        val kl = key.lowercase()
        return kl == "kcsj" || kl == "timecode" || kl == "time"
    }

    private fun oneOf(obj: JsonObject, vararg keys: String): String {
        for (k in keys) {
            val el = obj.get(k) ?: continue
            if (el.isJsonPrimitive) {
                val v = el.asString
                if (v.isNotEmpty()) return v
            }
        }
        return ""
    }

    private fun oneOfInt(obj: JsonObject, vararg keys: String): Int {
        for (k in keys) {
            val el = obj.get(k) ?: continue
            if (el.isJsonPrimitive) {
                val v = el.asJsonPrimitive
                if (v.isNumber()) return v.asInt
                if (v.isString) { val n = v.asString.toIntOrNull(); if (n != null) return n }
            }
        }
        return 0
    }

    private fun parseSections(str: String): Pair<Int, Int> {
        val parts = str.replace("节", "").split("-")
        val start = parts.firstOrNull()?.toIntOrNull() ?: 0
        val end = parts.getOrNull(1)?.toIntOrNull() ?: start
        return Pair(start, if (end < start) start else end)
    }

    /** 安全取整：数字原样返回，数字字符串转 Int，其余返回 0（不抛异常） */
    private fun safeInt(el: JsonElement): Int = when {
        el.isJsonPrimitive && el.asJsonPrimitive.isNumber -> el.asInt
        el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString.toIntOrNull() ?: 0
        else -> 0
    }

    /** 安全取字符串：仅对 JsonPrimitive 生效，对象/数组/Null 返回 null */
    private fun safeString(el: JsonElement?): String? =
        if (el != null && el.isJsonPrimitive) el.asString else null
}
