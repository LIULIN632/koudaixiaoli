package com.pocket.xiaoli.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.DESKTOP_VERSION
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.state.setAutoLaunch
import com.pocket.xiaoli.desktop.state.exportDiagnosticBundle
import com.pocket.xiaoli.desktop.state.LoginPurpose
import com.pocket.xiaoli.desktop.state.setImportDiagnostics
import com.pocket.xiaoli.desktop.data.Store
import com.pocket.xiaoli.desktop.logic.buildIcs
import com.pocket.xiaoli.desktop.logic.weeklyCourseCount
import com.pocket.xiaoli.core.exam.ExamCountdown
import com.pocket.xiaoli.desktop.ui.components.DividerLine
import com.pocket.xiaoli.desktop.ui.components.IconBadge
import com.pocket.xiaoli.desktop.ui.components.PocketCard
import com.pocket.xiaoli.desktop.ui.components.PocketModal
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.ScreenTitle
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.SectionHeader
import com.pocket.xiaoli.desktop.ui.components.Segmented
import com.pocket.xiaoli.desktop.data.HolidayStore
import com.pocket.xiaoli.desktop.data.ImportDiagnostics
import com.pocket.xiaoli.desktop.data.CampusConfig
import com.pocket.xiaoli.desktop.logic.buildConfigChecklist
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.pocket.xiaoli.desktop.ui.components.SettingRow
import com.pocket.xiaoli.desktop.ui.components.StatTile
import com.pocket.xiaoli.desktop.ui.components.SwitchPocket
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.Spacing
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import com.pocket.xiaoli.desktop.ui.util.Format
import com.pocket.xiaoli.desktop.ui.util.Platform

