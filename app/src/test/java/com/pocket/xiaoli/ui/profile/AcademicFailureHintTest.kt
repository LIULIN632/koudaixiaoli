package com.pocket.xiaoli.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教务验证失败那句提示的两支文案。
 *
 * ## 钉的是"谁该背这个失败"
 *
 * 上一轮做学校接入矩阵时发现：`loginVerified` 的判据是 `jwType == ZHENGFANG`，
 * 于是"正方协议在烟台理工跑通过"被当成"这 9 所正方学校都能用"。
 * 一所**从没实测过**的学校登录失败时，如果界面只说"核对学号与密码后重试"，
 * 学生会反复改密码、怀疑自己记错了，而真实原因可能是这台服务器的版本/路径与本校不同。
 *
 * 所以这里钉三件事：
 * 1. 实测过的学校 → 文案与改造前**逐字相同**（基线与老用户习惯都不动）；
 * 2. 没实测过的学校 → 必须明说"本 App 还没实测过"，并把责任从用户身上挪开；
 * 3. 两支都得给一条**当下能做的事**（仅保存 / 网页导入），不能只说"失败了"。
 */
class AcademicFailureHintTest {

    @Test
    fun `实测过的学校文案一字不改`() {
        assertEquals(
            "核对学号与密码后重试；改过密码请填新密码。教务系统维护时可选「仅保存」。",
            academicFailureHint(loginProven = true),
        )
    }

    @Test
    fun `没实测过的学校先把话说清楚`() {
        val hint = academicFailureHint(loginProven = false)
        assertTrue(hint, hint.contains("还没实测过"))
        assertTrue("不能让人以为是自己的账号问题", hint.contains("未必是你的账号问题"))
        assertFalse("这一支不许把原因推给学号密码之外的东西", hint.contains("教务系统维护"))
    }

    /** 两支都要给出路：只说"失败"而不说"现在能做什么"，用户会停在原地反复点验证。 */
    @Test
    fun `两支都留有退路`() {
        assertTrue(academicFailureHint(loginProven = true).contains("仅保存"))
        val unproven = academicFailureHint(loginProven = false)
        assertTrue(unproven, unproven.contains("仅保存") && unproven.contains("网页导入"))
    }
}
