package com.pocket.xiaoli.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.entity.HomeworkEntity
import com.pocket.xiaoli.data.entity.SchemeEntity
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.data.backup.BackupCrypto
import com.pocket.xiaoli.ui.profile.components.AboutDialog
import com.pocket.xiaoli.ui.profile.components.BackupPassphraseDialog
import com.pocket.xiaoli.ui.profile.components.ShareExportDialog
import com.pocket.xiaoli.ui.profile.components.ShareMenuDialog
import com.pocket.xiaoli.ui.service.ExpressPickupOverlay
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.timetable.components.SchemeManagerDialog
import com.pocket.xiaoli.util.TimetableImageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 父页/设置页请求的一次性面板（见 [ProfileDialogHost] 的 `pendingQuickDialog`）。
 *
 * 判据是**谁触发**：
 *
 * | 值 | 触发点 | 为什么走请求而不是开关 |
 * |---|---|---|
 * | [CLEAR_DATA] / [CHECK_UPDATE] / [ABOUT] | 设置页 | 设置页排在 host 之后，改不了它的 state |
 * | [EXPRESS_PICKUP] / [CARD_ACCOUNT] | 「我的」页服务格 | 这两个没有"面板里点完要跳走"的后续，开关留在父页只会多一份无人渲染的 State |
 *
 * 反过来，「当前校区」「改名」「提前提醒时间」这类**父页自己就有入口**的面板仍由父页持有开关
 * 并自己渲染：父页把设置页画在最后，这些框跟在它后面就能正确盖住它。
 *
 * ## ⚠️ 不要在父页与宿主里各留一份同名开关
 *
 * 同名两份 `showXxx` 是这次重构里最容易踩的坑：父页置位它自己那份、
 * 宿主读自己那份 —— **点了没反应，而且不报错**。所以判据只有一条：
 * **这个框的入口在哪一层，开关和渲染就都在那一层。**
 */
enum class ProfileQuickDialog {
    CLEAR_DATA,
    RESET_ALL_DATA,
    CHECK_UPDATE,
    ABOUT,
    EXPRESS_PICKUP,
    CARD_ACCOUNT,
    SCHEME_MANAGER,
    SHARE_MENU,
}

/**
 * 绑定信息页请求拉开的学习平台面板（见 [ProfileDialogHost] 的 `pendingLearningRequest`）。
 *
 * @param platform 目标平台（超星 / 智慧树）
 * @param returnToBinding 面板关掉后回「绑定信息」页 —— 用户是从那儿点进来的，
 *   不该被丢回「我的」页首页
 * @param sync true = 打开作业同步面板；false = 直接进该平台的网页登录页
 */
data class ProfileLearningRequest(
    val platform: LearningPlatform,
    val returnToBinding: Boolean = false,
    val sync: Boolean = false,
)

