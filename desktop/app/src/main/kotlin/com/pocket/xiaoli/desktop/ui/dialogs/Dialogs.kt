package com.pocket.xiaoli.desktop.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.ai.LlmConfig
import com.pocket.xiaoli.desktop.logic.buildIcs
import com.pocket.xiaoli.desktop.logic.suggestValues
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.state.cancelPendingImport
import com.pocket.xiaoli.desktop.state.confirmPendingImport
import java.io.File
import com.pocket.xiaoli.desktop.data.DefaultTimeSlots
import com.pocket.xiaoli.desktop.data.NewEntity
import com.pocket.xiaoli.desktop.data.Store
import com.pocket.xiaoli.desktop.data.Term
import com.pocket.xiaoli.desktop.data.Todo
import com.pocket.xiaoli.desktop.data.parseClock
import com.pocket.xiaoli.desktop.ui.components.ConfirmDialog
import com.pocket.xiaoli.desktop.ui.components.DividerLine
import com.pocket.xiaoli.desktop.ui.components.GhostIconButton
import com.pocket.xiaoli.desktop.ui.components.PocketModal
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.Segmented
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import com.pocket.xiaoli.desktop.ui.util.Format
import com.pocket.xiaoli.desktop.ui.util.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 一行「标签 + 输入框」，表单里到处都是。 */
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    val colors = pocketColors()
    Column(modifier) {
        Text(label, style = PocketTypography.label, color = colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        PocketTextField(value = value, onValueChange = onChange, placeholder = placeholder, password = password)
    }
}

// ═══════════════════════ 学期 ═══════════════════════

