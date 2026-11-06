package com.pocket.xiaoli.desktop.state

import com.pocket.xiaoli.desktop.DESKTOP_VERSION_CODE
import com.pocket.xiaoli.desktop.data.*

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocket.xiaoli.desktop.ai.ChatEvent
import com.pocket.xiaoli.desktop.ai.LlmClient
import com.pocket.xiaoli.desktop.ai.LlmConfig
import com.pocket.xiaoli.desktop.ai.PocketTools
import com.pocket.xiaoli.core.academic.CaptchaResult
import com.pocket.xiaoli.core.academic.ParsedCourse
import com.pocket.xiaoli.core.academic.ZfGradeItem
import com.pocket.xiaoli.core.academic.ZfTerm
import com.pocket.xiaoli.desktop.logic.AcademicLogin
import com.pocket.xiaoli.desktop.logic.AcademicLoginOutcome
import com.pocket.xiaoli.desktop.logic.AcademicSession
import com.pocket.xiaoli.desktop.logic.ImportPlan
import com.pocket.xiaoli.desktop.logic.answerFromKnowledge
import com.pocket.xiaoli.desktop.logic.matchQuickAnswer
import com.pocket.xiaoli.desktop.logic.PendingExam
import com.pocket.xiaoli.desktop.logic.SearchHit
import com.pocket.xiaoli.desktop.logic.SearchKind
import com.pocket.xiaoli.desktop.logic.TimetableFilter
import com.pocket.xiaoli.desktop.logic.parseExamNotice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 应用状态（Compose 的单一真源）。
 *
 * ## 为什么状态集中在这里
 *
 * 桌面端有跨屏联动：知识库点「问鲤鱼」要切到鲤鱼页并带上问题；改完学期要立刻反映到
 * 课表周次；配好 API 鲤鱼页要立即解禁。集中一份状态 + 明确的写入口，比每个屏幕
 * 各存一份再靠回调同步更不容易漂。
 */
/** 登录目的：导入课表 or 只建会话查成绩。 */
enum class LoginPurpose { IMPORT, GRADES }

class AppState {

    var data by mutableStateOf(Store.data)
        private set

    /**
     * JVM 起来到**首帧组合**的毫秒数，配置体检卡用它说「启动快不快」。
     *
     * 口径要说清楚：它不含 jpackage 启动器自己的时间，所以比 `measure-coldstart.ps1`
     * 量的「双击到窗口出现」小一截（本机实测差 0.3~0.5s）。两个数各自有意义，不能混着报。
     */
    var firstFrameMillis by mutableStateOf<Long?>(null)
        private set

    fun markFirstFrame() {
        if (firstFrameMillis == null) {
            // 裁剪后的 runtime 缺 java.management 时会抛 NoClassDefFoundError（是 Error 不是 Exception），
            // 这段跑在 AWT 事件线程上，不兜住就会在首帧打出一条堆栈。量不到就显示「还没测到」。
            firstFrameMillis = runCatching {
                java.lang.management.ManagementFactory.getRuntimeMXBean().uptime
            }.getOrNull()
        }
    }

    var chats by mutableStateOf(loadChats())
        private set
    var activeChatId by mutableStateOf(chats.firstOrNull()?.id)
        private set
    var draft by mutableStateOf("")
    var streaming by mutableStateOf(false)
        private set

    var llm by mutableStateOf(AppPrefs.loadLlm())
        private set

    /** 导航：0 首页 / 1 课表 / 2 鲤鱼 / 3 校园 / 4 我的 */
    var tab by mutableStateOf(0)

    /** 周次覆盖：0 = 跟随今天，其余 = 指定周（课表左右翻周用）。 */
    var weekOverride by mutableStateOf(0)

    /** 课表页的老师/教室筛选（不跳页面，而是把不相关的格子弱化掉）。 */
    var timetableFilter by mutableStateOf(TimetableFilter())

    var courseEditor by mutableStateOf<Course?>(null)
    var showTodoEditor by mutableStateOf(false)
    var showApiDialog by mutableStateOf(false)
    var showStudentDialog by mutableStateOf(false)
    var showImportDialog by mutableStateOf(false)
    var showTermDialog by mutableStateOf(false)
    var showOnboarding by mutableStateOf(!AppPrefs.onboarded())
    var showAbout by mutableStateOf(false)
    var showSlotDialog by mutableStateOf(false)
    var showIcsDialog by mutableStateOf(false)

