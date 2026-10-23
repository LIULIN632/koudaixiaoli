package com.pocket.xiaoli.util

/**
 * 学号脱敏：**只保留末 4 位**，其余一律星号。
 *
 * ## 为什么必须只有一份实现
 *
 * 同一个学号会出现在多个页面（「我的」页身份卡、绑定信息页、今日校园、选课页…）。
 * **同一个学号被两种方式遮住，用户会怀疑自己看错了账号** ——
 * 这不是审美问题，是"这串数字到底是不是我的学号"的信任问题。
 *
 * 2026-10 实测：改动前全仓有 **5 份**各自的实现、**3 种规则**：
 *
 * | 位置 | 规则 | 例（`2023123456789`） |
 * |---|---|---|
 * | `BoundLogin` / `ProfileIdentity` / `ProfileRedesign` / `LearningPlatformUi` | 末 4 位 | `*********6789` |
 * | `AccountBindingScreen.maskAccount` | 首 3 + 末 2 | `202****89` |
 * | `TodayCampusScreen.maskStudentId` | 首 2 + 末 2（星号封顶 6） | `20******89` |
 *
 * 本文件是**唯一真源**：新页面直接用 [maskStudentId]，**不要再抄一份**。
 * 规则与 `BoundLogin.maskStudentId` 的历史行为逐字一致 —— 它有单测
 *（`BoundLoginTest`），所以那一处只是改为委托到本函数，测试原样通过。
 *
 * ## 边界行为（都有单测盯着）
 *
 * - `null` / 空白 → `""`（不显示"****"，那是"有账号但看不全"的意思，不能拿来表示"没有"）
 * - 长度 ≤ 4 → `****`（宁可全遮，也不要把短号整段露出来）
 */
fun maskStudentId(value: String?): String {
    val s = value.orEmpty().trim()
    return when {
        s.isEmpty() -> ""
        s.length <= 4 -> "****"
        else -> "*".repeat(s.length - 4) + s.takeLast(4)
    }
}
