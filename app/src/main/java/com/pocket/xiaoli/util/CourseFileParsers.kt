package com.pocket.xiaoli.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocket.xiaoli.data.entity.CourseEntity

/**
 * 课表文件的**纯文本解析层**。
 *
 * 抽出来的理由只有一个：这五类固定样例（正常 / 空 / 超大 / 字段缺失 / 旧版本）
 * 原来一条都测不了 —— 解析器全藏在 `importCoursesFromUri(context, uri, …)` 背后，
 * 而样例是磁盘上的字节，JVM 单测里没有 `Uri` 也没有 `ContentResolver`。
 * 读流、开 ZIP 的部分留在 [CourseFileImport]，这里只留**拿到文本之后的判断**。
 *
 * 契约：**任何解析器都不抛异常**。读不懂就返回空列表，由调用方把「空」和
 * 「失败」区分开（见 `CourseImportOutcome`）。
 */
internal object CourseFileParsers {

    private val TR = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    private val TD = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
    private val STRIP_TAGS = Regex("<[^>]*>")
    private val SHARED_STRINGS = Regex("<t[^>]*>([^<]*)</t>")
    private val SHEET_ROWS = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
    private val SHEET_CELLS = Regex("<c[^>]*><v>(\\d+)</v></c>")

    /** 列序固定为：课程名称, 教师, 教室, 星期, 开始节次, 结束节次, 周次。 */
    private const val MIN_COLUMNS = 6

    // ───────────────────────────────────────────────
    //  CSV
    // ───────────────────────────────────────────────