    /** 输入法自检弹窗（只能人工看，见 ImeProbeDialog）。 */
    var showImeProbe by mutableStateOf(false)
    var icsFileName by mutableStateOf("课表.ics")

    /** 全局搜索（Ctrl+K）。查询与选中项都放这里，面板只负责渲染。 */
    var showSearch by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    /** 服务页当前段：0 服务入口 / 1 成绩 / 2 考试 / 3 校历 / 4 知识库 / 5 快问快答。搜索跳转要能指定它。 */
    var campusSection by mutableStateOf(
        System.getProperty("xiaoli.campus.section")?.toIntOrNull()?.coerceIn(0, 5) ?: 0,
    )

    /** 知识库搜索词与要展开的文档（全局搜索跳过来时用）。 */
    var knowledgeQuery by mutableStateOf("")
    var knowledgeFocusId by mutableStateOf<String?>(null)

    /** 校历要定位到的那一天（事件开始日）。 */
    var calendarFocusEpochDay by mutableStateOf<Long?>(null)

    /** 待办详情弹窗。 */
    var selectedTodo by mutableStateOf<Todo?>(null)

    // ── 教务登录（正方）──
    var showLogin by mutableStateOf(false)
    var loginPurpose by mutableStateOf(LoginPurpose.IMPORT)
    var loginUser by mutableStateOf("")
    var loginPass by mutableStateOf("")
    var loginBusy by mutableStateOf(false)
    var loginProgress by mutableStateOf("")
    var loginError by mutableStateOf<String?>(null)
    var loginCaptcha by mutableStateOf<CaptchaResult?>(null)
    var loginCaptchaCode by mutableStateOf("")

    /** 登录成功后的会话；成绩查询复用同一客户端（Cookie 还在）。 */
    var academicSession by mutableStateOf<AcademicSession?>(null)

    /** 导入结果停在确认框里（课表可疑时不静默覆盖）；null = 没有待确认的导入。 */
    var pendingImport by mutableStateOf<AcademicLoginOutcome?>(null)

    /** 待确认导入的替换计划（门数 / 覆盖数 / 保留数 / 警告），给确认框显示。 */
    var importPlan by mutableStateOf<ImportPlan?>(null)

    /** 最近一次导入前的备份文件名（只在成功导入后有意义）。 */
    var importBackupFile by mutableStateOf<String?>(null)

    // ── 成绩查询 ──
    var gradeTerms by mutableStateOf<List<ZfTerm>>(emptyList())
    var grades by mutableStateOf<List<ZfGradeItem>>(emptyList())
    var gradeTermIndex by mutableStateOf(0)
    var gradesLoading by mutableStateOf(false)
    var gradesError by mutableStateOf<String?>(null)

    /** 天气：快照 + 是否正在联网 + 一句"这次没联网成功/用的是缓存"。解析与节流都在共享核心。 */
    var weather by mutableStateOf<com.pocket.xiaoli.core.weather.WeatherSnapshot?>(null)
    var weatherBusy by mutableStateOf(false)
    var weatherNote by mutableStateOf<String?>(null)

    /** 上一次登录拉到的成绩缓存（按学期存），没会话时先看它，别把用户挡在登录页外。 */
    val cachedGradeTerms: List<GradesCache> get() = data.gradesCache

    /** 缓存视图里当前选中的是第几份缓存（列表按查询时间倒序，见 `AppStateData.cachedTermsSorted`）。 */
    var cachedGradeIndex by mutableStateOf(0)

    // ── 考试安排（教务没有接口，走「粘贴通知 → 解析 → 确认」）──
    /** 用户粘进来的通知原文。 */
    var examNotice by mutableStateOf("")
    /** 解析出的候选考试（非空表示停在预览，等用户确认）。 */
    var examPending by mutableStateOf<List<PendingExam>>(emptyList())
    /** 解析扫过的候选行数（给"读了 N 行、认出 M 条"的反馈）。 */
    var examScanned by mutableStateOf(0)
    /** 解析/添加的提示（空 = 不显示）。 */
    var examMessage by mutableStateOf<String?>(null)
    /** 是否展开粘贴框。 */
    var examEditorOpen by mutableStateOf(false)

