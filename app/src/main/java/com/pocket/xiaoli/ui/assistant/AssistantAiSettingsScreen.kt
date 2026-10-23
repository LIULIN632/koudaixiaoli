package com.pocket.xiaoli.ui.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.agent.AssistantLlmConfig
import com.pocket.xiaoli.ui.components.PocketPasswordTransformation
import com.pocket.xiaoli.ui.components.TopBarCircleButton
import com.pocket.xiaoli.ui.glass.glassSource
import com.pocket.xiaoli.ui.glass.glassTopBarSurface
import com.pocket.xiaoli.ui.glass.rememberAppBackdrop
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.Motion
import com.pocket.xiaoli.ui.theme.rememberSinkIndication
import kotlinx.coroutines.delay

/**
 * 鲤鱼设置页的**当前层级**。
 *
 * ⚠️ 顺序有意义：转场按 `ordinal` 判断方向（去更深的页 = 右进左出，回首页 = 左进右出）。
 * 往中间插页要同步检查 [AssistantAiSettingsScreen] 里的方向判断，别让"进去"和"回来"
 * 变成同一个方向的滑。
 */
private enum class AiSettingsPage(val title: String) {
    HOME("AI 设置"),
    MASCOT("形象与动作"),
    APPEARANCE("对话外观"),
    PROACTIVE("主动提醒"),
    MODEL("模型与密钥"),
}

