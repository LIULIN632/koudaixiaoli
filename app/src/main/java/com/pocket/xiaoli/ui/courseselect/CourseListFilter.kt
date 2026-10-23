package com.pocket.xiaoli.ui.courseselect

import com.pocket.xiaoli.network.CourseSelectItem

/**
 * 课程列表的检索过滤（**纯函数**，可单测）。
 *
 * ## 为什么单独提出来
 * "搜了没结果"和"解析出来就是空的"在界面上长得一模一样，但责任完全不同：
 * 前者是用户自己的关键词问题，后者可能是接口字段变了。
 * 把它做成纯函数就能穷举测试，而不必靠肉眼在真机上试。
 *
 * ## 检索范围
 * 课程名 / 课程代码 / 教师 / 课程类别，**大小写不敏感**（课程代码里有字母）。
 * 刻意不搜"学分/学时"这类数字：用户想找"3 学分的课"时，
 * 应该用课程类别或直接看列表，按数字搜会把"3 学分"和"第 3 教学楼"混在一起。
 */
internal object CourseListFilter {

    fun apply(items: List<CourseSelectItem>, query: String): List<CourseSelectItem> {
        val q = query.trim()
        if (q.isEmpty()) return items
        return items.filter { matches(it, q) }
    }

    private fun matches(item: CourseSelectItem, query: String): Boolean {
        // 拆成空白分隔的多个关键词，全部命中才算（"高数 张三"能缩小到那个教学班）
        val terms = query.split(' ', '　').filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        return terms.all { term ->
            item.kcmc.contains(term, ignoreCase = true) ||
                item.kch.contains(term, ignoreCase = true) ||
                item.jsxm.contains(term, ignoreCase = true) ||
                item.kclbmc.contains(term, ignoreCase = true) ||
                item.kcxzmc.contains(term, ignoreCase = true)
        }
    }

    /**
     * 合计学分。
     *
     * ⚠️ 学分是**字符串**（教务可能给 `"4"` / `"3.5"` / `""`），所以逐条 `toDoubleOrNull`，
     * 解析不出来的按 0 计 —— 这里只是给用户一个"这批课大概多少学分"的参考，
     * 不是用于任何判断的权威值（真正的总学分上限由教务在提交时校验）。
     */
    fun totalCredits(items: List<CourseSelectItem>): Double =
        items.sumOf { it.xf.toDoubleOrNull() ?: 0.0 }

    /** 学分合计的展示文本：整数不显示小数点（"12" 而不是 "12.0"） */
    fun formatCredits(value: Double): String =
        if (value <= 0.0) "" else if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    /**
     * 余量判读：从 `sxrsmc`（教务已排版，形如 `"120/60"` = 上限/已选）
     * 或 `sxrs` + `yxrs` 两个独立字段算出来。
     *
     * 返回 null 表示**信息不足**（该部署没下发人数），此时界面不显示进度条，
     * 而不是画一个 0/0 的假条 —— 那会让用户以为"这门课没人选"。
     */
    fun capacity(item: CourseSelectItem): Capacity? {
        val limit = item.sxrs.toIntOrNull()
        val used = item.yxrs.toIntOrNull()
        if (limit != null && limit > 0 && used != null) {
            return Capacity(limit = limit, used = used)
        }
        // 退路：从 "120/60" 这种已排版文本里拆
        val parts = item.sxrsmc.split('/')
        if (parts.size == 2) {
            val l = parts[0].trim().toIntOrNull()
            val u = parts[1].trim().toIntOrNull()
            if (l != null && l > 0 && u != null) return Capacity(limit = l, used = u)
        }
        return null
    }

    /** 余量：上限人数与已选人数 */
    data class Capacity(val limit: Int, val used: Int) {
        val remaining: Int get() = limit - used
        val isFull: Boolean get() = used >= limit
        /** 0f..1f，用于进度条 */
        val ratio: Float get() = (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    }
}
