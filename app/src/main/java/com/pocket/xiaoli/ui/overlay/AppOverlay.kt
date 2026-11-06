package com.pocket.xiaoli.ui.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 页内浮层（"弹窗"）的宿主。
 *
 * ## 为什么不用 `Dialog` / `AlertDialog`
 *
 * 那两个都是**独立的系统窗口**。窗口一旦交给系统，背景就不再归我们管：
 * 有的 ROM（实测反馈来自 ColorOS / OriginOS）会给"带 dim 的弹窗窗口"自动加一层背景模糊，
 * 于是弹窗后面那张页面糊成一片 —— 但用户要的是「原界面清晰可见、弹窗盖在上面」。
 * 窗口级模糊我们既没法关、也没法调，所以只能不进那套机制。
 *
 * 改成页内浮层以后：背景就是页面本身（一个像素都没被动过），
 * 只有我们自己画的一层半透明遮罩把它压暗；模糊与否完全由我们决定。
 *
 * 附带好处：浮层在 `glassSource` 录制层**之外**（录制层只包 `NavHost`，见
 * `ui/navigation/AppNavigation.kt` 里 `Modifier.glassSource(...)` 那一行），
 * 所以浮层里的玻璃卡**可以**采样背后的页面 —— 将来想让弹窗卡片变成真玻璃，路是通的。
 *
 * ## 只支持一层
 *
 * 同时只保留最新的一层：第二层 `show` 会顶掉第一层。这不是省事，
 * 而是浮层没有系统窗口那样的栈语义 —— 真需要"弹窗上再弹窗"的场景在本项目里不存在，
 * 硬做栈只会让返回键行为变得难以推理。
 */
@Stable
class AppOverlayHostState {

    private var entry by mutableStateOf<Entry?>(null)

    internal val current: Entry? get() = entry

    /** 显示一层浮层。[onDismiss] 是**唯一的关闭口径**（返回键、点遮罩都走它）。 */
    fun show(onDismiss: () -> Unit, content: @Composable () -> Unit) {
        entry = Entry(onDismiss, content)
    }

    fun hide() {
        if (entry != null) entry = null
    }
}

internal class Entry(
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
)

/**
 * 浮层宿主的句柄。
 *
 * 用 `staticCompositionLocalOf` 而不是 `compositionLocalOf`：宿主实例在 App 启动时就定了、
 * 永不替换，按静态局部值处理可以省掉一层订阅（每次 `show/hide` 只要重绘浮层那一层）。
 */
val LocalAppOverlayHost = staticCompositionLocalOf<AppOverlayHostState?> { null }

/**
 * 浮层渲染层：放在页面**最上面**（`AppNavigation` 里底栏之后），因此盖得住底栏。
 *
 * 返回键由这里统一接管：浮层存在时它是最内层（最后注册）的处理器，
 * 所以会先于页面自己的返回逻辑生效，并且只调用浮层自己的 [Entry.onDismiss] ——
 * 这样"点遮罩关闭"和"按返回关闭"走的是同一段状态清理。
 */
@Composable
fun AppOverlayLayer(host: AppOverlayHostState) {
    val entry = host.current ?: return
    BackHandler { entry.onDismiss() }
    entry.content()
}

/**
 * 把一个浮层挂到宿主上，离开组合时自动收起。
 *
 * 用法（页面里）：
 * ```
 * if (someState) {
 *     AppOverlayDialog(onDismiss = { someState = false }) { dismiss ->
 *         MyDialogCard(onDismiss = dismiss)
 *     }
 * }
 * ```
 * `someState` 一变回 false，本组合退出 → `onDispose` 收起浮层，不需要手动 `hide()`。
 *
 * 注意 [content] 会拿到终止回调：卡片内部所有"关闭"都应该调它，
 * 而不是自己去改外面那个布尔值 —— 否则浮层会留在宿主里成为一层"看不见但吃得掉点击"的浮尸。
 */
