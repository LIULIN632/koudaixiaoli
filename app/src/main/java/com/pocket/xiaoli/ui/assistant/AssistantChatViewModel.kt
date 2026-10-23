package com.pocket.xiaoli.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.agent.AgentEvent
import com.pocket.xiaoli.data.agent.AssistantChatRepository
import com.pocket.xiaoli.data.agent.AssistantConversation
import com.pocket.xiaoli.data.agent.AssistantLlmConfig
import com.pocket.xiaoli.data.agent.ChatMessage
import com.pocket.xiaoli.data.agent.ChatRole
import com.pocket.xiaoli.data.agent.ERROR_MARKER_TOOL_NAME
import com.pocket.xiaoli.data.agent.TextCoalescer
import com.pocket.xiaoli.data.agent.withoutErrorMark
import com.pocket.xiaoli.data.knowledge.AssistantCard
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.data.agent.AgentPhasePolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 流式文本的节拍间隔（毫秒）。
 *
 * 50ms ≈ 3 帧：攒下来的通常只有一两个 token，观感与逐字输出无法区分，
 * 但界面状态更新的次数从"每个 token 一次"降到"每 20 次/秒"。
 *
 * 为什么不设得更长（比如 100ms）：中文快模型下 100ms 能攒到 5~8 个字，
 * 那就真的能看出"一块一块蹦"了。
 */
private const val FLUSH_INTERVAL_MS = 50L

/**
 * 「正在查」临时条目的固定 id。
 *
 * 为什么要让它进消息列表而不是像以前那样在列表外单独挂一条：
 * 它在时间线上真实地插在工具开始与结束之间，只有进入列表才能保证
 * 脚注区的顺序与"最后一轮"的判定不出错（见 AssistantScreen 的 toTurns）。
 *
 * `internal` 而非 private：展示层要按它把这条占位认出来（不显示名字、不计入项数）。
 */
internal const val RUNNING_TOOL_ID = "__running_tool__"

/**
 * 界面上一条消息。
 *
 * ⚠️ 这是**扁平、忠实于历史顺序**的列表：不在这里做「一轮一条目」的分组。
 * 分组是纯展示问题（把连续的工具条目折进回答末尾），放在 `AssistantScreen` 里算，
 * 这样 ViewModel 的状态与落库顺序始终一一对应，不会出现"界面上合并了、
 * 落库时序却丢了"的双口径。
 */
data class AssistantUiMessage(
    val role: ChatRole,
    val content: String,
    /** 工具名（仅 TOOL 角色），用于展示友好名称。 */
    val toolName: String? = null,
    /** 该条是否还在流式输出中（仅最后一条 assistant 可能为 true）。 */
    val streaming: Boolean = false,
    /** 该条是不是错误卡片（[content] 是可读文案，完整报错见 [errorDetail]）。 */
    val isError: Boolean = false,
    /** 仅 TOOL：这次执行真的失败了（未知工具 / 执行体抛异常），界面用警示色而非成功色。 */
    val toolFailed: Boolean = false,
    /**
     * 仅 ASSISTANT：本轮的思考链。
     *
     * ⚠️ 刻意**不落库**：它没有长期价值（重看一遍推理过程没人会做），
     * 而且会白占上下文预算。所以重启 App 后旧回复的思考栏会消失，这是设计而不是 bug。
     */
    val reasoning: String = "",
    /**
     * 仅 ASSISTANT：思考链**开始到达**的时刻（毫秒）。
     *
     * 用来算"思考了 N 秒" —— 这是 DeepSeek 那个摘要栏最有用的一处信息：
     * 用户等的就是这个过程的耗时，不给数字他只会觉得"卡了很久"。
     * 0 表示这一轮没有思考链（服务商不回 `reasoning_content`）。
     */
    val reasoningStartedAt: Long = 0L,
    /**
     * 仅 ASSISTANT：思考链**结束**的时刻（毫秒）。
     *
     * 判据是正文开始到达（模型想完了才会说话）。0 表示还在思考或没有思考链。
     */
    val reasoningEndedAt: Long = 0L,
    /** 仅错误卡片：原始报错全文（HTTP 体、异常信息），默认收起，点「详情」才看。 */
    val errorDetail: String? = null,
    /** 仅错误卡片：可以「重试」—— 条件是出错的那一轮有用户原话可重发。 */
    val needsRetry: Boolean = false,
    /**
     * 仅 TOOL：该工具产出的**结构化卡片**（成绩的统计或明细）。
     *
     * ⚠️ **刻意不落库**（与 [reasoning] 同一策略）：它由本机数据现算，
     * 历史回复重新打开时没有它，文字里的数字仍然在（"已存档 41 门课程，平均分 78.3…"）。
     * 要落库就得给 `assistant_messages` 加一列 JSON + 一次 Room 迁移，
     * 而卡片的价值主要在"刚查完那一眼"，不值得为它动表结构。
     */
    val card: AssistantCard? = null,
    /**
     * 稳定标识，用作 LazyColumn 的 item key。
     *
     * 为什么需要它：聊天列表是**不断向下追加**的，若不给 key，Compose 只能按
     * 位置识别条目 —— 一旦历史被裁剪或最后一条被流式替换，位置就会错位，
     * 表现为滚动跳动、条目状态串位。
     *
     * ⚠️ 刻意放在**参数列表末尾**：本类有多处按位置构造
     * （`AssistantUiMessage(ChatRole.USER, input)`），把 id 插到前面会静默改变
     * 所有位置参数的语义，导致一堆类型不匹配。
     */
    val id: String = java.util.UUID.randomUUID().toString(),
)

