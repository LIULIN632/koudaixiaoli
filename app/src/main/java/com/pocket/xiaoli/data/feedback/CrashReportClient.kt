package com.pocket.xiaoli.data.feedback

import android.content.Context
import com.google.gson.JsonObject
import com.pocket.xiaoli.BuildConfig
import com.pocket.xiaoli.data.SelfHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 崩溃日志直传（「快捷反馈」的后端）。
 *
 * ## 为什么不是"自动后台上传"
 *
 * 崩溃日志里有堆栈、类名、机型，偶尔还会带出**用户本机的路径**（我们自己的导出目录
 * 就是按学号命名的，见 `memory/01-constraints.md` 第 3 条）。所以这里的纪律是：
 * **只有用户按下「发送」才上行**，且发送前把本机路径抹成 `<path>`。
 * 自动化只做一件事——发现新崩溃时**提醒一次**，不替用户决定。
 *
 * ## 为什么要记"已发送"
 *
 * 同一份日志会被反复看到（每次打开反馈页都列出来）。不记账的话用户会重复提交，
 * 服务端每天 10 条的限制会被自己耗光。这里按**文件名**记账：发过的就不再出现在
 * 「待发送」里，但仍然保留在本地，随时可以再发（用户点「重发」）。
 */
object CrashReportClient {

    private const val PATH = "/v1/crash/submit"
    private const val PREFS = "crash_reports"
    private const val KEY_SENT = "sent_files"
    private const val KEY_PROMPTED = "prompted_files"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 待发送的崩溃日志，最新的在前。
     *
     * 只取 `crash_*.txt` 且**内容非空**（0 字节空壳是历史版本崩溃时进程被掐死留下的，
     * `CrashHandler.getLatestCrashLog` 已经排过一次，这里再排一次，因为这里遍历全部）。
     */
    fun pending(context: Context): List<File> {
        val dir = crashDir(context) ?: return emptyList()
        if (!dir.exists()) return emptyList()
        val sent = sentNames(context)
        return dir.listFiles { f ->
            f.isFile && f.name.startsWith("crash_") && f.name.endsWith(".txt") &&
                f.length() > 0L && f.name !in sent
        }?.sortedByDescending { it.lastModified() }.orEmpty()
    }

    /** 全部崩溃日志（含已发送），反馈页里"历史"那一段用。 */
    fun all(context: Context): List<File> {
        val dir = crashDir(context) ?: return emptyList()
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f ->
            f.isFile && f.name.startsWith("crash_") && f.name.endsWith(".txt") && f.length() > 0L
        }?.sortedByDescending { it.lastModified() }.orEmpty()
    }

    /**
     * 从日志文本里抽出"一眼能看懂的那一行"：第一个 `XxxException: …` 或 `XxxError: …`。
     *
     * 反馈页上每条崩溃只显示这个摘要 + 时间 —— 一屏塞三份完整堆栈没人看。
     */
    internal fun headline(log: String): String {
        val line = log.lineSequence()
            .map(String::trim)
            .firstOrNull { l ->
                (l.contains("Exception") || l.contains("Error")) &&
                    !l.startsWith("at ") && !l.startsWith("Caused by:") &&
                    l.length in 8..200
            }
        return line ?: log.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().take(120)
    }

    /**
     * 日志脱敏：把绝对路径里"用户名/学号"这一段抹掉。
     *
     * 实测会出现的形状：`/storage/emulated/0/Android/data/…/files/Download/2023123456/…`、
     * `C:\Users\xxx\…`。只保留最后两段，其余换成 `…`，既不影响定位，也带不出身份。
     */
    internal fun sanitize(log: String): String =
        log.lineSequence().joinToString("\n") { line ->
            line.replace(Regex("""(/storage/emulated/\d+/[^\s:]+|[A-Za-z]:\\Users\\[^\s:]+)""")) { m ->
                val raw = m.value
                val parts = raw.split('/', '\\').filter { it.isNotBlank() }
                // 只留文件名：倒数第二段通常就是学号/用户名所在的那一级，留两段等于没抹。
                if (parts.size <= 1) raw else "…/" + parts.last()
            }
        }

    /**
     * 发送一份日志。与 [FeedbackRemote] 同一套地址解析与错误分类，
     * 唯一区别是路径与体积（日志可以有 200KB）。
     */
    suspend fun send(
        context: Context,
        file: File,
        note: String,
        anonId: String,
        ticket: String,
    ): SubmitOutcome = withContext(Dispatchers.IO) {
        val base = FeedbackRemote.endpoint(context)
            ?: return@withContext SubmitOutcome.Rejected("这台设备没有配置反馈通道")

        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
        if (raw.isNullOrBlank()) {
            return@withContext SubmitOutcome.Rejected("这份日志是空的，没什么可发")
        }
        val body = JsonObject().apply {
            addProperty("ticket", ticket)
            addProperty("iid", anonId)
            addProperty("versionCode", BuildConfig.VERSION_CODE)
            addProperty("versionName", BuildConfig.VERSION_NAME)
            addProperty("device", android.os.Build.MODEL)
            addProperty("os", "Android ${android.os.Build.VERSION.RELEASE}")
            addProperty("note", note.trim())
            addProperty("log", sanitize(raw))
        }.toString()

        try {
            val request = Request.Builder()
                .url(FeedbackRemote.urlFor(base, PATH))
                .addHeader("X-App-Key", SelfHost.TELEMETRY_KEY)
                .post(body.toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val outcome = FeedbackRemote.classify(response.code)
                if (outcome is SubmitOutcome.Sent) markSent(context, file.name)
                outcome
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SubmitOutcome.Retryable("网络不通：${e.javaClass.simpleName}")
        } catch (e: Exception) {
            SubmitOutcome.Retryable("发送失败：${e.javaClass.simpleName}")
        }
    }

    /** 提醒过（或用户点过「不发」）的文件名，避免每次启动都弹。 */
    fun shouldPrompt(context: Context): File? {
        val candidate = pending(context).firstOrNull() ?: return null
        return candidate.takeIf { it.name !in promptedNames(context) }
    }

    fun markPrompted(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = promptedNames(context) + name
        prefs.edit().putStringSet(KEY_PROMPTED, next).apply()
    }

    private fun sentNames(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_SENT, emptySet())?.toSet().orEmpty()

    private fun promptedNames(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PROMPTED, emptySet())?.toSet().orEmpty()

    private fun markSent(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SENT, sentNames(context) + name).apply()
    }

    private fun crashDir(context: Context): File? = runCatching {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        dir?.let { File(it, "crash_logs") }
    }.getOrNull()
}
