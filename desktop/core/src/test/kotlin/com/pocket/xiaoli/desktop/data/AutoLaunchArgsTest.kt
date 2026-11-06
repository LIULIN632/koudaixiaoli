package com.pocket.xiaoli.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 开机自启只测**参数形状**（不碰注册表）。
 *
 * 真写注册表是用户主动打开时才发生的事，单测不该改这台机器；
 * 但「路径带空格会不会被拆开」「有没有走 shell」这两点必须钉住 —— 那是命令注入面。
 */
class AutoLaunchArgsTest {

    @Test
    fun `add 用参数列表传值且路径整体加引号`() {
        val exe = """C:\Users\some user\AppData\Local\Programs\PocketXiaoli\PocketXiaoli.exe"""
        val args = AutoLaunch.args("add", exe)
        assertEquals("reg", args.first(), "必须是直接调 reg.exe，不能拼给 shell")
        assertTrue(args.contains("/v"), args.toString())
        assertTrue(args.contains("PocketXiaoli"), args.toString())
        val value = args[args.indexOf("/d") + 1]
        assertTrue(value.startsWith("\"") && value.endsWith("\""), "路径要整体加引号：$value")
        assertTrue(args.contains("/f"), "重复打开时不该弹确认框")
    }

    @Test
    fun `query 与 delete 只动自己那一项`() {
        val delete = AutoLaunch.args("delete")
        assertEquals(listOf("reg", "delete"), delete.take(2))
        assertTrue(delete.none { it.contains("HKLM") }, "只能写当前用户：$delete")
        assertTrue(AutoLaunch.args("query").contains("/v"))
    }

    @Test
    fun `未知动作直接拒绝而不是拼个半截命令`() {
        assertFailsWith<IllegalArgumentException> { AutoLaunch.args("import") }
    }
}