    /** 仅调试/验收：覆盖教务地址（本地 Mock 服务端用）。 */
    var loginBaseUrlOverride: String? = null

    /** 仅调试/验收：验证码自动填入，跑通无头登录截图。 */
    var loginAutoCaptcha: String? = null

    private var loginJob: Job? = null
    private var captchaWaiter: CompletableDeferred<String>? = null

    /** 上课提醒的最近一次去重键（同一门课同一天同一节只提醒一次）。 */
    var lastClassReminderKey: String? = null

    /** 待办提醒的最近一次去重键（同一天的多条待办只提醒一次）。 */
    var lastTodoReminderKey: String? = null

    /** 考试提醒的最近一次去重键（提前一次、当天一次）。 */
    var lastExamReminderKey: String? = null

    /** 仅调试/验收：固定「现在」的时间，截图时不用真等十分钟。 */
    var reminderNowOverride: LocalDateTime? = null

    /** 轻提示（保存成功/导入失败等），由外壳统一渲染并自动消失。 */
    var notice by mutableStateOf<String?>(null)

    /**
     * 发现的新版本；null = 没有更新 / 还没查过。
     *
     * 只在 `versionCode` 比本机大时才会非空 —— 界面据此在左侧栏显示一条「新版本 · 去下载」，
     * 不做自动安装（Windows 上替换运行中的程序要外挂进程，坑大收益小）。
     */
    var updateAvailable by mutableStateOf<com.pocket.xiaoli.desktop.data.DesktopUpdate.Release?>(null)
        private set

    private var chatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    val activeChat: ChatSession?
        get() = chats.firstOrNull { it.id == activeChatId }

    val llmConfigured: Boolean get() = llm.isConfigured

    /**
     * 检查更新。
     *
     * - 自动路径（[force] = false）：每天最多一次，时间戳记在 `AppPrefs`，失败静默。
     * - 手动路径（[force] = true）：不看时间戳，由「关于」页或侧栏那条提示触发。
     *
     * 失败一律当成「暂时查不到」，不弹任何错误 —— 更新检查不该打断用户。
     */
    fun checkForUpdate(force: Boolean = false) {
        if (!force && !AppPrefs.shouldAutoCheckUpdate()) return
        scope.launch {
            val release = withContext(Dispatchers.IO) { runCatching { DesktopUpdate.check() }.getOrNull() }
            AppPrefs.markUpdateChecked()
            if (release != null && DesktopUpdate.isNewer(release.versionCode, DESKTOP_VERSION_CODE)) {
                updateAvailable = release
            }
        }
    }

    // ═══════════ 数据 ═══════════

    fun mutate(block: (AppData) -> AppData) {
        Store.update(block)
        data = Store.data
    }

    fun refresh() {
        data = Store.data
    }

    fun saveTerm(term: Term) {
        mutate { current ->
            val exists = current.terms.any { it.id == term.id }
            val terms = if (exists) current.terms.map { if (it.id == term.id) term else it } else current.terms + term
            current.copy(terms = terms, activeTermId = term.id, settings = current.settings.copy(activeTermId = term.id))
        }
    }

    fun saveCourse(course: Course) {
        mutate { current ->
            val exists = current.courses.any { it.id == course.id }
            current.copy(
                courses = if (exists) current.courses.map { if (it.id == course.id) course else it } else current.courses + course
            )
        }
    }

    fun deleteCourse(id: String) {
        mutate { current ->
            current.copy(
                origin = if (current.origin == "import") current.origin else "manual",
                courses = current.courses.filter { it.id != id },
            )
        }
    }

    fun saveTodo(todo: Todo) {
        mutate { current ->
            val exists = current.todos.any { it.id == todo.id }
            current.copy(todos = if (exists) current.todos.map { if (it.id == todo.id) todo else it } else current.todos + todo)
        }
    }

    fun toggleTodo(id: String) {
        mutate { current ->
            current.copy(todos = current.todos.map { if (it.id == id) it.copy(done = !it.done) else it })
        }
    }

    fun deleteTodo(id: String) {
        mutate { current -> current.copy(todos = current.todos.filter { it.id != id }) }
    }

    // ═══════════ 全局搜索 ═══════════

    fun openSearch() {
        searchQuery = ""
        showSearch = true
    }

