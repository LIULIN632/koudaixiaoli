package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 烟台理工默认密码规则测试。
 * 规则来自公开渠道核查，一旦学校调整规则，这里会先失败，起到提醒作用。
 */
class YitAccountDefaultsTest {

    @Test
    fun studentIdRule() {
        assertEquals("Ytlg@123456", YitAccountDefaults.passwordFromStudentId("2024123456"))
    }

    @Test
    fun studentIdRuleTakesOnlyLastSix() {
        assertEquals("Ytlg@654321", YitAccountDefaults.passwordFromStudentId("2021654321"))
    }

    @Test
    fun studentIdRuleRejectsShortInput() {
        assertNull(YitAccountDefaults.passwordFromStudentId("12345"))
        assertNull(YitAccountDefaults.passwordFromStudentId(""))
    }

    @Test
    fun studentIdRuleTrimsWhitespace() {
        assertEquals("Ytlg@123456", YitAccountDefaults.passwordFromStudentId("  2024123456  "))
    }

    @Test
    fun idCardRule() {
        assertEquals("Ytlg@123456", YitAccountDefaults.passwordFromIdCard("37060220000101123456"))
    }

    @Test
    fun idCardRuleRejectsShortInput() {
        assertNull(YitAccountDefaults.passwordFromIdCard("12345"))
    }

    @Test
    fun prefixIsStable() {
        assertEquals("Ytlg@", YitAccountDefaults.PREFIX)
    }
}