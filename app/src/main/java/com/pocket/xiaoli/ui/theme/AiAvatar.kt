package com.pocket.xiaoli.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.data.agent.AgentPhase
import kotlinx.coroutines.delay

// ⚠️ 这个文件里**不该再有**别的助手常量：
// - 头像的**状态**（IDLE / THINKING / TOOL / ERROR）已搬到 `data/agent/AgentPhase`
//   —— 它描述的是 Agent 生命周期、且 DI 容器要持有一份，住在 UI 层会让依赖方向倒置
//   （`di/AppContainer` 会被迫 import `ui.theme`）；
// - 助手的**默认名**已搬到 `data/agent/AssistantIdentity`，同理；
// - 形象（画哪条鱼、什么颜色）已搬到 `ui/theme/Mascot.kt` + `MascotAvatar.kt`。
// 留在这里的只有"光环怎么跟随状态"，那才是这个组件真正的职责。

/**
 * 鲤鱼头像 = **状态光环** + **吉祥物**。
 *
 * 状态通过光环颜色 + 动效表达，吉祥物本身的形状与配色由用户选（见 [MascotLook]）——
 * "此刻在干什么"与"我长什么样"是两件正交的事，分别由 [state] 与 [look] 表达。
 *
 * ## 为什么形象是从 [LocalMascotLook] 拿的，而不是参数
 *
 * 三处调用点（对话气泡 / 空态欢迎页 /「我的」徽标）都在很深的层级上，
 * 而设置页改了形象要让**它们全都能立刻变**。按参数穿透要改十几个签名
 * （项目为 `onComposerFocusChange` 踩过：结果只有一处生效）。
 * 需要画"非当前形象"的地方（设置页的形状宫格）显式传 [look] 覆盖即可。
 *
 * ## ⚠️ 光环的 520 / 900ms 是字面时长，**已被 `MotionTokenUsageTest` 登记**
 *
 * 它是"按阶段切换的呼吸节奏"（查工具比思考更急），不等于 [Motion.breath] 那一档。
 * 挪走或改成 token 会让那条登记失效（该测试有反向断言），改之前先读它的 KDoc。
 *
 * @param state 当前 Agent 状态
 * @param size  头像直径（吉祥物按它的 [MASCOT_BOX_RATIO] 画在圈内）
 * @param look  画哪个吉祥物，默认当前用户选的那个
 */
@Composable
fun AiAvatar(
    state: AgentPhase,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    look: MascotLook = LocalMascotLook.current,
) {
    val colors = LocalPocketXiaoliColors.current

    // 光环颜色按状态取语义色
    val ringColor: Color = when (state) {
        AgentPhase.IDLE -> colors.accentMain.copy(alpha = 0.35f)
        AgentPhase.THINKING -> colors.accentMain
        AgentPhase.TOOL -> colors.info
        AgentPhase.ERROR -> colors.danger
    }

    // 只有进行中的状态才脉冲；静息不闪，避免视觉噪声。
    // ⚠️ 系统要求"减少动画"时**一律不脉冲**：一个永不停止的呼吸缩放对前庭敏感用户
    // 是最该被关掉的东西，而它是无限循环、不会自己结束。见 `Motion.kt` 的 reduceMotion()。
    val pulsing = (state == AgentPhase.THINKING || state == AgentPhase.TOOL) && !reduceMotion()
    val transition = rememberInfiniteTransition(label = "aiAvatar")
    val pulse by transition.animateFloat(
        initialValue = if (pulsing) 0.86f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // 查工具比思考更"急"，节奏更快
            animation = tween(if (state == AgentPhase.TOOL) 520 else 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aiAvatarPulse",
    )

    val scale = if (pulsing) pulse else 1f
    val bgAlpha = if (pulsing) 0.22f * pulse + 0.06f else 0.14f

    // 「干完了」也要有反应：从忙（思考 / 查工具）回到静息的那一下，演一遍收工动作
    //（DS 的 `bt-done`：往上一提 + 眯一下眼）。只演一遍，演完就静。
    //
    // ⚠️ 只在**真的发生切换**时播：`previous` 的初值就是首帧的 state，于是快照里
    // "直接以 IDLE 组合出来"的头像一次都不会播（与 `MascotAvatar` 一次性动作同一条规矩，
    // 踩坑记录见 `memory/03-pitfalls.md` §1.10）。
    var previous by remember { mutableStateOf(state) }
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        val wasBusy = previous == AgentPhase.THINKING || previous == AgentPhase.TOOL
        previous = state
        if (state == AgentPhase.IDLE && wasBusy) {
            celebrating = true
            delay(MASCOT_DONE_PERIOD_MS.toLong())
            celebrating = false
        } else if (state != AgentPhase.IDLE) {
            celebrating = false
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .background(ringColor.copy(alpha = bgAlpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // 吉祥物：脉冲时跟着一起淡一点，让"正在忙"这件事在鱼身上也读得出来
        MascotAvatar(
            size = size * MASCOT_BOX_RATIO,
            look = look,
            modifier = Modifier.alpha(if (pulsing) pulse else 1f),
            // 光环说"在忙"，形象本身也要跟着忙：光环比颜色更早被眼睛读到，
            // 但只有光在动会像"界面卡住了"，形象动起来才是"它自己在跑"（DS Bot 的做法）。
            motion = if (celebrating) MascotMotion.PROUD else mascotMotionFor(state),
        )
    }
}

/**
 * 阶段 → 形象动作。抽成函数是为了能被单测**逐项**覆盖（不必起整个对话）。
 *
 * [MascotMotion.SEARCH] / [MascotMotion.WAITING] 暂时没有对应的 [AgentPhase] ——
 * 阶段只有四个取值，而"翻资料"和"等你答话"目前都不改变阶段。它们由设置页的
 * 「动作试演」直接演示，等真有信号了再接到这里。
 */
internal fun mascotMotionFor(state: AgentPhase): MascotMotion = when (state) {
    AgentPhase.THINKING -> MascotMotion.PONDER
    AgentPhase.TOOL -> MascotMotion.WORKING
    // 出错不回到 IDLE（见 AgentPhase.ERROR 的 KDoc）：这里给它一次性的
    // "跳两下"，跳完就停，之后靠光环保持红色。
    AgentPhase.ERROR -> MascotMotion.HOP
    AgentPhase.IDLE -> MascotMotion.IDLE
}

/**
 * 吉祥物占用头像直径的比例。
 *
 * 形状数据画在 100×100 视口里，而鱼身只占中间约 44% 高、65%~78% 宽 —— 所以按比例铺满整圈
 * 会显得"顶到边"，这里收到 0.86：最宽的形状（海豚 78%）仍留出一圈光环的呼吸，
 * 而 64dp 的欢迎页头像里那条鱼能占满圈子的六成。
 */
private const val MASCOT_BOX_RATIO = 0.86f