    /**
     * 首行**只有**含「课程名称」时才当表头跳过；否则第一行按数据行处理。
     * 这是原有行为，不改 —— 有的学校导出就是没有表头的裸 CSV。
     */
    fun csv(text: String, schemeId: Long): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        val lines = text.lines()
        val startIdx = if (lines.isNotEmpty() && lines[0].contains("课程名称")) 1 else 0
        for (i in startIdx until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            val cells = splitCsvLine(line)
            if (cells.size < MIN_COLUMNS) continue
            val day = cells.getOrNull(3)?.toIntOrNull() ?: continue
            val startSlot = cells.getOrNull(4)?.toIntOrNull() ?: continue
            val endSlot = cells.getOrNull(5)?.toIntOrNull() ?: continue
            courses.add(
                CourseEntity(
                    name = cells[0],
                    teacher = cells.getOrElse(1) { "" },
                    room = cells.getOrElse(2) { "" },
                    dayOfWeek = day,
                    startSlot = startSlot,
                    endSlot = endSlot,
                    weeks = cells.getOrElse(6) { "" },
                    schemeId = schemeId
                )
            )
        }
        return courses
    }

    /** 支持 `"含,逗号的课程名"` 这种引号字段（原来的行为，逐字保留）。 */
    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    cells.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        cells.add(current.toString().trim())
        return cells
    }

    // ───────────────────────────────────────────────
    //  HTML
    // ───────────────────────────────────────────────

    fun html(html: String, schemeId: Long): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        TR.findAll(html).drop(1).forEach { tr ->
            val cells = TD.findAll(tr.value)
                .map { it.groupValues[1].replace(STRIP_TAGS, "").trim() }
                .filter { it.isNotBlank() }
                .toList()
            if (cells.size < MIN_COLUMNS) return@forEach
            val day = cells[3].toIntOrNull() ?: return@forEach
            val startSlot = cells[4].toIntOrNull() ?: return@forEach
            val endSlot = cells[5].toIntOrNull() ?: return@forEach
            courses.add(
                CourseEntity(
                    name = cells[0],
                    teacher = cells.getOrElse(1) { "" },
                    room = cells.getOrElse(2) { "" },
                    dayOfWeek = day,
                    startSlot = startSlot,
                    endSlot = endSlot,
                    weeks = cells.getOrElse(6) { "" },
                    schemeId = schemeId
                )
            )
        }
        return courses
    }

    // ───────────────────────────────────────────────
    //  xlsx（ZIP 内的 sharedStrings.xml + sheet1.xml）
    // ───────────────────────────────────────────────

    fun xlsx(sharedStringsXml: String, sheetXml: String, schemeId: Long): List<CourseEntity> {
        if (sheetXml.isBlank()) return emptyList()
        val strings = SHARED_STRINGS.findAll(sharedStringsXml).map { it.groupValues[1] }.toList()
        val courses = mutableListOf<CourseEntity>()
        SHEET_ROWS.findAll(sheetXml).drop(1).forEach { row ->
            val cells = SHEET_CELLS.findAll(row.value).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            if (cells.size < MIN_COLUMNS) return@forEach
            val nameIdx = cells.getOrNull(0) ?: return@forEach
            val teacherIdx = cells.getOrNull(1) ?: return@forEach
            val roomIdx = cells.getOrNull(2) ?: return@forEach
            val day = cells.getOrNull(3) ?: return@forEach
            val startSlot = cells.getOrNull(4) ?: return@forEach
            val endSlot = cells.getOrNull(5) ?: return@forEach
            // 周次是文本（如 "1-16"），在 xlsx 里存的是 sharedStrings 的**下标**。
            // 原来的 `cells[6].toString()` 把下标直接当成了周次，于是 Excel 导入后
            // 周次会变成 "4" 这种数字 —— 与 CSV / HTML 两条路径的结果对不上。
            // 下标越界时退回字面值（真·数字单元格的情况）。
            val weeksStr = cells.getOrNull(MIN_COLUMNS)?.let { strings.getOrElse(it) { it.toString() } } ?: ""
            courses.add(
                CourseEntity(
                    name = strings.getOrElse(nameIdx) { "" },
                    teacher = strings.getOrElse(teacherIdx) { "" },
                    room = strings.getOrElse(roomIdx) { "" },
                    dayOfWeek = day,
                    startSlot = startSlot,
                    endSlot = endSlot,
                    weeks = weeksStr,
                    schemeId = schemeId
                )
            )
        }
        return courses
    }

    // ───────────────────────────────────────────────
    //  JSON 备份（含旧版本迁移）
    // ───────────────────────────────────────────────

    /**
     * 读 [com.pocket.xiaoli.data.model.CourseBackup] 的 JSON。
     *
     * 为什么不用 `Gson.fromJson(…, CourseBackup::class.java)`：
     * **Gson 不走 Kotlin 默认值** —— 旧备份里没有 `courses` 时它给的是 `null`，
     * 落到 `List<CourseEntity>` 上就是一次 NPE，被外层 catch 成「空文件」。
     * 这里显式取字段，**缺什么都只当没有那门课**，于是
     * `version`/`exportTime`/`school` 缺失的老备份照样能读进来。
     */
    fun backup(json: String, schemeId: Long = 0L): List<CourseEntity> {
        val root = try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            return emptyList()
        }
        if (!root.isJsonObject) return emptyList()
        val array = root.asJsonObject.get("courses") ?: return emptyList()
        if (!array.isJsonArray) return emptyList()
        return array.asJsonArray.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            CourseEntity(
                name = obj.stringOr("name"),
                teacher = obj.stringOr("teacher"),
                room = obj.stringOr("room"),
                dayOfWeek = obj.intOr("dayOfWeek"),
                startSlot = obj.intOr("startSlot"),
                endSlot = obj.intOr("endSlot"),
                weeks = obj.stringOr("weeks"),
                schemeId = schemeId
            )
        }
    }

    /** 备份文件头里的 `version`，缺失按 0（= 最老的格式）算。 */
    fun backupVersion(json: String): Int = try {
        val root = JsonParser.parseString(json)
        if (root.isJsonObject) root.asJsonObject.intOr("version") else 0
    } catch (_: Exception) {
        0
    }

    private fun JsonObject.stringOr(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: ""

    private fun JsonObject.intOr(key: String): Int =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: 0

    // ───────────────────────────────────────────────
    //  去重
    // ───────────────────────────────────────────────

    /**
     * 按 **(课程名, 星期, 开始节次)** 去重，且只在目标课表内比对 —— 原有规则。
     * 抽出来是为了让「导入 → 导出 → 再导入」的幂等性能被单测钉住。
     */
    fun mergeNew(existing: List<CourseEntity>, incoming: List<CourseEntity>): List<CourseEntity> =
        incoming.filter { new ->
            existing.none { old ->
                old.name == new.name &&
                    old.dayOfWeek == new.dayOfWeek &&
                    old.startSlot == new.startSlot
            }
        }
}
