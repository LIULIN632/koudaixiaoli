package com.pocket.xiaoli.desktop.data

import java.io.File

/**
 * 开机自启：写当前用户的注册表启动项（`HKCU\...\Run`），不需要管理员权限。
 *
 * ## 为什么默认关
 *
 * 往开机启动里塞东西是对用户机器的**长期改动**，装完就自己开是很多人反感的行为。
 * 所以这里只有用户在「我的 → 数据」里主动打开时才会写注册表，关掉时删掉那一项。
 *
 * ## 为什么用 reg.exe 而不是写注册表 API
 *
 * 桌面端没有引入 JNA/win4j；`reg.exe` 是系统自带，参数用列表传给 ProcessBuilder
 * （**不经过 shell**），所以路径里的空格与反斜杠不会被二次解释，也不存在命令注入面。
 */
object AutoLaunch {

    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "PocketXiaoli"

    /** 安装版可执行文件的位置（自启要启动它，而不是开发用的 jar）。 */
    val installedExe: File?
        get() = System.getenv("LOCALAPPDATA")
            ?.let { File(it, "Programs\\PocketXiaoli\\PocketXiaoli.exe") }
            ?.takeIf { it.isFile }

    /** `reg` 命令参数（纯函数，单测钉住"值名 + 带引号的 exe"这个形状）。 */
    internal fun args(action: String, exePath: String? = null): List<String> = when (action) {
        "add" -> listOf("reg", "add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", "\"$exePath\"", "/f")
        "delete" -> listOf("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        "query" -> listOf("reg", "query", RUN_KEY, "/v", VALUE_NAME)
        else -> throw IllegalArgumentException("未知的 reg 动作：$action")
    }

    fun isEnabled(): Boolean = run("query") == 0

    /** 打开自启。返回给用户看的文案（失败时说清为什么）。 */
    fun enable(): String {
        val exe = installedExe ?: return "没找到安装版（%LOCALAPPDATA%\\Programs\\PocketXiaoli\\PocketXiaoli.exe），先用安装程序装一次才能开机自启"
        return if (run("add", exe.absolutePath) == 0) "已开启开机自启（写 HKCU 启动项：$VALUE_NAME）"
        else "开启失败：写不了注册表启动项"
    }

    fun disable(): String {
        // reg delete 对"值本来就不存在"也返回非 0，所以先查再删，别把失败说成成功
        if (!isEnabled()) return "已关闭开机自启（本机没有启动项）"
        return if (run("delete") == 0) "已关闭开机自启" else "关闭失败：删不掉注册表启动项"
    }

    private fun run(action: String, exePath: String? = null): Int = runCatching {
        val process = ProcessBuilder(args(action, exePath))
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText()   // 读空管道，避免子进程阻塞
        process.waitFor()
    }.getOrElse { -1 }
}