    /**
     * 执行一条搜索结果。
     *
     * 这里集中决定「命中之后去哪个页、打开哪个弹窗」，搜索层与页面都不需要知道彼此。
     */
    fun activateSearch(hit: SearchHit) {
        showSearch = false
        when (hit.kind) {
            SearchKind.COURSE -> {
                tab = 1
                hit.course?.let { courseEditor = it }
            }
            SearchKind.TODO -> selectedTodo = hit.todo
            SearchKind.KNOWLEDGE -> {
                tab = 3
                campusSection = 4
                knowledgeQuery = hit.knowledgeQuery.orEmpty()
                knowledgeFocusId = hit.knowledgeId
            }
            SearchKind.CALENDAR -> {
                tab = 3
                campusSection = 3
                calendarFocusEpochDay = hit.calendarStartEpochDay
            }
            SearchKind.PAGE -> {
                tab = hit.page ?: 0
                hit.campusSection?.let { campusSection = it }
            }
        }
    }

    // ═══════════ 教务登录 ═══════════

    fun openLogin(purpose: LoginPurpose = LoginPurpose.IMPORT) {
        loginPurpose = purpose
        loginError = null
        loginProgress = ""
        loginCaptcha = null
        loginCaptchaCode = ""
        showLogin = true
    }

    fun closeLogin() {
        if (loginBusy) return
        showLogin = false
        loginCaptcha = null
        captchaWaiter = null
    }

    fun startLogin() {
        val user = loginUser.trim()
        val pass = loginPass
        if (user.isEmpty() || pass.isEmpty()) {
            loginError = "学号和密码都要填"
            return
        }
        val baseUrl = loginBaseUrlOverride?.takeIf { it.isNotBlank() }
            ?: CampusConfig.byId(data.settings.campusId)?.jwBaseUrl?.takeIf { it.isNotBlank() }
        if (baseUrl == null) {
            loginError = "当前校区没有配置教务地址"
            return
        }
        loginJob?.cancel()
        loginError = null
        loginCaptcha = null
        loginCaptchaCode = ""
        loginBusy = true
        loginProgress = "正在连接教务系统……"
        // 「我的 → 数据 → 导入诊断」开着时，把这一次抓取的原始请求/响应落盘（密码已打码、不记头）
        if (data.settings.importDiagnosticsEnabled) {
            ImportDiagnostics.begin(if (loginPurpose == LoginPurpose.IMPORT) "教务导入" else "成绩查询", baseUrl, user)
        }
        loginJob = scope.launch {
            val outcome = runCatching {
                AcademicLogin.login(
                    baseUrl = baseUrl,
                    username = user,
                    password = pass,
                    fetchSchedule = loginPurpose == LoginPurpose.IMPORT,
                    onProgress = { message -> scope.launch { loginProgress = message } },
                    onCaptcha = { captcha -> awaitCaptcha(captcha) },
                )
            }.getOrElse { error ->
                AcademicLoginOutcome(false, error.message ?: "登录失败，请稍后重试")
            }
            val logDir = ImportDiagnostics.end()
            loginBusy = false
            loginCaptcha = null
            captchaWaiter = null
            if (!outcome.success && logDir != null) {
                loginError = (outcome.error ?: "登录失败") + "（诊断已存到 ${logDir.absolutePath}）"
            }
            if (outcome.success) {
                academicSession = outcome.session
                showLogin = false
                if (loginPurpose == LoginPurpose.IMPORT) {
                    // 课表数量/来源可疑时停在确认框，不静默覆盖现有课表
                    routeImportAfterLogin(outcome)
                } else {
                    notify("登录成功，正在查询成绩…")
                    loadGrades(reloadTerms = true)
                }
            } else {
                loginError = outcome.error ?: "登录失败"
                loginProgress = ""
            }
        }
    }

    fun submitCaptcha() {
        val code = loginCaptchaCode.trim()
        if (code.isEmpty()) {
            loginError = "请输入验证码"
            return
        }
        loginError = null
        captchaWaiter?.complete(code)
    }

    fun refreshLoginCaptcha() {
        val current = loginCaptcha ?: return
        scope.launch {
            loginCaptcha = runCatching { current.refresh() }.getOrNull() ?: current
            loginCaptchaCode = ""
        }
    }

