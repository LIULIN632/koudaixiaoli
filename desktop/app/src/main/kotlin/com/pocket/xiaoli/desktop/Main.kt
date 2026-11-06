package com.pocket.xiaoli.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.pocket.xiaoli.desktop.data.InstanceGuard
import com.pocket.xiaoli.desktop.logic.pendingClassReminder
import com.pocket.xiaoli.desktop.logic.pendingExamReminder
import com.pocket.xiaoli.desktop.logic.pendingTodoReminder
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.state.LoginPurpose
import com.pocket.xiaoli.desktop.state.seedExamNotice
import com.pocket.xiaoli.desktop.ui.DesktopApp
import java.awt.Dimension
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * 桌面端入口。
 *
 * 为什么用 `remember { AppState() }` 而不是全局单例：Compose Desktop 的 Window 内容会在
 * 首次组合时重建，状态放在 remember 里跟窗口同生命周期，关窗即释放；全局单例在多窗口场景下
 * 会互相污染（本项目只有一个窗口，但保持这个习惯）。
 *
 * 支持几个开发用开关（普通用户不会传）：
 * - `--tab=N`（0 首页 / 1 课表 / 2 鲤鱼 / 3 服务 / 4 我的）：截图验收时直接打开某一页
 * - `--demo=markdown`：往鲤鱼里塞一段长文样例，用来回归中文排版 / 中英混排 / 标点悬挂
 * - `--demo=api|term|student|import|about|onboarding`：直接打开对应弹窗，用于截图/dialog 验收
 * - `--demo=login|grades|exam`：跳到服务页的登录 / 成绩 / 考试段。
 *   考试段只把一段示例通知填进粘贴框（内存），要点「添加」才会写进数据文件。
 * - `--reminder-at=2026-11-01T08:00`：固定「现在」，用来截图上课/待办/考试提醒而不必真等
 * - `--search=关键词`：直接打开全局搜索并填入关键词（截图验收用）
 * - `--ask=问题`：切到鲤鱼并直接发送这一句（验收离线回答用）
 * - `--update-holidays`：不开窗口，拉一次节假日数据（写到 `<数据目录>/holidays/2026.json`）。
 * - `--selftest=net`：不开窗口，只做一次 HTTPS 握手并把结果写进 %TEMP%\xiaoli-selftest.txt。
 *   用途：打包后裁剪 runtime（只带 java.base/java.desktop/java.logging/jdk.crypto.ec/
 *   jdk.unsupported/jdk.charsets）时，验证 TLS 仍然可用 —— 这条不靠肉眼看界面。
 * - `--export-ics[=文件名]`：不开窗口，把课表写进 `<数据目录>/exports/` 并打印结果。
 *   用途：导出链路可被命令行验证（GUI 点击难自动化）。
 */
