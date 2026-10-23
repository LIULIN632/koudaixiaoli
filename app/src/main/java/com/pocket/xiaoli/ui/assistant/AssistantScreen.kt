package com.pocket.xiaoli.ui.assistant

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
// ⚠️ 下面两行**不是**未使用的 import，删掉会直接编译失败。
// 它们是 `by` 委托的**运算符扩展**：`val x by remember { … }` 编译成 `x.getValue(…)`，
// 但源码里从不出现 `getValue` 这个名字，所以任何"按符号名找未使用 import"的清理
//（包括拆分时的机械清理）都会把它们误判成垃圾。编译器报的错很绕：
// "Type 'MutableState<Boolean>' has no method 'setValue' / Property delegate must have a 'getValue' method"。
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.agent.AgentPhase
import com.pocket.xiaoli.data.agent.ChatRole
import com.pocket.xiaoli.ui.components.TopBarCircleButton
import com.pocket.xiaoli.ui.glass.LocalBottomBarInset
import com.pocket.xiaoli.ui.glass.glassSource
import com.pocket.xiaoli.ui.glass.glassTopBarSurface
import com.pocket.xiaoli.ui.glass.rememberAppBackdrop
import com.pocket.xiaoli.ui.proactive.ProactiveBubbleHost
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.Motion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * 鲤鱼页的**宿主**：整页骨架 + 两段只有它才关心的逻辑。
 *
 * ## 这个文件现在只负责"整页"层面的四件事
 *
 * | 职责 | 在哪 |
 * |---|---|
 * | 会话生命周期（新建 / 删除当前 / 消费「我的」页投递的分析请求） | [AssistantScreen] |
 * | **滚动跟随**：贴底判定 + 上滑即停 + 回到底部 | [AssistantScreen] |
 * | 顶栏（玻璃、标题、历史 / 新对话 / AI 设置） | [AssistantScreen] |
 * | 未配置模型的横幅、「回到底部」浮标 | 本文件底部 |
 *
 * ## ⚠️ 其余三块已经拆出去 —— 想改它们别在这里找
 *
 * 拆分前这里是 **1704 行 / 25 个顶层声明**，是全项目最大的单文件。
 * 而"输入框长什么样"、"一轮对话怎么排"、"空态欢迎页有什么"这三件事与整页的
 * 滚动跟随/顶栏**没有任何共享状态**，却能互相干扰到"改一处要读一整个文件"。
 *
 * | 想改什么 | 去哪个文件 |
 * |---|---|
 * | 输入框的高度、发送/停止按钮、placeholder | `AssistantComposer.kt` |
 * | 气泡 / 思考链折叠栏 / 工具脚注 / 轮末操作条 / 「什么算一轮」 | `ChatTurn.kt` |
 * | 空态的问候卡、建议问题网格 | `AssistantWelcome.kt` |
 * | 共用的推导型尺寸（头像、内容列缩进、轮距） | `AssistantLayout.kt` |
 *
 * 那次拆分是**纯移动、逻辑一行未改**，证据是三组既有用例（`30..33` 输入框快照、
 * `40..45` 消息区快照 + 无障碍语义、`50/51` 欢迎页快照）在拆分前后逐像素一致。
 * 所以：**再拆的时候也照这个办法验** —— 先确认有快照覆盖，再动手。
 */

/**
 * 判定「用户已经贴着底部」的容差（px）。
 *
 * 72px 约等于一行正文：流式追加时新内容会先把最后一行往下推，若不允许这点偏移，
 * 每次增量都会被判成"用户上滑了"，自动跟随直接失效。
 */
private const val BOTTOM_SLACK_PX = 72