    private suspend fun awaitCaptcha(captcha: CaptchaResult): String = withContext(Dispatchers.Main) {
        loginCaptcha = captcha
        loginCaptchaCode = ""
        captchaWaiter = null
        val waiter = CompletableDeferred<String>()
        captchaWaiter = waiter
        loginProgress = "请输入验证码"
        loginAutoCaptcha?.takeIf { it.isNotBlank() }?.let { code ->
            scope.launch {
                delay(500)
                if (!waiter.isCompleted) waiter.complete(code)
            }
        }
        waiter.await()
    }

    // ═══════════ 成绩查询 ═══════════

    fun loadGrades(reloadTerms: Boolean = false) {
        val session = academicSession ?: run {
            // 有缓存就先给缓存（GradesSection 会显示"缓存于…"），没缓存才要求登录
            if (data.gradesCache.isEmpty()) gradesError = "先登录教务"
            return
        }
        if (gradesLoading) return
        gradesLoading = true
        gradesError = null
        scope.launch {
            try {
                if (reloadTerms || gradeTerms.isEmpty()) {
                    gradeTerms = session.fetchTerms()
                    if (gradeTermIndex !in gradeTerms.indices) gradeTermIndex = 0
                }
                if (gradeTerms.isEmpty()) {
                    throw IllegalStateException("没有可查询的学期，登录可能已失效")
                }
                val term = gradeTerms[gradeTermIndex]
                grades = session.fetchGrades(term.xnm, term.xqm)
                if (grades.isNotEmpty()) cacheGrades(term, grades)
            } catch (error: Exception) {
                gradesError = error.message ?: "查询失败"
                if (error.message?.contains("会话") == true) academicSession = null
            } finally {
                gradesLoading = false
            }
        }
    }

    fun selectGradeTerm(index: Int) {
        if (index == gradeTermIndex || index !in gradeTerms.indices) return
        gradeTermIndex = index
        loadGrades()
    }







    fun refreshGrades() {
        gradeTerms = emptyList()
        grades = emptyList()
        loadGrades(reloadTerms = true)
    }

    fun saveStudent(no: String, name: String, major: String, className: String, campusId: String) {
        mutate { current ->
            current.copy(
                settings = current.settings.copy(
                    studentNo = no,
                    studentName = name,
                    major = major,
                    className = className,
                    campusId = campusId,
                )
            )
        }
    }

    fun setSetting(block: (AppSettings) -> AppSettings) {
        mutate { current -> current.copy(settings = block(current.settings)) }
    }

    val currentWeek: Int get() = Store.currentWeek()

    fun displayedWeek(): Int = if (weekOverride > 0) weekOverride else currentWeek

    // ═══════════ AI 配置 ═══════════

    fun saveLlm(config: LlmConfig) {
        llm = config
        AppPrefs.saveLlm(config)
        data = Store.data
    }

    // ═══════════ 会话 ═══════════

    // 落盘细节在 :core 的 ChatStore（UI 层不做 IO）
    private fun persistChats() = ChatStore.save(chats)

    private fun loadChats(): List<ChatSession> = ChatStore.load()