/** 助手页整体状态。 */
data class AssistantUiState(
    val messages: List<AssistantUiMessage> = emptyList(),
    /** 正在执行的工具显示名，非空时界面展示"正在查…"。 */
    val runningTool: String? = null,
    val isStreaming: Boolean = false,
    val isConfigured: Boolean = false,
    val error: String? = null,
    /**
     * 会话切换计数器：每次切到别的会话 / 开新对话 / 删掉当前会话都 +1。
     *
     * 为什么需要它：[AssistantChatViewModel.currentConversationId] 是普通属性，
     * 读它**不会**触发重组。于是"顶栏该显示「新对话」还是「清空」"会因为
     * 读不到变化而停在错误的一侧 —— 切完会话回到鲤鱼页，顶栏还是旧的判断。
     *
     * 为什么不用一个 `isNewConversation: Boolean`：那种布尔量在"切到 A 再切到 B"
     * 时值不变，反而丢了信息。计数器是单调的，任何一次切换都能被观察到。
     */
    val conversationNonce: Int = 0,
)

/**
 * 助手对话 ViewModel：把 [com.pocket.xiaoli.data.agent.AssistantAgentLoop] 的事件流
 * 转成可渲染的界面状态，并负责会话落库与恢复。
 *
 * 与旧的「正则路由」实现的关系：旧的 `AssistantToolRouter`（关键词 `when` 分发）已删除 ——
 * 路由职责被 LLM 的 function calling 取代。但 `AssistantTools` / `AssistantFormatters`
 * **保留且仍在使用**：它们现在是被 LLM 调用的工具实现（见 `AssistantToolBindings`），
 * 不再是 UI 的直接入口。这保证了旧能力零损失。
 */
