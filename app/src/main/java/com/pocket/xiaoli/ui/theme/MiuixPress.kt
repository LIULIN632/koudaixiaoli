package com.pocket.xiaoli.ui.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.TiltFeedback

/**
 * 给任意可点元素加上 **MIUIX 的按压反馈**。
 *
 * ## 为什么需要它
 *
 * Compose 的 `Modifier.clickable` 默认只给一个 ripple（水波纹）。本项目里有些可点元素
 * 连 ripple 都没有（例如首页课程卡片里的「去上课」胶囊、SectionHeader 的尾部动作文字），
 * 用户点了之后**没有任何视觉回应**，只有跳转发生 —— 这是真实的交互缺陷。
 *
 * MIUIX 的 [SinkFeedback]（按下时轻微下沉）与 [TiltFeedback]（按触点轻微倾斜）
 * 比 ripple 更贴近系统原生手感，也与已换成 MIUIX `Card` 的卡片风格一致。
 *
 * ## 用法
 *
 * ```kotlin
 * Text("A101", modifier = Modifier.miuixPress { onGoToClass(room) })
 * ```
 *
 * ⚠️ MIUIX 的 `Card(onClick = ...)` **已内置**按压反馈，不要重复叠加。
 *
 * ## 实现要点
 *
 * - **[onClick] 必须放在参数列表最后**，否则 `Modifier.miuixPress { ... }` 的尾随 lambda
 *   会绑定到它前面那个参数上（Compose 惯例：回调放最后就是为了尾随 lambda）。
 * - 用 [composed] 是因为内部要 `remember` 一个 [MutableInteractionSource]；
 *   每次重组新建它会导致按压状态丢失、反馈闪烁。
 * - `indication` 传 [SinkFeedback]/[TiltFeedback]（二者都是 `IndicationNodeFactory`），
 *   即**用 MIUIX 反馈替代默认 ripple**；[PressFeedbackType.None] 时退回 [LocalIndication]。
 * - `@SuppressLint("ModifierFactoryUnreferencedReceiver")`：`composed` 工厂不读取接收者，
 *   lint 会误报；MIUIX 自己的 `Modifier.overScrollVertical` 等同样处理。
 */
@SuppressLint("ModifierFactoryUnreferencedReceiver")
fun Modifier.miuixPress(
    type: PressFeedbackType = PressFeedbackType.Sink,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val feedback = remember(type) {
        when (type) {
            PressFeedbackType.None -> null
            PressFeedbackType.Sink -> SinkFeedback()
            PressFeedbackType.Tilt -> TiltFeedback()
        }
    }
    this.clickable(
        interactionSource = interactionSource,
        indication = feedback ?: LocalIndication.current,
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * 底栏 tab 按下时，图标与文字的透明度。
 *
 * 抄 MIUIX `NavigationBar` 的做法（`SelectedPressedAlpha = 0.5f`、
 * `UnselectedPressedAlpha = 0.6f`、静息 `UnselectedAlpha = 0.4f`）：**按下就在同一帧变淡**，
 * 而不是等 ripple 慢慢长出来 —— 浅色玻璃栏上 ripple 几乎看不见，用户会以为"点了没反应"。
 * 取 0.6（未选中档）是因为本项目的底栏内容本来就是满不透明的，0.5 会显得过黑。
 */
const val PRESSED_CONTENT_ALPHA = 0.6f

/**
 * 给**已经有** `clickable` / `combinedClickable` 的行换一套按压反馈（只换 `indication`）。
 *
 * 用在那些已经带了长按、`enabled`、自定义 `interactionSource` 的行上 —— 那种行不应该为了
 * 一点下沉反馈去改写成 [miuixPress]。和 [miuixPress] 用的是同一枚 [SinkFeedback]，
 * 手感一致，两处只需改一处。
 */
@Composable
fun rememberSinkIndication(): IndicationNodeFactory = remember { SinkFeedback() }

/**
 * 底栏一格按下时，内容该用多少透明度。
 *
 * 三处底栏（`SoftBottomBar` / `BottomBarTab` / `GlassBottomTab`）共用这一条规则，
 * 免得将来只改了一处、另两处还是旧手感。鲤鱼格返回 1f：[isAssistant] 那一格的反馈是
 * 形象自己的按压回弹（`AssistantTabIcon` 的 `snapTo(0.88f)`），再叠一层变淡会显脏。
 */
internal fun tabContentAlpha(pressed: Boolean, isAssistant: Boolean): Float =
    if (pressed && !isAssistant) PRESSED_CONTENT_ALPHA else 1f
