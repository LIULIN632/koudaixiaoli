package com.pocket.xiaoli.network

import com.pocket.xiaoli.data.entity.CourseEntity
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** JSON schedule parsing — extracted from ZhengfangClient to keep file sizes manageable. */
object ZhengfangScheduleParser {

    fun parseJsonSchedule(json: String): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        try {
            val rootEl = JsonParser.parseString(json)

            if (rootEl.isJsonArray) {
                android.util.Log.d("ZhengfangSchedule", "JSON root is array, size=${rootEl.asJsonArray.size()}")
                for (item in rootEl.asJsonArray) {
                    val obj = item.asJsonObject ?: continue
                    addCourseFromJsonObj(obj, courses)
                }
                android.util.Log.d("ZhengfangSchedule", "JSON root array: parsed ${courses.size} courses")
                return courses
            }

            if (!rootEl.isJsonObject) {
                android.util.Log.w("ZhengfangSchedule", "JSON root is not object or array")
                return courses
            }
            val root = rootEl.asJsonObject

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
            android.util.Log.d("ZhengfangSchedule", "Found ${allArrays.size} arrays, best score=$bestScore, size=${kbList?.size() ?: 0}")

            if (kbList == null || kbList.size() == 0) {
                android.util.Log.w("ZhengfangSchedule", "No course-like array found. Top-level keys: ${root.keySet()}")
                android.util.Log.d("ZhengfangSchedule", "JSON preview: ${json.take(500)}")
                return courses
            }

            if (kbList.size() > 0 && kbList.get(0).isJsonObject) {
                android.util.Log.d("ZhengfangSchedule", "First course keys: ${kbList.get(0).asJsonObject.keySet()}")
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
            android.util.Log.d("ZhengfangSchedule",
                "JSON parsed: ${courses.size} courses, rejected: name=$rejectedName day=$rejectedDay section=$rejectedSection")
        } catch (e: Exception) {
            android.util.Log.e("ZhengfangSchedule", "JSON parse error: ${e.message}", e)
        }
        return courses
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

    fun addCourseFromJsonObj(obj: JsonObject, courses: MutableList<CourseEntity>): String {
        val keys = obj.keySet()
        val keySetStr = keys.joinToString(",")

        var name = ""
        var teacher = ""
        var room = ""
        var day = 0
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
        if (day !in 1..7) {
            val dayName = oneOf(obj, "xqjmc", "dayName")
            day = when {
                dayName.contains("一") -> 1; dayName.contains("二") -> 2
                dayName.contains("三") -> 3; dayName.contains("四") -> 4
                dayName.contains("五") -> 5; dayName.contains("六") -> 6
                dayName.contains("日") || dayName.contains("天") -> 7
                else -> day
            }
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
            if (name.isNotEmpty()) android.util.Log.d("ZhengfangSchedule", "Rejected (name<2): '$name' keys=$keySetStr")
            return "name"
        }
        if (day !in 1..7) {
            // 日期解析失败直接拒绝该课程，绝不 coerce 到周一（错误数据比无数据更伤信任）
            android.util.Log.d("ZhengfangSchedule", "Rejected (day=$day not in 1..7): name='$name' keys=$keySetStr")
            return "day"
        }
        if (secStr.isEmpty() && startSecInt == 0 && endSecInt == 0) {
            android.util.Log.d("ZhengfangSchedule", "Rejected (no section): name='$name' keys=$keySetStr")
            return "section"
        }

        val (rawStart, rawEnd) = if (startSecInt > 0) {
            Pair(startSecInt, if (endSecInt >= startSecInt) endSecInt else startSecInt)
        } else {
            parseSections(secStr)
        }
        if (rawStart !in 1..12) {
            android.util.Log.d("ZhengfangSchedule", "Rejected (bad section=$rawStart): name='$name' keys=$keySetStr")
            return "section"
        }
        val startSlot = rawStart.coerceIn(1, 12)
        val endSlot = rawEnd.coerceIn(startSlot, 12)

        val weeks = parseWeeks(weekStr)
        val weekType = computeWeekType(weeks)

        courses.add(CourseEntity(
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