fun main(args: Array<String>) {
    // 教务抓取日志与未捕获异常都落到本机 app.log（滚动 5MB×2）。
    // 桌面端原来这些日志是静默丢弃的 —— 出问题只能靠用户复述"它不好使"。
    com.pocket.xiaoli.core.academic.AcademicLog.sink = { level, tag, message, error ->
        com.pocket.xiaoli.desktop.data.AppLog.log(
            level.uppercaseChar().toString(),
            tag,
            if (error == null) message else "$message :: ${error.javaClass.simpleName}: ${error.message}",
        )
    }
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        com.pocket.xiaoli.desktop.data.AppLog.error("Uncaught", "${thread.name} :: ${error.stackTraceToString().take(4000)}")
    }
    com.pocket.xiaoli.desktop.data.AppLog.info("App", "启动 v$DESKTOP_VERSION args=${args.joinToString(" ")}")
    if (args.any { it == "--tray-selftest" }) {
        println("tray: supported=${java.awt.SystemTray.isSupported()}")
        return
    }
    val selftest = args.firstOrNull { it.startsWith("--selftest=") }?.substringAfter('=')
    if (selftest == "net") {
        runNetworkSelftest()
        return
    }
    args.firstOrNull { it.startsWith("--compare-shots=") }?.let { arg ->
        // 截图门禁用的逐像素比较（做成 jar 的 CLI，是为了不依赖 PowerShell 里那套
        // System.Drawing 的加载怪癖）：`--compare-shots=<基线>|<实际>|<阈值百分比>`
        val parts = arg.substringAfter('=').split('|')
        if (parts.size < 2) {
            println("usage: --compare-shots=<baseline.png>|<actual.png>|[thresholdPct]")
            return
        }
        val threshold = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.05
        val outcome = com.pocket.xiaoli.desktop.data.ShotDiff.compare(parts[0], parts[1])
        println(outcome.line(threshold))
        if (!outcome.passes(threshold)) kotlin.system.exitProcess(1)
        return
    }
    if (args.any { it == "--check-update" }) {
        // 不开窗口，只跑一次更新检查并把结果打一行 —— 让「检查更新」这条链可被脚本/CI 验证。
        val release = kotlinx.coroutines.runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.pocket.xiaoli.desktop.data.DesktopUpdate.check() } }
        println(
            when {
                release == null -> "update: none (mirrors unreachable or no desktop block)"
                com.pocket.xiaoli.desktop.data.DesktopUpdate.isNewer(release.versionCode, DESKTOP_VERSION_CODE) ->
                    "update: newer versionCode=" + release.versionCode + " versionName=" + release.versionName + " url=" + release.url
                else -> "update: up-to-date local=" + DESKTOP_VERSION + "(" + DESKTOP_VERSION_CODE + ") remote=" + release.versionName + "(" + release.versionCode + ")"
            }
        )
        return
    }
    if (args.any { it == "--update-holidays" }) {
        // 不开窗口，只拉一次节假日数据；成功/失败都打印一行，方便脚本验收
        val result = kotlinx.coroutines.runBlocking { com.pocket.xiaoli.desktop.data.HolidayStore.updateFromNetwork() }
        val line = result.fold(
            onSuccess = { n -> "holidays: ok count=$n file=" + com.pocket.xiaoli.desktop.data.HolidayStore.summary },
            onFailure = { e -> "holidays: failed " + (e.message ?: e::class.simpleName) },
        )
        println(line)
        return
    }
    val exportArg = args.firstOrNull { it == "--export-ics" || it.startsWith("--export-ics=") }
    if (exportArg != null) {
        val name = exportArg.substringAfter('=', "")
        println(AppState().exportIcs(name.ifBlank { "课表.ics" }))
        return
    }
    // 单实例：数据是整文件写盘的，两个进程同时开就会互相覆盖改动。
    // 开机自启装上之后，「开机一个 + 手动双击一个」是必然会发生的事，所以第二次启动
    // 不再开新窗口，而是把已有窗口叫到前台，本次进程直接退出。
    when (instanceGuard.acquire { WindowActivator.request() }) {
        InstanceGuard.Outcome.HandedOff -> {
            com.pocket.xiaoli.desktop.data.AppLog.info(
                "Instance",
                "已有实例在运行，本次启动把窗口交给它（端口 ${instanceGuard.port}）",
            )
            return
        }
        InstanceGuard.Outcome.PortTakenByStranger -> com.pocket.xiaoli.desktop.data.AppLog.error(
            "Instance",
            "回环端口 ${instanceGuard.port} 被别的服务占用，单实例守卫失效（功能照常，但别同时开两个）",
        )
        InstanceGuard.Outcome.Primary -> Unit
    }
    runApp(args)
}

/** 顶层持有：进程活多久它守多久，退出时回环端口随进程释放。 */
private val instanceGuard = InstanceGuard()

