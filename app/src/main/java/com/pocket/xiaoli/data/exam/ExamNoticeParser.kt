package com.pocket.xiaoli.data.exam

/**
 * 从考试通知文本里抽取考试安排。
 *
 * ## 为什么是规则解析而不是直接交给 AI
 *
 * 考试通知的格式其实**很固定**（教务模板千篇一律：课程 / 日期 / 时间 / 考场 / 座位号）。
 * 规则解析的好处：
 *
 * 1. **离线可用** —— 不需要 API Key，不需要联网
 * 2. **可单测** —— 纯函数，边界值能钉住（见 `ExamNoticeParserTest`）
 * 3. **确定性** —— 同一份通知每次结果一致，不会"今天认出来明天认不出来"
 *
 * AI 更适合兜底**格式不规整**的通知（比如一段口语化的群消息）。
 * 两者是互补关系，不是替代：先跑规则，解析不出条目时再问用户要不要交给 AI。
 *
 * ## 解析策略
 *
 * 按行处理，因为粘贴进来的表格通常**一行就是一条考试**：
 *
 * ```
 * 高等数学A   2026-01-15   14:00-16:00   教三楼305   12
 * ```
 *
 * 对每行：
 * 1. 跳过表头行（含"课程名称/考试时间/考场"这类词且没有日期）
 * 2. 抽出日期、时间、座位号（它们有强特征，能可靠识别）
 * 3. 剩下的文本里，去掉已识别的片段，剩下的就是课程名与考场
 * 4. 用「含楼/室/馆/号」等特征把考场从课程名里分出来
 *
 * 识别不出日期的行**不算一条考试** —— 宁可少认，不可认错
 * （错误的考试提醒比没有提醒更糟）。
 */
object ExamNoticeParser {

