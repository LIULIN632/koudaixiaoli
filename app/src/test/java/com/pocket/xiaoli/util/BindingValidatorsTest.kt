package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 绑定表单的本地校验规则。
 *
 * 为什么要为"提示文案"写测试：这些函数的返回值**直接渲染给用户看**，
 * 所以它们同时承担两个责任 —— 判对错（返回 null 还是非 null）、说清原因（文案）。
 * 只测"非 null"会漏掉后者，而"按钮点了没反应、也不说为什么"正是用户抱怨的那件事。
 */
class BindingValidatorsTest {

    @Test
    fun `学号必须是 6 到 20 位数字`() {
        assertNull(BindingValidators.studentId("2023123456789"))
        assertNull(BindingValidators.studentId(" 2023123456789 "))
        assertNull(BindingValidators.studentId("123456"))
        assertNull(BindingValidators.studentId("1".repeat(20)))
        assertEquals("请填写学号", BindingValidators.studentId(""))
        assertEquals("请填写学号", BindingValidators.studentId("   "))
        // 下面这些是**真实会被手滑输进来**的形态
        assertEquals("学号应是一串 6–20 位数字", BindingValidators.studentId("12345"))        // 太短
        assertEquals("学号应是一串 6–20 位数字", BindingValidators.studentId("1".repeat(21)))  // 太长
        assertEquals("学号应是一串 6–20 位数字", BindingValidators.studentId("20231234a"))
        // 全角数字：中文输入法下真的会发生，而它拿去登录只会失败
        assertEquals("学号应是一串 6–20 位数字", BindingValidators.studentId("２０２３１２３４５６７８９"))
    }

    @Test
    fun `密码至少六位`() {
        assertNull(BindingValidators.password("Ytlg@123456"))
        assertNull(BindingValidators.password("123456"))
        assertEquals("请填写密码", BindingValidators.password(""))
        assertEquals(
            "密码至少 6 位（初始密码是 Ytlg@ + 学号后 6 位）",
            BindingValidators.password("12345"),
        )
    }

    @Test
    fun `学校名称不能为空`() {
        assertNull(BindingValidators.schoolName("烟台理工学院"))
        assertEquals("请填写学校名称", BindingValidators.schoolName(""))
        assertEquals("请填写学校名称", BindingValidators.schoolName("  "))
    }

    @Test
    fun `手机号是 11 位且以 1 开头`() {
        assertNull(BindingValidators.phone("13800138000"))
        assertNull(BindingValidators.phone(" 13800138000 "))
        assertEquals("请填写手机号", BindingValidators.phone(""))
        assertEquals("手机号应是 11 位数字", BindingValidators.phone("1380013800"))   // 10 位
        assertEquals("手机号应是 11 位数字", BindingValidators.phone("23800138000"))  // 不以 1 开头
        assertEquals("手机号应是 11 位数字", BindingValidators.phone("1380013800a"))
    }

    @Test
    fun `宿舍编号可以留空，且从缴费页抄来的写法一律放行`() {
        // 留空 = 退回"楼栋 + 房间号"匹配，是合法状态
        assertNull(BindingValidators.dormCodeHint(""))
        assertNull(BindingValidators.dormCodeHint("   "))
        assertNull(BindingValidators.dormCodeHint("BS10S0602"))
        // ⚠️ 下面这几条是最重要的：编号是用户从缴费页**抄**过来的，
        // `DormElectricityRepository.normalizeCode` 明说空格 / - / _ / # 都不敏感。
        // 第一版校验器只允许字母数字，会把它们全挡在保存外面 —— 而编号是电费精确认人的唯一依据。
        assertNull(BindingValidators.dormCodeHint("BS10-S0602"))
        assertNull(BindingValidators.dormCodeHint("bs10_s0602"))
        assertNull(BindingValidators.dormCodeHint("BS10 S0602"))
        assertNull(BindingValidators.dormCodeHint("BS10#S0602"))
        // 只在"明显不像编号"时给提示（且只是提示，不阻塞保存）
        assertTrue(BindingValidators.dormCodeHint("BS")!!.contains("长度不太对"))
        assertTrue(BindingValidators.dormCodeHint("北校10楼602")!!.contains("不太像编号的字符"))
    }
}