class AssistantChatViewModel(
    private val container: AppContainer,
    private val repository: AssistantChatRepository,
    val config: AssistantLlmConfig,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState(isConfigured = config.isConfigured))
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    /** 当前会话 id；首次发消息时才创建，避免打开页面就产生空会话。 */
    private var conversationId: Long? = null

    /**
     * 当前会话 id（供会话列表标出"正在聊的是哪条"）。
     *
     * 暴露成属性而不是塞进 [AssistantUiState]：它不是界面**状态**，
     * 而是"消息列表属于哪个会话"的身份。放进 state 会让每次变更都要走一遍
     * 数据类的 copy，而它本身几乎不变。
     */
    val currentConversationId: Long? get() = conversationId

    /** 正在进行的对话任务，用于「停止」。 */
    private var runningJob: Job? = null

    /** 该会话此前已持久化的消息（不含本轮），作为每次请求的上下文。 */
    private var history: List<ChatMessage> = emptyList()

    /**
     * 最近一次发送的用户原话。
     *
     * 只服务于「重试」：整轮失败（网络断、401、服务端 5xx）时用户想原样再来一次，
     * 不应该逼他把自己那句话重新打一遍。成功一轮后清空，避免拿旧原话误重发。
     */
    private var lastUserInput: String? = null

    /**
     * 「正在换会话」标志。
     *
     * 读库与写界面不在同一帧：读库期间 init 里的"续接最近会话"可能正好完成，
     * 把用户刚选的会话覆盖掉。这个标志让所有异步载入路径都能看见"有人在改会话"。
     */
    private var opening = false

    init {
        // 续接最近一次会话，避免每次进来都是空的
        viewModelScope.launch {
            val latest = repository.latestConversationId() ?: return@launch
            // 读库期间用户可能已经点了「新对话」或切到别的会话：那样就不能再把它覆盖回去
            if (opening || conversationId != null) return@launch
            opening = true
            conversationId = latest
            // 刻意保留 ERROR_MARKER_TOOL_NAME 标记：界面要靠它把失败的历史渲染成错误卡片。
            // 摘标记的动作在**出口**（AssistantAgentLoop 拼请求时），不在入口 ——
            // 这样同一个内存对象既能服务 UI，又不会污染模型上下文。
            history = repository.loadMessages(latest)
            _state.update { it.copy(messages = history.toUiMessages()) }
            opening = false
        }
        refreshConfigured()
    }

    /** 设置页改完配置后调用，刷新"是否可用"状态。 */
    fun refreshConfigured() {
        _state.update { it.copy(isConfigured = config.isConfigured) }
    }

    /**
     * 发送一条用户消息并驱动 Agent 直到出结果。
     *
     * 并发保护：正在流式时忽略新的发送（界面也会禁用发送按钮），
     * 避免两个循环同时往同一会话追加消息导致上下文错乱。
     *
     * [annex] 是**只给模型看**的本地快照（主动气泡点进来时带的那件事）。
     * 它不进聊天气泡、也不落库 —— 落库与展示都只用 [text]。
     * ⚠️ 「重试」重发的是 [text]（没有快照）：用户点的确实是同一句话，
     * 而快照描述的是"点气泡那一刻的缓存"，重试时它可能已经过时。
     */
    fun send(text: String, annex: String? = null) {
        val input = text.trim()
        if (input.isEmpty() || _state.value.isStreaming) return

        // 记下这一轮的用户原话：失败时「重试」要原样重发
        lastUserInput = input

        _state.update {
            it.copy(
                messages = it.messages + AssistantUiMessage(ChatRole.USER, input),
                error = null,
                isStreaming = true,
                runningTool = null,
            )
        }
        // 同步全局 Agent 阶段：「我的」页的鲤鱼光环会变成呼吸脉冲，
        // 让用户在别的 Tab 也能感知"助手正在干活"。
        // 阶段策略集中在 `AgentPhasePolicy`（可单测），不在这里写散装的 when。
        container.setAiAvatarPhase(AgentPhasePolicy.onTurnStart())

        runningJob = viewModelScope.launch {
            val loop = container.newAssistantAgentLoop()
            val turnMessages = mutableListOf<ChatMessage>()
            // 本轮回答的**本地真相**：不依赖界面状态，落库与判空都用它
            val answer = StringBuilder()
            // 本轮是否以失败告终。用 flag 而不是读 `_state.value.error`：
            // error 是给「我的」页鲤鱼光环看的展示态，会随下一轮发送被清空，
            // 拿它当控制流会让收尾逻辑依赖一个展示字段（历史上就是这种耦合）。
            var errored = false

            // ── 流式合并 ──
            // localMessages 是本轮界面状态的**唯一写者**：代理事件只改它，绝不动 _state。
            // 只有节拍到了（或顺序敏感的控制事件/收尾）才把整批一次性推给界面。
            // 这样界面状态更新的次数从"每个 token 一次"降到"每 50ms 一次"。
            val localMessages = _state.value.messages.toMutableList()
            val coalescer = TextCoalescer<String, AgentEvent>(
                merge = { chunks -> chunks.joinToString("") },
            )
            // 思考链计时（整轮共用一对）：见 AssistantUiMessage.reasoningStartedAt 的说明
            var reasoningStartedAt = 0L
            var reasoningEndedAt = 0L

            suspend fun applyBlock(block: TextCoalescer.Flush<String, AgentEvent>) {
                when (block) {
                    is TextCoalescer.Flush.Text -> {
                        // 节拍到了：把正在流的那段**定稿**，新块开一条新消息。
                        //
                        // 为什么不能直接往后追加：一轮里模型可能"说半句 → 调工具 → 再说"，
                        // 中间夹着 TOOL 消息。若一律追加到最后一条 assistant 上，
                        // 工具段落之后的那半句会被拼到**工具之前**去，顺序就错了。
                        sealStreaming(localMessages)
                        val now = System.currentTimeMillis()
                        val hasReasoning = !block.reasoning.isNullOrEmpty()
                        // 思考链计时：第一次收到画开始时间，正文一来就画结束时间。
                        // 正文到达 = 模型想完了（它想完才会开口）—— 这是唯一可靠的判据，
                        // 服务商不会专门发一个"思考结束"的帧。
                        if (hasReasoning && reasoningStartedAt == 0L) reasoningStartedAt = now
                        if (block.chunk.isNotEmpty() && reasoningStartedAt > 0L && reasoningEndedAt == 0L) {
                            reasoningEndedAt = now
                        }
                        localMessages += AssistantUiMessage(
                            role = ChatRole.ASSISTANT,
                            content = block.chunk,
                            reasoning = block.reasoning.orEmpty(),
                            reasoningStartedAt = reasoningStartedAt,
                            reasoningEndedAt = reasoningEndedAt,
                            streaming = true,
                        )
                    }

                    is TextCoalescer.Flush.Events -> block.events.forEach { event ->
                        when (event) {
                            is AgentEvent.ToolStarted -> {
                                sealStreaming(localMessages)
                                // 头像切到「查工具」态：蓝色光环 + 更快的脉冲。
                                // 用 `?.let` 而不是直接赋值：该策略对"不改变阶段"的事件
                                // 返回 null（正文/思考链增量、工具完成都属于此类）。
                                AgentPhasePolicy.onEvent(event)?.let(container::setAiAvatarPhase)
                                // 临时条目：让"正在查"参与同一份消息列表的顺序，
                                // 这样脚注区不需要在列表外单独挂一条（历史实现是列表外挂）
                                localMessages += AssistantUiMessage(
                                    role = ChatRole.TOOL,
                                    content = "",
                                    toolName = event.displayName,
                                    id = RUNNING_TOOL_ID,
                                )
                                pushUi(localMessages, runningTool = event.displayName)
                            }

                            is AgentEvent.ToolFinished -> {
                                localMessages.removeAll { it.id == RUNNING_TOOL_ID }
                                // 工具结果作为独立条目展示：用户能看到"它查了什么、查到什么"。
                                // 展示层会把连续的 TOOL 条目折进同一轮回答的脚注区。
                                localMessages += AssistantUiMessage(
                                    role = ChatRole.TOOL,
                                    content = event.preview.ifBlank { "（无内容）" },
                                    toolName = event.displayName,
                                    toolFailed = event.failed,
                                    card = event.card,
                                )
                                pushUi(localMessages, runningTool = null)
                            }

                            // 整轮正常结束。**不能**忽略：节拍器可能刚好还没把最后几个
                            // token 推出去，只看 _state 会把结尾吞掉（历史上就是这类时序漏字）。
                            is AgentEvent.Answer -> {
                                finalizeAnswer(localMessages, answer.toString().ifBlank { event.text })
                                pushUi(localMessages, runningTool = null, isStreaming = false)
                            }

                            // 整轮失败
                            is AgentEvent.Error -> {
                                errored = true
                                finalizeError(localMessages, display = event.message, detail = event.detail)
                                pushUi(localMessages, runningTool = null, isStreaming = false, error = event.message)
                            }

                            // 文本增量走缓冲，不会作为事件到这里；真出现也只是防御性忽略
                            else -> Unit
                        }
                    }
                }
            }

            suspend fun flush() {
                coalescer.canFlush().forEach { applyBlock(it) }
            }

            // 节拍器：没有待发内容时什么都不做（不产生状态更新）。
            // 用 while+delay 而不是构造一个流：读起来直白，也不必为"每 50ms 一个单位"引入流。
            val heartbeat = launch {
                while (true) {
                    delay(FLUSH_INTERVAL_MS)
                    if (coalescer.hasPending) flush()
                }
            }

            val loopRunner = launch {
                loop.run(
                    userInput = input,
                    history = history,
                    // 每轮结束把最新完整消息列表同步过来，供落库使用
                    onMessagesChanged = { msgs ->
                        turnMessages.clear()
                        turnMessages += msgs
                    },
                    llmAnnex = annex,
                ).collect { event ->
                    when (event) {
                        is AgentEvent.TextDelta -> {
                            // 累积给落库用的全文，界面侧只入缓冲（等节拍）
                            answer.append(event.text)
                            coalescer.onText(event.text)
                        }

                        // 思考链同样入缓冲，但它**不进 answer**（那不是回答正文）
                        is AgentEvent.ReasoningDelta -> coalescer.onReasoning(event.text)

                        // 控制事件一律**按到达顺序入队**，绝不插队到待发文本之前：
                        // 否则工具脚注会被画在那半句前面，一轮一条目的版式立刻错乱
                        else -> coalescer.onEvent(event)
                    }
                }
            }

            try {
                // 等本轮跑完。collect 抛错也要落到 finally，不能丢收尾
                loopRunner.join()
                // 正常结束：冲掉缓冲后，末尾的 Answer/Error 已被按顺序应用
                flush()
            } finally {
                // 关键：用户点「停止」会取消本协程。若不做保护，落库会被跳过，
                // 用户发出的那句话就永久丢了（下次进来历史里没有）。
                // 用 NonCancellable 保证「已发生的对话」一定写进库。
                withContext(NonCancellable) {
                    heartbeat.cancel()
                    loopRunner.cancel()
                    // 用户停止时缓冲里可能还压着最后一两个字；forceFlush 保证不丢
                    flush()
                    // 取消的位置可能刚好卡在工具执行中：那条「正在查」占位必须清掉，
                    // 否则界面会永远停在转圈上，而且它会被当成一条真实工具消息落库
                    localMessages.removeAll { it.id == RUNNING_TOOL_ID }
                    if (_state.value.isStreaming) {
                        // 这条路径是「模型没走完就结束了」：用户点了停止，或循环被取消。
                        // 此刻屏幕上已经有正文，但**从没进过 turnMessages** ——
                        // 不补这一步，重启后整条回复会凭空消失（只活在那一屏内存里）。
                        finalizeAnswer(localMessages, answer.toString())
                        pushUi(localMessages, runningTool = null, isStreaming = false)
                        persistFinalAnswer(answer.toString())
                    }
                    // 头像归位：出错停在 ERROR 态（用户回到「我的」页能看到需要处理），
                    // 正常结束回到 IDLE。这条策略住在 `AgentPhasePolicy.onTurnEnd`，
                    // 判据是**本轮局部标志** `errored` —— 不能读 `_state.value.error`，
                    // 那是展示字段、会被下一轮发送清空（历史上就是这样耦合的）。
                    container.setAiAvatarPhase(AgentPhasePolicy.onTurnEnd(errored))
                    persistTurn(input, turnMessages, isErrorTurn = errored)
                }
            }
        }
    }

    /**
     * 把本地消息列表整体推给界面。这是本轮唯一触碰 [_state] 消息的地方。
     *
     * 三个收尾字段（流式标记、正在执行的工具、错误文案）必须**一起**提交：
     * 它们是同一次节拍的结果，分开写会让界面短暂处于自相矛盾的状态
     * （例如"已经结束了但还在转圈"）。
     */
    private fun pushUi(
        messages: List<AssistantUiMessage>,
        runningTool: String?,
        isStreaming: Boolean = true,
        error: String? = null,
    ) {
        _state.update {
            it.copy(
                messages = messages.toList(),
                runningTool = runningTool,
                isStreaming = isStreaming,
                error = error,
            )
        }
    }

    /** 把"正在流式的最后一条 assistant"定稿（去掉 streaming 标记）。 */
    private fun sealStreaming(messages: MutableList<AssistantUiMessage>) {
        val lastIndex = messages.indexOfLast { it.role == ChatRole.ASSISTANT && it.streaming }
        if (lastIndex >= 0) {
            messages[lastIndex] = messages[lastIndex].copy(streaming = false)
        }
    }

    /**
     * 结束流式：把最后一条 assistant 定稿，若模型没吐过字则补一条。
     *
     * 入参是**本轮本地消息列表**（不是 `_state`）：收尾必须与流式合并共用同一份列表，
     * 否则节拍器还没推出去的最后一小段文字会被这里的"从 _state 重读"覆盖掉。
     * 改完由调用方 [pushUi] 一次推给界面。
     */
    private fun finalizeAnswer(messages: MutableList<AssistantUiMessage>, text: String) {
        val lastIndex = messages.indexOfLast { it.role == ChatRole.ASSISTANT && !it.isError }
        if (lastIndex >= 0) {
            // 已有正文（无论还在流式还是已被节拍器定稿）→ 用完整全文覆盖，
            // 保证"界面显示的内容"与"落库的内容"完全一致。
            // ⚠️ 只改 content/streaming：copy 会保留 reasoning 等其它字段，
            // 别改成新建对象（那会把思考链悄悄丢掉）
            messages[lastIndex] = messages[lastIndex].copy(
                content = text.ifBlank { messages[lastIndex].content.ifBlank { "（没有返回内容）" } },
                streaming = false,
            )
        } else if (text.isNotBlank()) {
            messages += AssistantUiMessage(ChatRole.ASSISTANT, text, streaming = false)
        }
    }

    /**
     * 出错收尾。
     *
     * 与正常收尾的关键区别：**不回填成普通 assistant 气泡**。
     *
     * 为什么不能用普通气泡承载错误（岱宗盒子就是栽在这里）：错误气泡没有配色、
     * 不可点、没有重试入口，用户只看到一行红字；而它那份实现里
     * `errorMessage` 字段设了却零引用，等于死状态。这里把三件事一起做掉：
     * - [display] 与 [detail] 分开：卡片上只显示可读文案（限行），HTTP 体/异常全文
     *   塞进「详情」，不再是刷屏的一大坨
     * - `needsRetry`：整轮失败时给出「重试」，重发用户原话
     * - 若这一刻模型已经吐了半句话，那半句**保留**在卡片上方（打断不丢内容）
     *
     * 同样操作**本地列表**，理由见 [finalizeAnswer]。
     */
    private fun finalizeError(messages: MutableList<AssistantUiMessage>, display: String, detail: String) {
        // 与错误无关的暂存半句：定稿保留（用户看得到已经收到的部分）
        sealStreaming(messages)
        messages += AssistantUiMessage(
            role = ChatRole.ASSISTANT,
            content = display,
            // 错误标记写进 toolName（assistant 行上这个字段本来恒为空）——
            // content 保持干净的人类可读文案，落库后下次进页面照旧能还原成卡片。
            // 标记的定义与"出口摘除"都在 data/agent（ERROR_MARKER_TOOL_NAME）
            toolName = ERROR_MARKER_TOOL_NAME,
            isError = true,
            // 原文只留一份，且限长：错误卡片是卡片，不是日志转储
            errorDetail = detail.take(ERROR_DETAIL_LIMIT).takeIf { it.isNotBlank() && it != display },
            needsRetry = lastUserInput != null,
        )
    }

    /** 把本轮新增消息落库，并维护 history 与标题。 */
    private suspend fun persistTurn(
        userInput: String,
        turnMessages: List<ChatMessage>,
        isErrorTurn: Boolean,
    ) {
        runCatching {
            val id = conversationId ?: repository.createConversation().also { conversationId = it }
            // turnMessages 是「历史 + 本轮」，只取本轮新增部分，避免重复插入历史
            var newOnes = turnMessages.drop(history.size)
            // 失败轮要把最后那条 assistant 正文打上界面标记，下次进页面才能还原成
            // 错误卡片（含「重试」）。标记的语义见 ERROR_MARKER_TOOL_NAME
            if (isErrorTurn && newOnes.lastOrNull()?.role == ChatRole.ASSISTANT) {
                newOnes = newOnes.dropLast(1) + newOnes.last().copy(toolName = ERROR_MARKER_TOOL_NAME)
            }
            // 内部标记不该进请求体（模型会把它当工具名），统一在出口摘掉
            newOnes = newOnes.map { it.withoutErrorMark() }
            if (newOnes.isNotEmpty()) {
                repository.appendMessages(id, newOnes)
            } else {
                // 极端情况（循环没回传）：至少把用户消息存下来
                repository.appendMessages(id, listOf(ChatMessage(ChatRole.USER, userInput)))
            }
            repository.ensureTitle(id, userInput)
            history = repository.loadMessages(id)
        }
    }

    /**
     * 落库「本轮回答正文」。
     *
     * 只在**模型没走完就结束**时调用（用户点了停止 / 循环被取消）。
     * 为什么必须单独做这一步：Agent 循环只在模型明确结束一轮时才通过
     * `onMessagesChanged` 交付 assistant 消息；而被取消时循环直接退出，
     * 那句已经完整显示在屏幕上的回答**从没进过 turnMessages**。
     * 结果是"画面上有、重启后没有"，下一轮发给模型的上下文里也没有 ——
     * 模型会以为自己上一轮没说话。
     *
     * 正常结束那条路径**刻意不在这里落库**：那会和 [persistTurn] 产生两条并发的
     * 读-改-写，插入顺序变得不确定（谁先谁后取决于调度，无法用测试锁住）。
     * 让"循环交付的消息"只有一个写入口，路径才是可推理的。
     */
    private suspend fun persistFinalAnswer(text: String) {
        val body = text.trim()
        if (body.isBlank()) return
        runCatching {
            val id = conversationId ?: repository.createConversation().also { conversationId = it }
            repository.appendMessages(id, listOf(ChatMessage(ChatRole.ASSISTANT, body)))
            history = repository.loadMessages(id)
        }
    }

    /**
     * 停止当前生成。
     *
     * 只负责取消；收尾（定稿 + 落库）由 [send] 里的 finally 块完成 ——
     * 这里是异步的，若在此处也收尾会出现两次定稿竞争。
     */
    fun stop() {
        runningJob?.cancel()
        runningJob = null
    }

    /**
     * 清空当前会话并开一段新的（会**删除**库里的那条会话记录）。
     *
     * ⚠️ 界面必须给它二次确认：这是破坏性操作，而 [deleteConversation]（会话列表里删一条）
     * 是有确认的 —— 两条路径删的是同一种东西，一个确认一个不确认会造成"点一下聊天记录就没了"。
     */
    fun clearAll() {
        runningJob?.cancel()
        runningJob = null
        viewModelScope.launch {
            conversationId?.let { repository.deleteConversation(it) }
            conversationId = null
            history = emptyList()
            lastUserInput = null
            _state.update {
                AssistantUiState(
                    isConfigured = config.isConfigured,
                    conversationNonce = it.conversationNonce + 1,
                )
            }
        }
    }

    // ── 会话列表 ──

    /**
     * 全部会话（新的在前）。
     *
     * 直接暴露仓储的 Flow：会话列表是**响应式**的 —— 发一条消息会更新标题与时间，
     * 删一条会话会少一行，这些都不需要列表页自己去重读一遍。
     * （岱宗盒子用的是 JSON 分文件 + 手动重读，既要自己维护一致性，
     * 又因为裸 `writeText` 有"崩溃丢整个列表"的风险。）
     */
    fun conversations(): Flow<List<AssistantConversation>> = repository.observeConversations()

    /**
     * 切换到某个历史会话。
     *
     * 正在流式时**拒绝**切换（而不是先取消再切）：取消要落库、要清「正在查」占位、
     * 要写失败标记，一整套收尾动作还在跑；这时候把 messages 换掉，收尾就会写到新会话里去。
     * 界面会把入口禁用，这里只是兜底。
     */
    fun openConversation(id: Long, onBlocked: () -> Unit = {}) {
        if (id == conversationId) return
        if (_state.value.isStreaming) {
            onBlocked()
            return
        }
        viewModelScope.launch {
            // 顺序要紧：先立 opening，再改 conversationId ——
            // init 里那条"续接最近会话"会检查 opening，插不进来
            opening = true
            conversationId = id
            lastUserInput = null
            history = repository.loadMessages(id)
            _state.update {
                it.copy(
                    messages = history.toUiMessages(),
                    runningTool = null,
                    error = null,
                    isStreaming = false,
                    // 让顶栏读到"会话变了"（currentConversationId 是普通属性，读它不触发重组）
                    conversationNonce = it.conversationNonce + 1,
                )
            }
            opening = false
        }
    }

    /**
     * 开一段新对话。
     *
     * 刻意**不**落库空会话：打开页面就产生一条"新对话"记录会污染会话列表
     * （用户点三次就是三条空记录）。真正的会话行在 [persistTurn] 里、
     * 等第一句话发出去时才创建。
     *
     * [onBlocked] 的用途：流式期间不允许切（收尾还在跑，换掉消息列表会把收尾写进别的会话），
     * 但**不能静默**这么做 —— 用户点了没反应会以为按钮坏了。界面用它给一句提示。
     */
    fun startNewConversation(onBlocked: () -> Unit = {}) {
        if (_state.value.isStreaming) {
            onBlocked()
            return
        }
        // 顺序要紧：先立 opening，再改状态 —— 否则 init 里那条"续接最近会话"
        // 可能在中间插进来，把刚清空的界面又填回旧会话
        opening = true
        conversationId = null
        history = emptyList()
        lastUserInput = null
        _state.update {
            AssistantUiState(
                isConfigured = config.isConfigured,
                conversationNonce = it.conversationNonce + 1,
            )
        }
        opening = false
    }

    /**
     * 删除一个会话；若删的是当前会话，顺带清空界面。
     *
     * [onBlocked] 同 [startNewConversation]：流式期间拒绝并回告，不静默。
     */
    fun deleteConversation(id: Long, onBlocked: () -> Unit = {}) {
        if (_state.value.isStreaming) {
            onBlocked()
            return
        }
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (id == conversationId) {
                opening = true
                conversationId = null
                history = emptyList()
                lastUserInput = null
                _state.update {
                    AssistantUiState(
                        isConfigured = config.isConfigured,
                        conversationNonce = it.conversationNonce + 1,
                    )
                }
                opening = false
            }
        }
    }

    /**
     * 重试上一条失败的请求。
     *
     * 语义：**把失败的那一轮整轮重来**，不是"接着失败的地方继续"。
     * 因此先把失败卡片从界面上摘掉（库里那条 error 文本不动 —— 它是"这一轮发生过什么"
     * 的记录，重试成功后新回复会追加在它后面，历史仍然可追溯）。
     *
     * 决定重发什么的原则是「以这条错误卡片下方最近的用户原话为准」，
     * 而不是用 [lastUserInput] —— 用户可能已经翻到别处、甚至在错误之后又发了新消息
     * （失败卡片刻意**不置 isStreaming**，所以新消息是发得出去的）。
     */
    fun retryLast() {
        if (_state.value.isStreaming) return
        val msgs = _state.value.messages
        val lastErrorIndex = msgs.indexOfLast { it.isError }
        if (lastErrorIndex < 0) return
        val isLast = lastErrorIndex == msgs.lastIndex
        val input = if (isLast) {
            lastUserInput
        } else {
            msgs.subList(lastErrorIndex + 1, msgs.size)
                .lastOrNull { it.role == ChatRole.USER }?.content
        }
        if (input.isNullOrBlank()) return

        // 摘掉失败卡片（若它后面还有内容，说明用户已经继续对话，不要动别人的消息）
        if (isLast && !msgs[lastErrorIndex].streaming) {
            _state.update { s ->
                if (s.isStreaming) s else s.copy(messages = s.messages.filterIndexed { i, _ -> i != lastErrorIndex }, error = null)
            }
        }
        send(input)
    }

    /**
     * 重新生成上一条回答。
     *
     * 与 [retryLast] 的区别：
     * - [retryLast] 用于**失败**之后（把失败卡片摘掉再来一次）
     * - 本方法用于**成功但不满意**之后（"答得不好，换一个说法"）
     *
     * 做法与 DeepSeek 的"重新生成"一致：把上一次的回答从**界面**上换掉，
     * 用同一句提问重新请求。库里那条旧回答**不删** —— 它是"这一轮发生过什么"的记录，
     * 而且删了也无法回收已经花掉的 token。界面上会看到新回答接在旧的位置，
     * 历史仍然完整（下次进来两条都在，因为库里从来没删过）。
     *
     * ⚠️ 已知取舍：正因如此，重新生成后**当次会话里**看到的是新回答，
     * 而**重进页面**会看到新旧两条都在。要"只有一条"就得真删库里的行，
     * 那会让历史不可追溯。我选了不删，因为聊天记录的完整比界面干净更重要。
     */
    fun regenerate() {
        if (_state.value.isStreaming) return
        val msgs = _state.value.messages
        val input = msgs.lastOrNull { it.role == ChatRole.USER }?.content
            ?: lastUserInput
        if (input.isNullOrBlank()) return
        // 从最后一条用户消息往后全部撤掉（含回答、工具脚注、思考链、错误卡片），
        // 只留用户那句 —— 否则新一轮的回答会接在旧回答后面，看起来像"补充说明"
        val lastUserIndex = msgs.indexOfLast { it.role == ChatRole.USER }
        if (lastUserIndex < 0) return
        _state.update { s ->
            if (s.isStreaming) s else s.copy(
                messages = s.messages.take(lastUserIndex + 1),
                error = null,
            )
        }
        send(input)
    }

    private fun List<ChatMessage>.toUiMessages(): List<AssistantUiMessage> {
        // 失败轮能不能重试，取决于它**上方有没有用户原话**可以重发
        // （重试的语义是"把这一轮重来"）。冷启动时 lastUserInput 是空的，
        // 若不看这一点，会给出一颗按了没反应的「重试」—— 那比没有更糟。
        var lastUserId: String? = null

        return mapNotNull { msg ->
            when (msg.role) {
                ChatRole.USER -> AssistantUiMessage(ChatRole.USER, msg.content).also { lastUserId = it.id }
                // 空 assistant（模型只调了工具没说话）不留空气泡
                ChatRole.ASSISTANT -> if (msg.content.isBlank()) null else {
                    val isErr = msg.isErrorText()
                    AssistantUiMessage(
                        role = ChatRole.ASSISTANT,
                        content = msg.content,
                        isError = isErr,
                        needsRetry = isErr && lastUserId != null,
                    )
                }
                // 历史里的 tool 消息只在界面显示名字与首行摘要，完整内容不进 UI（模型仍能读到）
                ChatRole.TOOL -> AssistantUiMessage(
                    role = ChatRole.TOOL,
                    content = msg.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(60),
                    toolName = msg.toolName,
                    // 与 AgentLoop.isToolFailure 同一套文案特征（这里不能引用它的私有常量，
                    // 所以只认注册表那条唯一失败产出的开头）
                    toolFailed = msg.content.startsWith(TOOL_FAILURE_PREFIX),
                )
                ChatRole.SYSTEM -> null
            }
        }
    }

    /**
     * 判断一条库里的 assistant 消息是不是失败轮。
     *
     * 依据是 [ERROR_MARKER_TOOL_NAME] —— 标记由 [finalizeError] 写入，
     * 跟着历史一起落库，所以重启 App 后失败那一轮仍然渲染成错误卡片（可重试）。
     */
    private fun ChatMessage.isErrorText(): Boolean =
        role == ChatRole.ASSISTANT && toolName == ERROR_MARKER_TOOL_NAME

    private companion object {
        /** 「详情」里原文的显示上限：错误卡片不是日志转储，超长一律截断。 */
        const val ERROR_DETAIL_LIMIT = 800

        /**
         * [com.pocket.xiaoli.data.agent.AssistantToolRegistry] 执行失败文案的开头。
         * ⚠️ 改那边的文案要同步改这里，否则从历史恢复后失败会被显示成成功。
         */
        const val TOOL_FAILURE_PREFIX = "工具 "
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AssistantChatViewModel(
                container = container,
                repository = container.assistantChatRepository,
                config = container.assistantLlmConfig,
            ) as T
        }
    }
}
