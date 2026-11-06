package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.academic.ZfGradeItem

/**
 * 成绩导出 CSV。
 *
 * 开头加 UTF-8 BOM：Excel（尤其中文 Windows 上的 WPS/Excel）没有 BOM 时会按 GBK 猜，
 * 课程名直接变乱码 —— 这是"导出成功但打不开看"的典型。
 */
fun gradesToCsv(items: List<ZfGradeItem>): String {
    val header = listOf("学期", "课程", "总分", "绩点", "学分", "考试类型", "平时", "期中", "期末")
    val lines = buildList {
        add(header.joinToString(",") { csvCell(it) })
        items.forEach { grade ->
            add(
                listOf(
                    grade.termLabel,
                    grade.courseName,
                    grade.totalScore,
                    grade.gpa,
                    grade.credits,
                    grade.examType,
                    grade.regular,
                    grade.midterm,
                    grade.final,
                ).joinToString(",") { csvCell(it) }
            )
        }
    }
    return BOM + lines.joinToString("\r\n") + "\r\n"
}

/** UTF-8 BOM（源码里这个字面量就是 U+FEFF 本身，不是转义，肉眼看不见是正常的）。 */
private const val BOM = "﻿"

/** 单元格：包引号、内部引号翻倍；逗号/换行/引号都不用特殊处理第二遍。 */
private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

/** 汇总统计（成绩页与 CSV 头部注释共用口径）。 */
data class GradeStats(val count: Int, val average: Double?, val credits: Double, val gpa: Double?)

fun gradeStats(items: List<ZfGradeItem>): GradeStats {
    val scores = items.mapNotNull { it.totalScore.trim().toDoubleOrNull() }
    val credits = items.mapNotNull { it.credits.trim().toDoubleOrNull() }.sum()
    val gpas = items.mapNotNull { it.gpa.trim().toDoubleOrNull() }
    return GradeStats(
        count = items.size,
        average = scores.takeIf { it.isNotEmpty() }?.average(),
        credits = credits,
        gpa = gpas.takeIf { it.isNotEmpty() }?.average(),
    )
}
