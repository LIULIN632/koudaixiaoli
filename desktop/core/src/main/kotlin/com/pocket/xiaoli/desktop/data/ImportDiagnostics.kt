package com.pocket.xiaoli.desktop.data

import com.pocket.xiaoli.core.academic.AcademicExchange
import com.pocket.xiaoli.core.academic.ImportDiagnosticsBridge
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 导入诊断：把一次教务抓取过程的请求与响应落到 `<数据目录>/logs/import-<时间>/`。
 *
 * ## 为什么要这个
 *
 * 「导入进来是空的」「少了几门课」是桌面端最难自查的一类问题：原因可能是学校部署的
 * URL 变体、学期参数、分页、表格变体或会话被踢 —— 这些全都只体现在**原始响应**里，
 * 界面上只会显示「没解析出课程」。有了落盘的样本，问题才能离线复现和修。
 *
 * ## 隐私边界
 *
 * 只写 URL / 表单 / 重定向链 / 响应正文，**不写任何请求头与响应头**（那里面是 Cookie）；
 * 表单里的密码类字段整值打码（`mm` / `password` / `pwd` …）。学号会留下 —— 它是复现问题
 * 必需的参数（课表接口按学号查），且它不是凭据。
 */
object ImportDiagnostics {

    private val dirStamp = SimpleDateFormat("yyyyMMdd-HHmmss")
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS")

    /** 当前这次抓取写到的目录；没在抓就是 null。 */
    @Volatile
    var currentDir: File? = null
        private set

    private var index = 0
    private val sessionLines = mutableListOf<String>()
    private val lock = Any()

    val logRoot: File get() = File(Store.dataDir, "logs")

    /** 最近一次诊断目录（失败时给「打开日志目录」按钮用）。 */
    @Volatile
    var lastDir: File? = null
        private set

    /**
     * 开始抓取：建目录、挂 sink、写头部说明。
     *
     * @param label 这次抓取是干什么（登录 / 课表 / 成绩），写进 session.txt 头部
     */
    fun begin(label: String, baseUrl: String, username: String): File? = synchronized(lock) {
        runCatching {
            val dir = File(logRoot, "import-" + dirStamp.format(Date()))
            dir.mkdirs()
            index = 0
            sessionLines.clear()
            sessionLines += "# 口袋小鲤 桌面端 导入诊断"
            sessionLines += "# 用途：$label"
            sessionLines += "# 教务：$baseUrl"
            // 学号只写后四位之外的信息没有意义，这里保留完整学号 —— 复现接口必需（不是凭据）
            sessionLines += "# 学号：$username"
            sessionLines += "# 说明：只记录 URL / 表单 / 重定向链 / 响应正文；密码字段已打码，Cookie 与请求头不记录。"
            currentDir = dir
            lastDir = dir
            ImportDiagnosticsBridge.onExchange = { exchange -> record(exchange) }
            dir
        }.getOrNull()
    }

    /** 结束抓取：摘掉 sink、把 session.txt 落盘，返回目录。 */
    fun end(): File? {
        ImportDiagnosticsBridge.onExchange = null
        return synchronized(lock) {
            val dir = currentDir
            if (dir != null) {
                runCatching { File(dir, "session.txt").writeText(sessionLines.joinToString("\n") + "\n", Charsets.UTF_8) }
            }
            currentDir = null
            dir
        }
    }

    private fun record(exchange: AcademicExchange) {
        val dir = synchronized(lock) { currentDir } ?: return
        val seq: Int
        synchronized(lock) {
            seq = ++index
            sessionLines += "[%03d] %s %s → %d %s".format(
                seq,
                exchange.method,
                exchange.url,
                exchange.status,
                if (exchange.requestForm.isBlank()) "" else "form=${exchange.requestForm}",
            )
            if (exchange.redirectChain.isNotEmpty()) {
                sessionLines += "      重定向经过：${exchange.redirectChain.joinToString(" -> ")}"
            }
        }
        val name = exchange.url.substringBefore('?').substringAfterLast('/').ifBlank { "page" }
        val file = File(dir, "%03d-%d-%s".format(seq, exchange.status, name.ifBlank { "root" } + ".txt"))
        runCatching {
            file.writeText(
                buildString {
                    appendLine("# ${exchange.method} ${exchange.url}")
                    appendLine("# status=${exchange.status}  time=${timeFormat.format(Date())}")
                    appendLine("# redirect chain: ${exchange.redirectChain.joinToString(" -> ").ifBlank { "(无)" }}")
                    appendLine("# request form: ${exchange.requestForm.ifBlank { "(无)" }}")
                    appendLine("# body length: ${exchange.body.length}")
                    appendLine()
                    append(exchange.body)
                },
                Charsets.UTF_8,
            )
        }
        synchronized(lock) { sessionLines += "      正文落盘：${file.name}" }
    }
}
