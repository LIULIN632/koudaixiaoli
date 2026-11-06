package com.pocket.xiaoli.desktop.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 应用日志与诊断包。
 *
 * ## 为什么要有
 *
 * 桌面端出问题时，用户能给我的只有一句「它不好使」。有了本机日志，一句「导出诊断包发我」
 * 就能拿到：崩溃堆栈、教务抓取的 AcademicLog、以及数据形状（多少门课、来源标记）。
 *
 * ## 脱敏边界
 *
 * 包里放 `app.log`、`notes.txt`（版本/JVM/数据条数）和**脱敏后的数据文件**：
 * `settings.aiApiKey` 一律换成 `(已脱敏)`，其余字段保留 —— 学号与课表是复现问题必需的，
 * 而它不是凭据。日志本身从不写密码：教务登录走 `AcademicLog`，那里只有 URL 与状态。
 */
object AppLog {

    private const val MAX_BYTES = 5_000_000L
    private const val KEEP = 2

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    val logDir: File get() = File(Store.dataDir, "logs")
    val logFile: File get() = File(logDir, "app.log")

    @Synchronized
    fun log(level: String, tag: String, message: String) {
        runCatching {
            logDir.mkdirs()
            rotateIfNeeded()
            logFile.appendText("${timeFormat.format(Date())} $level/$tag: $message\n", Charsets.UTF_8)
        }
    }

    fun info(tag: String, message: String) = log("I", tag, message)
    fun error(tag: String, message: String) = log("E", tag, message)

    /** 超过 5MB 就滚一档：app.log → app.log.1 →（丢弃更旧的）。 */
    private fun rotateIfNeeded() {
        if (!logFile.exists() || logFile.length() < MAX_BYTES) return
        val oldest = File(logDir, "app.log.$KEEP")
        if (oldest.exists()) oldest.delete()
        for (index in KEEP downTo 2) {
            val from = File(logDir, "app.log.${index - 1}")
            if (from.exists()) from.renameTo(File(logDir, "app.log.$index"))
        }
        logFile.renameTo(File(logDir, "app.log.1"))
    }

    /**
     * 导出诊断包到 `<数据目录>/exports/diagnostic-<时间>.zip`，返回文件。
     *
     * 失败返回 null（调用方给白话提示）；这里不抛异常，因为"导出诊断包"本身失败不该
     * 把用户卡在一个错误弹窗里。
     */
    fun exportBundle(appVersion: String): File? = runCatching {
        val dir = File(Store.dataDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val out = File(dir, "diagnostic-$stamp.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("notes.txt"))
            zip.write(notes(appVersion).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            if (logFile.exists()) {
                zip.putNextEntry(ZipEntry("app.log"))
                logFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            File(logDir, "app.log.1").takeIf { it.exists() }?.let { rolled ->
                zip.putNextEntry(ZipEntry("app.log.1"))
                rolled.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            ImportDiagnostics.lastDir?.takeIf { it.isDirectory }?.let { diag ->
                diag.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        zip.putNextEntry(ZipEntry("import-diagnostics/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            zip.putNextEntry(ZipEntry("pocket-desktop.redacted.json"))
            zip.write(redactedData().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        out
    }.getOrNull()

    private fun notes(appVersion: String): String = buildString {
        appendLine("口袋小鲤 · 桌面版 诊断包")
        appendLine("生成时间：${timeFormat.format(Date())}")
        appendLine("版本：$appVersion")
        appendLine("Java：${System.getProperty("java.version")} ${System.getProperty("java.vendor")}")
        appendLine("系统：${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("用户主目录：${System.getProperty("user.home")}")
        val data = Store.data
        appendLine("数据形状：学期 ${data.terms.size} · 课程 ${data.courses.size} · 待办 ${data.todos.size} · 考试 ${data.exams.size} · 成绩缓存 ${data.gradesCache.size}")
        appendLine("数据来源标记：${data.origin.ifBlank { "(未标注)" }} · 当前校区：${data.settings.campusId}")
        appendLine("AI：${if (data.settings.aiApiKey.isBlank()) "未配置" else "已配置（Key 已脱敏）"} · 模型 ${data.settings.aiModel}")
        appendLine("开关：关闭到托盘 ${data.settings.minimizeToTray} · 上课提醒 ${data.settings.classReminderEnabled} · 待办提醒 ${data.settings.todoReminderEnabled} · 考试提醒 ${data.settings.examReminderEnabled} · 导入诊断 ${data.settings.importDiagnosticsEnabled}")
        appendLine()
        appendLine("说明：pocket-desktop.redacted.json 里只把 settings.aiApiKey 替换成「(已脱敏)」，其余原样保留。")
    }

    private fun redactedData(): String = redactedJsonFrom(Store.exportJson())

    /**
     * 只替换 API Key；解析失败时返回一行说明而不是原始文本（宁可少给，不泄 Key）。
     *
     * 拆成吃字符串的纯函数是为了能单测 —— 脱敏这件事不该靠"跑一遍界面点导出"来保证。
     */
    internal fun redactedJsonFrom(json: String): String = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val settings = root.getAsJsonObject("settings")
        if (settings != null && settings.has("aiApiKey")) {
            settings.addProperty("aiApiKey", if (settings.get("aiApiKey").asString.isBlank()) "" else "(已脱敏)")
        }
        GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)
    }.getOrElse { "{ \"error\": \"数据文件解析失败，未打包原始内容：${it.message}\" }" }
}
