package com.pocket.xiaoli.core

/**
 * 课程**简称**（课表格子里显示的短名）—— 三层来源，按优先级：
 *
 * 1. **用户手改**（课程实体的 `shortName` 字段）：最高优先，谁也不需要猜；
 * 2. **内置表** [BUILTIN]：已知会撑破格子的长课名，人工给一个好读的简称；
 * 3. **规则兜底** [suggest]：去括号 → 在「与」处断开 → 去通用尾巴。
 *
 * 这是两端**唯一实现**：Android 的 `util/CourseShortName.kt` 与桌面的
 * `logic/CourseNames.kt` 都只做转发，不再各存一份规则与内置表。
 *
 * ## 规则兜底**宁可返回 null，也不硬截断**
 *
 * "取前 N 个字"会造出「土木工程施」这种既不是原名、又不像简称的东西。
 * 判断不了就返回 `null`，由渲染层用两行 + 小一档字号放下原名。
 */
object CourseShortName {

    /** 显示目标长度：超过它才考虑给简称。 */
    const val MAX_CHARS = 6

    /**
     * 人工维护的简称表（键 = 教务里的完整课名）。
     *
     * ⚠️ 只放**确实撑破格子、且规则兜底给不出好结果**的课名；规则能推出来的别重复维护。
     */
    val BUILTIN: Map<String, String> = mapOf(
        // 用户 2026-10 实际反馈被截断的三门
        "工程会计与财务管理" to "工程会计",
        "建筑设备与智能建造" to "建筑设备",
        "建筑设备与智能化" to "建筑设备",
        // 名字极长的公共课（思政系列），全名在任何机型上都放不下
        "毛泽东思想和中国特色社会主义理论体系概论" to "毛概",
        "习近平新时代中国特色社会主义思想概论" to "习概",
        "思想政治理论课综合实践" to "思政实践",
    )

    /**
     * 给一门课推荐简称：返回 `null` 表示**不需要 / 推不出**（调用方应显示原名）。
     *
     * @param fullName 教务给的完整课名
     */
    fun suggest(fullName: String): String? {
        val name = fullName.trim()
        if (name.isEmpty()) return null

        BUILTIN[name]?.let { hit ->
            return hit.takeIf { it.isNotBlank() && it != name }
        }

        var candidate = stripParenthetical(name)
        if (candidate.length > MAX_CHARS) {
            candidate = splitAtAnd(candidate) ?: candidate
        }
        if (candidate.length > MAX_CHARS) {
            candidate = stripGenericTail(candidate)
        }
        return candidate.takeIf { it.isNotBlank() && it != name && it.length < name.length }
    }

    /** 「大学英语（一）」→「大学英语」；中文括号与英文括号都认；只去**结尾**的括号段。 */
    private fun stripParenthetical(name: String): String {
        val stripped = name
            .replace(Regex("[（(][^（()）]*[)）]\\s*$"), "")
            .trim()
        return stripped.ifBlank { name }
    }

    /**
     * 在「与」处断开：「工程会计与财务管理」→「工程会计」。
     *
     * ⚠️ 只有**断得放心**时才断：左边 ≥3 字（够识别）、右边 ≥2 字（确实是个并列成分）。
     */
    private fun splitAtAnd(name: String): String? {
        val idx = name.indexOf('与')
        if (idx < 3) return null
        val left = name.substring(0, idx).trim()
        val right = name.substring(idx + 1).trim()
        if (right.length < 2) return null
        return left.takeIf { it.length >= 3 && it.length <= MAX_CHARS }
    }

    /** 去掉结尾的通用词：「数据库原理及应用」→「数据库原理」。 */
    private fun stripGenericTail(name: String): String {
        for (tail in GENERIC_TAILS) {
            if (name.endsWith(tail) && name.length - tail.length >= 3) {
                val cut = name.dropLast(tail.length).trim()
                if (cut.isNotBlank() && cut.length <= MAX_CHARS) return cut
            }
        }
        return name
    }

    /** 结尾通用词。⚠️ 只在**去掉后仍有 ≥3 字**且不超长时才用（见 [stripGenericTail]）。 */
    private val GENERIC_TAILS = listOf("及应用", "与应用", "与技术", "基础教程", "实践教程")
}
