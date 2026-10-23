package com.pocket.xiaoli.util

import android.app.Application
import android.os.Build
import android.os.Environment
import com.pocket.xiaoli.telemetry.Telemetry
import com.pocket.xiaoli.telemetry.TelemetryEvent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val application: Application
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            writeCrashLog(e)
        } catch (_: Exception) {
            // silently fail — don't crash the crash handler
        }
        // 只记"崩了一次"，**不带堆栈、不带设备型号**。
        // 堆栈里有文件路径，有些路径会带出学号（导出目录名就是学号）。
        // 进程正在死，这一条多半发不出去，会留在本地队列里，下次启动补发。
        try {
            Telemetry.track(TelemetryEvent.CrashOccurred)
        } catch (_: Throwable) {
            // 统计坏了绝不能影响崩溃日志与系统的默认处理
        }
        defaultHandler?.uncaughtException(t, e)
    }

    private fun writeCrashLog(e: Throwable) {
        val logDir = getLogDir()
        if (logDir == null || !logDir.exists()) {
            logDir?.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val logFile = File(logDir, "crash_$timestamp.txt")

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("========================================")
        pw.println("口袋小鲤 - 崩溃日志")
        pw.println("========================================")
        pw.println("崩溃时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        pw.println("设备型号: ${Build.MODEL} (${Build.MANUFACTURER})")
        pw.println("系统版本: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        pw.println("APP版本: ${getAppVersion()}")
        pw.println("----------------------------------------")
        pw.println("错误堆栈:")
        pw.println("----------------------------------------")
        e.printStackTrace(pw)
        pw.println("----------------------------------------")
        pw.flush()
        pw.close()

        // 先写临时文件再原子重命名。
        //
        // 为什么不能直接 writeText 到最终文件名：崩溃处理线程本身随时可能被系统掐死
        // （进程正在退出、或又崩了一次）。直接写会留下一个**0 字节但名字正确**的文件，
        // 而 getLatestCrashLog() 按 lastModified 取最新的，就会把这个空壳当成"最近的崩溃日志"——
        // 表现就是用户在「我的」页点导出，拿到一个空文件（这正是线上反馈的问题）。
        // 重命名在多数文件系统上是原子的：要么看不到，要么内容是完整的。
        val tmp = File(logDir, "${logFile.name}.tmp")
        tmp.writeText(sw.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(logFile)) {
            // 重命名失败（极少数情况）就退回直接写，至少不会丢日志
            logFile.writeText(sw.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun getLogDir(): File? {
        return try {
            val dir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null) File(dir, "crash_logs") else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 取"最近一次崩溃日志"。
     *
     * ⚠️ 必须**排掉 0 字节文件**：历史版本直接写最终文件名，进程被掐死时会留下空壳。
     * 空壳的 `lastModified` 往往是最新的，不排掉就会取到它 —— 上层读出来是空字符串，
     * 用户看到的就是"导出的日志是空的"。这里排掉后，取到的是**上一个完整的**崩溃记录，
     * 比返回空更能说明问题。
     */
    fun getLatestCrashLog(): File? {
        val dir = getLogDir() ?: return null
        if (!dir.exists()) return null
        val files = dir.listFiles { f ->
            f.isFile && f.name.startsWith("crash_") && f.name.endsWith(".txt") && f.length() > 0L
        }
        return files?.maxByOrNull { it.lastModified() }
    }

    private fun getAppVersion(): String {
        return try {
            val pkgInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            pkgInfo.versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