/**
 * 鲤鱼的**唯一设置入口**（`assistant_ai_settings`）。
 *
 * ## 结构：一个首页 + 四个二级页
 *
 * 改造前是**一张 1000 行的长页**：形象宫格、字号、提醒档位、模型表单四段竖着排。
 * 找"改字号"要滑过 17 个形状与 12 个色块；改完模型想再换形象又要滑回顶部。
 * 现在首页只回答"有哪几项、现在各是什么值"，每项点进去才是它的控件。
 *
 * ## 为什么二级页不走 NavHost 路由
 *
 * 走路由意味着每次进二级页都重建组合。而**模型表单是输入中的文本**：
 * 在接口地址里打了一半、切去看形象、回来就只剩已保存的旧值 —— 这是真实的丢数据。
 * 页面状态挂在本宿主上（`page` + [ModelDraft]），切页只是换内容，草稿一直活着。
 *
 * 系统返回键由本文件内的 [BackHandler] 先接住（Compose 的返回处理是内层优先）：
 * 二级页 → 首页，首页才交还给导航。**漏了它，用户在二级页按返回会直接退出到鲤鱼页。**
 *
 * ## 生效方式：三页即时、一页显式保存
 *
 * - **形象 / 外观 / 主动提醒**：点一下立即生效。换形状是"点了就能看见、点回去就撤销"，
 *   多一个保存按钮只会让用户怀疑"我是不是还没存"。
 * - **模型**：Key / 地址 / 提示词是输入中的文本，逐字写盘既没意义，也看不出存没存。
 *
 * ⚠️ 因此整个设置区里**只能有模型页那一个"保存"按钮** —— 两种生效方式并存时，
 * 按钮的语义必须是唯一的。离开模型页不会丢草稿，但**要点保存才生效**；
 * 未保存时首页的「模型与密钥」会明说"有未保存的修改"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantAiSettingsScreen(onBack: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val container = remember { app.appContainer }
    val config = remember { container.assistantLlmConfig }
    val store = remember { container.tabAppearanceStore }
    val proactiveStore = remember { container.proactiveStore }

    // 主动提醒档位：收集 Flow（改完要立刻回显，且规则层每轮现读同一个值）
    val proactiveLevel by proactiveStore.level.collectAsStateWithLifecycle()

    // 形象与外观：**收集 Flow**（不是读 store 的属性 —— 属性读的是内存快照、
    // 不参与 Compose 快照系统，改了不会重组，这个坑在本项目踩过两次）
    val tab by store.state.collectAsStateWithLifecycle()
    val look = tab.assistantMascotLook

    // 模型草稿挂在宿主上：切到别的二级页再回来，输入到一半的 Key 还在。
    val draft = remember(config) { ModelDraft(config) }

    // 保存反馈自动复位：否则用户改完字段再回来，按钮还写着"已保存"，
    // 看起来像"这次修改也存进去了"。1.6 秒够看清，又不至于一直占着状态。
    LaunchedEffect(draft.saved) {
        if (draft.saved) {
            delay(1600)
            draft.saved = false
        }
    }

    var page by remember { mutableStateOf(AiSettingsPage.HOME) }
    BackHandler(enabled = page != AiSettingsPage.HOME) {
        page = AiSettingsPage.HOME
    }

    // 顶栏磨砂的采样源。与鲤鱼页同一套做法：录制挂在 Scaffold 的**内容区**上，
    // 顶栏是内容区的**兄弟节点** —— 录到的只有内容，不含顶栏自己。
    // ⚠️ 采样源录到自己会成环：RenderThread 级 SIGSEGV（原生崩溃，catch 不住）。
    val pageGlass = rememberAppBackdrop()

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(page.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TopBarCircleButton(
                        // 首页交给导航；二级页先回首页（与系统返回键同一个动作）
                        onClick = { if (page == AiSettingsPage.HOME) onBack() else page = AiSettingsPage.HOME },
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = if (page == AiSettingsPage.HOME) "返回" else "返回 AI 设置",
                        backdrop = pageGlass,
                    )
                },
                // 磨砂顶栏：底色与底边发丝线交给它画。
                // TopAppBar 自己的底色必须透明，否则会盖住玻璃。
                modifier = Modifier.glassTopBarSurface(
                    backdrop = pageGlass,
                    surfaceColor = colors.surfaceBase,
                    dividerColor = colors.borderDivider,
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        // 顶栏高度不再把内容整体顶下去，而是交给滚动内容自己消化（见下面的 top）——
        // 否则内容永远到不了顶栏背后，磨砂没有东西可采，等于白做。
        val topInset = padding.calculateTopPadding()
        // 采样源挂在**所有页共用的容器**上：转场时新旧两页同时存在，
        // 若各自挂一个采样源，顶栏会在两段录制之间来回抖。
        Column(modifier = Modifier.fillMaxSize().glassSource(pageGlass)) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val distance = { fullWidth: Int -> if (forward) fullWidth / 10 else -fullWidth / 10 }
                    (
                        fadeIn(tween(Motion.enter)) +
                            slideInHorizontally(tween(Motion.expand, easing = Motion.easing), distance)
                        ).togetherWith(
                        fadeOut(tween(Motion.exit)) +
                            slideOutHorizontally(tween(Motion.exit), distance)
                    )
                },
                label = "ai-settings-page",
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // 只让出**底部**的系统导航区；顶部交给滚动内容自己撑开
                        .padding(bottom = padding.calculateBottomPadding())
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = skin.spaceLg,
                            end = skin.spaceLg,
                            top = topInset + skin.spaceMd,
                            bottom = skin.spaceMd,
                        ),
                    verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
                ) {
                    when (current) {
                        AiSettingsPage.HOME -> AssistantSettingsHome(
                            look = look,
                            fontScale = tab.assistantFontScale,
                            showReasoning = tab.assistantShowReasoning,
                            proactiveLevel = proactiveLevel,
                            configured = draft.configured,
                            modelName = draft.model,
                            hasUnsavedModel = draft.dirty,
                            onOpenMascot = { page = AiSettingsPage.MASCOT },
                            onOpenAppearance = { page = AiSettingsPage.APPEARANCE },
                            onOpenProactive = { page = AiSettingsPage.PROACTIVE },
                            onOpenModel = { page = AiSettingsPage.MODEL },
                        )

                        AiSettingsPage.MASCOT -> MascotSettingsSection(
                            look = look,
                            onPickShape = { store.setAssistantMascotShape(it) },
                            onPickColor = { store.setAssistantMascotColor(it) },
                        )

                        AiSettingsPage.APPEARANCE -> {
                            AnswerPreview(
                                fontScale = tab.assistantFontScale,
                                showReasoning = tab.assistantShowReasoning,
                            )
                            AppearanceSettingsSection(
                                fontScale = tab.assistantFontScale,
                                showReasoning = tab.assistantShowReasoning,
                                onFontScale = { store.setAssistantFontScale(it) },
                                onReasoning = { store.setAssistantShowReasoning(it) },
                            )
                        }

                        AiSettingsPage.PROACTIVE -> ProactiveSettingsSection(
                            level = proactiveLevel,
                            onPick = { proactiveStore.setLevel(it) },
                        )

                        AiSettingsPage.MODEL -> ModelSettingsPage(draft = draft)
                    }
                }
            }
        }
    }
}

/**
 * 模型编辑态的宿主级草稿。
 *
 * 用普通类 + `mutableStateOf` 而不是一串 `remember { mutableStateOf(...) }`：
 * 七个字段 + 两个派生值（[configured] / [dirty]）分散在宿主里会把"切页不丢"这件事
 * 埋进一堆变量名中间。集中之后，宿主的职责只剩 `remember { ModelDraft(config) }`。
 */