/**
 * `scrollToItem` 的负偏移魔法值。
 *
 * LazyColumn 的 `scrollOffset` 是"从条目起点再往前滚多少"，
 * 给一个远超内容高度的值就能把列表顶到**绝对底部** ——
 * 这比 `animateScrollToItem(lastIndex)` 更可靠：后者在"最后一条已经部分可见"
 * 时**不会滚动**，而流式正文恰恰一直在让最后一条变长，于是表现为"长回答不跟随"。
 * （岱宗盒子只用 `scrollToItem(lastIndex)`，它的自动滚动就是这么漏的。）
 *
 * 为什么用 `scrollToItem` 而不是 `animateScrollToItem`：这里是逐帧跟随，
 * 每帧再叠一段动画只会互相打断、越跟越糊；瞬间贴底反而更跟手。
 */
private val STICK_TO_BOTTOM_OFFSET = -100_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantChatViewModel,
    state: AssistantUiState,
    onBack: () -> Unit,
    showBack: Boolean = true,
    onOpenSettings: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current

    // 顶栏玻璃的采样源。
    // ⚠️ 录制挂在 Scaffold 的**内容区**上、而不是外层容器上 —— 顶栏是内容区的**兄弟节点**，
    // 这样录到的只有"页面内容"，不含顶栏自己。录到自己会成环：RenderThread 级 SIGSEGV。
    val pageGlass = rememberAppBackdrop()
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val container = remember { app.appContainer }
    // 鲤鱼的外观设置（「外观 → 鲤鱼」）。必须**收集 Flow**而不是读 store 的当前属性：
    // 属性读的是内存快照、不参与 Compose 快照系统，改了不会触发重组
    // （这个坑在会话列表的 currentConversationId 与底栏样式上都踩过）。
    val tabAppearance by container.tabAppearanceStore.state.collectAsStateWithLifecycle()
    // Agent 此刻在做什么。消息区的头像按**本轮数据**推导（见 assistantAvatarPhase），
    // 但空态欢迎页没有"本轮"可推导 —— 它要用的是全局那个"此刻"。
    // 两处都读同一个真源（`AgentPhase`），只是粒度不同。
    val agentPhase by container.aiAvatarPhase.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // ⚠️ 这里原先还有一个 `pendingClear`（「结束这段对话」的确认框）。已随那个按钮一起删除：
    // 它与「新对话」是同一个动作，而"开一段新的"是**非**销毁性的（旧会话留在「历史对话」里），
    // 本就不该要确认框 —— 确认框只留给"做错了用户自己恢复不了"的动作。

    // ── 消费「我的」页投递的待分析请求（如诊断日志）──
    // 鲤鱼页是 saveState/restoreState 的底部 Tab，切走再回来 ViewModel 不重建，
    // 所以不能靠 ViewModel 的 init {} 接这条请求，必须在这里监听。
    // 用 nonce 而非"内容非空"判断，避免重进页面时把同一条请求重复发出去。
    val pendingAnalyze by container.pendingAiAnalyze.collectAsStateWithLifecycle()
    var handledAnalyzeNonce by remember { mutableStateOf(0L) }
    LaunchedEffect(pendingAnalyze?.nonce) {
        val request = pendingAnalyze ?: return@LaunchedEffect
        if (request.nonce <= handledAnalyzeNonce) return@LaunchedEffect
        handledAnalyzeNonce = request.nonce
        // 先清信箱再发送：即使发送过程中用户切走页面导致协程取消，
        // 也不会在下次进入时把旧请求再发一遍。
        container.consumeAiAnalyze()
        // send() 对流式中的输入是"静默忽略"的。这里必须自己先看一眼，
        // 否则用户点「让鲤鱼看看」时会看到页面跳过来了、消息却没发出去，还不知道为什么。
        if (state.isStreaming) {
            Toast.makeText(context, "鲤鱼还在回答上一条，稍后再试", Toast.LENGTH_LONG).show()
        } else {
            viewModel.send(request.text)
        }
    }

    // ── 滚动跟随 ──
    // 意图与位置分开两个状态：
    // - stickToBottom：用户「想不想」跟着最新内容（贴底时为 true；一旦上滑就 false）
    // - isAtBottom：此刻「是不是」真的贴底（只用来决定「回到底部」按钮露不露）
    // 合成一个状态会让"上滑读历史"与"新内容到达"互相覆盖，历史上就是这么跳的。
    var stickToBottom by remember { mutableStateOf(true) }

    // 一轮 = 一个条目：连续的 assistant / tool 消息合成一个 turn，
    // 由 turn 自己决定把工具折到回答末尾的脚注区
    val turns = remember(state.messages) { state.messages.toTurns() }

    // 「已经贴底」= 还能往下滚时，最后一条的底部离视口底部不超过容差。
    // 用 listState.canScrollForward 而不是比较 visibleItems 的 index：
    // 后者在"最后一条高于一屏"时会把"读者正停在长回复的开头"误判成贴底。
    val isAtBottom by remember(listState) {
        derivedStateOf {
            if (!listState.canScrollForward) return@derivedStateOf true
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            (info.viewportEndOffset - last.offset - last.size) < BOTTOM_SLACK_PX
        }
    }

    // 「可以停在这儿不动」= 已经贴底，或者最后一条**从头就在视口里**。
    //
    // 后者是新建的那条长回答：它的开头就落在视口顶部，于是钉住不动，
    // 用户从第一句读起，而"回到底部"按钮会请他去看结尾 ——
    // 若没有这一条，流式正文一旦超过一屏就会立刻把人拽到尾巴上，开头永远读不到。
    val anchored by remember(listState) {
        derivedStateOf {
            if (isAtBottom) return@derivedStateOf true
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.offset <= info.viewportStartOffset + BOTTOM_SLACK_PX
        }
    }

    // 用户开始上滑（离开可停留位置）就停止跟随。刻意**不**在用户滑回底部时
    // 自动恢复跟随 —— 那是"我明明只想看一眼上面"最常见的误伤来源。
    // 恢复跟随只有两个入口：「回到底部」按钮、或者用户真的发了一条新消息。
    LaunchedEffect(listState) {
        snapshotFlow { anchored }
            .distinctUntilChanged()
            .filter { !it }
            .collect { stickToBottom = false }
    }

    // 只有用户**主动发问**才重新开启跟随（这时"最新"就是他刚问的，必须让他看到回复）。
    // 刻意不用 `messages.size` 当条件：那会让"工具返回"、"流式追加"也算成新消息，
    // 于是用户上滑读历史时又被拽回底部 —— 正是要避免的那个毛病。
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.lastOrNull()?.role == ChatRole.USER) stickToBottom = true
    }

    /**
     * 跟随贴底。
     *
     * 为什么不是"内容变了就 scrollToItem"（岱宗盒子的做法）：
     * ① 它只在**行数变化**时滚，流式正文变长不跟随，长回答要用户自己追着滚；
     * ② 它不管用户在哪，上滑读历史时会被新行硬拽到底。
     *
     * 这里用快照流：**在流内部**读内容（而不是当 effect 的 key），否则每次增量都会
     * 取消并重启协程，"读到的内容"和"要滚到的位置"永远是上一帧的，跟随会一顿一顿；
     * 收集时才判断 [anchored]，已经不落后就彻底 no-op ——
     * 这一句才是"用户上滑不被硬拽"、"长回答开头不被跳过"的共同保证。
     */
    LaunchedEffect(listState, stickToBottom) {
        if (!stickToBottom) return@LaunchedEffect
        snapshotFlow { state.messages }
            .collect { messages ->
                // 空态（还没发过话 / 历史还在读库）没什么可滚的。
                // 判 turns.size 而不是 messages.size：两者在"只调了工具还没说话"时不同，
                // 而列表项数是按 turns 算的，滚动目标必须以它为准。
                if (messages.isEmpty() || turns.isEmpty()) return@collect
                // turns.size 是脚注前一项；配合负偏移即"贴到绝对底部"
                if (!anchored) listState.scrollToItem(turns.size, STICK_TO_BOTTOM_OFFSET)
            }
    }

    Scaffold(
        // 与其它页面统一：不设的话会落到 Material3 默认的 colorScheme.background，
        // 那个色令牌与各页面用的 surfaceBase 不是同一个来源，会露出底色不一致。
        containerColor = colors.surfaceBase,
        topBar = {
            // 标题居中 + 圆形浅底按钮。
            //
            // 原来左对齐的标题配一条纯白顶栏，读起来就是"一条白带压在页面上"；
            // 居中标题 + 两侧圆形按钮是参考软件那种版式，而且关键一步是**把顶栏底色改成
            // 与页面同色**（原来是 surfaceCard 白）—— 否则无论按钮怎么改，顶上仍然是那一条白带。
            CenterAlignedTopAppBar(
                // 标题下补一句定位：整页只有"鲤鱼"两个字时，用户看不出它和别的聊天软件
                // 有什么不同；这行小字把"数据在本机、随时能问"这个产品差异放在第一屏。
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("鲤鱼", fontWeight = FontWeight.Bold)
                        Text(
                            text = "本机数据 · 随时问我",
                            fontSize = 10.sp,
                            color = colors.textTertiary,
                        )
                    }
                },
                // 顶栏玻璃：采的是内容区录下来的那一层，所以内容从它背后滚过去时会真的被模糊
                modifier = Modifier.glassTopBarSurface(
                    backdrop = pageGlass,
                    surfaceColor = colors.surfaceBase,
                    dividerColor = colors.borderDivider,
                ),
                navigationIcon = {
                    // 「历史记录」放**左上角**（用户要求）。它与右上角是一对语义：
                    // 左边是"去别处"，右边是"改动当前这段 / 改这个助手"，分开两侧就不会几个图标挤在一起。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showBack) {
                            TopBarCircleButton(
                                onClick = onBack,
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                label = "返回",
                                backdrop = pageGlass,
                            )
                        }
                        if (onOpenHistory != null) {
                            TopBarCircleButton(
                                onClick = onOpenHistory,
                                icon = Icons.Outlined.History,
                                label = "历史对话",
                                backdrop = pageGlass,
                            )
                        }
                    }
                },
                actions = {
                    // 「新对话」只在**这一段聊过东西**时出现。
                    // 空对话上摆一个"新对话"是死按钮 —— 点了什么都不会发生，
                    // 用户会以为坏了。所以用"有没有内容"当可见条件，而不是恒显。
                    if (state.messages.isNotEmpty()) {
                        TopBarCircleButton(
                            onClick = {
                                viewModel.startNewConversation(
                                    onBlocked = {
                                        Toast.makeText(context, "鲤鱼还在回答，等它说完再开新对话", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            },
                            icon = Icons.Filled.Add,
                            label = "新对话",
                            backdrop = pageGlass,
                        )
                    }
                    // 「AI 设置」**恒显**（与上面那个相反）。它管的是形象 / 外观 / 模型 ——
                    // 三件事都与"当前这段对话"无关，空态下同样是用户可能想改的东西。
                    //
                    // ⚠️ 这里原本还有一个「结束这段对话」（退格图标）按钮：它与「新对话」
                    // **是同一个动作**（都调 startNewConversation），只是多一个确认弹窗。
                    // 同一件事两个入口只会让人猜"这两个是不是不一样"，已删 ——
                    // 而它是**非**销毁性的（旧会话保留在「历史对话」里），本就不需要确认框。
                    if (onOpenSettings != null) {
                        TopBarCircleButton(
                            onClick = onOpenSettings,
                            icon = Icons.Outlined.Settings,
                            label = "AI 设置",
                            backdrop = pageGlass,
                        )
                    }
                },
                // 底色交给上面的玻璃 modifier 画，这里必须透明，否则会盖住玻璃
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        // 顶栏高度：内容区**不再**被顶栏往下推（那样内容就永远到不了顶栏背后，
        // 玻璃没东西可采，等于白做），而是把这段高度交给列表的 contentPadding 顶部去消化。
        val topInset = padding.calculateTopPadding()

        Column(
            modifier = Modifier
                .fillMaxSize()
                // 只让出**底部**的系统导航区；顶部留给列表自己撑开（见上的 topInset）
                .padding(bottom = padding.calculateBottomPadding())
                // 把内容区录进采样源，供顶栏采样
                .glassSource(pageGlass)
                // 页面底色加一层**极淡的竖向渐变**（顶部一点点主题色，往下褪成透明）。
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.accentMain.copy(alpha = if (isDark) 0.10f else 0.07f),
                            Color.Transparent,
                        )
                    )
                )
                // ⚠️ 这里**不让出底栏高度**：底栏是浮层，整列上移会在输入区与底栏之间空出一条死白，
                // 底栏悬在空白上既没有东西可折射，看起来也就是"tab 后面多了一块背景板"。
                // 改成让消息列表铺满整屏（含底栏那块），输入区浮在列表之上 ——
                // 底栏背后因此是真实的对话内容（滚动时看得见玻璃），最后一条消息也仍能滚到输入区上方。
                .padding(horizontal = skin.spaceLg),
        ) {
            if (!state.isConfigured) {
                // 内容区现在从屏幕顶端开始，所以横幅要自己让开顶栏高度
                Box(Modifier.padding(top = topInset)) {
                    NotConfiguredBanner(onOpenSettings = { onOpenSettings?.invoke() })
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 输入区高度由它自己上报：字号 / 系统字体缩放都会改变它，写死会错位
                var composerHeightPx by remember { mutableIntStateOf(0) }
                val composerHeight = with(LocalDensity.current) { composerHeightPx.toDp() }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // 轮距**逐项算**（见 TurnGap）：提问与它的回答要贴紧、两段对话之间才拉开。
                    // 统一一个间距做不到这件事 —— 那只会让一问一答也离得同样远。
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(
                        // 顶部让开顶栏：内容因此会从顶栏背后滚过去
                        top = topInset + 12.dp,
                        // 让最后一条消息能滚到输入区**上方**：输入区高度 + 间距 + 底栏
                        bottom = 12.dp + composerHeight + LocalBottomBarInset.current,
                    ),
                ) {
                    if (state.messages.isEmpty()) {
                        // 之前这里没接 onSend，导致建议问题点了没反应 —— 现在真的能点了
                        item {
                            AssistantWelcome(
                                phase = agentPhase,
                                onPick = { text ->
                                    query = ""
                                    viewModel.send(text)
                                },
                                // 用户在别处戳了一下鲤鱼时，那句闲话落在空态问候的第二行
                                //（底栏气泡在鲤鱼页被抑制，否则会压住输入框）
                                heroLine = ProactiveBubbleHost.heroLine,
                            )
                        }
                    }
                    itemsIndexed(turns, key = { _, turn -> turn.key }) { index, turn ->
                        val baseDensity = LocalDensity.current
                        val fontScale = tabAppearance.assistantFontMultiplier
                        // 「外观 → 鲤鱼 → 回答字号」。只缩放**消息轮**（正文 / 行内代码 /
                        // 代码块 / 思考链 / 工具脚注一起缩放），不动输入框、欢迎页与页脚说明。
                        //
                        // 用 Density 的 fontScale，而不是给每个 Text 乘一个系数：一条回答里
                        // 混着好几种字号，逐个改必然漏掉一两处（代码块、时间戳…）。
                        // ⚠️ 乘在**系统字号**之上：用户在系统里调大字体时这里仍按倍数叠加。
                        CompositionLocalProvider(
                            LocalDensity provides Density(
                                baseDensity.density,
                                baseDensity.fontScale * fontScale,
                            )
                        ) {
                            // 间距挂在条目自身：轮距不是常量，取决于"这一轮是不是一段新对话"
                            Column(modifier = Modifier.padding(top = turnGapOf(index, turn).toDp())) {
                                ChatTurn(
                                    turn = turn,
                                    runningTool = state.runningTool,
                                    streaming = state.isStreaming,
                                    onRetry = { viewModel.retryLast() },
                                    onRegenerate = { viewModel.regenerate() },
                                    showReasoning = tabAppearance.assistantShowReasoning,
                                )
                            }
                        }
                    }

                    // ── 追问 chips ──
                    //
                    // 三个条件缺一不可：
                    // - **最后一轮是助手在说话**（`user == null` 且有正文）：用户刚打完字、
                    //   回答还没来时推荐"你还可以问…"是答非所问；
                    // - **不在流式中**：它正吐字时摆一排问题，等于诱惑用户打断它；
                    // - **列表非空**：空态由欢迎页那 5 张大卡负责，两处不要同时出现。
                    val lastTurn = turns.lastOrNull()
                    if (
                        !state.isStreaming &&
                        lastTurn != null &&
                        lastTurn.user == null &&
                        lastTurn.answer.isNotBlank()
                    ) {
                        item {
                            FollowUpChips(
                                onPick = { text ->
                                    query = ""
                                    viewModel.send(text)
                                },
                                modifier = Modifier.padding(top = skin.spaceMd),
                            )
                        }
                    }

                    item {
                        Text(
                            text = "回答由 AI 生成，校园数据来自本机；办事结果以学校官方为准。",
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(top = skin.spaceXs)
                        )
                    }
                }

                // 「回到底部」：只在用户上滑读历史、且真的不在可停留位置时出现。
                // 这是岱宗盒子那份实现里最缺的一环 —— 它靠"行数变化就 scrollToItem"，
                // 用户上滑时会被新内容硬拽到底，读历史根本读不成。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // 抬到输入区之上：输入区现在浮在列表里，不再是列表的兄弟节点
                        .padding(
                            bottom = composerHeight + LocalBottomBarInset.current + skin.spaceSm,
                            end = skin.spaceXs,
                        ),
                ) {
                    // 显式套一层 Column：AnimatedVisibility 在 ColumnScope 上才可用，
                    // 直接用会被外层 Column 的 receiver 抢走（编译期报 implicit receiver）
                    Column {
                        AnimatedVisibility(
                            visible = !anchored,
                            enter = fadeIn(tween(Motion.enter)) + slideInVertically(tween(Motion.enter)) { it / 2 },
                            exit = fadeOut(tween(Motion.exit)),
                        ) {
                            BackToBottomButton(
                                following = stickToBottom,
                                onClick = {
                                    stickToBottom = true
                                    scope.launch {
                                        // turns.size 是脚注前一项；配合负偏移即"贴到绝对底部"
                                        listState.animateScrollToItem(turns.size, STICK_TO_BOTTOM_OFFSET)
                                    }
                                },
                            )
                        }
                    }
                }
                // ── 输入区：浮在列表与底栏之上 ──
                //
                // 位置 = 底栏栏高 + 一点呼吸。因为列表已经铺到屏幕底部，
                // 这里不再需要"整列上移"，也就不会在输入区与底栏之间留下死白。
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = LocalBottomBarInset.current + skin.spaceSm)
                        .onSizeChanged { composerHeightPx = it.height },
                ) {
                    InputRow(
                        query = query,
                        onQueryChange = { query = it },
                        isStreaming = state.isStreaming,
                        onSend = {
                            val text = query.trim()
                            if (text.isNotEmpty()) {
                                query = ""
                                viewModel.send(text)
                            }
                        },
                        onStop = { viewModel.stop() },
                    )
                }
            }
        }
    }
}

