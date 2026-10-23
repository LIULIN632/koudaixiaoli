package com.pocket.xiaoli.util

/**
 * 课表配色的**唯一真源**：颜色按什么分配。
 *
 * ## 为什么要有这个文件（2026-10 的 P0）
 *
 * 用户反馈："「土木工程施工」在周二出现粉色、在周五又出现橙色，我会以为是两门课。" —— 查下来
 * 根因是**颜色按位置分配**：所有解析器都写 `colorIndex = (day * 3 + startSlot) % 15`
 * （`ZhengfangScheduleParser` / `ZhengfangHtmlParser` / `QiangZhiClient` / 网页导入 …共 9 处）。
 * 于是"同一门课"在每一天、每一节都换色 —— 而课表里颜色是**识别课程的第一信号**，
 * 比课名更早进入眼睛。颜色不一致 = 用户把一门课当成两门。
 *
 * ## 规则
 *
 * 颜色的主键是**课程名**（不是节次、不是行号）：
 *
 * ```
 * stableCourseColorIndex("土木工程施工") == stableCourseColorIndex("土木工程施工")   // 周二/周五同色
 * ```
 *
 * 用自写的 FNV-1a 而不是 `String.hashCode()`：后者的算法虽然也在 JDK 规范里冻结，
 * 但它是"按字符算 31 进制"的弱散列，`高等数学A` 与 `高等数学B` 这类只差一两个字符的课名
 * 容易挤到相邻色（在 15 色的小调色板上尤其明显）。FNV-1a 的雪崩更好，
 * 而且**不依赖任何平台实现**（换个 JDK / ART 版本也得到同一个值，配色不会随系统升级漂移）。
 *
 * 同名不同教师（如两个班的"大学英语"）会拿到同一个颜色 —— 这是**刻意的**：
 * [`com.pocket.xiaoli.ui.theme.CourseColorMode.TEACHER`] 才是"按教师分色"的那档，
 * 想区分老师就切那一档，而不是让"按课程"这档偷偷按教师分。
 */
object CourseColorIndex {

    /** 调色板大小。与 `ui/theme/CourseColors.kt` 的 `CourseColors` 条数一致（15）。 */
    const val PALETTE_SIZE = 15

    /**
     * 把课程名映射到 `0 until `[PALETTE_SIZE]。
     *
     * 大小写与空白不敏感（"高等数学 " 与 "高等数学" 同色）—— 教务导出的课名常带尾空格。
     */
    fun forCourseName(name: String): Int {
        val key = normalize(name)
        if (key.isEmpty()) return 0
        // FNV-1a 32 位
        var hash = 0x811C9DC5.toInt()
        for (ch in key) {
            hash = hash xor ch.code
            hash *= 16777619
        }
        return (hash and 0x7fffffff) % PALETTE_SIZE
    }

    /**
     * 教师名 → 下标。与 [forCourseName] 共用同一套散列，**只是主键不同**：
     * 同一个老师的所有课同色（`CourseColorMode.TEACHER`）。
     */
    fun forTeacherName(name: String): Int = forCourseName(name)

    /** 归一化：去首尾空白 + 去掉所有空格 + 转小写。 */
    private fun normalize(raw: String): String =
        raw.trim().lowercase().replace(" ", "").replace("　", "")
}
