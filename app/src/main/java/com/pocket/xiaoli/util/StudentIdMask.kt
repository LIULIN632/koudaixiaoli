package com.pocket.xiaoli.util

/**
 * 学号脱敏：**规则在共享层**（`com.pocket.xiaoli.core.text.maskStudentId`），这里只保留
 * 原有的包路径与函数名，避免全仓几十处调用点跟着改。
 *
 * 规则：只保留末 4 位（`2023123456789` → `*********6789`）；`null`/空白 → `""`；长度 ≤ 4 → `****`。
 * 详细理由见共享层那份 KDoc（为什么必须只有一份实现、历史上 5 份实现的教训）。
 */
fun maskStudentId(value: String?): String = com.pocket.xiaoli.core.text.maskStudentId(value)