private class ModelDraft(private val config: AssistantLlmConfig) {
    var baseUrl by mutableStateOf(config.baseUrl)
    var model by mutableStateOf(config.model)
    var apiKey by mutableStateOf(config.apiKey)
    var systemPrompt by mutableStateOf(config.systemPrompt)
    var allowWeb by mutableStateOf(config.allowWebTools)
    var showKey by mutableStateOf(false)
    var saved by mutableStateOf(false)

    /** 三项都填了才算能聊；缺一项时首页显示「待配置」。 */
    val configured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    /** 草稿与已保存配置有差异。首页用它提示"改了但没存"。 */
    val dirty: Boolean
        get() = baseUrl != config.baseUrl ||
            model != config.model ||
            apiKey != config.apiKey ||
            systemPrompt != config.systemPrompt ||
            allowWeb != config.allowWebTools

    fun save() {
        config.baseUrl = baseUrl
        config.model = model
        config.apiKey = apiKey
        config.systemPrompt = systemPrompt
        config.allowWebTools = allowWeb
        saved = true
    }
}

/** 「模型与密钥」二级页：隐私说明 + 表单 + 全设置区唯一的主色保存按钮。 */
@Composable
private fun ModelSettingsPage(draft: ModelDraft) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
    ) {
        PrivacyNote(configured = draft.configured)

        SettingsCard(title = "模型") {
            Field(
                label = "接口地址",
                value = draft.baseUrl,
                onValueChange = { draft.baseUrl = it },
                placeholder = AssistantLlmConfig.DEFAULT_BASE_URL,
                hint = "填到 /v1 为止，不要带 /chat/completions",
            )
            Spacer(Modifier.height(skin.spaceCompact))
            Field(
                label = "模型名",
                value = draft.model,
                onValueChange = { draft.model = it },
                placeholder = AssistantLlmConfig.DEFAULT_MODEL,
                hint = "常见：deepseek-chat / qwen-plus / moonshot-v1-8k / gpt-4o-mini",
            )
            Spacer(Modifier.height(skin.spaceCompact))
            Field(
                label = "API Key",
                value = draft.apiKey,
                onValueChange = { draft.apiKey = it },
                placeholder = "sk-...",
                hint = "加密存在本机，不上传；点右侧眼睛可临时核对是否粘错",
                isPassword = !draft.showKey,
                trailing = {
                    IconButton(onClick = { draft.showKey = !draft.showKey }) {
                        Icon(
                            imageVector = if (draft.showKey) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (draft.showKey) "隐藏 Key" else "显示 Key",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            Spacer(Modifier.height(skin.spaceMd))
            Field(
                label = "系统提示词（可选）",
                value = draft.systemPrompt,
                onValueChange = { draft.systemPrompt = it },
                placeholder = "留空则使用内置提示词",
                hint = "想改变助手的语气或职责范围时再填",
                singleLine = false,
            )
            Spacer(Modifier.height(skin.spaceSm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("清空自定义提示词", fontSize = 13.sp, color = colors.textPrimary)
                    Spacer(Modifier.height(skin.spaceMicro))
                    Text("回到内置的默认行为", fontSize = 12.sp, color = colors.textSecondary)
                }
                Text(
                    text = "重置",
                    modifier = Modifier
                        .clickable { draft.systemPrompt = "" }
                        .padding(horizontal = skin.spaceSm, vertical = skin.spaceXs),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.accentMain,
                )
            }

            Spacer(Modifier.height(skin.spaceSm))
            SwitchLine(
                title = "允许联网工具",
                subtitle = "当前版本还没有可用的联网工具，打开也不会生效；后续接入后此处生效",
                checked = draft.allowWeb,
                onToggle = { draft.allowWeb = it },
            )
        }

        // 改了没存时把话说在按钮上面 —— 用户按返回离开这一页前最后一次看见的就是它
        if (draft.dirty) {
            Text(
                text = "有未保存的修改：返回上一页不会丢；离开 AI 设置前记得点下面的按钮。",
                fontSize = 11.sp,
                color = colors.accentMain,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = skin.spaceXs),
            )
        }

        // ── 主操作：整个设置区唯一一个用主色实底的按钮，只管模型这一页 ──
        SaveButton(saved = draft.saved, onClick = draft::save)

        Footnote("若提示 401，多为 Key 或接口地址不对；若提示 404，检查接口地址是否已包含 /v1。")
    }
}

/**
 * 「对话外观」二级页顶部的**回答预览**。
 *
 * 字号这一项在其它页面里没有可见结果 —— 二级页里改完，用户要退出去问一句才看得到。
 * 预览卡把"标准 / 大"变成一条真的回答，并把倍率**用在与消息区相同的 Density 上**
 * （见 `AssistantScreen` 的 `LocalDensity provides Density(..., fontScale * multiplier)`），
 * 所以这里看到的比例就是聊天里看到的比例。
 */
@Composable
private fun AnswerPreview(fontScale: Int, showReasoning: Boolean) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val multiplier = when (fontScale) {
        0 -> 0.9f
        2 -> 1.15f
        else -> 1.0f
    }
    val shape = RoundedCornerShape(skin.radiusChip)
    SettingsCard(title = "预览") {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                baseDensity.density,
                baseDensity.fontScale * multiplier,
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surfaceHighlight)
                    .padding(skin.spaceMd),
            ) {
                if (showReasoning) {
                    Text(
                        text = "思考链 · 已开启",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = "今天有 3 节课，第一节在 A101，8:00 开始；作业还有 2 项没交。",
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    lineHeight = 21.sp,
                )
            }
        }
        InstantHint("这里只预览字号；实际回答会按同一倍率缩放正文、代码块与思考链。")
    }
}