@Composable
fun AppOverlayDialog(
    onDismiss: () -> Unit,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val host = LocalAppOverlayHost.current ?: return
    val dismiss by rememberUpdatedState(onDismiss)
    val body by rememberUpdatedState(content)

    DisposableEffect(host) {
        host.show(onDismiss = { dismiss() }) { body { dismiss() } }
        onDispose { host.hide() }
    }
}

/**
 * 页内底部抽屉：一层我们自己画的遮罩 + 从屏幕底部升起的卡片。
 *
 * ## 为什么不用 MIUIX 的 `WindowBottomSheet`
 *
 * 那个名字里的 `window` 就是问题本身 —— 它开的是**独立窗口**，窗口后面那一层归 ROM 管。
 * 同一件事在 `memory/03` §九 记过一次（当时是 `AlertDialog`，ColorOS / OriginOS 会把
 * 弹窗背景整页糊掉），后来为了换抽屉外壳，病又跟着回来了：用户看到一块"空白背景"，
 * 以为跳去了新页面。挂进浮层宿主以后，背景就是页面本身，压暗多少由我们说了算。
 *
 * ## 顺带把测试补回来
 *
 * 窗口式抽屉在 JVM 快照里**抓不到** —— 所以那四张抽屉此前一张基线都没有，
 * 退化成空白也没有任何东西变红。页内浮层能截，视觉与点击从此钉得住。
 *
 * [dismissible] = false 时，点遮罩、按返回、内容里任何"关闭"走的都是**同一个闸门**
 * （强制更新那条要求关不掉；能单点关掉就不叫闸门）。
 *
 * ⚠️ 已知取舍：出场有上滑动画，**退场没有** —— 宿主是"立即移除"模型，
 * 要做退场动画得先给浮层加一个"正在关闭"的状态，那是另一件事。
 */
@Composable
fun AppOverlaySheet(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val close: () -> Unit = if (dismissible) onDismiss else ({})
    AppOverlayDialog(onDismiss = close) {
        val skin = LocalSkin.current
        val colors = LocalPocketXiaoliColors.current
        // 第一帧还没升起，下一帧才置真 —— AnimatedVisibility 需要一次 false→true 才播入场动画
        var raised by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { raised = true }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 遮罩自己没有文字，测试要靠这个标签点到它（点抽屉里不算关）
                    .testTag("app-overlay-scrim")
                    .clickable(onClick = close)
                    // 半透明黑，不是模糊也不是实心一块：原来的界面必须透得过去（用户口径）
                    .background(Color.Black.copy(alpha = if (raised) 0.42f else 0f))
            )
            AnimatedVisibility(
                visible = raised,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(colors.surfaceCard)
                        // 长列表（方案、菜单项）到此封顶，里面自己滚
                        .heightIn(max = 620.dp)
                ) {
                    // 拖拽条只是装饰性的抽屉记号：真正的关闭是点遮罩 / 返回键 / 内容里的按钮，
                    // 跟手下拉需要嵌套滚动，留给以后（见上面的退场动画同一条）
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = skin.spaceSm)
                            .width(32.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(colors.borderDivider)
                    )
                    Text(
                        title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(
                            start = skin.spaceLg,
                            end = skin.spaceLg,
                            top = skin.spaceXs,
                            bottom = skin.spaceSm,
                        ),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 左右留白由外壳给（四张抽屉的正文原来都没有自己的水平内边距，
                            // 是窗口式外壳替它们加的）
                            .padding(horizontal = skin.spaceLg)
                            .padding(bottom = skin.spaceMd),
                        // ⚠️ 这里**不能再套 verticalScroll**：四张抽屉里有三张正文自己会滚，
                        // 滚动容器套滚动容器 = 内层拿到无限高约束，Compose 直接抛异常。
                        // 能滚是因为上面的 `heightIn(max = 620.dp)` 给正文划了上限，
                        // 正文那个 verticalScroll 拿到的是有界高度。
                        content = content,
                    )
                }
            }
        }
    }
}
