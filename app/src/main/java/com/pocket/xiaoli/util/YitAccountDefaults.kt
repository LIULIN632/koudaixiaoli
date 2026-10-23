package com.pocket.xiaoli.util

/**
 * 烟台理工学院默认账号密码规则 —— 依据公开渠道核查（2026-09）。
 *
 * - 统一身份认证（智慧校园系统）：用户名 = 学号，初始密码 = `Ytlg@` + 学号后六位
 * - 迎新系统：初始密码 = `Ytlg@` + 身份证号后六位
 *
 * ## 使用约束（重要）
 * 这是**初始**密码，学生自行修改后即失效。因此本工具**只用于「一键填入」的便利**：
 * - 不做自动登录
 * - 不做猜测性重试（避免撞库观感，也避免账号被锁）
 * - 不在日志/错误信息里输出密码
 *
 * 与「聚在工大」的 `getJxglstuDefaultPassword()`（`Hfut@#$%` + 身份证后 6 位）是同一思路。
 */
object YitAccountDefaults {

    /** 初始密码统一前缀 */
    const val PREFIX = "Ytlg@"

    /** 取末几位 */
    private const val SUFFIX_LEN = 6

    /**
     * 智慧校园 / 教务等系统的初始密码：`Ytlg@` + 学号后 6 位。
     * @return 学号不足 6 位时返回 null（无法推算）
     */
    fun passwordFromStudentId(studentId: String): String? {
        val s = studentId.trim()
        if (s.length < SUFFIX_LEN) return null
        return PREFIX + s.takeLast(SUFFIX_LEN)
    }

    /**
     * 迎新系统的初始密码：`Ytlg@` + 身份证号后 6 位。
     * @return 身份证号不足 6 位时返回 null
     */
    fun passwordFromIdCard(idCard: String): String? {
        val s = idCard.trim().replace(" ", "")
        if (s.length < SUFFIX_LEN) return null
        return PREFIX + s.takeLast(SUFFIX_LEN)
    }

    /** 规则说明文案，用于 UI 提示 */
    const val HINT = "初始密码：Ytlg@ + 学号后 6 位。若已自行修改过，请填你自己的密码。"
}