/** 顶部说明卡：告诉用户 Key 存在哪、什么会离开本机。 */
@Composable
private fun PrivacyNote(configured: Boolean) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        color = colors.accentMain.copy(alpha = 0.08f),
    ) {
        Row(modifier = Modifier.padding(skin.spaceCompact)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = colors.accentMain,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(skin.spaceSm))
            Column {
                Text(
                    text = if (configured) "已配置，可以开始对话" else "还需要你自己的 API Key",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    "Key 只存在本机加密存储里，不上传到任何服务器；" +
                        "但对话内容会发给你填的模型服务商，请自行选择可信服务商。",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/**
 * 主操作按钮：主色实底 + 保存后短暂变成"已保存"。
 *
 * ⚠️ 它只管**模型**这一页。文案必须写明范围 —— 整个设置区唯一的主色实底按钮，
 * 用户会合理地以为"点了就是全保存"。
 */
@Composable
private fun SaveButton(saved: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = rememberSinkIndication(),
            onClick = onClick,
        ),
        shape = RoundedCornerShape(skin.radiusLarge),
        color = colors.accentMain,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = saved,
                enter = fadeIn(tween(Motion.exit)),
                exit = fadeOut(tween(Motion.exit)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(skin.spaceSmall))
                }
            }
            Text(
                text = if (saved) "已保存" else "保存模型配置",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

/**
 * 一个配置字段。
 *
 * 无描边（与鲤鱼页输入框同一套语法）+ 字段名在上、说明在下，
 * 让"这行是填什么的"不用靠猜。
 */
@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    hint: String,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
        Spacer(Modifier.height(skin.spaceSmall))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 13.sp, color = colors.textTertiary) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            // API Key 只可能是 ASCII：锁成 `Ascii`（= 可见密码变体，输入法不联想、不纠错），
            // 并用**共享**的隐藏变换实例（内联新建会让输入法组合中的字抖/丢，见 PocketField 的 KDoc）
            visualTransformation = if (isPassword) PocketPasswordTransformation else VisualTransformation.None,
            keyboardOptions = if (isPassword) {
                KeyboardOptions(keyboardType = KeyboardType.Ascii)
            } else {
                KeyboardOptions.Default
            },
            trailingIcon = trailing,
            shape = RoundedCornerShape(skin.radiusChip),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentMain,
                unfocusedBorderColor = colors.borderDivider,
                cursorColor = colors.accentMain,
            ),
        )
        Spacer(Modifier.height(skin.spaceXs))
        Text(hint, fontSize = 11.sp, color = colors.textSecondary, lineHeight = 16.sp)
    }
}