/**
 * 「回到底部」。
 *
 * 存在的理由：流式回答里用户往上一滑，跟随就该停 —— 但停了之后得有个明确的
 * 回到最新的入口，否则用户只能自己一路滑回去。[following] 为 false 时带一个小圆点，
 * 提示"下面还有新内容"，这也是它值得点的理由。
 */
@Composable
private fun BackToBottomButton(following: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderDivider),
        shadowElevation = 3.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!following) {
                Box(modifier = Modifier.size(6.dp).background(colors.accentMain, CircleShape))
                Spacer(Modifier.width(skin.spaceSmall))
            }
            Icon(
                imageVector = Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = colors.accentMain,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(skin.spaceMicro))
            Text("回到底部", fontSize = 12.sp, color = colors.accentMain)
        }
    }
}

/**
 * 回答下方的**追问 chips**。
 *
 * ## 为什么是横滑而不是换行
 *
 * 5 条问题换行会占三到四行，把刚看的那段回答往上顶 —— 而它出现在这里的前提正是
 * "用户刚读完一段回答"。横滑只占一行，也顺便暗示"还有更多"。
 *
 * ## 候选词从哪来
 *
 * 与空态欢迎页**共用** `ASSISTANT_PROMPTS`（唯一真源）。两处各写一份的后果不是
 * "多打几个字"，而是漂移：空态推荐问 A、追问推荐问 B，而且删一个问题必然漏掉一处。
 *
 * ⚠️ 列表里的每一条都必须**今天就能真答出来**，理由见 `ASSISTANT_PROMPTS` 的 KDoc。
 *
 * ⚠️ 标 `internal` 而非 `private`：它的宿主是整页 `AssistantScreen`（要 ViewModel 与 DI 容器），
 * 快照渲不动整页 —— 所以像 `GradeEvidenceTable` 那样抽出来单独渲，顺带能断言触摸高度。
 */
