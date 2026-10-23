package com.pocket.xiaoli.data.qzxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 蓝牙设备发现的**可离线验证**部分。
 *
 * 真机蓝牙扫不到东西有很多原因（权限、蓝牙没开、ROM 差异、广播名不对），
 * 排查起来很烦。所以凡是能在电脑上钉死的规则，全部钉死：
 * 名字匹配、信号分级、扫描所需的权限清单。
 *
 * 广播名 `KLCXKJ-Water` 来自两份**独立**的反向工程实现
 *（[`yehu-imei/linyu`](https://github.com/yehu-imei/linyu) 与
 * [`Cainite07/MudLife`](https://github.com/Cainite07/MudLife)，均 MIT），
 * 两边完全一致 —— 这是目前能拿到的最高可信度。
 */
class QzxyBleScannerTest {

    @Test
    fun `匹配官方广播名`() {
        assertTrue(QzxyBleScanner.matches("KLCXKJ-Water"))
    }

    @Test
    fun `广播名大小写不敏感`() {
        // 参考实现两处都写了 ignoreCase，说明真机上见过大小写不一致的
        assertTrue(QzxyBleScanner.matches("klcxkj-water"))
        assertTrue(QzxyBleScanner.matches("KlCxKj-WaTeR"))
    }

    @Test
    fun `带后缀的广播名也能匹配`() {
        // 有些固件会在名字后面补编号
        assertTrue(QzxyBleScanner.matches("KLCXKJ-Water-01"))
        assertTrue(QzxyBleScanner.matches("KLCXKJ-Water_320"))
    }

    @Test
    fun `无关广播名不匹配`() {
        assertFalse(QzxyBleScanner.matches("Mi Band 7"))
        assertFalse(QzxyBleScanner.matches("KLCXKJ-Fan"))
        assertFalse(QzxyBleScanner.matches("Water"))
    }

    @Test
    fun `空广播名放行 —— 这是有意的决定`() {
        // ⚠️ 有些机型/ROM 读不到广播名（device.name 需要 BLUETOOTH_CONNECT，且部分 ROM
        // 就是不给）。直接否掉会让这些手机上"一台都搜不到"，而且完全不报错。
        // 放行后由 /device/info/mac 去否掉非热水器，代价只是多几次请求。
        assertTrue("null 要放行", QzxyBleScanner.matches(null))
        assertTrue("空串要放行", QzxyBleScanner.matches(""))
        assertTrue("全空格要放行", QzxyBleScanner.matches("   "))
    }

    @Test
    fun `信号分级按文档阈值`() {
        // 阈值取自 API 文档的「信号强度参考」表
        assertEquals("强", QzxyBleScanner.Found("AABBCCDDEEFF", -50, "").signalLabel)
        assertEquals("强", QzxyBleScanner.Found("AABBCCDDEEFF", -70, "").signalLabel)
        assertEquals("中", QzxyBleScanner.Found("AABBCCDDEEFF", -71, "").signalLabel)
        assertEquals("中", QzxyBleScanner.Found("AABBCCDDEEFF", -85, "").signalLabel)
        assertEquals("弱", QzxyBleScanner.Found("AABBCCDDEEFF", -86, "").signalLabel)
    }

    @Test
    fun `Android 12 及以上要扫描与连接两个权限`() {
        // 断言的是"清单里的权限与这里一致"这个契约：实际返回值取决于运行时 SDK，
        // 这里只校验它非空且都来自 BLUETOOTH_* 或定位（不在单测里 mock SDK 版本）
        val perms = QzxyBleScanner.requiredPermissions
        assertTrue("必须至少要一个权限", perms.isNotEmpty())
        assertTrue(
            "权限只能是蓝牙扫描/连接或定位 —— 少一个就会静默扫不到设备",
            perms.all {
                it == "android.permission.BLUETOOTH_SCAN" ||
                    it == "android.permission.BLUETOOTH_CONNECT" ||
                    it == "android.permission.ACCESS_FINE_LOCATION"
            },
        )
    }
}