    fun newChat() {
        val session = ChatSession(
            id = "chat_" + System.currentTimeMillis(),
            title = "新对话",
            messages = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
        chats = listOf(session) + chats
        activeChatId = session.id
        persistChats()
    }

    /**
     * 开发用（`--demo=markdown`）：塞一段长文样例，用来回归中文排版。
     *
     * 刻意覆盖四种容易出问题的情形：纯中文长段（标点悬挂）、中英混排（行首规则）、
     * 列表与表格（换行与对齐）、代码块（不换行溢出）。
     */
    fun seedMarkdownDemo() {
        val text = """
            ## 一、纯中文长段

            校园网接入说明：本学期起，图书馆四层的无线网络统一改为实名认证。同学们需要用学号加校园卡密码登录，首次登录请先在教学楼一层的自助机上激活账号；若提示「账号不存在」，请携带校园卡到信息中心办理，办理时间为工作日上午八点到十一点三十、下午一点三十到五点，节假日除外。账号激活后二十四小时内生效，若超过二十四小时仍无法登录，请拨打服务电话或在意见反馈里留言，我们会尽快排查（通常一个工作日内回复）。另外请注意：同一账号最多同时在线三台设备，超出后最早登录的设备会被强制下线，这个限制是为了保证带宽。

            ## 二、中英混排

            The library opens at 8:00 a.m. and closes at 22:30, 周末略有不同。如果你在 Windows 上遇到 `AccessDeniedException`，先检查杀毒软件的「受控文件夹访问」；在 macOS 上则看「完全磁盘访问权限」。API Key 只保存在本机，格式长得像 `sk-xxxx`（前缀不同厂商不一致），不会上传，也不写日志。

            ## 三、列表与表格

            - 第一步：在教学楼一层自助机激活账号
            - 第二步：等待二十四小时（期间可以先用访客网络）
            - 第三步：用学号登录，成功后建议改一次密码

            | 地点 | 开放时间 | 备注 |
            |---|---|---|
            | 图书馆四层 | 08:00 - 22:30 | 考试周延长到 23:30 |
            | 教学楼一层自助机 | 07:30 - 21:00 | 只办理激活 |
            | 信息中心 | 工作日 08:00 - 17:00 | 需带校园卡 |

            ## 四、代码块

            ```kotlin
            val term = Term(id = "t1", name = "2026-2027 学年 第一学期", startDate = "2026-08-31")
            val week = termWeekOf(term, LocalDate.now())
            println("第 " + week + " 周")
            ```
        """.trimIndent()

        val session = ChatSession(
            id = "demo_markdown",
            title = "排版样例",
            messages = listOf(
                ChatMessage(
                    id = "demo_markdown_1",
                    role = "assistant",
                    text = text,
                    at = System.currentTimeMillis(),
                )
            ),
            createdAt = System.currentTimeMillis(),
        )
        chats = listOf(session) + chats.filter { it.id != session.id }
        activeChatId = session.id
    }

    fun selectChat(id: String) {
        activeChatId = id
    }

    fun deleteChat(id: String) {
        chats = chats.filter { it.id != id }
        if (activeChatId == id) activeChatId = chats.firstOrNull()?.id
        if (chats.isEmpty()) newChat() else persistChats()
    }

    fun clearChats() {
        chats = emptyList()
        activeChatId = null
        persistChats()
    }

    private fun updateChat(chat: ChatSession) {
        chats = chats.map { if (it.id == chat.id) chat else it }
        persistChats()
    }

    private fun appendMessage(chatId: String, message: ChatMessage) {
        val chat = chats.firstOrNull { it.id == chatId } ?: return
        val updated = chat.copy(
            title = if (chat.messages.none { it.role == "user" } && message.role == "user") {
                message.text.take(18).ifBlank { "新对话" }
            } else {
                chat.title
            },
            messages = chat.messages + message,
        )
        updateChat(updated)
    }

    /** 发出当前草稿；[onUpdate] 每次内容变化后调用（界面用来滚到底部）。 */
    fun send(onUpdate: () -> Unit = {}) {
        val text = draft.trim()
        if (text.isEmpty() || streaming) return
        var chat = activeChat
        if (chat == null) {
            newChat()
            chat = activeChat ?: return
        }
        val chatId = chat.id
        draft = ""
        appendMessage(chatId, ChatMessage(id = "u_" + System.nanoTime(), role = "user", text = text, at = System.currentTimeMillis()))
        onUpdate()

        // 没配 Key 不等于问不了：校园事实题本机知识库就有，先答出来，再说怎么联网。
        if (!llm.isConfigured) {
            appendMessage(
                chatId,
                ChatMessage(id = "a_" + System.nanoTime(), role = "assistant", text = offlineAnswerFor(text), at = System.currentTimeMillis()),
            )
            onUpdate()
            return
        }

        val history = chats.firstOrNull { it.id == chatId }?.messages.orEmpty()
            .filter { !it.failed && it.text.isNotBlank() }
            .map { (if (it.role == "user") "user" else "assistant") to it.text }

        val tools = PocketTools.build()
        val assistantId = "a_" + System.nanoTime()
        appendMessage(chatId, ChatMessage(id = assistantId, role = "assistant", text = "", at = System.currentTimeMillis()))
        streaming = true
        onUpdate()

        chatJob = scope.launch {
            val client = LlmClient(llm, tools)
            val buffer = StringBuilder()
            val reasoning = StringBuilder()
            val traces = mutableListOf<ToolTrace>()
            var failure: String? = null

            fun flush() {
                val chatNow = chats.firstOrNull { it.id == chatId } ?: return
                val updated = chatNow.copy(
                    messages = chatNow.messages.map { message ->
                        if (message.id == assistantId) {
                            message.copy(
                                text = buffer.toString(),
                                reasoning = reasoning.toString(),
                                tools = traces.toList(),
                                failed = failure != null && buffer.isEmpty(),
                            )
                        } else {
                            message
                        }
                    }
                )
                updateChat(updated)
                onUpdate()
            }

            val result = withContext(Dispatchers.IO) {
                client.chat(PocketTools.SYSTEM_PROMPT, history) { event ->
                    when (event) {
                        is ChatEvent.Delta -> {
                            buffer.append(event.text)
                            scope.launch { flush() }
                        }
                        is ChatEvent.Reasoning -> {
                            reasoning.append(event.text)
                            scope.launch { flush() }
                        }
                        is ChatEvent.ToolStart -> {
                            traces.add(ToolTrace(name = event.name, label = event.label, done = false))
                            scope.launch { flush() }
                        }
                        is ChatEvent.ToolDone -> {
                            val index = traces.indexOfLast { it.name == event.name && !it.done }
                            if (index >= 0) {
                                traces[index] = traces[index].copy(summary = event.summary, done = true)
                            } else {
                                traces.add(ToolTrace(name = event.name, label = event.name, summary = event.summary, done = true))
                            }
                            scope.launch { flush() }
                        }
                        is ChatEvent.Failed -> {
                            failure = event.message
                            if (buffer.isEmpty()) {
                                buffer.append(event.message)
                            } else {
                                buffer.append("\n\n⚠️ ").append(event.message)
                            }
                            scope.launch { flush() }
                        }
                    }
                }
            }
            // 以最终结果为准：流式的最后一帧可能没赶上
            if (result.first.isNotBlank()) {
                buffer.clear()
                buffer.append(result.first)
            }
            if (result.second.isNotBlank()) {
                reasoning.clear()
                reasoning.append(result.second)
            }
            streaming = false
            flush()
        }
    }

    fun stopStreaming() {
        chatJob?.cancel()
        chatJob = null
        streaming = false
        val chat = activeChat ?: return
        updateChat(
            chat.copy(
                messages = chat.messages.map { message ->
                    if (message.role == "assistant" && message.text.isBlank()) message.copy(text = "（已停止）", failed = true) else message
                }
            )
        )
    }

    fun retryLast() {
        val chat = activeChat ?: return
        val lastUser = chat.messages.lastOrNull { it.role == "user" } ?: return
        draft = lastUser.text
        send()
    }

    /** 从知识库等页面跳到鲤鱼并带上问题。 */
    fun prefill(query: String) {
        draft = query
        tab = 2
    }

    fun openApiDialog() {
        showApiDialog = true
    }

    fun notify(message: String) {
        notice = message
    }

    // ═══════════ 导入导出 ═══════════

    fun importJson(json: String): Int {
        // 解析与校验放在 :core 的 Store（数据层），这里只负责刷新界面状态
        val imported = Store.parseImport(json)
        Store.replace(imported)
        data = Store.data
        return data.courses.size + data.todos.size
    }

    fun exportJson(): String = Store.exportJson()

    /**
     * 导出课表 ICS 到 `数据目录/exports/<name>`，返回给用户看的文案。
     *
     * 逻辑已在共享层（`buildIcs` → `expandIcsWeekEvents`），这里只负责落盘与话术；
     * 弹窗（`IcsExportDialog`）和无头 CLI（`--export-ics`）都走这一处，避免两份实现。
     */
    fun exportIcs(name: String = "课表.ics"): String {
        val term = data.activeTerm ?: return "没有可导出的课表（先加学期/课程）"
        val ics = com.pocket.xiaoli.desktop.logic.buildIcs(
            courses = data.activeCourses,
            term = term,
            slots = data.slots,
        )
        return runCatching {
            val dir = java.io.File(Store.dataDir, "exports").apply { mkdirs() }
            val file = java.io.File(dir, name.ifBlank { "课表.ics" })
            file.writeText(ics, Charsets.UTF_8)
            val count = ics.lines().count { it.startsWith("BEGIN:VEVENT") }
            "已导出：${file.absolutePath}（共 $count 节）"
        }.getOrElse { "导出失败：${it.message ?: "未知错误"}" }
    }

    companion object {
        fun today(): LocalDate = LocalDate.now()
    }
}
