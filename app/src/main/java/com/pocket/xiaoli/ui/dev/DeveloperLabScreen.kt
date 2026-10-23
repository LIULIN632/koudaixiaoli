package com.pocket.xiaoli.ui.dev

import android.content.ClipData
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.network.NetworkLogStore
import com.pocket.xiaoli.network.ZhengfangCourseSelectDebug
import com.pocket.xiaoli.data.FeatureFlag
import com.pocket.xiaoli.data.MockScenario
import com.pocket.xiaoli.util.CourseImportOutcome
import com.pocket.xiaoli.util.DeepLinkDebug
import com.pocket.xiaoli.util.DeepLinkTarget
import com.pocket.xiaoli.util.DiagnosticInput
import com.pocket.xiaoli.util.importCoursesFromUri
import com.pocket.xiaoli.util.DiagnosticReport
import com.pocket.xiaoli.ui.map3d.IndoorFloorPlanPreviewScreen
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pocket.xiaoli.ui.theme.LocalSkin
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 开发者实验室。
 *
 * ## 为什么默认完全隐藏
 * 这些是排障与试验用的能力，普通用户看到只会困惑。所以入口藏在
 * 「关于 → 连点版本号 7 次」，解锁后才会在「我的」里出现。
 *
 * ## 当前包含
 * - 诊断报告（自动脱敏 + 一键复制）
 * - 网络日志（内存环形缓冲 + 复制 CURL）
 * - Room 数据查看器（只读表列表 / JSON 预览 / 导出）
 * - 室内平面图预览（4F 拓扑草案，未实测；验证点房间 → 示意路线）
 * - Mock 数据模式（首页今天有课 / 无课 / 考试周 / 网络异常预览）
 * - 数据导入导出（全库 JSON / 课程 CSV / 课程 ICS / 导入 JSON / CSV）
 * - Deep Link 调试（输入 pocketxiaoli://... 直接路由）
 * - 清除缓存 / 重置 App（重置后自动重启进程）
 * - 当前数据量 / 功能开关
 * - 关闭开发者模式
 *
 * 所有能力默认隐藏，仅用于本地排障；「重置 App」会清空本地数据并自动重启。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperLabScreen(
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val container = app.appContainer
    val scope = rememberCoroutineScope()

    val schemeId by container.activeSchemeId.collectAsStateWithLifecycle()
    // 统一用 WithLifecycle 版本：这些 Flow 里除了 roomCells/customPois 是 StateFlow，
    // 其余都是 Room 生成的冷 Flow，写在 collectAsState 参数里会每次重组重新注册观察者。
    val courses by container.repository.allCourses.collectAsStateWithLifecycle(initialValue = emptyList())
    val schemes by container.repository.allSchemes.collectAsStateWithLifecycle(initialValue = emptyList())
    val timeSlots by container.repository.allTimeSlots.collectAsStateWithLifecycle(initialValue = emptyList())
    val homework by container.repository.getAllHomework()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by container.repository.getTasksByScheme(schemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val savedGrades by container.savedGradeDao.getAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val roomCells by container.roomCells.collectAsStateWithLifecycle()
    val pois by container.customPois.collectAsStateWithLifecycle()
    val flags by container.featureFlags.collectAsStateWithLifecycle()
    val mockScenario by container.mockScenario.collectAsStateWithLifecycle()
    val semesterStart by container.semesterStart.collectAsStateWithLifecycle()
    val semesterWeeks by container.semesterWeeks.collectAsStateWithLifecycle()
    val networkLogs by NetworkLogStore.entries.collectAsStateWithLifecycle()
    // 选课接口的原始往来：选课列表"空但没报错"时，只有这份原文能定位是哪一步不对
    val courseSelectDebug by ZhengfangCourseSelectDebug.entries.collectAsStateWithLifecycle()

    var report by remember { mutableStateOf<String?>(null) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var showNetworkLog by remember { mutableStateOf(false) }
    var showCourseSelectDebug by remember { mutableStateOf(false) }
    var showDataViewer by remember { mutableStateOf(false) }
    var showIndoorPreview by remember { mutableStateOf(false) }
    var deepLinkInput by remember { mutableStateOf("pocketxiaoli://timetable") }
    var showResetConfirm by remember { mutableStateOf(false) }

    val importDataLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val type = context.contentResolver.getType(uri)
                    ?.takeIf { it.contains("csv", ignoreCase = true) }
                    ?.let { "csv" }
                    ?: "backup"
                val outcome = importCoursesFromUri(
                    context = context,
                    repository = container.repository,
                    uri = uri,
                    type = type,
                    schemeId = container.activeSchemeId.value,
                )
                val message = when (outcome) {
                    is CourseImportOutcome.Success -> "导入完成：新增 ${outcome.added} 门课程"
                    CourseImportOutcome.Empty -> "文件中没有可导入的课程"
                    CourseImportOutcome.AllDuplicates -> "课程均已存在，未新增"
                    is CourseImportOutcome.Failure -> "导入失败：${outcome.message ?: "未知错误"}"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun buildReport(): String {
        val pkg = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val dbVersion = runCatching {
            app.database.openHelper.readableDatabase.version
        }.getOrDefault(-1)
        return DiagnosticReport.build(
            DiagnosticInput(
                appVersionName = pkg?.versionName ?: "未知",
                appVersionCode = pkg?.longVersionCode?.toInt() ?: 0,
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                deviceModel = Build.MANUFACTURER + " " + Build.MODEL,
                dbVersion = dbVersion,
                courseCount = courses.size,
                schemeCount = schemes.size,
                timeSlotCount = timeSlots.size,
                homeworkCount = homework.size,
                taskCount = tasks.size,
                savedGradeCount = savedGrades.size,
                roomCount = roomCells.size,
                poiCount = pois.size,
                recentError = app.crashHandler.getLatestCrashLog()?.readText()?.takeIf { it.isNotBlank() },
            )
        )
    }

    fun copyToClipboard(text: String, label: String = "内容") {
        val ok = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (ok) "$label 已复制" else "复制失败",
            Toast.LENGTH_SHORT
        ).show()
    }

    if (showDataViewer) {
        RoomDataViewerScreen(
            database = app.database,
            onBack = { showDataViewer = false },
        )
        return
    }

    if (showIndoorPreview) {
        IndoorFloorPlanPreviewScreen(onBack = { showIndoorPreview = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者实验室", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            Spacer(Modifier.height(skin.spaceXs))

            DevCard(title = "诊断报告") {
                Text(
                    "含版本 / 设备 / 数据量 / 最近错误；生成时自动脱敏（学号等长数字、文件路径、邮箱）。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceSm))
                Button(onClick = { report = buildReport() }) { Text("生成并查看") }
            }

            DevCard(title = "网络日志") {
                Text(
                    "已记录 ${networkLogs.size} 条（仅内存，最多 ${NetworkLogStore.MAX_ENTRIES} 条，重启即清空）。" +
                        "复制时 ticket / token 等敏感参数会自动打码。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    Button(onClick = { showNetworkLog = true }) { Text("查看") }
                    TextButton(onClick = { NetworkLogStore.clear() }) { Text("清空") }
                }
            }

            // ── 选课原始响应 ──
            // 为什么单独一张卡，而不是并进上面的网络日志：
            // `NetworkLogStore` 只记 URL 与字节数（**刻意不记请求体/响应体**，怕凭据落盘），
            // 而选课这件事出问题时的表现是「登录成功、列表空、不报错」——
            // 定位它需要的恰好是**响应原文**（容器名/字段名到底叫什么）。两者用途不重叠。
            DevCard(title = "选课原始响应") {
                val suspiciousCount = courseSelectDebug.count { it.suspicious }
                Text(
                    text = if (courseSelectDebug.isEmpty()) {
                        "还没有记录。去「服务 → 选课」查询一次课程，回来就能看到请求体与响应原文。"
                    } else {
                        "已记录 ${courseSelectDebug.size} 次查询（仅内存，最多 ${ZhengfangCourseSelectDebug.MAX_ENTRIES} 次，重启即清空）。" +
                            if (suspiciousCount > 0) {
                                "⚠️ 其中 $suspiciousCount 次「拿到了响应但没解析出课程」—— 这通常意味着字段名或容器名与预期不符。"
                            } else {
                                "复制时学号 / 姓名等身份字段的值会自动打码。"
                            }
                    },
                    fontSize = 12.sp,
                    color = if (suspiciousCount > 0) colors.warning else colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    Button(
                        onClick = { showCourseSelectDebug = true },
                        enabled = courseSelectDebug.isNotEmpty(),
                    ) { Text("查看原文") }
                    TextButton(
                        onClick = {
                            copyToClipboard(ZhengfangCourseSelectDebug.exportText(), "选课原始响应")
                        },
                        enabled = courseSelectDebug.isNotEmpty(),
                    ) { Text("复制") }
                    TextButton(onClick = { ZhengfangCourseSelectDebug.clear() }) { Text("清空") }
                }
            }

            DevCard(title = "Deep Link 调试") {
                Text(
                    "输入 URI，解析后可直接打开；pocketxiaoli://... 会回到 App 内部路由。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceSm))
                OutlinedTextField(
                    value = deepLinkInput,
                    onValueChange = { deepLinkInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Deep Link URI", fontSize = 12.sp) },
                )
                val target = remember(deepLinkInput) { DeepLinkDebug.resolve(deepLinkInput) }
                Spacer(Modifier.height(skin.spaceSmall))
                Text(
                    text = DeepLinkDebug.describe(target),
                    fontSize = 11.sp,
                    color = if (target is DeepLinkTarget.Invalid) colors.danger else colors.textSecondary,
                )
                Spacer(Modifier.height(skin.spaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    Button(
                        enabled = target !is DeepLinkTarget.Invalid,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkInput))
                            if (target is DeepLinkTarget.Route) {
                                intent.setPackage(context.packageName)
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    Toast.makeText(context, "没有可打开该链接的应用", Toast.LENGTH_SHORT).show()
                                }
                        },
                    ) { Text("打开") }
                    TextButton(onClick = { copyToClipboard(deepLinkInput, "Deep Link") }) { Text("复制") }
                }
            }

            DevCard(title = "Room 数据查看器") {
                Text(
                    "只读浏览 Room 表：表列表 + 行数统计 + JSON 预览，可复制当前页或导出整表全部行。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceSm))
                Button(onClick = { showDataViewer = true }) { Text("打开查看器") }
            }

            DevCard(title = "室内平面图预览（内部）") {
                Text(
                    "按 4F 指引牌整理的拓扑草案：37 节点 / 36 边，验证「点房间 → 高亮 → 示意路线」。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    "未实测、未按比例；路线不报米数，起点只敢画到 4F 东北楼梯口。不面向普通用户。",
                    fontSize = 11.sp,
                    color = colors.warning,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(skin.spaceSm))
                Button(onClick = { showIndoorPreview = true }) { Text("打开拓扑预览") }
            }

            DevCard(title = "数据导入导出") {
                Text(
                    "开发者工具：全库 JSON / 课程 CSV / 课程 ICS 导出；支持 JSON 备份与 CSV 导入。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val json = withContext(Dispatchers.IO) {
                                    DeveloperDataExport.databaseToJson(app.database.openHelper.readableDatabase)
                                }
                                copyToClipboard(json, "全库 JSON")
                            }
                        },
                    ) { Text("全库 JSON", fontSize = 12.sp) }
                    TextButton(
                        onClick = { copyToClipboard(DeveloperDataExport.coursesToCsv(courses), "课程 CSV") },
                    ) { Text("课程 CSV", fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    TextButton(
                        onClick = {
                            copyToClipboard(
                                DeveloperDataExport.coursesToIcs(courses, timeSlots, semesterStart, semesterWeeks),
                                "课程 ICS",
                            )
                        },
                    ) { Text("课程 ICS", fontSize = 12.sp) }
                    TextButton(
                        onClick = {
                            importDataLauncher.launch(
                                arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/*")
                            )
                        },
                    ) { Text("导入 JSON / CSV", fontSize = 12.sp) }
                }
            }

            DevCard(title = "Mock 数据模式") {
                Text(
                    "仅影响首页展示，不写入真实数据。当前：${mockScenario.label}",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceXs))
                MockScenario.entries.forEach { scenario ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { container.setMockScenario(scenario) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = scenario == mockScenario,
                            onClick = { container.setMockScenario(scenario) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(scenario.label, fontSize = 13.sp, color = colors.textPrimary)
                            Text(scenario.description, fontSize = 11.sp, color = colors.textTertiary)
                        }
                    }
                }
            }

            DevCard(title = "当前数据量") {
                val rows = listOf(
                    "课程" to courses.size,
                    "课表方案" to schemes.size,
                    "时间段" to timeSlots.size,
                    "待办作业" to homework.size,
                    "任务" to tasks.size,
                    "成绩存档" to savedGrades.size,
                    "教室" to roomCells.size,
                    "POI" to pois.size,
                )
                rows.forEach { (label, count) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(label, fontSize = 13.sp, color = colors.textSecondary, modifier = Modifier.weight(1f))
                        Text("$count", fontSize = 13.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            DevCard(title = "功能开关") {
                FeatureFlag.entries.forEach { flag ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(flag.label, fontSize = 14.sp, color = colors.textPrimary)
                            Text(flag.description, fontSize = 11.sp, color = colors.textTertiary)
                        }
                        Switch(
                            checked = flag.key in flags,
                            onCheckedChange = { container.setFlag(flag, it) },
                        )
                    }
                }
            }

            DevCard(title = "清除缓存 / 重置 App") {
                Text(
                    "清理网页缓存与登录态，或清空所有本地数据并重启。重置不可恢复。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceXs))
                TextButton(
                    onClick = {
                        container.clearWebCacheAndCookies()
                        Toast.makeText(context, "已清除缓存与网页登录态", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("清除缓存 / 登录态", fontSize = 12.sp) }
                TextButton(
                    onClick = { showResetConfirm = true },
                ) { Text("重置 App", fontSize = 12.sp, color = colors.danger) }
            }

            DevCard(title = "开发者模式") {
                Text(
                    "关闭后，「我的」里的开发者入口会重新隐藏（数据不受影响）。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceSm))
                TextButton(onClick = { showLockConfirm = true }) {
                    Text("关闭开发者模式", color = colors.danger)
                }
            }

            Spacer(Modifier.height(skin.spaceXxl))
        }
    }

    if (showNetworkLog) {
        val logText = remember(networkLogs) {
            buildString {
                appendLine("── 网络日志（${networkLogs.size} 条，最新在前）──")
                appendLine("（ticket / token 等敏感参数已打码）")
                networkLogs.forEach { e ->
                    val status = e.status?.toString() ?: "ERR"
                    appendLine("${e.method} $status ${e.durationMs}ms")
                    appendLine("  ${NetworkLogStore.redactUrl(e.url)}")
                    e.error?.let { appendLine("  ! $it") }
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showNetworkLog = false },
            title = { Text("网络日志（${networkLogs.size}）") },
            text = {
                if (networkLogs.isEmpty()) {
                    Text("还没有记录。去用一下教务导入或服务跳转，再回来看。", fontSize = 12.sp)
                } else {
                    LazyColumn {
                        items(networkLogs, key = { it.id }) { e ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceXs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${e.method}  ${e.status ?: "ERR"}  ${e.durationMs}ms",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = NetworkLogStore.redactUrl(e.url),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.textSecondary,
                                    )
                                    e.error?.let {
                                        Text(it, fontSize = 10.sp, color = colors.danger)
                                    }
                                }
                                TextButton(
                                    onClick = { copyToClipboard(NetworkLogStore.toCurl(e), "cURL") },
                                ) { Text("CURL", fontSize = 10.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { copyToClipboard(logText, "网络日志"); showNetworkLog = false },
                    enabled = networkLogs.isNotEmpty(),
                ) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { showNetworkLog = false }) { Text("关闭") } },
        )
    }

    if (showCourseSelectDebug) {
        // 原文按需重算：`collectAsStateWithLifecycle` 已经保证 entries 变了才重组，
        // 每次重组算一遍导出文本没有额外代价，且不必把 entries 再传一层。
        val exportText = remember(courseSelectDebug) { ZhengfangCourseSelectDebug.exportText() }
        AlertDialog(
            onDismissRequest = { showCourseSelectDebug = false },
            title = { Text("选课原始响应（${courseSelectDebug.size}）") },
            text = {
                if (courseSelectDebug.isEmpty()) {
                    Text("还没有记录。去「服务 → 选课」查询一次课程，再回来看。", fontSize = 12.sp)
                } else {
                    LazyColumn {
                        items(courseSelectDebug, key = { it.atMillis.toString() + it.label }) { e ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceXs)) {
                                Text(
                                    text = "【${e.label}】解析出 ${e.parsedCount} 条 / 服务端声明 ${e.totalResult} 条",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (e.suspicious) colors.warning else colors.textPrimary,
                                )
                                Text(
                                    text = "请求体：${e.requestBody}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.textSecondary,
                                    maxLines = 6,
                                )
                                Text(
                                    text = "响应原文开头：\n" + ZhengfangCourseSelectDebug
                                        .redactIdentityFields(e.responseText)
                                        .take(1200),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(skin.spaceXs))
                                TextButton(
                                    onClick = {
                                        copyToClipboard(
                                            ZhengfangCourseSelectDebug.exportText(),
                                            "选课原始响应",
                                        )
                                    },
                                ) { Text("复制全部", fontSize = 10.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { copyToClipboard(exportText, "选课原始响应"); showCourseSelectDebug = false },
                    enabled = courseSelectDebug.isNotEmpty(),
                ) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { showCourseSelectDebug = false }) { Text("关闭") } },
        )
    }

    report?.let { text ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text("诊断报告") },            text = {
                Text(
                    text = text,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                Button(onClick = { copyToClipboard(text, "诊断报告"); report = null }) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { report = null }) { Text("关闭") } },
        )
    }

    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            title = { Text("关闭开发者模式") },
            text = { Text("确定要隐藏开发者入口吗？") },
            confirmButton = {
                TextButton(onClick = {
                    container.lockDeveloper()
                    showLockConfirm = false
                    onBack()
                }) { Text("关闭") }
            },
            dismissButton = { TextButton(onClick = { showLockConfirm = false }) { Text("取消") } },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置 App") },
            text = { Text("将清空所有本地数据：课表、成绩存档、账号凭据、Cookie、缓存、网络日志。重置后立即重启，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) { container.resetAllData() }
                        container.clearWebCacheAndCookies()
                        restartApp(context)
                    }
                }) { Text("清空并重启", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun DevCard(title: String, content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceCompact)
                .clickable(enabled = false) {},
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceSm))
            content()
        }
    }
}












private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    if (intent != null) context.startActivity(intent)
    android.os.Process.killProcess(android.os.Process.myPid())
}