/**
 * 「我的」页的**临时面板**：所有弹窗 / 底部面板 / 确认框。
 *
 * ## 这个文件为什么存在
 *
 * 这些面板原先全部堆在 `ProfileScreen` 里，导致那个 Composable 长到 1700 行，
 * 而且职责含糊 —— 它同时是"路由"、"状态机"和"弹窗列表"。后果不是审美问题：
 * 改一个弹窗要在一屏装不下的范围里找它的 State、渲染分支和返回分支三处。
 *
 * 现在按**生命周期**切开：
 *
 * - **子页面**（会独占一屏、要参与返回层级）留在 `ProfileScreen`
 * - **临时面板**（弹一下就没、不需要独立返回层级）归到这里
 *
 * ## 与 ProfileScreen 的状态契约
 *
 * 本组件**自己持有** `showXxx` 这类面板开关；父页只通过两类回调介入：
 *
 * 1. `onOpenXxx` —— 父页要拉开某个面板（如身份卡的铅笔图标拉开改名框）
 * 2. `onNavigateXxx` —— 面板里点完要跳去别的东西（如检查更新里点"更新日志"）
 *
 * ## 学习平台（超星 / 智慧树）：点行直达网页登录，中间不再放面板
 *
 * 「绑定信息」页的学习平台卡里，每一行本身已经写清了平台、登录状态和动作
 * （"登录" / "换绑"），所以点它**直接进 `LearningWebLoginScreen`**。
 * 原先这里夹着一个 `LearningWebSheet`（标题 + Tab + 状态 + 「打开网页登录」按钮），
 * 它的每一项都与那两行重复，只是多让用户点一次 —— 2026-10-26 删掉。
 * 面板上唯一不重复的「清除登录状态」搬进了绑定页既有的「账号管理（危险操作）」组，
 * 与解绑教务共用同一个二次确认框。
 *
 * ⚠️ 登录态**只有一份**：由父页 `ProfileScreen` 从 `container` 读，本组件不再各存一份。
 * 保存或清除 Cookie 后必须调 [onLearningCookieChanged]，否则返回绑定页时那一行的点还是旧的。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialogHost(
    app: PocketXiaoliApplication,
    container: com.pocket.xiaoli.di.AppContainer,
    selectedCampus: CampusInfo,
    dormInfo: com.pocket.xiaoli.data.model.DormInfo?,
    schemes: List<SchemeEntity>,
    activeSchemeId: Long,
    allHomework: List<HomeworkEntity>,
    developerUnlocked: Boolean,
    /**
     * 父页（设置页）请求拉开某个「一次性确认框」。
     *
     * 为什么用"请求"而不是三个 `showXxx: Boolean` 参数：设置页在组合顺序上排在
     * **本组件之后**（它要盖住面板），所以它没法直接改本组件的内部 state。
     * 传三个布尔则要父页各自存一份开关、且关掉时还得回写 —— 一个信号 + 消费后清空
     * 更省事，也不会出现"父页以为还开着"的分叉状态。
     */
    pendingQuickDialog: ProfileQuickDialog? = null,
    onConsumeQuickDialog: () -> Unit = {},
    /** 绑定信息页请求拉开的学习平台面板 —— 见 [ProfileLearningRequest]。 */
    pendingLearningRequest: ProfileLearningRequest? = null,
    onConsumeLearningRequest: () -> Unit = {},
    /**
     * 学习平台登录态变了（保存 / 清除 Cookie）。
     *
     * 本组件不持登录态副本，改完 prefs 得让父页重读 —— 否则绑定页那一行的点还是旧的。
     */
    onLearningCookieChanged: () -> Unit = {},
    /**
     * 本组件是否有面板开着 —— 父页把它并进 `hasSubPage`（用于藏底栏）。
     *
     * ⚠️ 这是**单向汇报**（host → 父页），返回键不走这条路：见下面 `BackHandler` 的说明。
     */
    onVisibilityChange: (Boolean) -> Unit = {},
    /**
     * 「检查更新」—— 真正的检查与更新抽屉都在导航层的 `AppUpdatePromptController` 里。
     * 这里只转发，不再持有第二套下载 / 安装状态。
     */
    onCheckUpdate: () -> Unit = {},
    // ── 面板里点完要跳去别处 ──
    onOpenAccountBinding: () -> Unit,
    onOpenCampusCardPage: () -> Unit,
    onOpenChangelog: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }

    // ── 面板开关：归本组件所有 ──
    var showLearningImport by remember { mutableStateOf(false) }
    var showLearningManualImport by remember { mutableStateOf(false) }
    var showLearningWebLogin by remember { mutableStateOf(false) }
    var learningWebPlatform by remember { mutableStateOf(LearningPlatform.CHAOXING) }
    /** 学习类面板关掉后要不要回到「绑定信息」页（父页的子页面）*/
    var learningReturnToBinding by remember { mutableStateOf(false) }

    var showClearDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    var showShareExportDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    var showRestoreFullConfirm by remember { mutableStateOf(false) }
    /** 导出前设置口令 / 恢复时输入口令。 */
    var showExportPassphrase by remember { mutableStateOf(false) }
    var showRestorePassphrase by remember { mutableStateOf(false) }
    /** 导出流程里临时持有的口令，导出完成后立即丢弃（不落盘、不进 keystore）。 */
    var exportPassphrase by remember { mutableStateOf<String?>(null) }
    /** 已读入、待用口令解开的加密备份正文。 */
    var pendingRestoreText by remember { mutableStateOf<String?>(null) }
    var showSchemeManager by remember { mutableStateOf(false) }
    var showCardAccountDialog by remember { mutableStateOf(false) }
    var showExpressPickup by remember { mutableStateOf(false) }

    /** 让父页（「我的」页 / 设置页）能拉开一次性面板 —— 见参数说明。 */
    LaunchedEffect(pendingQuickDialog) {
        when (pendingQuickDialog) {
            ProfileQuickDialog.CLEAR_DATA -> showClearDialog = true
            ProfileQuickDialog.RESET_ALL_DATA -> showResetAllDialog = true
            ProfileQuickDialog.CHECK_UPDATE -> onCheckUpdate()
            ProfileQuickDialog.ABOUT -> showAboutDialog = true
            ProfileQuickDialog.EXPRESS_PICKUP -> showExpressPickup = true
            ProfileQuickDialog.CARD_ACCOUNT -> showCardAccountDialog = true
            ProfileQuickDialog.SCHEME_MANAGER -> showSchemeManager = true
            ProfileQuickDialog.SHARE_MENU -> showShareMenu = true
            null -> Unit
        }
        if (pendingQuickDialog != null) onConsumeQuickDialog()
    }

    /** 绑定信息页请求拉开的学习平台面板。语义与 [pendingQuickDialog] 完全一致。 */
    LaunchedEffect(pendingLearningRequest) {
        val req = pendingLearningRequest ?: return@LaunchedEffect
        learningReturnToBinding = req.returnToBinding
        learningWebPlatform = req.platform
        // `sync` 那条要的是"把作业搬进待办"；其余一律就是"我要登录" —— 直接进网页，不再过一层面板。
        if (req.sync) showLearningImport = true else showLearningWebLogin = true
        onConsumeLearningRequest()
    }

    // ═══════════════════════════════════════════
    //  返回键：本组件自己接管
    // ═══════════════════════════════════════════
    //
    // ⚠️ 为什么不是"把开关状态汇报给父页、由父页的 BackHandler 统一处理"：
    //
    // Compose 的 `BackHandler` 是**后注册先执行**（内层优先）。本组件由 `ProfileScreen`
    // 调用、且排在设置页之前，所以这里的 handler 天然比父页那个先拿到返回键 ——
    // 正好对应"弹窗浮在最上面，应该先关弹窗"。
    //
    // 反过来做（父页统一判断）会踩两个坑：
    // 1. 父页要拿得到本组件的开关状态，只能靠回调汇报 → 状态两份、且有一帧延迟；
    // 2. 设置页排在更后面、它自己也可能处理返回，"该谁先"就变成靠排列巧合来保证。
    //
    // [onVisibilityChange] 只用于藏底栏，与返回键无关。
    val anyPanelOpen = showLearningWebLogin ||
        showLearningImport || showLearningManualImport || showClearDialog ||
        showResetAllDialog ||
        showShareExportDialog ||
        showAboutDialog || showShareMenu || showRestoreFullConfirm || showSchemeManager ||
        showExportPassphrase || showRestorePassphrase ||
        showCardAccountDialog || showExpressPickup

    LaunchedEffect(anyPanelOpen) { onVisibilityChange(anyPanelOpen) }

    BackHandler(enabled = anyPanelOpen) {
        // 顺序 = 层叠顺序（最上面的先关）。一次只关一层，与系统返回键的直觉一致。
        when {
            showClearDialog -> showClearDialog = false
            showResetAllDialog -> showResetAllDialog = false
            showRestoreFullConfirm -> showRestoreFullConfirm = false
            // 口令框被返回键关掉时要一并丢掉暂存的密文，否则下次打开恢复会串上一次的文件。
            showRestorePassphrase -> { showRestorePassphrase = false; pendingRestoreText = null }
            showExportPassphrase -> showExportPassphrase = false
            showAboutDialog -> showAboutDialog = false
            showShareExportDialog -> showShareExportDialog = false
            showShareMenu -> showShareMenu = false
            showSchemeManager -> showSchemeManager = false
            showCardAccountDialog -> showCardAccountDialog = false
            showExpressPickup -> showExpressPickup = false
            showLearningWebLogin -> {
                showLearningWebLogin = false
                if (learningReturnToBinding) { learningReturnToBinding = false; onOpenAccountBinding() }
            }
            showLearningManualImport -> showLearningManualImport = false
            showLearningImport -> showLearningImport = false
        }
    }

    // ── 分享 / 备份的导出器 ──
    // 只在面板里被调用，所以跟着面板一起搬进来。父页不再需要持有这 5 个 launcher。
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                var failed = false
                withContext(Dispatchers.IO) {
                    try {
                        val courses = app.repository.allCourses.firstOrNull() ?: emptyList()
                        val json = gson.toJson(
                            com.pocket.xiaoli.data.model.CourseBackup(
                                exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                    .format(Date()),
                                courses = courses
                            )
                        )
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                    } catch (e: Exception) {
                        failed = true
                        android.util.Log.e("ProfileDialogHost", "导出失败: exportCourseBackup", e)
                    }
                }
                Toast.makeText(
                    context,
                    if (failed) "导出失败，请重试" else "备份已导出",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                var failed = false
                withContext(Dispatchers.IO) {
                    try {
                        val courses = app.repository.allCourses.firstOrNull() ?: emptyList()
                        val csv = buildString {
                            appendLine("课程名称,学分,教师,教室,星期,开始节次,结束节次,周类型,周数")
                            courses.forEach { c ->
                                appendLine("${c.name},${c.credits},${c.teacher},${c.room},${c.dayOfWeek},${c.startSlot},${c.endSlot},${c.weekType},${c.weeks}")
                            }
                        }
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(csv.toByteArray(Charsets.UTF_8))
                        }
                    } catch (e: Exception) {
                        failed = true
                        android.util.Log.e("ProfileDialogHost", "导出失败: exportCourseCsv", e)
                    }
                }
                Toast.makeText(
                    context,
                    if (failed) "导出失败，请重试" else "CSV已导出",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val icsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        uri?.let {
            scope.launch {
                var failed = false
                withContext(Dispatchers.IO) {
                    try {
                        val courses = app.repository
                            .getCoursesByScheme(container.activeSchemeId.value)
                            .first()
                        val slots = app.repository.allTimeSlots.first()
                        val ics = com.pocket.xiaoli.util.CourseIcsExporter.export(
                            courses = courses,
                            timeSlots = slots,
                            semesterStart = container.semesterStart.value,
                            totalWeeks = container.semesterWeeks.value,
                        )
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(ics.toByteArray(Charsets.UTF_8))
                        }
                    } catch (e: Exception) {
                        failed = true
                        android.util.Log.e("ProfileDialogHost", "导出失败: exportCourseIcs", e)
                    }
                }
                Toast.makeText(
                    context,
                    if (failed) "导出失败，请重试" else ".ics 已导出",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val fullBackupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                var failed = false
                withContext(Dispatchers.IO) {
                    try {
                        val json = com.pocket.xiaoli.ui.dev.DeveloperDataExport.databaseToJson(
                            app.database.openHelper.readableDatabase
                        )
                        // 导出的是一份**全量用户数据快照**（课表 / 成绩存档 / 待办 / 笔记 /
                        // 绑定过的教务账号标识）。明文落盘等于用户一同步到网盘就公开了，
                        // 所以这里必须加密（口令来自上一步的口令框，App 不保存它）。
                        val passphrase = exportPassphrase
                            ?: error("没有拿到备份口令，请重新导出")
                        val encrypted = com.pocket.xiaoli.data.backup.BackupCrypto.encrypt(
                            json,
                            passphrase.toCharArray(),
                        )
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(encrypted.toByteArray(Charsets.UTF_8))
                        }
                    } catch (e: Exception) {
                        failed = true
                        android.util.Log.e("ProfileDialogHost", "导出失败: exportFullBackup", e)
                    }
                }
                // 口令用完即弃：留在内存里没有收益，下一次导出必须重新输入。
                exportPassphrase = null
                Toast.makeText(
                    context,
                    if (failed) "导出失败，请重试" else "加密备份已导出（口令请自行保管）",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 真正执行恢复。
     *
     * ⚠️ `legacyPlaintext = true` 只可能来自**本 App 旧版本**导出的明文备份。
     * 仍然支持它，否则用户手上已有的备份会全部作废；但会明确告诉用户这份没加密。
     */
    val runRestore: (String, Boolean) -> Unit = { json, legacyPlaintext ->
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    com.pocket.xiaoli.data.database.DatabaseBackupRestorer.restore(
                        app.database.openHelper.writableDatabase,
                        json,
                    )
                }
            }
            result.onSuccess {
                Toast.makeText(
                    context,
                    if (legacyPlaintext) {
                        "旧版明文备份已恢复，即将重启（建议之后再导出一份加密备份）"
                    } else {
                        "完整备份已恢复，即将重启"
                    },
                    Toast.LENGTH_LONG
                ).show()
                restartApp(context)
            }.onFailure {
                Toast.makeText(
                    context,
                    "恢复失败：${it.message ?: "文件格式不正确"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val fullRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val read = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?.takeIf { it.isNotBlank() }
                            ?: error("备份文件为空")
                    }
                }
                read.onSuccess { body ->
                    if (com.pocket.xiaoli.data.backup.BackupCrypto.isEncryptedBackup(body)) {
                        // 加密备份：先收下文本，问出口令再解 —— 解密是纯 CPU 活，
                        // 放主线程会在 20 万次 PBKDF2 上卡住一帧以上。
                        pendingRestoreText = body
                        showRestorePassphrase = true
                    } else {
                        // 旧版明文备份：直接恢复，但明确告知没加密。
                        runRestore(body, true)
                    }
                }.onFailure {
                    Toast.makeText(
                        context,
                        "读取失败：${it.message ?: "文件不可读"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    //  面板
    // ═══════════════════════════════════════════

    if (showLearningWebLogin) {
        LearningWebLoginScreen(
            platform = learningWebPlatform,
            onSaveCookie = { cookie ->
                if (cookie.isBlank()) {
                    Toast.makeText(context, "未读取到登录状态，请先完成登录", Toast.LENGTH_LONG).show()
                } else {
                    container.saveLearningCookie(learningWebPlatform, cookie)
                    onLearningCookieChanged()
                    showLearningWebLogin = false
                    if (learningReturnToBinding) { learningReturnToBinding = false; onOpenAccountBinding() }
                    Toast.makeText(context, "${learningWebPlatform.label} 登录状态已保存", Toast.LENGTH_SHORT).show()
                }
            },
            onClose = {
                showLearningWebLogin = false
                if (learningReturnToBinding) { learningReturnToBinding = false; onOpenAccountBinding() }
            }
        )
    }

    if (showLearningImport) {
        LearningSyncDialog(
            platform = LearningPlatform.CHAOXING,
            cookie = container.loadLearningCookie(LearningPlatform.CHAOXING),
            existing = allHomework,
            onPersist = { list ->
                var added = 0
                list.forEach { item ->
                    app.repository.insertHomework(
                        HomeworkEntity(
                            courseName = item.courseName,
                            content = item.title,
                            dueDate = item.dueDate,
                            completed = item.completed,
                            schemeId = container.activeSchemeId.value
                        )
                    )
                    added++
                }
                added
            },
            onNeedLogin = {
                showLearningImport = false
                learningReturnToBinding = true
                learningWebPlatform = LearningPlatform.CHAOXING
                showLearningWebLogin = true
            },
            onOpenManualImport = {
                showLearningImport = false
                showLearningManualImport = true
            },
            onDismiss = { showLearningImport = false }
        )
    }

    if (showLearningManualImport) {
        LearningManualImportDialog(
            onConfirm = { type, text ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        lines.forEach { line ->
                            val parts = line.split(',', '|').map { it.trim() }
                            if (type == "exam" || type == "homework") {
                                val courseName = parts.getOrNull(0).orEmpty()
                                val title = parts.getOrNull(1).orEmpty().ifBlank {
                                    if (type == "exam") "考试" else "作业"
                                }
                                val dueDate = parts.getOrNull(2).orEmpty()
                                app.repository.insertHomework(
                                    HomeworkEntity(
                                        courseName = courseName,
                                        content = title,
                                        dueDate = dueDate,
                                        schemeId = container.activeSchemeId.value
                                    )
                                )
                            } else {
                                val title = parts.getOrNull(0).orEmpty().ifBlank { "收件箱消息" }
                                val content = parts.getOrNull(1).orEmpty()
                                app.repository.insertTask(
                                    TaskEntity(
                                        name = if (content.isBlank()) title else "$title · $content",
                                        schemeId = container.activeSchemeId.value
                                    )
                                )
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        showLearningManualImport = false
                        Toast.makeText(context, "已加入待办", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            // ⚠️ 关掉"手动导入"要关它自己。原实现在这里是 `showLearningImport = false`
            // （复制粘贴留下的错），只是因为它打开时 `showLearningImport` 已经是 false 才没暴露。
            onDismiss = { showLearningManualImport = false }
        )
    }

    if (showExpressPickup) {
        // 与「服务」页的「快递」入口**同一个弹窗**（`ExpressPickupDialog`）。
        // 曾经为这条路单独包过一个整页窗口形态，2026-10-18 需求方要求回到弹窗：一个
        // 只有两行平台入口的功能不该占满一屏，两条路径也就此合流成一个实现。
        ExpressPickupOverlay(onDismiss = { showExpressPickup = false })
    }

    // ── 清空所有课表数据（危险操作）──
    //
    // ⚠️ 这里曾经**没有渲染分支**：设置页的「清空所有课表数据」
    //（ProfileSettingsScreen · 危险操作）会把 showClearDialog 置 true，
    // 返回键与 anyPanelOpen 也都认这个 state，但全仓库没有一处把它画出来 ——
    // 表现就是"点了没反应"。2026-10 补上。
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有课表数据") },
            text = {
                Text(
                    "将删除所有课程与自建方案，只保留一张空的「默认课表」。\n" +
                        "此操作不可恢复，建议先在「我的 → 备份与分享」里导出备份。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // 先清掉所有课程（跨全部方案），再把"非默认"方案逐个级联删除
                            //（课程 / 待办 / 好物 / 树洞 / 考试会跟着走）。
                            app.repository.deleteAll()
                            app.repository.allSchemes.firstOrNull()
                                .orEmpty()
                                .filter { it.id != 0L }
                                .forEach { app.repository.deleteSchemeCascade(it.id) }
                        }
                        // 保留默认方案（id = 0）作为空壳。
                        // 它的存在由数据库种子保证（PocketXiaoliDatabase 里的
                        // `INSERT OR IGNORE INTO schemes (id, ...) VALUES (0, ...)`），
                        // 所以不必在这里新建 —— 但**必须**把 activeScheme 指回它，
                        // 否则 App 会停在"指向一个已删除方案"的状态。
                        container.setActiveScheme(0L, "默认课表")
                        showClearDialog = false
                        Toast.makeText(context, "已清空所有课表数据", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("清空", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }

    // ── 删除全部本地数据（危险操作 · 面向普通用户）──
    //
    // ⚠️ 这个入口原先**只存在于开发者实验室**（「我的 → 关于」连点 7 次解锁）。
    // 隐私政策承诺「用户数据可删除」，但普通用户找不到那个隐藏入口 = 承诺不成立。
    // 现在从「我的 → 设置 → 危险操作」直接可达（[SettingsSubPages.DangerContent]）。
    //
    // 与「清空所有课表数据」的区别：那个只删课程与自建方案，这个连账号凭据、Cookie、
    // 网络日志、崩溃日志一起删 —— 等价于卸载重装，但不用卸载。
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("删除全部本地数据") },
            text = {
                Text(
                    "将清空：课表与自建方案、成绩存档、待办与考试、好物清单、留声树洞、\n" +
                        "教务与校园卡等账号凭据、Cookie、网络日志、崩溃日志、全部设置。\n\n" +
                        "此操作立即生效、不可恢复，App 会随即重启。\n" +
                        "如果还想留一份，请先取消，去「我的 → 备份与分享」导出备份。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetAllDialog = false
                    scope.launch {
                        // resetAllData 走 IO（清库 + 扫 shared_prefs + 删外部文件）；
                        // clearWebCacheAndCookies 必须留在主线程（WebView 只能在主线程碰）。
                        withContext(Dispatchers.IO) { container.resetAllData() }
                        container.clearWebCacheAndCookies()
                        restartApp(context)
                    }
                }) { Text("全部删除并重启", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) { Text("取消") }
            },
        )
    }

    if (showShareExportDialog) {
        ShareExportDialog(
            gson = gson,
            app = app,
            onDismiss = { showShareExportDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            context = context,
            onDismiss = { showAboutDialog = false },
            // 已解锁时传 null：连点不再有任何反应
            onUnlockDeveloper = if (developerUnlocked) null else {
                {
                    container.unlockDeveloper()
                    Toast.makeText(
                        context,
                        // 路径必须与设置页里那个「高级」组一致。
                        // 这里曾经写的是「设置 → 开发者实验室」——**这条路径不存在**：
                        // 开发者实验室的入口当时只在「课表设置」页底部（解锁后才出现），
                        // 而「设置」页里从来没有它。用户照着提示找会找不到。
                        "开发者模式已开启：我的 → 设置 → 高级",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
        )
    }

    if (showShareMenu) {
        ShareMenuDialog(
            onDismiss = { showShareMenu = false },
            onGenerateCode = { showShareMenu = false; showShareExportDialog = true },
            onExportBackup = {
                showShareMenu = false
                backupExportLauncher.launch("课表备份_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.json")
            },
            onExportImage = {
                showShareMenu = false
                scope.launch {
                    val courses = withContext(Dispatchers.IO) {
                        app.repository.getCoursesByScheme(container.activeSchemeId.value).first()
                    }
                    val slots = withContext(Dispatchers.IO) { app.repository.allTimeSlots.first() }
                    val uri = withContext(Dispatchers.IO) {
                        TimetableImageExporter.export(context, courses, slots, container.autoCurrentWeek())
                    }
                    if (uri != null) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "分享课表"))
                    } else {
                        Toast.makeText(context, "导出失败,请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onExportCsv = {
                showShareMenu = false
                csvExportLauncher.launch("课表_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv")
            },
            onExportIcs = {
                showShareMenu = false
                icsExportLauncher.launch("课表_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.ics")
            },
            onExportFullBackup = {
                showShareMenu = false
                // 先要口令再选文件：CreateDocument 的返回走 ActivityResult，
                // 若反过来（先选文件再要口令）一旦用户在口令框上按返回，就会留下一个空文件。
                showExportPassphrase = true
            },
            onRestoreFullBackup = {
                showShareMenu = false
                showRestoreFullConfirm = true
            }
        )
    }

    if (showRestoreFullConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreFullConfirm = false },
            title = { Text("恢复完整备份") },
            text = { Text("将覆盖当前所有本地数据（课表、成绩存档、账号、设置等），并立即重启。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreFullConfirm = false
                    fullRestoreLauncher.launch(arrayOf("application/json"))
                }) { Text("选择备份文件") }
            },
            dismissButton = { TextButton(onClick = { showRestoreFullConfirm = false }) { Text("取消") } },
        )
    }

    if (showExportPassphrase) {
        BackupPassphraseDialog(
            title = "设置备份口令",
            helper = "备份里含课表、成绩存档、待办、笔记与账号标识，所以导出的是加密文件。" +
                "口令不保存在 App 里，忘了就解不开 —— 请自己存好。",
            confirmNeeded = true,
            onConfirm = { passphrase ->
                exportPassphrase = passphrase
                showExportPassphrase = false
                val stamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                fullBackupExportLauncher.launch("口袋小鲤_完整备份_$stamp.json")
            },
            onDismiss = { showExportPassphrase = false },
        )
    }

    if (showRestorePassphrase) {
        BackupPassphraseDialog(
            title = "输入备份口令",
            helper = "这是导出这份备份时设置的口令。App 不保存它。",
            confirmNeeded = false,
            onConfirm = { passphrase ->
                val envelope = pendingRestoreText
                showRestorePassphrase = false
                pendingRestoreText = null
                if (envelope == null) {
                    Toast.makeText(context, "没有待恢复的备份，请重新选择文件", Toast.LENGTH_LONG).show()
                } else {
                    scope.launch {
                        // PBKDF2 迭代 20 万次 + GCM 解密，放 IO 线程；口令错会在 decrypt 里抛，
                        // 这里统一翻成一句人话。
                        val plaintext = withContext(Dispatchers.IO) {
                            runCatching { BackupCrypto.decrypt(envelope, passphrase.toCharArray()) }
                        }
                        plaintext.onSuccess { runRestore(it, false) }.onFailure {
                            Toast.makeText(
                                context,
                                "恢复失败：${it.message ?: "口令不对，或文件已损坏"}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            },
            onDismiss = { showRestorePassphrase = false; pendingRestoreText = null },
        )
    }

    if (showSchemeManager) {
        SchemeManagerDialog(
            schemes = schemes,
            activeSchemeId = activeSchemeId,
            onSwitchScheme = { scheme ->
                container.setActiveScheme(scheme.id, scheme.name)
                showSchemeManager = false
            },
            onCreateScheme = { name ->
                scope.launch {
                    val id = app.repository.createScheme(name)
                    container.setActiveScheme(id, name)
                }
            },
            onRenameScheme = { scheme, newName ->
                scope.launch {
                    app.repository.updateScheme(scheme.copy(name = newName))
                    if (scheme.id == container.activeSchemeId.value) {
                        container.setActiveScheme(scheme.id, newName)
                    }
                }
            },
            onDeleteScheme = { scheme ->
                scope.launch {
                    app.repository.deleteSchemeCascade(scheme.id)
                    if (scheme.id == container.activeSchemeId.value) {
                        val def = app.repository.getSchemeById(0L)
                            ?: SchemeEntity(id = 0, name = "默认课表")
                        container.setActiveScheme(def.id, def.name)
                    }
                }
            },
            onDismiss = { showSchemeManager = false }
        )
    }
}

// ⚠️ 2026-10-14 仓库已落地：原名 PocketXiaoyanTimetable → … → koudaixiaoli。
// GitHub 仓库：https://github.com/LIULIN632/koudaixiaoli
private const val GITHUB_REPO_URL = "https://github.com/LIULIN632/koudaixiaoli"

/**
 * 恢复完整备份后重启进程。
 *
 * 与 `ProfileScreen` 里那份同名实现重复了 —— 这是本文件唯一一处刻意的重复：
 * 它只有 8 行，而把它提成公共工具要新建一个 util 文件（或往现有 util 里塞一个
 * 与"备份恢复"强绑定的函数）。**要合并的话，两处一起改**，
 * 位置建议 `util/CrashLogExporter.kt` 旁边。
 */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply {
            addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        }
    if (intent != null) context.startActivity(intent)
    android.os.Process.killProcess(android.os.Process.myPid())
}
