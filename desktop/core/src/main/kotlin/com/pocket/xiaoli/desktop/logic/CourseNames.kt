package com.pocket.xiaoli.desktop.logic

/**
 * 课程名 → 展示名的规则（移植自 Android 端 `CourseShortName`）。
 *
 * 两条规则是同类问题的两个答案：课表格子放不下全名，要么**自动缩短**（这里），
 * 要么**让用户自己起简称**。移动端的结论是"两个都要"：先给建议，用户可改。
 */
object CourseShortName {

    /** 课名格子最多放得下的字数（12sp 字号下）。 */
    const val MAX_CHARS = com.pocket.xiaoli.core.CourseShortName.MAX_CHARS

    /**
     * 给 [name] 一个更短的展示名；给不出就返回 null（调用方回退到原名）。
     *
     * 判据：**结果必须比原名短**，否则"简称"反而更占地方。
     */
    fun suggest(name: String): String? = com.pocket.xiaoli.core.CourseShortName.suggest(name)
    /** 课表格子里实际显示的名字：用户填的简称优先，其次自动建议，最后原名。 */
    fun displayName(name: String, shortName: String, maxChars: Int = MAX_CHARS): String {
        val custom = shortName.trim()
        if (custom.isNotEmpty()) return custom
        val suggested = suggest(name)
        if (suggested != null && suggested.length <= maxChars) return suggested
        return name
    }
}

/** 课名规范化：去首尾空白、小写、去掉中英文空格。 */
fun normalizeCourseName(name: String): String =
    name.trim().lowercase().replace(" ", "").replace("\u3000", "")

/**
 * 由课名算出配色下标（FNV-1a 32 位 → 15 色取模）。
 *
 * ## 为什么按课名而不是按课时
 *
 * 用户要的是「同一门课永远同一个颜色」——按课时（id）取色会让同一门课
 * 在单双周分段、不同教室的两次上课**颜色不同**，看起来像两门课。
 */
fun autoColorIndex(name: String): Int =
    com.pocket.xiaoli.core.course.CourseColorIndex.forCourseName(name)