private fun runApp(args: Array<String>) = application {
    val state = remember { AppState() }
    val devTab = args.firstOrNull { it.startsWith("--tab=") }?.substringAfter('=')?.toIntOrNull()
    val demo = args.firstOrNull { it.startsWith("--demo=") }?.substringAfter('=')
    val search = args.firstOrNull { it.startsWith("--search=") }?.substringAfter('=')
    val ask = args.firstOrNull { it.startsWith("--ask=") }?.substringAfter('=')
    val loginUrl = args.firstOrNull { it.startsWith("--login-url=") }?.substringAfter('=')
    val loginUser = args.firstOrNull { it.startsWith("--login-user=") }?.substringAfter('=')
    val loginPass = args.firstOrNull { it.startsWith("--login-pass=") }?.substringAfter('=')
    val loginCaptcha = args.firstOrNull { it.startsWith("--login-captcha=") }?.substringAfter('=')
    val loginAuto = args.any { it == "--login-auto" }
    val reminderAt = args.firstOrNull { it.startsWith("--reminder-at=") }
        ?.substringAfter('=')
        ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    val trayState = rememberTrayState()
    var windowRef by remember { mutableStateOf<ComposeWindow?>(null) }
    var trayNotified by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { state.reminderNowOverride = reminderAt }
    LaunchedEffect(Unit) {
        while (true) {
            val now = state.reminderNowOverride ?: LocalDateTime.now()
            if (state.data.settings.todoReminderEnabled) {
                val todo = pendingTodoReminder(state.data, now)
                if (todo != null && todo.key != state.lastTodoReminderKey) {
                    state.lastTodoReminderKey = todo.key
                    state.notify(todo.message)
                    if (state.reminderNowOverride != null) {
                        println("todo-reminder: ${todo.key} | ${todo.message}")
                    }
                    runCatching {
                        trayState.sendNotification(
                            Notification("待办提醒", todo.message, Notification.Type.Info),
                        )
                    }
                }
            }
            if (state.data.settings.examReminderEnabled) {
                val exam = pendingExamReminder(state.data, now)
                if (exam != null && exam.key != state.lastExamReminderKey) {
                    state.lastExamReminderKey = exam.key
                    state.notify(exam.message)
                    if (state.reminderNowOverride != null) {
                        println("exam-reminder: ${exam.key} | ${exam.message}")
                    }
                    runCatching {
                        trayState.sendNotification(
                            Notification("考试提醒", exam.message, Notification.Type.Warning),
                        )
                    }
                }
            }
            if (state.data.settings.classReminderEnabled) {                val lead = state.data.settings.classReminderLead
                val reminder = pendingClassReminder(state.data, now, lead)
                if (reminder != null && reminder.key != state.lastClassReminderKey) {
                    state.lastClassReminderKey = reminder.key
                    val message = buildString {
                        append(reminder.courseName)
                        if (reminder.room.isNotBlank()) append(" · ").append(reminder.room)
                        append(" · ").append(reminder.minutesUntilStart).append(" 分钟后上课")
                    }
                    state.notify(message)
                    if (state.reminderNowOverride != null) {
                        println("reminder: ${reminder.key} | $message")
                    }
                    runCatching {
                        trayState.sendNotification(
                            Notification("上课提醒", message, Notification.Type.Warning),
                        )
                    }
                }
            }
            delay(30_000)
        }
    }

    Window(
        onCloseRequest = {
            val w = windowRef
            if (state.data.settings.minimizeToTray && w != null) {
                w.isVisible = false
                if (!trayNotified) {
                    trayState.sendNotification(
                        Notification("口袋小鲤", "还在后台运行，点托盘图标就能回来"),
                    )
                    trayNotified = true
                }
            } else {
                exitApplication()
            }
        },
        title = "口袋小鲤 · 桌面版",
        state = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(1240.dp, 820.dp),
        ),
    ) {
        LaunchedEffect(Unit) {
            windowRef = window
            WindowActivator.action = { WindowActivator.showWindow(window) }
            window.minimumSize = Dimension(1000, 660)
            if (devTab != null) state.tab = devTab.coerceIn(0, 4)
            when (demo) {
                "markdown" -> {
                    state.seedMarkdownDemo()
                    state.tab = 2
                }
                "api" -> state.showApiDialog = true
                "term" -> state.showTermDialog = true
                "student" -> state.showStudentDialog = true
                "import" -> state.showImportDialog = true
                "login" -> state.openLogin()
                "grades" -> {
                    state.tab = 3
                    state.campusSection = 1
                    state.openLogin(LoginPurpose.GRADES)
                }
                "exam" -> {
                    state.tab = 3
                    state.campusSection = 2
                    state.seedExamNotice(sampleExamNotice())
                }
                "about" -> state.showAbout = true
                "ime" -> state.showImeProbe = true
                "onboarding" -> state.showOnboarding = true
            }
            if (loginUrl != null) state.loginBaseUrlOverride = loginUrl
            if (loginUser != null) state.loginUser = loginUser
            if (loginPass != null) state.loginPass = loginPass
            if (loginCaptcha != null) state.loginAutoCaptcha = loginCaptcha
            if (loginAuto) {
                if (!state.showLogin) state.openLogin()
                state.startLogin()
            }
            if (ask != null) {
                // 验收用：直接问一句并发送（没配 Key 时走本机知识库/快问快答的离线回答）
                state.tab = 2
                state.draft = ask
                state.send()
            }
            if (search != null) {
                state.openSearch()
                state.searchQuery = search
            }
        }
        DesktopApp(state)
    }
    Tray(
        icon = painterResource("tray-icon.png"),
        state = trayState,
        tooltip = "口袋小鲤 · 桌面版",
        onAction = {
            windowRef?.let {
                it.isVisible = true
                it.toFront()
            }
        },
        menu = {
            Item("打开主窗口", onClick = {
                windowRef?.let {
                    it.isVisible = true
                    it.toFront()
                }
            })
            Item("打开课表", onClick = {
                state.tab = 1
                windowRef?.let {
                    it.isVisible = true
                    it.toFront()
                }
            })
            Separator()
            Item("退出", onClick = { exitApplication() })
        },
    )
}