    // ── 日期：覆盖通知里常见的几种写法 ──
    private val DATE_PATTERNS = listOf(
        // 2026-01-15 / 2026/1/15 / 2026.1.15
        Regex("""(20\d{2})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{1,2})"""),
        // 2026年1月15日
        Regex("""(20\d{2})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日?"""),
        // 1月15日（缺年份，保留原文，年份由展示层补）
        Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日"""),
    )

    // ── 时间：14:00-16:00 / 14:00—16:00 / 14:00~16:00 / 14:00 ──
    private val TIME_RANGE = Regex("""(\d{1,2}[:：]\d{2})\s*[-—~～至到]\s*(\d{1,2}[:：]\d{2})""")
    private val TIME_SINGLE = Regex("""(\d{1,2}[:：]\d{2})""")

    // ── 座位号：座位/座号/座位号 后跟数字 ──
    private val SEAT = Regex("""(?:座位号?|座号)\s*[:：]?\s*(\d{1,4})""")

    // ── 考场特征词：出现这些词的位置更可能是考场 ──
    private val VENUE_HINTS = listOf(
        "楼", "室", "馆", "考场", "教室", "馆区", "中心", "实验", "机房的",
        "阶梯", "报告厅", "体育场", "操场",
    )
    // 纯房间号形式，如 A101 / B-203 / 三楼305 / 305
    //
    // ⚠️ 前缀必须用 **Unicode 字母类 `\p{L}`** 而不是 `[A-Za-z]`：
    // 否则「教三楼305」会在「楼」处断开，只匹配到 "305"，
    // 于是课程名被切成「教三」+ 考场「楼305」—— 测试抓出过这个 bug。
    private val ROOM_CODE = Regex("""(\p{L}?\d{1,2}[-]?\d{2,4})""")

    // ── 表头特征：这些行是标题，不是数据 ──
    private val HEADER_HINTS = listOf("课程名称", "考试科目", "考试时间", "考场", "座位", "序号", "考试安排", "考试通知")

    // ── 去掉这些行（通知前言/落款）──
    private val NOISE_HINTS = listOf("注意事项", "考生须知", "特此通知", "教务处", "学院", "签字", "盖章")

    /**
     * 解析结果。
     *
     * @param exams   识别出的考试（已去重，见 [parse] 说明）
     * @param scanned 扫描过的非空行数，用于给用户"解析了 N 行、认出 M 条"的反馈
     */
    data class Result(
        val exams: List<ExamSchedule>,
        val scanned: Int,
    )

    fun parse(text: String): Result {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var scanned = 0
        val found = mutableListOf<ExamSchedule>()

        for (line in lines) {
            if (NOISE_HINTS.any { line.contains(it) }) continue
            scanned++

            val date = extractDate(line)
            // 没有日期就不认为是一条考试 —— 避免把标题、说明、落款当成考试
            if (date == null) continue
            if (isHeader(line, date)) continue

            val time = extractTime(line)
            // 座位号：优先取「座位号:」标签形式；表格里常是**末尾裸数字**，
            // 由 splitCourseAndLocation 在剥离时通过 inferredSeat 回传
            val labeledSeat = SEAT.find(line)?.groupValues?.get(1).orEmpty()

            // 课程名与考场：从"去掉已识别片段"的剩余文本里分
            var rest = line
            for (p in DATE_PATTERNS) rest = p.replace(rest, " ")
            rest = TIME_RANGE.replace(rest, " ")
            rest = TIME_SINGLE.replace(rest, " ")
            rest = SEAT.replace(rest, " ")
            rest = rest.replace(Regex("""[\t|,，;；]+"""), " ").trim()

            var inferredSeat: String? = null
            val (course, location) = splitCourseAndLocation(rest) { inferredSeat = it }
            if (course.isBlank()) continue

            found += ExamSchedule(
                courseName = course,
                date = date,
                time = time,
                location = location,
                seat = labeledSeat.ifBlank { inferredSeat.orEmpty() },
            )
        }

        return Result(
            // 去重：同一门课同一天可能因跨页/重复粘贴出现两次
            exams = found.distinctBy { it.courseName + "|" + it.date + "|" + it.time },
            scanned = scanned,
        )
    }

    /** 抽出日期原文（保留用户看到的写法，不重排格式）。 */
    private fun extractDate(line: String): String? {
        for (p in DATE_PATTERNS) {
            val m = p.find(line) ?: continue
            return m.value.replace(Regex("""\s+"""), "").let {
                // 「年月日」写法保留原样；分隔符写法统一成 `-` 便于比较。
                // ⚠️ 必须同时归一化 `/` 与 `.` —— 只处理 `/` 会漏掉 `2026.1.15`
                if (it.contains("年")) it else it.replace(Regex("""[/.]"""), "-")
            }
        }
        return null
    }

    private fun extractTime(line: String): String {
        TIME_RANGE.find(line)?.let { m ->
            val a = m.groupValues[1].replace('：', ':')
            val b = m.groupValues[2].replace('：', ':')
            return "$a-$b"
        }
        // 单个时间也可能是开考时间
        return TIME_SINGLE.find(line)?.groupValues?.get(1)?.replace('：', ':').orEmpty()
    }

    /**
     * 末尾裸数字是否是座位号。
     *
     * 粘贴的表格列序通常是 `课程 | 日期 | 时间 | 考场 | 座位号`，
     * 末尾那个**没有"座位号:"前缀的裸数字**就是座位。
     * 若把当它考场，整列都会错位（实测被测试抓出：考场变成 "12"）。
     *
     * 判据保守：1–3 位纯数字，且值看起来像座位（≤ 300）—— 更大的数字更可能是房间号。
     */
    private fun inferSeat(parts: List<String>): String? {
        val last = parts.lastOrNull() ?: return null
        if (!Regex("""^\d{1,3}$""").matches(last)) return null
        val v = last.toIntOrNull() ?: return null
        return if (v in 1..300) last else null
    }

    /**
     * 把「剩余文本」切成课程名与考场。
     *
     * ## 判据顺序（重要）
     *
     * 粘贴的表格列序通常是：`课程 | 日期 | 时间 | 考场 | 座位号`。
     * 抽掉日期/时间后剩 `课程 考场 座位`，此时**末尾的裸数字是座位号，不是考场**。
     *
     * 所以顺序是：
     * 1. 末尾裸数字 → 剥离为座位号（由 [inferSeat] 在调用处处理）
     * 2. 有**考场特征词**（楼/室/馆…）的片段 → 那是考场，其余是课程
     * 3. 否则，末尾形如房间号（A101 / 三楼305）→ 切出来当考场
     * 4. 都判不出 → 整段当课程名。**宁可考场为空，也不要把课程名切错**
     */
    private fun splitCourseAndLocation(
        rest: String,
        onInferredSeat: (String) -> Unit = {},
    ): Pair<String, String> {
        if (rest.isBlank()) return "" to ""

        var parts = rest.split(Regex("""\s{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }

        // 1) 末尾裸数字先当座位号剥掉 —— 否则它会被第 3 步误认成考场
        inferSeat(parts)?.let {
            onInferredSeat(it)
            parts = parts.dropLast(1)
        }
        if (parts.isEmpty()) return "" to ""

        if (parts.size >= 2) {
            val venue = parts.lastOrNull { p -> VENUE_HINTS.any { p.contains(it) } }
            if (venue != null) {
                val course = parts.filter { it != venue }.joinToString(" ").trim()
                if (course.isNotBlank()) return course to venue
            }
        }

        // 单块（或以空格而非多空格分隔）：尝试从尾部摘出房间号
        val single = parts.joinToString("  ")
        val tailRoom = ROOM_CODE.findAll(single).lastOrNull()
        if (tailRoom != null) {
            val idx = tailRoom.range.first
            val course = single.substring(0, idx).trim()
            val venue = single.substring(idx).trim()
            // 只有课程名还剩东西、且摘出的片段不是纯 1–2 位数字时才切：
            // 纯短数字更可能是座位号或节次，不是考场
            if (course.isNotBlank() && !Regex("""^\d{1,2}$""").matches(venue)) {
                return course to venue
            }
        }

        return parts.joinToString(" ") to ""
    }

    private fun isHeader(line: String, date: String?): Boolean {
        // 有日期就不是表头（表头不会带具体日期）
        if (date != null && line != date) return false
        return HEADER_HINTS.count { line.contains(it) } >= 2
    }
}
