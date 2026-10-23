package com.pocket.xiaoli.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 动效时长与曲线 —— 时长的**唯一来源**。
 *
 * ## 为什么要有它
 *
 * 改造前全 App 有 **26 处 `tween(...)` 直接写数字**，时长取值 120 / 150 / 180 / 260 /
 * 400 / 700 六种。散着写的直接后果不是"不好看"，而是**改不动**：
 * 想把整体节奏调快一点，得先 grep 出所有 `tween(`，再逐个判断哪个是"进场"、
 * 哪个是"退场"——而数字本身不携带语义。
 *
 * ## 取值是怎么定的（不是设计新值，是**归纳既有值**）
 *
 * | 名称 | 值 | 语义 | 为什么是这个值 |
 * |---|---|---|---|
 * | [exit] | 120ms | 退场（淡出 / 收起） | 既有用法最长出现的就是它；**退场要比进场快**，这是"消失要干脆"的通用做法 |
 * | [enter] | 150ms | 进场（淡入 / 滑入） | 同上 |
 * | [expand] | 180ms | 展开收起、路由转场 | 位移距离比淡入淡出更长，需要多一点时间；`AppNavigation` 的转场也用它 |
 * | [breath] | 700ms | 循环呼吸 | 配合 `RepeatMode.Reverse` 的往复周期，不是单次动画时长 |
 *
 * 全部**照抄既有数值**，所以把调用点接过来是**视觉零变化**的重构。
 *
 * ## ⚠️ 刻意**没有**纳入的两个孤值
 *
 * - **260ms**（`HomeScreen` 2 处）、**400ms**（`GradeDistributionChart` 1 处）：
 *   各自只有 1~2 处、语义尚未确认。强行归并到上面某一档**等于替它们做设计决定**，
 *   而那需要看过那两处动画的意图才能判断 —— 所以留到确认后再说，
 *   这里只登记不吞并（"看着不一样就统一"是把动效改坏的最快路径）。
 * - `AiAvatar` 的 520 / 900ms 是**按阶段取不同节奏**的（查工具比思考更急），
 *   属于[breath]的变体而非同一个值，也不吞并。
 *
 * ## 曲线
 *
 * 目前只有 [easing] 一条。循环动画（呼吸）**不用** spring：弹簧在往复循环里
 * 会有可见的过冲顿挫，配 `tween + RepeatMode.Reverse` 才是平稳的呼吸感。
 *
 * ## 如果将来某一套皮肤想要不同的动效节奏
 *
 * 那就把它变成 `SkinTokens` 的一个字段（和 `radiusCard` / `spaceMd` 同层），
 * **而不是**在这里加一个 `if (skin == MINIMAL)`。理由与色彩体系一致：
 * 皮肤的所有差异都必须由**数据**表达，不能由渲染层的分支表达。
 */
object Motion {

    /** 退场（淡出 / 收起）。比进场快 —— 消失要干脆。 */
    const val exit = 120

    /** 进场（淡入 / 滑入）。 */
    const val enter = 150

    /** 展开收起、路由转场。位移更长，所以比淡入淡出多一点时间。 */
    const val expand = 180

    /** 循环呼吸的往复周期。配合 `RepeatMode.Reverse` 使用，**不要**用 spring。 */
    const val breath = 700

    /** 统一缓动曲线。 */
    val easing = FastOutSlowInEasing
}

/**
 * 系统是否要求**减少动画**（Android 的"移除动画" / 开发者选项把动画缩放设为 0）。
 *
 * ## 为什么直接读 `Settings.Global.ANIMATOR_DURATION_SCALE`
 *
 * 一开始想用 Compose 的 `LocalMotionDurationScale`，但**这个项目用的 Compose 版本里没有它**
 * —— 实测 `androidx.compose.ui` 只有 `MotionDurationScale` 接口与
 * `platform/MotionDurationScaleImpl` 实现，没有对应的 CompositionLocal。
 * 所以直接读系统设置：这是那份数据的**源头**，也正好是 Compose 自己读的同一个键。
 *
 * ⚠️ 这里刻意**不去读 `MotionDurationScaleImpl`**（要靠反射拿内部实例，脆）。
 *
 * ## 它管不到协程 `delay()` —— 这是最要紧的一条
 *
 * Compose 的动画 API（`animateFloatAsState` / `rememberInfiniteTransition` …）
 * 由框架自己乘这个缩放，缩放为 0 时它们本来就停在原地。
 * 但**手写的 `while (true) { delay(...) }` 不会** —— 那是协程，不是动画框架。
 * 所以凡是自己数拍子的地方都必须显式调这个函数
 * （全 App 只有一处：`PromoBanner` 的自动轮播，它此前**没有任何开关能停**）。
 *
 * ## 为什么不用 `remember` 缓存
 *
 * 这个开关在**运行中可能被改**（用户切到设置里关掉动画再回来）。
 * 读一次 `Settings.Global` 是极廉价的 IPC-free 调用，没有缓存的必要；
 * 缓存反而会让"改了没生效"再次发生 —— 那正是本函数要修的病。
 */
@Composable
fun reduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * 按系统设置把一次动画时长改成 0（即时）或保持原值。
 *
 * 用在**自己传 `durationMillis` / `animationSpec`** 的地方 —— 那些写法把时长写死在
 * 代码里，看不出"这个动效愿不愿意为用户让路"。
 *
 * ## 为什么不直接 `if (reduceMotion()) 0 else x`
 *
 * 那样每个调用点都要写一遍分支，而这个分支的**判据**必须全 App 一致。
 * 收成一个函数，将来若要改成"减弱但仍保留一点动效"也只改这里。
 */
@Composable
fun motionAwareDuration(durationMillis: Int): Int =
    if (reduceMotion()) 0 else durationMillis

/**
 * 给 `animationSpec` 用的"尊重系统设置"版本。
 *
 * `rememberInfiniteTransition` 这类**无限循环**动画：系统要求减少动画时，
 * 直接返回 0 时长 —— 视觉上停在初始帧（而不是继续无限脉冲）。
 * 对前庭敏感的用户来说，"一个永不停止的脉冲"正是最该被关掉的东西。
 *
 * ⚠️ 三元表达式写在 `remember` **外面**：`remember { }` 的 calculation lambda
 * 不是 @Composable 上下文，把 `reduceMotion()` 放进去会编译不过。
 */
@Composable
fun motionAwareInfiniteDuration(durationMillis: Int): Int {
    val reduced = reduceMotion()
    return remember(durationMillis, reduced) { if (reduced) 0 else durationMillis }
}