/** 「我的」：身份卡 + 学业/外观/AI/数据/关于五组设置。 */
@Composable
fun ProfileScreen(state: AppState) {
    val colors = pocketColors()
    val settings = state.data.settings
    var confirmingReset by remember { mutableStateOf(false) }


    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.widthIn(max = 980.dp).fillMaxWidth()) {
            ScreenTitle(
                title = "我的",
                subtitle = "本地资料与偏好；没有任何信息会离开这台电脑",
                actions = {
                    SecondaryButton("导入 / 导出", icon = PocketIcons.Download) { state.showImportDialog = true }
                    PrimaryButton("编辑资料", icon = PocketIcons.Edit) { state.showStudentDialog = true }
                },
            )
            Spacer(Modifier.height(Spacing.md))

            // 身份卡
            PocketCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(accentSoftColor()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            Format.initials(settings.studentName),
                            style = PocketTypography.sectionTitle.copy(fontWeight = FontWeight.Bold),
                            color = accentColor(),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            settings.studentName.ifBlank { "还没填名字" },
                            style = PocketTypography.sectionTitle,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            listOfNotNull(
                                Format.maskStudentNo(settings.studentNo).ifBlank { null },
                                settings.major.ifBlank { null },
                                settings.className.ifBlank { null },
                            ).joinToString(" · ").ifBlank { "点「编辑资料」补上学号/专业/班级" },
                            style = PocketTypography.caption,
                            color = colors.textTertiary,
                        )
                    }
                    TagChip("烟台理工", color = accentColor(), background = accentSoftColor())
                }
                Spacer(Modifier.height(16.dp))
                DividerLine()
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    val week = state.currentWeek
                    val totalWeeks = state.data.activeTerm?.totalWeeks ?: 20
                    StatTile("本周课程", "${weeklyCourseCount(state.data.activeCourses, week, totalWeeks)}", hint = "节")
                    StatTile("未完成待办", "${state.data.todos.count { !it.done }}", hint = "条")
                    StatTile("对话", "${state.chats.size}", hint = "个")
                    StatTile("当前周次", if (week > 0) "$week" else "—", hint = if (week > 0) "/ $totalWeeks" else "未开学")
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // 学业
            SectionHeader("学业")
            Spacer(Modifier.height(6.dp))
            PocketCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                SettingRow(
                    "学期设置",
                    subtitle = state.data.activeTerm?.let { "${it.displayName} · ${it.startDate} 起 ${it.totalWeeks} 周" }
                        ?: "还没建学期（课表周次会显示不出来）",
                    icon = PocketIcons.CalendarDays,
                    onClick = { state.showTermDialog = true },
                    trailing = { TagChip("修改") },
                )
                SettingRow(
                    "节次时间",
                    subtitle = "共 ${state.data.slots.size} 节 · 第 1 节 ${state.data.slots.firstOrNull()?.start ?: "08:00"} 开始",
                    icon = PocketIcons.Clock,
                    onClick = { state.showSlotDialog = true },
                    trailing = { TagChip("调整") },
                )
                SettingRow(
                    "学生信息",
                    // 原来列的是"有哪些字段"，看不出你填没填；现在直接说当前状态
                    subtitle = studentSummary(settings),
                    icon = PocketIcons.GraduationCap,
                    onClick = { state.showStudentDialog = true },
                    trailing = { TagChip("编辑") },
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            // 外观
            SectionHeader("外观")
            Spacer(Modifier.height(6.dp))
            PocketCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                SettingRow(
                    "深色模式",
                    subtitle = "夜间看课表不刺眼；也可以跟随系统",
                    icon = PocketIcons.Moon,
                    trailing = {
                        SwitchPocket(checked = settings.darkMode, onChange = { on ->
                            state.setSetting { it.copy(darkMode = on) }
                        })
                    },
                )
                SettingRow(
                    "跟随系统",
                    subtitle = "系统切换深浅色时自动跟随",
                    icon = PocketIcons.Sun,
                    trailing = {
                        SwitchPocket(checked = settings.followSystemDark, onChange = { on ->
                            state.setSetting { it.copy(followSystemDark = on) }
                        })
                    },
                )
                SettingRow(
                    "关闭窗口时收进托盘",
                    subtitle = "点 × 不退出，从托盘菜单可以随时回来；关掉则点 × 直接退出",
                    icon = PocketIcons.Pin,
                    trailing = {
                        SwitchPocket(checked = settings.minimizeToTray, onChange = { on ->
                            state.setSetting { it.copy(closeToTray = on) }
                        })
                    },
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("强调色", style = PocketTypography.body, color = colors.textPrimary, modifier = Modifier.width(90.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ACCENT_SWATCHES.forEach { hex ->
                            val selected = settings.accentHex == hex
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (hex.isBlank()) colors.textSecondary else parseHex(hex))
                                    .clickable { state.setSetting { it.copy(accentHex = hex) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (settings.accentHex.isBlank()) "默认品牌蓝" else "#${settings.accentHex.uppercase()}",
                        style = PocketTypography.caption,
                        color = colors.textTertiary,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // 提醒
            SectionHeader("提醒")
            Spacer(Modifier.height(6.dp))
            PocketCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                SettingRow(
                    "上课前提醒",
                    subtitle = "应用运行时，开课前弹托盘气泡并在应用内提示",
                    icon = PocketIcons.Bell,
                    trailing = {
                        SwitchPocket(checked = settings.classReminderEnabled, onChange = { on ->
                            state.setSetting { it.copy(classReminder = on) }
                        })
                    },
                )
                if (settings.classReminderEnabled) {
                    val leadOptions = listOf(5, 10, 15, 20)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("提前", style = PocketTypography.body, color = colors.textPrimary, modifier = Modifier.width(90.dp))
                        Segmented(
                            options = leadOptions.map { "$it 分钟" },
                            selectedIndex = leadOptions.indexOf(settings.classReminderLead).coerceAtLeast(0),
                            modifier = Modifier.weight(1f),
                            onSelect = { index ->
                                state.setSetting { it.copy(reminderLeadMinutes = leadOptions[index]) }
                            },
                        )
                    }
                }
                SettingRow(
                    "待办到期提醒",
                    subtitle = "到期当天 09:00 起提醒一次；同一天只提醒一次",
                    icon = PocketIcons.TodoList,
                    trailing = {
                        SwitchPocket(checked = settings.todoReminderEnabled, onChange = { on ->
                            state.setSetting { it.copy(todoReminder = on) }
                        })
                    },
                )
                SettingRow(
                    "考试提醒",
                    subtitle = "开考前 ${ExamCountdown.SOON_DAYS} 天内告知一次，考试当天再提醒一次",
                    icon = PocketIcons.CalendarDays,
                    trailing = {
                        SwitchPocket(checked = settings.examReminderEnabled, onChange = { on ->
                            state.setSetting { it.copy(examReminder = on) }
                        })
                    },
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            // AI
            SectionHeader("AI")
            Spacer(Modifier.height(6.dp))
            PocketCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                SettingRow(
                    "模型配置",
                    subtitle = if (state.llmConfigured) "已配置 · ${state.llm.model}" else "未配置 · 填 Base URL / Key / 模型 后可用",
                    icon = PocketIcons.Sparkles,
                    iconTint = accentColor(),
                    onClick = { state.openApiDialog() },
                    trailing = { TagChip(if (state.llmConfigured) "已就绪" else "去配置", color = accentColor(), background = accentSoftColor()) },
                )
                SettingRow(
                    "温度 ${String.format("%.1f", settings.aiTemperature)}",
                    subtitle = "越低越稳；查课表这类事实问题建议 0.2~0.4",
                    icon = PocketIcons.Timer,
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            // 数据
            SectionHeader("数据", subtitle = "全部在本机：${Store.dataDir.absolutePath}")
            Spacer(Modifier.height(6.dp))
            PocketCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                // 配置体检：教务 / AI / 数据来源 / 启动 —— 缺哪项，那行就能点进去补
                val campus = CampusConfig.byId(state.data.settings.campusId)
                val checklist = buildConfigChecklist(
                    jwUrl = campus?.jwBaseUrl,
                    jwLoggedIn = state.academicSession != null,
                    aiConfigured = state.llmConfigured,
                    aiModel = state.llm.model,
                    origin = state.data.origin,
                    courseCount = state.data.activeCourses.size,
                    startupMillis = state.firstFrameMillis,
                )
                checklist.rows.forEach { row ->
                    SettingRow(
                        row.label,
                        subtitle = row.detail,
                        icon = when (row.label) {
                            "教务" -> PocketIcons.Lock
                            "AI" -> PocketIcons.Sparkles
                            "启动" -> PocketIcons.Timer
                            else -> PocketIcons.Info
                        },
                        onClick = when {
                            row.label == "教务" && row.action == "登录" -> { { state.openLogin(LoginPurpose.IMPORT) } }
                            row.label == "教务" -> { { state.showStudentDialog = true } }
                            row.label == "AI" -> { { state.showApiDialog = true } }
                            row.label == "数据来源" -> { { state.openLogin(LoginPurpose.IMPORT) } }
                            else -> null
                        },
                        trailing = {
                            val done = row.ok
                            TagChip(
                                row.action ?: if (done) "已就绪" else "待补",
                                color = if (done) colors.success else colors.warning,
                                background = if (done) colors.success.copy(alpha = 0.12f) else colors.warning.copy(alpha = 0.12f),
                            )
                        },
                    )
                }
                SettingRow(
                    "导入 / 导出 JSON",
                    subtitle = "与移动端 AppData 结构互通，换设备直接搬",
                    icon = PocketIcons.Upload,
                    onClick = { state.showImportDialog = true },
                    trailing = { TagChip("打开") },
                )
                SettingRow(
                    "打开数据目录",
                    subtitle = "课表数据、会话记录都在这里",
                    icon = PocketIcons.Folder,
                    onClick = { Platform.openDirectory(Store.dataDir) },
                    trailing = { TagChip("打开") },
                )
                SettingRow(
                    "开机自启",
                    subtitle = "写入当前用户的注册表启动项，启动的是安装版；关掉即删除",
                    icon = PocketIcons.Timer,
                    trailing = {
                        SwitchPocket(checked = settings.autoLaunchEnabled, onChange = { on ->
                            state.notify(state.setAutoLaunch(on))
                        })
                    },
                )
                SettingRow(
                    "首页天气",
                    subtitle = "打开首页时联网拉一次烟台天气，半小时不重复；关掉就不再请求，那条也不显示",
                    icon = PocketIcons.Sun,
                    trailing = {
                        SwitchPocket(checked = settings.homeWeatherEnabled, onChange = { on ->
                            state.setSetting { it.copy(homeWeather = on) }
                        })
                    },
                )
                SettingRow(
                    "导入诊断",
                    subtitle = "把教务返回的原始页面、学期参数与重定向链存到本机 logs/import-*；不记密码与 Cookie",
                    icon = PocketIcons.Info,
                    trailing = {
                        SwitchPocket(checked = settings.importDiagnosticsEnabled, onChange = { on ->
                            state.setImportDiagnostics(on)
                        })
                    },
                )
                SettingRow(
                    "打开诊断日志",
                    subtitle = if (ImportDiagnostics.lastDir != null) "最近一次：" + ImportDiagnostics.lastDir!!.name else "还没抓过；开着开关导入一次就会有",
                    icon = PocketIcons.Folder,
                    onClick = { Platform.openDirectory(ImportDiagnostics.lastDir ?: ImportDiagnostics.logRoot) },
                    trailing = { TagChip("打开") },
                )
                SettingRow(
                    "导出诊断包",
                    subtitle = "日志 + 数据形状 + 导入诊断样本打成 zip；API Key 已脱敏，出问题发这一个文件就够",
                    icon = PocketIcons.Download,
                    onClick = { state.notify(state.exportDiagnosticBundle()) },
                    trailing = { TagChip("导出") },
                )
                SettingRow(
                    "导出课表 ICS",
                    subtitle = "按周展开，可导入手机或系统日历",
                    icon = PocketIcons.CalendarDays,
                    onClick = {
                        if (state.data.activeTerm == null) {
                            state.notify("没有可导出的课表（先加学期/课程）")
                        } else {
                            state.icsFileName = "课表-" + state.data.activeTerm!!.name.replace(Regex("[\\/:*?\"<>|]"), "-") + "-第" + state.currentWeek + "周.ics"
                            state.showIcsDialog = true
                        }
                    },
                    trailing = { TagChip("导出") },
                )
                var holidayUpdating by remember { mutableStateOf(false) }
                val holidayScope = rememberCoroutineScope()
                SettingRow(
                    "节假日数据",
                    subtitle = remember(HolidayStore.version) { HolidayStore.summary } + " · 来源：国务院放假通知",
                    icon = PocketIcons.CalendarDays,
                    onClick = {
                        if (!holidayUpdating) {
                            holidayUpdating = true
                            holidayScope.launch {
                                val result = HolidayStore.updateFromNetwork()
                                holidayUpdating = false
                                result
                                    .onSuccess { count -> state.notify("节假日已更新：$count 条") }
                                    .onFailure { error ->
                                        state.notify("更新失败：${error.message ?: "网络异常"}；继续用本地数据")
                                    }
                            }
                        }
                    },
                    trailing = { TagChip(if (holidayUpdating) "更新中…" else "更新") },
                )
                SettingRow(
                    "自动更新节假日",
                    subtitle = "每天最多拉一次；只取公共节假日，不涉及你的任何信息",
                    icon = PocketIcons.Refresh,
                    trailing = {
                        SwitchPocket(
                            checked = settings.holidayAutoUpdate,
                            onChange = { on -> state.setSetting { it.copy(holidayAutoUpdate = on) } },
                        )
                    },
                )
                SettingRow(
                    "恢复示例数据",
                    subtitle = "清空当前课表，换成一批演示课程",
                    icon = PocketIcons.Refresh,
                    iconTint = colors.danger,
                    onClick = { confirmingReset = true },
                    trailing = { TagChip("重置", color = colors.danger) },
                )
                if (confirmingReset) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(Radii.chip))
                            .background(colors.danger.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "确定用示例数据覆盖现有课表与待办？建议先导出备份。",
                            style = PocketTypography.caption,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton("取消") { confirmingReset = false }
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton("覆盖") {
                            Store.resetToSample()
                            state.refresh()
                            confirmingReset = false
                            state.notify("已恢复示例数据")
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // 关于
            SectionHeader("关于")
            Spacer(Modifier.height(6.dp))
            PocketCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                SettingRow(
                    "口袋小鲤 · 桌面版",
                    subtitle = "v$DESKTOP_VERSION · 与手机端同源 · 非官方构建",
                    icon = PocketIcons.Fish,
                    onClick = { state.showAbout = true },
                    trailing = { TagChip("详情") },
                )
                SettingRow(
                    "开源仓库与协议",
                    subtitle = "禁止广告/统计 SDK；数据不上传（与移动端同一红线）",
                    icon = PocketIcons.Book,
                    onClick = { state.showAbout = true },
                    trailing = { TagChip("查看") },
                )
            }

            Spacer(Modifier.height(30.dp))
            Text(
                "口袋小鲤 · ${Store.dataDir.absolutePath}",
                style = PocketTypography.label,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

private val ACCENT_SWATCHES = listOf("", "4D6BFE", "6C5CE7", "00A88E", "E8A33D", "E2556F")

private fun parseHex(hex: String): Color =
    runCatching { Color(("FF" + hex.removePrefix("#")).toLong(16)) }.getOrDefault(Color(0xFF4D6BFE))

/** 学生信息那一行要说"填没填"，而不是把字段名列一遍。 */
private fun studentSummary(settings: com.pocket.xiaoli.desktop.data.AppSettings): String {
    val filled = listOfNotNull(
        settings.studentNo.takeIf { it.isNotBlank() }?.let { Format.maskStudentNo(it) },
        settings.className.takeIf { it.isNotBlank() },
    )
    return if (filled.isEmpty()) "还没填学号（登录教务要用）" else filled.joinToString(" · ")
}
