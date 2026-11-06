package com.pocket.xiaoli.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.data.AppPrefs
import com.pocket.xiaoli.desktop.data.CampusConfig
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.state.LoginPurpose
import com.pocket.xiaoli.desktop.data.Term
import com.pocket.xiaoli.desktop.ui.components.PocketMascot
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.Spacing
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import com.pocket.xiaoli.desktop.ui.util.Format
import java.time.LocalDate

/**
 * 首次启动引导：课表从哪来 → 连上 AI（可跳过）→ 你是谁。
 *
 * 三步的设计意图：
 * 1. **第一步就把「真实数据」摆在最前面**。以前引导只让手填学期，用户装完打开看到的是
 *    一份演示课表 —— 那就是「只能看不能用」的观感来源。现在第一屏直接给「登录教务导课表」。
 * 2. **不重复实现表单**：教务登录与 AI 配置都复用主界面的同一个弹窗（`App.kt` 在引导层也渲染它们），
 *    引导只负责把入口摆出来，不负责再写一份字段。
 * 3. **每一步都能跳过**：「我的」里都有对应入口，引导不是必经之路。
 */
@Composable
fun OnboardingScreen(state: AppState) {
    val colors = pocketColors()
    var step by remember { mutableStateOf(0) }

    // 手填学期
    var termName by remember { mutableStateOf(defaultTermName()) }
    var startDate by remember { mutableStateOf(defaultStartDate()) }
    var totalWeeks by remember { mutableStateOf("20") }
    var termError by remember { mutableStateOf<String?>(null) }
    var fillTermManually by remember { mutableStateOf(false) }

    // 学生信息
    var no by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var major by remember { mutableStateOf("") }

    val campus = CampusConfig.byId(state.data.settings.campusId)
    val jwUrl = campus?.jwBaseUrl

    fun finish() {
        AppPrefs.setOnboarded(true)
        state.showOnboarding = false
        state.refresh()
    }

    fun saveManualTerm(): Boolean {
        val date = runCatching { LocalDate.parse(startDate.trim()) }.getOrNull()
        val weeks = totalWeeks.trim().toIntOrNull()
        return when {
            termName.isBlank() -> { termError = "学期名别空着"; false }
            date == null -> { termError = "日期写成 2026-08-31 这种"; false }
            weeks == null || weeks !in 1..40 -> { termError = "周数填 1~40"; false }
            else -> {
                state.saveTerm(
                    Term(
                        id = "term_" + System.currentTimeMillis(),
                        name = termName.trim(),
                        startDate = Format.ymd(date),
                        totalWeeks = weeks,
                        sortOrder = 0,
                    )
                )
                termError = null
                true
            }
        }
    }

    Row(Modifier.fillMaxSize().background(colors.surfaceBase)) {
        // 左栏：品牌 + 吉祥物
        Column(
            Modifier
                .weight(1.05f)
                .fillMaxHeight()
                .background(accentSoftColor())
                .padding(horizontal = Spacing.xl, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            PocketMascot(Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "口袋小鲤",
                style = PocketTypography.screenTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            Text(
                "烟台理工学院 · 桌面版",
                style = PocketTypography.sectionTitle,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(22.dp))
            listOf(
                "登录教务就能把真实课表、成绩拉进来（密码不保存）",
                "考试安排靠粘贴通知解析 —— 正方没有这个接口",
                "鲤鱼能查校园知识库，也能连你自己的 AI",
                "数据全在本机，没有账号，没有埋点",
            ).forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .clip(CircleShape)
                            .background(accentColor())
                            .height(6.dp)
                            .width(6.dp)
                    )
                    Text(line, style = PocketTypography.body, color = colors.textSecondary)
                }
            }
            Spacer(Modifier.height(30.dp))
            Text(
                "与手机端同源的桌面移植 · 非官方构建",
                style = PocketTypography.label,
                color = colors.textTertiary,
            )
        }

        // 右栏：步骤
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = Spacing.xl, vertical = 48.dp),
        ) {
            Text(
                when (step) {
                    0 -> "课表从哪来"
                    1 -> "要不要连自己的 AI"
                    else -> "填一下你是谁"
                },
                style = PocketTypography.screenTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (step) {
                    0 -> "推荐直接登录教务导入；不登录也能用，只是首页先看到的是内置示例数据。"
                    1 -> "不配 Key 也能问校园知识库与快问快答；配了才能联网问答。Key 只存本机。"
                    else -> "只在本机保存，页面上展示时学号会自动打码。"
                },
                style = PocketTypography.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(26.dp))

            Column(Modifier.weight(1f)) {
                when (step) {
                    0 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceCard(
                                icon = PocketIcons.GraduationCap,
                                title = if (jwUrl.isNullOrBlank()) "从教务导入" else "从教务导入 · ${campus?.name.orEmpty()}",
                                body = if (jwUrl.isNullOrBlank()) {
                                    "当前校区没有配置教务地址，可以先手填学期"
                                } else {
                                    "学号 + 密码 + 验证码登录正方，导入后替换本学期课表；密码不保存"
                                },
                                enabled = !jwUrl.isNullOrBlank(),
                                action = "登录教务",
                                onAction = { state.openLogin(LoginPurpose.IMPORT) },
                            )
                            ChoiceCard(
                                icon = PocketIcons.CalendarDays,
                                title = "手填一个学期",
                                body = "只想先记自己的课：填学期名、开学周一和总周数",
                                enabled = true,
                                action = if (fillTermManually) "收起" else "展开",
                                onAction = { fillTermManually = !fillTermManually },
                            )
                            if (fillTermManually) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Radii.card))
                                        .background(colors.surfaceAlt)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    PocketTextField(value = termName, onValueChange = { termName = it }, placeholder = "2026-2027 第一学期")
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text("开始日期（周一）", style = PocketTypography.label, color = colors.textTertiary)
                                            PocketTextField(value = startDate, onValueChange = { startDate = it }, placeholder = "2026-08-31")
                                        }
                                        Column(Modifier.width(110.dp)) {
                                            Text("总周数", style = PocketTypography.label, color = colors.textTertiary)
                                            PocketTextField(value = totalWeeks, onValueChange = { totalWeeks = it }, placeholder = "20")
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(16, 18, 20, 22).forEach { weeks ->
                                            TagChip(
                                                "$weeks 周",
                                                color = if (totalWeeks == "$weeks") colors.textPrimary else colors.textSecondary,
                                                onClick = { totalWeeks = "$weeks" },
                                            )
                                        }
                                    }
                                    termError?.let { Text(it, style = PocketTypography.caption, color = colors.danger) }
                                    PrimaryButton("保存学期", icon = PocketIcons.Check) {
                                        if (saveManualTerm()) fillTermManually = false
                                    }
                                }
                            }
                            ChoiceCard(
                                icon = PocketIcons.Info,
                                title = "先看看示例数据",
                                body = "内置一份烟台理工风格的演示课表，随时可以在首页一键清掉",
                                enabled = true,
                                action = "保持示例",
                                onAction = { /* 什么都不做，进入下一步即可 */ },
                            )
                        }
                    }

                    1 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (state.llmConfigured) PocketIcons.Check else PocketIcons.Info,
                                    contentDescription = null,
                                    tint = if (state.llmConfigured) colors.success else colors.textTertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (state.llmConfigured) "已配置：${state.llm.model} @ ${state.llm.baseUrl}" else "还没配置 API Key",
                                    style = PocketTypography.body,
                                    color = colors.textPrimary,
                                )
                            }
                            PrimaryButton("填 API Key", icon = PocketIcons.Settings) { state.showApiDialog = true }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "兼容任何 OpenAI 协议接口（默认 DeepSeek）。跳过这一步，鲤鱼仍能查课表、待办、校历和校园知识库。",
                                style = PocketTypography.caption,
                                color = colors.textTertiary,
                            )
                        }
                    }

                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("学号", style = PocketTypography.label, color = colors.textTertiary)
                                    PocketTextField(value = no, onValueChange = { no = it }, placeholder = "20230001")
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("姓名", style = PocketTypography.label, color = colors.textTertiary)
                                    PocketTextField(value = name, onValueChange = { name = it }, placeholder = "张三")
                                }
                            }
                            Text("专业", style = PocketTypography.label, color = colors.textTertiary)
                            PocketTextField(value = major, onValueChange = { major = it }, placeholder = "计算机科学与技术")
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radii.chip))
                                    .background(colors.surfaceAlt)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(PocketIcons.Lock, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.width(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "不想填也可以，直接进 → 首页/课表照常使用。",
                                    style = PocketTypography.caption,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                    }
                }
            }

            // 底部：步骤点 + 按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (index == step) accentColor() else colors.surfaceHighlight)
                                .height(6.dp)
                                .width(if (index == step) 18.dp else 6.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (step < 2) {
                    SecondaryButton("跳过") { finish() }
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton("下一步", icon = PocketIcons.ChevronRight) { step++ }
                } else {
                    PrimaryButton("进入口袋小鲤", icon = PocketIcons.Check) {
                        if (no.isNotBlank() || name.isNotBlank()) {
                            state.saveStudent(no.trim(), name.trim(), major.trim(), "", "ytlg_main")
                        }
                        finish()
                    }
                }
            }
        }
    }
}