/**
 * 打包后 TLS 冒烟：对一个真实 HTTPS 端点发一次请求，把「HTTP 状态码或异常类名」写文件。
 *
 * 为什么不直接看界面：AI 配置弹窗的测试结果在可滚动内容区里，裁剪过 runtime 的包一旦
 * 缺 jdk.crypto.ec，界面上的表现和「网络不通」长得一样，肉眼分不出来。写文件 + 退出码
 * 才能进脚本。
 */
private fun runNetworkSelftest() {
    val result: String = runCatching<String> {
        // 用 JDK 自带 HttpsURLConnection 而不是 OkHttp：这里要验的正是「裁剪后的 runtime 里 TLS 栈
        // 还完整吗」，绕开第三方库才能定位到 jdk.crypto.ec / java.base 这一层。
        val url = java.net.URI.create("https://api.deepseek.com/v1/models").toURL()
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.requestMethod = "GET"
        try {
            val code = conn.responseCode
            val suite = (conn as? javax.net.ssl.HttpsURLConnection)?.cipherSuite ?: "n/a"
            "ok http=$code tls=$suite java=${System.getProperty("java.version")} modules=trimmed"
        } finally {
            conn.disconnect()
        }
    }.getOrElse { error: Throwable ->
        "fail ${error.javaClass.name}: ${error.message}"
    }

    // 写数据目录而不是 %TEMP%：这台机器上打包后的 JVM 写 %TEMP% 会被拒（Access denied），
    // 而 %APPDATA%\PocketXiaoli 是应用自己的目录，可写。
    runCatching {
        val out = com.pocket.xiaoli.desktop.data.Store.dataDir.resolve("selftest.txt")
        out.parentFile?.mkdirs()
        out.writeText(result, Charsets.UTF_8)
    }
    println("selftest: $result")
}

/**
 * 验收用（`--demo=exam`）：一段"典型教务期末考试通知"。
 *
 * 日期用**今天往后推**构造，所以倒计时永远是「还有 N 天」，截图不会随日历过期；
 * 里面刻意夹了标题行、表头行和「注意事项」噪音行，用来演示解析器把它们跳掉。
 * 这段文本只填进粘贴框（内存），**不写数据文件** —— 用户点「添加」才落盘。
 */
private fun sampleExamNotice(): String {
    val today = LocalDate.now()
    return """
        关于组织${today.year}—${today.year + 1}学年第一学期期末考试的通知
        课程名称    考试时间    考场    座位号
        大学英语    ${today.plusDays(2)}    09:00-11:00    教二楼201    5
        线性代数    ${today.plusDays(5)}    14:00-16:00    教三楼305    22
        高等数学A    ${today.plusDays(12)}    08:30-10:30    图书馆报告厅    12
        注意事项：请携带学生证与身份证提前十五分钟到场
    """.trimIndent()
}