@Composable
internal fun FollowUpChips(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
    ) {
        ASSISTANT_PROMPTS.forEach { prompt ->
            Surface(
                shape = RoundedCornerShape(50),
                // 和空态欢迎页的彩色建议卡呼应：主色浅底 + 同色描边 + 一颗小火花，
                // 让"这是一条可以直接问的问题"比白底灰字更明确。
                color = colors.accentMain.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, colors.accentMain.copy(alpha = 0.18f)),
                modifier = Modifier
                    // ⚠️ 触摸高度**写死下限**而不是靠内边距估：chip 天然长得矮，
                    // 而 44dp 是本项目的无障碍底线（见 `UI验收手册`）。
                    // 估算值（12sp 文字 + 上下 12dp）在系统字号调大后会变，
                    // 而快照与语义断言都看不出来 —— 会话列表的气泡关闭键踩过同一个坑。
                    .heightIn(min = 44.dp)
                    .clickable { onPick(prompt.title) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = skin.spaceMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = colors.accentMain,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(skin.spaceSmall))
                    Text(
                        text = prompt.title,
                        fontSize = 12.sp,
                        // 标签文字用 textPrimary 而不是主色：主色在 12sp 下对比度
                        // 约 4.3:1，压线；颜色信号交给底色与火花图标承担。
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotConfiguredBanner(onOpenSettings: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(skin.radiusChip),
        color = colors.surfaceHighlight,
        border = BorderStroke(1.dp, colors.accentMain.copy(alpha = 0.18f)),
    ) {
        Column(modifier = Modifier.padding(skin.spaceCompact)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 图标块：让这条横幅看起来是一张"待办卡片"，而不是一块提示文字。
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accentMain.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = colors.accentMain,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(skin.spaceSm))
                Text("还没配置 AI 模型", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            }
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                "鲤鱼需要一个大模型才能对话。支持任何 OpenAI 兼容接口（DeepSeek、通义、Kimi 等），需要你自己的 API Key。",
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.accentMain,
                modifier = Modifier.clickable(onClick = onOpenSettings),
            ) {
                Row(
                    modifier = Modifier.padding(start = skin.spaceLg, end = skin.spaceMd, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "去配置",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        // 主色实底上的字**必须**显式用白色。
                        // 这里原先读的是 `MaterialTheme.colorScheme.inverseOnSurface`，
                        // 而本项目 8 套配色只定义了 light/dark 的主角色，`inverseOnSurface`
                        // 一路是 Material 的默认值 —— 浅色默认近白（碰巧能看），
                        // 深色默认是 #313033，于是深色主题下就成了"深灰字压在主题色上"。
                        color = Color.White,
                    )
                    Spacer(Modifier.width(skin.spaceXs))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}