/** 引导里的一张可点选项卡：左边说明，右边一个动作。 */
@Composable
private fun ChoiceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    enabled: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    val colors = pocketColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .background(colors.surfaceAlt)
            .then(if (enabled) Modifier.clickable(onClick = onAction) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (enabled) accentSoftColor() else colors.surfaceBase),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) accentColor() else colors.textTertiary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = PocketTypography.cardTitle, color = if (enabled) colors.textPrimary else colors.textTertiary)
            Spacer(Modifier.height(2.dp))
            Text(body, style = PocketTypography.caption, color = colors.textSecondary)
        }
        Spacer(Modifier.width(10.dp))
        TagChip(
            action,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
            background = if (enabled) colors.surfaceCard else colors.surfaceBase,
        )
    }
}

/** 默认学期名：9 月~次年 1 月算第一学期。 */
private fun defaultTermName(): String {
    val now = LocalDate.now()
    val year = if (now.monthValue >= 8) now.year else now.year - 1
    val next = (year + 1).toString().takeLast(2)
    return "$year-$next 第一学期"
}

/** 默认开学日：当年 8 月最后一周的周一（近似，用户可改）。 */
private fun defaultStartDate(): String {
    val now = LocalDate.now()
    var date = LocalDate.of(now.year, 8, 25)
    while (date.dayOfWeek.value != 1) date = date.plusDays(1)
    return Format.ymd(date)
}