@Composable
fun TermDialog(state: AppState) {
    val colors = pocketColors()
    var editingId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(state.data.terms.isEmpty()) }
    val editing = state.data.terms.firstOrNull { it.id == editingId }
    var name by remember(editingId) { mutableStateOf(editing?.name ?: "") }
    var startDate by remember(editingId) { mutableStateOf(editing?.startDate ?: LocalDate.now().let { "${it.year}-${"%02d".format(it.monthValue)}-${"%02d".format(it.dayOfMonth)}" }) }
    var totalWeeks by remember(editingId) { mutableStateOf((editing?.totalWeeks ?: 20).toString()) }
    var error by remember(editingId) { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Term?>(null) }

    PocketModal(
        title = "学期设置",
        subtitle = "周次 = 从开始日算起；课表按它显示第几周",
        onDismiss = { state.showTermDialog = false },
        width = 520.dp,
        footer = {
            if (!creating) {
                PrimaryButton("新建学期", icon = PocketIcons.Plus) {
                    creating = true
                    editingId = null
                    name = ""
                    totalWeeks = "20"
                }
                Spacer(Modifier.weight(1f))
                SecondaryButton("关闭") { state.showTermDialog = false }
            } else {
                Spacer(Modifier.weight(1f))
                SecondaryButton("返回列表") {
                    creating = false
                    editingId = null
                    error = null
                }
                Spacer(Modifier.width(10.dp))
                PrimaryButton("保存学期") {
                    val date = runCatching { LocalDate.parse(startDate.trim()) }.getOrNull()
                    val weeks = totalWeeks.trim().toIntOrNull()
                    when {
                        name.isBlank() -> error = "学期名不能为空"
                        date == null -> error = "开始日期要写成 2026-08-31"
                        weeks == null || weeks !in 1..40 -> error = "周数填 1~40"
                        else -> {
                            val term = Term(
                                id = editingId ?: "term_" + System.currentTimeMillis(),
                                name = name.trim(),
                                startDate = Format.ymd(date),
                                totalWeeks = weeks,
                                sortOrder = state.data.terms.size,
                            )
                            state.saveTerm(term)
                            state.notify("学期已保存：${term.displayName}")
                            creating = false
                            editingId = null
                            error = null
                        }
                    }
                }
            }
        },
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (creating) {
                Field("学期名", name, { name = it }, "如：2026-2027 第一学期")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("开始日期（周一）", startDate, { startDate = it }, "2026-08-31", Modifier.weight(1f))
                    Field("总周数", totalWeeks, { totalWeeks = it }, "20", Modifier.width(100.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(16, 18, 20, 22).forEach { weeks ->
                        TagChip("$weeks 周", color = if (totalWeeks == "$weeks") colors.textPrimary else colors.textSecondary, onClick = { totalWeeks = "$weeks" })
                    }
                }
                if (error != null) Text(error!!, style = PocketTypography.caption, color = colors.danger)
            } else {
                state.data.terms.forEach { term ->
                    val active = term.id == state.data.activeTerm?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.chip))
                            .background(if (active) accentSoftColor() else colors.surfaceAlt.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(term.displayName, style = PocketTypography.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
                            Text("${term.startDate} 起 · ${term.totalWeeks} 周", style = PocketTypography.label, color = colors.textTertiary)
                        }
                        if (active) {
                            TagChip("当前", color = accentColor(), background = accentSoftColor())
                        } else {
                            SecondaryButton("设为当前") { state.saveTerm(term) }
                        }
                        Spacer(Modifier.width(6.dp))
                        GhostIconButton(PocketIcons.Edit, contentDescription = "编辑", size = 26.dp) {
                            editingId = term.id
                            creating = true
                            name = term.name
                            startDate = term.startDate
                            totalWeeks = term.totalWeeks.toString()
                        }
                        GhostIconButton(PocketIcons.Trash, contentDescription = "删除", size = 26.dp) {
                            pendingDelete = term
                        }
                    }
                }
                if (state.data.terms.isEmpty()) {
                    Text("还没有学期，点「新建学期」开始。", style = PocketTypography.caption, color = colors.textTertiary)
                }
                DividerLine()
                Text(
                    "为什么必须填开始日期：周次是按「开始日 + 7×(周−1)」算的，跟系统日历无关。",
                    style = PocketTypography.label,
                    color = colors.textTertiary,
                )
            }
        }
    }

    // 删除学期要二次确认（Android 端硬约束，桌面端照搬）
    pendingDelete?.let { term ->
        ConfirmDialog(
            title = "删除学期",
            message = "「${term.displayName}」里的课程记录会保留，但不再属于任何学期。确定删除？",
            onConfirm = {
                state.mutate { current ->
                    current.copy(
                        terms = current.terms.filter { it.id != term.id },
                        activeTermId = if (current.activeTermId == term.id) "" else current.activeTermId,
                    )
                }
                pendingDelete = null
                state.notify("已删除学期「${term.displayName}」（课程记录保留）")
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

// ═══════════════════════ 学生信息 ═══════════════════════

@Composable
fun StudentDialog(state: AppState) {
    val colors = pocketColors()
    val settings = state.data.settings
    var no by remember { mutableStateOf(settings.studentNo) }
    var name by remember { mutableStateOf(settings.studentName) }
    var major by remember { mutableStateOf(settings.major) }
    var className by remember { mutableStateOf(settings.className) }
    var campus by remember { mutableStateOf(settings.campusId) }

    PocketModal(
        title = "学生信息",
        subtitle = "只保存在本机；页面上展示时会自动脱敏学号",
        onDismiss = { state.showStudentDialog = false },
        footer = {
            Spacer(Modifier.weight(1f))
            SecondaryButton("取消") { state.showStudentDialog = false }
            Spacer(Modifier.width(10.dp))
            PrimaryButton("保存") {
                state.saveStudent(no.trim(), name.trim(), major.trim(), className.trim(), campus.trim())
                state.showStudentDialog = false
                state.notify("学生信息已保存")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("学号", no, { no = it }, "如 20230001", Modifier.weight(1f))
                Field("姓名", name, { name = it }, "如 张三", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("专业", major, { major = it }, "如 计算机科学与技术", Modifier.weight(1f))
                Field("班级", className, { className = it }, "如 计科 2301", Modifier.weight(1f))
            }
            Field("校区", campus, { campus = it }, "ytlg_main")
            Text("提示：学号在页面上显示为 ${Format.maskStudentNo(no.ifBlank { "20230001" })}", style = PocketTypography.label, color = colors.textTertiary)
        }
    }
}

// ═══════════════════════ AI 配置 ═══════════════════════

@Composable
fun ApiConfigDialog(state: AppState) {
    val colors = pocketColors()
    val config = state.llm
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var model by remember { mutableStateOf(config.model) }
    var temperature by remember { mutableStateOf(config.temperature.toString()) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun draft() = LlmConfig(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        temperature = temperature.trim().toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.3,
    )

    PocketModal(
        title = "AI 配置",
        subtitle = "任填 Base URL / API Key / 模型 三项即可启用联网问答",
        onDismiss = { state.showApiDialog = false },
        width = 720.dp,
        footer = {
            SecondaryButton(if (testing) "测试中…" else "测试连接", icon = PocketIcons.Refresh) {
                if (!testing) {
                    testing = true
                    testResult = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { probe(draft()) }
                        testResult = result
                        // 测试结果在可滚动内容区里，滚动位置不对就看不见；顺手用底部气泡再说一遍
                        state.notify(result)
                        testing = false
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("取消") { state.showApiDialog = false }
            Spacer(Modifier.width(10.dp))
            PrimaryButton("保存") {
                val next = draft()
                if (!next.isConfigured) {
                    testResult = "Base URL / API Key / 模型 三项都要填"
                } else {
                    state.saveLlm(next)
                    state.showApiDialog = false
                    state.notify("AI 配置已保存，可以开始问鲤鱼了")
                }
            }
        },
    ) {
        // 两列布局：桌面端横着放得下，表单就不会超过一屏（竖排一列时底部按钮会被裁）
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Base URL", baseUrl, { baseUrl = it }, "https://api.deepseek.com/v1")
                Field("API Key", apiKey, { apiKey = it }, "sk-…", password = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("模型", model, { model = it }, "deepseek-chat", Modifier.weight(1f))
                    Field("温度", temperature, { temperature = it }, "0.3", Modifier.width(86.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("deepseek-chat", "deepseek-reasoner", "gpt-4o-mini").forEach { preset ->
                        TagChip(preset, onClick = { model = preset })
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "填三项就能用：接口地址、Key、模型名。任何兼容 OpenAI 协议的地址都行（DeepSeek / 通义 / 本地 Ollama 网关）。",
                    style = PocketTypography.caption,
                    color = colors.textTertiary,
                )
                Text(
                    // 不写绝对路径：它带着用户名，进了截图/文档就是隐私泄漏；
                    // 而且绝对路径会让截图基线在不同机器上必然不一致（截图门禁会红）。
                    "Key 只存本机（数据目录：%APPDATA%/PocketXiaoli）。AI 只在你点发送时看到这次问题相关的课表/校历内容，没有后台统计。",
                    style = PocketTypography.caption,
                    color = colors.textTertiary,
                )
                SecondaryButton("打开 DeepSeek 开放平台", icon = PocketIcons.LinkExternal) {
                    Platform.openUrl("https://platform.deepseek.com/api_keys")
                }
                if (testResult != null) {
                    Text(
                        testResult!!,
                        style = PocketTypography.caption,
                        color = if (testResult!!.contains("成功")) colors.success else colors.danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.chip))
                            .background(colors.surfaceAlt)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** 连通性探测：GET {baseUrl}/models，只看状态码（不花 token，也不会泄露业务数据）。 */
private fun probe(config: LlmConfig): String {
    if (!config.isConfigured) return "Base URL / API Key / 模型 三项都要填"
    return runCatching {
        val url = java.net.URI.create(config.baseUrl.trim().trimEnd('/') + "/models").toURL()
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        val code = conn.responseCode
        conn.disconnect()
        when {
            code in 200..299 -> "连接成功（HTTP $code）"
            code == 401 || code == 403 -> "Key 被拒绝（HTTP $code），检查是否复制完整"
            code == 404 -> "接口路径不对（HTTP 404），Base URL 一般要带 /v1"
            else -> "服务返回 HTTP $code"
        }
    }.getOrElse { "连不上：${it.message}（检查网络/代理）" }
}

// ═══════════════════════ 待办编辑 ═══════════════════════

@Composable
fun TodoEditorDialog(state: AppState) {
    val colors = pocketColors()
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(0) }
    var courseName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    PocketModal(
        title = "新建待办",
        subtitle = "截止日期可留空",
        onDismiss = { state.showTodoEditor = false },
        footer = {
            Spacer(Modifier.weight(1f))
            SecondaryButton("取消") { state.showTodoEditor = false }
            Spacer(Modifier.width(10.dp))
            PrimaryButton("保存") {
                if (title.isBlank()) {
                    error = "待办内容不能为空"
                } else if (due.isNotBlank() && runCatching { LocalDate.parse(due.trim()) }.isFailure) {
                    error = "截止日期要写成 2026-11-01"
                } else {
                    val todo = NewEntity.todo().copy(
                        title = title.trim(),
                        due = due.trim(),
                        priority = priority,
                        courseName = courseName.trim(),
                        note = note.trim(),
                    )
                    state.saveTodo(todo)
                    state.showTodoEditor = false
                    state.notify("待办已添加")
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("要做什么", title, { title = it }, "如：交数据结构实验报告")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Field("截止日期", due, { due = it }, "2026-11-01", Modifier.weight(1f))
                Field("关联课程", courseName, { courseName = it }, "可选", Modifier.weight(1f))
            }
            // 课程名候选：直接点，省得手打一遍打成另一个写法（之后按课程筛就筛不到）
            val courseNames = remember(state.data.courses) { suggestValues(state.data.courses, pick = { c -> c.name }) }
            if (courseNames.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    courseNames.forEach { value ->
                        TagChip(value, onClick = { courseName = value })
                    }
                }
            }
            Column {
                Text("优先级", style = PocketTypography.label, color = colors.textTertiary)
                Spacer(Modifier.height(4.dp))
                Segmented(options = listOf("普通", "重要", "紧急"), selectedIndex = priority, onSelect = { priority = it })
            }
            Field("备注", note, { note = it }, "可选")
            if (error != null) Text(error!!, style = PocketTypography.caption, color = colors.danger)
        }
    }
}

// ═══════════════════════ 节次时间 ═══════════════════════

@Composable
fun SlotDialog(state: AppState) {
    val colors = pocketColors()
    val slots = state.data.slots
    val editing = slots.map { it.index to (it.start to it.end) }.toMap().toMutableMap()
    var draft by remember { mutableStateOf(slots.map { it.index to (it.start) }) }
    var error by remember { mutableStateOf<String?>(null) }

    PocketModal(
        title = "节次时间",
        subtitle = "课表时间轴与「下一节课」都按这里算",
        onDismiss = { state.showSlotDialog = false },
        width = 560.dp,
        footer = {
            SecondaryButton("恢复默认 12 节") {
                state.mutate { it.copy(slots = DefaultTimeSlots) }
                state.showSlotDialog = false
                state.notify("已恢复默认节次时间")
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("取消") { state.showSlotDialog = false }
            Spacer(Modifier.width(10.dp))
            PrimaryButton("保存") {
                val bad = slots.any { slot ->
                    parseClock(editing[slot.index]?.first ?: "") == null ||
                        parseClock(editing[slot.index]?.second ?: "") == null
                }
                if (bad) {
                    error = "时间要写成 08:00 / 08:45 这种 HH:mm"
                } else {
                    state.mutate { current ->
                        current.copy(
                            slots = current.slots.map { slot ->
                                val pair = editing[slot.index] ?: (slot.start to slot.end)
                                slot.copy(start = pair.first, end = pair.second)
                            }
                        )
                    }
                    state.showSlotDialog = false
                    state.notify("节次时间已保存")
                }
            }
        },
    ) {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slots.forEach { slot ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("第 ${slot.index} 节", style = PocketTypography.caption, color = colors.textSecondary, modifier = Modifier.width(56.dp))
                    PocketTextField(
                        value = editing[slot.index]?.first ?: "",
                        onValueChange = { text -> editing[slot.index] = text to (editing[slot.index]?.second ?: slot.end) },
                        modifier = Modifier.width(110.dp),
                        placeholder = "08:00",
                    )
                    Text("–", style = PocketTypography.caption, color = colors.textTertiary)
                    PocketTextField(
                        value = editing[slot.index]?.second ?: "",
                        onValueChange = { text -> editing[slot.index] = (editing[slot.index]?.first ?: slot.start) to text },
                        modifier = Modifier.width(110.dp),
                        placeholder = "08:45",
                    )
                }
            }
            if (error != null) Text(error!!, style = PocketTypography.caption, color = colors.danger)
        }
    }
}

// ═══════════════════════ 导入 / 关于 ═══════════════════════

@Composable
fun ImportDialog(state: AppState) {
    val colors = pocketColors()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    PocketModal(
        title = "导入 / 导出",
        subtitle = "和移动端是同一份 JSON 结构（AppData），可以直接互相搬",
        onDismiss = { state.showImportDialog = false },
        width = 580.dp,
        footer = {
            SecondaryButton("选文件…", icon = PocketIcons.Upload) {
                val content = Platform.readTextFile()
                if (content != null) text = content
            }
            SecondaryButton("导出到文件…", icon = PocketIcons.Download) {
                val path = Platform.saveTextFile("口袋小鲤-数据-${Format.ymd(LocalDate.now())}.json", state.exportJson())
                if (path != null) {
                    state.showImportDialog = false
                    state.notify("已导出到 $path")
                }
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("取消") { state.showImportDialog = false }
            Spacer(Modifier.width(10.dp))
            PrimaryButton("导入") {
                runCatching { state.importJson(text) }
                    .onSuccess { count ->
                        state.showImportDialog = false
                        state.notify("导入成功：$count 条记录")
                    }
                    .onFailure { error = it.message ?: "导入失败" }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                minHeight = 220.dp,
                placeholder = "把 JSON 粘到这里，或点下面的「选文件…」",
                textStyle = PocketTypography.mono,
            )
            if (error != null) Text(error!!, style = PocketTypography.caption, color = colors.danger)
            Text(
                "导入会覆盖当前学期的课程/待办/学期列表；建议先导出备份。",
                style = PocketTypography.label,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
fun AboutDialog(state: AppState) {
    val colors = pocketColors()
    PocketModal(
        title = "关于 口袋小鲤 · 桌面版",
        subtitle = "与手机端同源的桌面移植（非官方构建）",
        onDismiss = { state.showAbout = false },
        footer = {
            SecondaryButton("打开数据目录", icon = PocketIcons.Folder) {
                Platform.openDirectory(Store.dataDir)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("知道了") { state.showAbout = false }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "口袋小鲤是烟台理工学院的校园助手。桌面版复刻了课表、待办、鲤鱼（AI 校园助手）、" +
                    "校历、知识库与快问快答，数据全部保存在本机。",
                style = PocketTypography.body,
                color = colors.textSecondary,
            )
            Text(
                "技术栈：Kotlin ${KotlinVersion.CURRENT} · Compose Multiplatform（Desktop）· 无第三方统计/广告 SDK。",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
            Text(
                "说明：教务系统（正方）导入、Android 专属能力（通知/小组件/生物识别）没有搬到桌面；" +
                    "课表用 JSON 导入导出互通。",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
            Text(
                "数据目录：${Store.dataDir.absolutePath}",
                style = PocketTypography.label,
                color = colors.textTertiary,
            )
        }
    }
}

/**
 * 「我的 → 导出课表 ICS」的弹窗。
 *
 * 放这里而不是屏幕内部：PocketModal 盖在宿主上层，属性在 `AppState` 里，
 * 这样导出弹窗和别的全局弹窗一样由外壳统一渲染（放在可滚动内容里会被裁掉 —— 踩过）。
 */
@Composable
fun IcsExportDialog(state: AppState) {
    PocketModal(
        title = "导出课表 ICS",
        subtitle = "保存到数据目录的 exports 子目录；按周展开，单双周/指定周次不会多出课",
        onDismiss = { state.showIcsDialog = false },
        width = 520.dp,
        footer = {
            Spacer(Modifier.weight(1f))
            SecondaryButton("取消") { state.showIcsDialog = false }
            PrimaryButton("导出") {
                val message = state.exportIcs(state.icsFileName)
                state.showIcsDialog = false
                state.notify(message)
            }
        },
    ) {
        PocketTextField(
            value = state.icsFileName,
            onValueChange = { state.icsFileName = it },
            placeholder = "课表.ics",
            singleLine = true,
        )
    }
}

// ═══════════════════ 教务导入的替换确认 ═══════════════════

/**
 * 导入结果可疑时的确认框：把「会动到什么」摊开给用户看，再决定替不替换。
 *
 * 触发条件在 `logic/ImportGuard.kt`：门数偏少、老师名与内置示例一致、或替换后数量变少。
 * 这一框存在的意义就一个 —— 不让一次失败的抓取静默覆盖掉用户原本完整的课表。
 */
@Composable
fun ImportConfirmDialog(state: AppState) {
    val colors = pocketColors()
    val plan = state.importPlan ?: return
    PocketModal(
        title = "这批课表看着不完整",
        subtitle = plan.summary,
        onDismiss = { state.cancelPendingImport() },
        width = 560.dp,
        footer = {
            SecondaryButton("取消，保持原样") { state.cancelPendingImport() }
            Spacer(Modifier.weight(1f))
            PrimaryButton("仍然替换", icon = PocketIcons.Refresh) { state.confirmPendingImport() }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.warnings.forEach { warning ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(PocketIcons.Info, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(warning, style = PocketTypography.body, color = colors.textPrimary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "如果确认学校就只有这些课，点「仍然替换」；想先查原因，去「我的 → 数据 → 导入诊断」把这一次抓取存下来。",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
        }
    }
}
