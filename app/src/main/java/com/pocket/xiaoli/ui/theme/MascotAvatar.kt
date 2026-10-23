package com.pocket.xiaoli.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 吉祥物的渲染层。形状与配色都是**数据**（见 [MascotShape] / [MascotPalette]），
 * 这里只负责把它画出来。
 *
 * ## 为什么不用 `res/drawable/ic_mascot.xml` + `AnimatedVectorDrawable`
 *
 * 调研稿给的第一版方案是"Figma 导出 → Vector Asset → AVD 眨眼"。那条路在**换装**上会崩：
 * 6 形状 × 12 色 = 72 张 drawable，或者运行时拼 XML —— 而形状本来就只是一条 path 字符串，
 * 直接 `PathParser` 解析比生成 XML 少一整层（也少一堆资源）。
 * 眨眼用 `animateFloat` 驱动眼睛高度，同样不必写 `objectAnimator`。
 *
 * ## 三个细节是**有意**的，改之前先读
 *
 * 1. **闭眼窗口放在周期末尾**：基准图抓到的是周期的早期（虚拟时间只推进一点点），
 *    于是抓到的都是睁眼帧。把"闭上"放在开头会让 35 张 golden 里凡是带头像的那几张
 *    随机抓到半闭的眼睛 —— 那是"基准图自己变红"最难查的一类。
 * 2. **周期值是文件内的具名常量，不进 [Motion]**：`Motion` 收的是缓动时长四档，
 *    而"隔几秒眨一次"是**循环周期**。`MotionTokenUsageTest` 禁的是 `tween(数字)` 与
 *    `durationMillis = 数字`，具名常量既不触雷、也不必为了绕开守卫塞进 `Motion`。
 * 3. **眼睛带一层薄的深色底**：白眼睛压在黄 / 淡紫 / 灰这类浅色身体上会糊成一片，
 *    在眼皮边缘垫一圈 18% 的黑就任何底色都读得出来，而正面看仍然只是"两只白眼"。
 */
@Composable
fun MascotAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    /** 画哪个形象。默认取 [LocalMascotLook]（全 App 统一的那一个）。 */
    look: MascotLook = LocalMascotLook.current,
    /**
     * 是否眨眼。
     *
     * ⚠️ 形状宫格里那些迷你形象传 `false`：它们的 `rememberInfiniteTransition`
     * 几乎同时启动，全部**同频眨眼**看起来像故障，而宫格要表达的是"形状长什么样"。
     * 大预览与真实头像保持 `true`。
     */
    blinking: Boolean = true,
    /**
     * 此刻在干什么 —— 决定身上那层**状态动效**（抄 DS Bot 的 `bt-ponder` / `bt-bob`）。
     *
     * 与 [blinking]（隔几秒眨一下）是两件事：眨眼表达"活着"，这个表达"在忙"。
     * 快照里传 [MascotMotion.IDLE]，否则基线会抓到某个相位。
     */
    motion: MascotMotion = MascotMotion.IDLE,
    /**
     * 表情（Grok Ball 那 32 个）。**只有带 [MascotShape.projection] 的形象认这个参数**
     *（圆球 / 楔子 / 宝石），其余形状有自己的眼形，传了也不画。
     *
     * 传 null 表示**跟着 [motion] 走**（见 [MascotFaces.forMotion]）——
     * 聊天里就是这么用的：想事情时换成「思考中」的眼神，出错时换成「出错」。
     */
    expression: MascotFace? = null,
) {
    val shape = look.shape
    val base = look.color
    // path 按数据缓存：重组（眨眼逐帧触发）不该每次重新解析一遍路径数据
    val path = remember(shape.pathData) { PathParser().parsePathString(shape.pathData).toPath() }
    // 叠色层同理：DS Bot 那些形象有三四层，每层都是一条 path
    val layerPaths = remember(shape.layers) {
        shape.layers.map { PathParser().parsePathString(it.pathData).toPath() }
    }
    // 轮廓眼（Grok Ball / npface）也是路径，单独缓存一份
    val eyeLayerPaths = remember(shape.eyeLayers) {
        shape.eyeLayers.map { PathParser().parsePathString(it.pathData).toPath() }
    }
    // 表情眼睛：Grok 三个身体的眼形不写死，按当前表情现算两条 path。
    // 换表情要重算（`face.id`），但同一次重组里眨眼逐帧不会再算。
    val face = expression ?: MascotFaces.forMotion(motion)
    val projectedEyePaths = remember(shape.name, shape.projection, face.id) {
        shape.projection?.let { projection ->
            MascotEyeRings.all[face.group].map { projection.eyePath(it) }
        }
    }
    // 两类"路径眼"合成一份绘制清单：颜色只有一处不同（投影眼固定白，眼层各带自己的色）。
    val pathEyes: List<Pair<androidx.compose.ui.graphics.Path, androidx.compose.ui.graphics.Color>> =
        when {
            projectedEyePaths != null -> projectedEyePaths.map { it to androidx.compose.ui.graphics.Color.White }
            else -> eyeLayerPaths.mapIndexed { index, p ->
                val layer = shape.eyeLayers[index]
                p to androidx.compose.ui.graphics.Color(layer.argb).copy(alpha = layer.alpha)
            }
        }
    // 每只眼的包围盒：眨眼 / 眯眼要绕**自己**的中心压，绕整体中心压会把两只眼挤到一起
    val eyeLayerBounds = remember(pathEyes) { pathEyes.map { it.first.getBounds() } }
    val eyeScaleY = rememberBlinkScaleY(blinking)
    val top = base.lightened(MASCOT_TOP_LIGHTEN)
    // 只为了给深色身体兜底一轮白边（见下面 Canvas 里的注释），不做任何配色改写
    val surfaceColor = LocalPocketXiaoliColors.current.surfaceBase
    // 小尺寸（消息头像 32dp / 底栏）**完全不打体积光**。32dp 上实测过两版：
    // 打 55% 时头顶仍出现一条浅带、跟浅底的圆底几乎同色，整条鱼读成一个糊块；
    // 归零以后小图回到"干净剪影 + 两只眼"，而 140dp 的预览照样有立体感。
    // 这条与 `MascotDataTest` 无关 —— 它是纯观感判断，证据在 40-turn-basic.png。
    val volume = if (size < SMALL_AVATAR_THRESHOLD) 0f else 1f

    // 状态动效：PONDER = 轻轻歪头 + 眯眼（DS `bt-ponder` + `bt-squint` 2.2s），
    // WORKING = 上下颠一颠（DS `bt-bob` 1.6s）。两者都是无限循环，只在进行中才挂；
    // 系统开了"减少动态效果"一律不动（同眨眼）。
    val pose = rememberMascotPose(motion)

    Canvas(modifier.size(size)) {
        // ⚠️ 形状数据住在 100×100 的视口里（见 [MascotShape]），所以**整块画布**要按比例放大，
        // 而不是给每个坐标各乘一次。
        //
        // 这里踩过一次：眼睛的位置是按 `eyeX/100*宽` 算的（所以是对的），
        // 而 path 直接 `drawPath` 了出去 —— 于是鱼身按"26 像素"画在画布左上角的
        // 百分之一大小处，两只眼睛孤零零地飘在下面。**编译、单测、语义检查全都正常**，
        // 只有把图渲出来才看得出来。改成进来先 scale，之后所有坐标都是**视口单位**，
        // 两类元素就不可能再各算各的。
        val unit = this.size.width / VIEWPORT
        scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
            // 整只形象的姿态：绕视口中心歪头、再整体上下挪（单位都是视口单位）。
            // ⚠️ 画在 `scale` 里面，是因为这里的一切坐标都是 100×100 视口单位；
            // 挂到外层 `graphicsLayer` 上就得再换一次算，还会连着透明留白一起转。
            rotate(degrees = pose.tiltDeg, pivot = Offset(VIEWPORT / 2f, VIEWPORT / 2f)) {
                translate(left = pose.slideUnits, top = pose.liftUnits) {
                    // 身体 + 鳍。两种填充：
                    // ① 形象自带配色（DS Bot 那 7 个）→ 照它源码：纯色或三色对角渐变，**不提亮**；
                    // ② 6 条鱼 → 本色 + 顶部往白里提 40%，给一点体积感。
                    when (val body = shape.body) {
                        null -> drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(top, base),
                                startY = 0f,
                                endY = VIEWPORT,
                            ),
                        )

                        else -> if (body.gradient.isEmpty()) {
                            drawPath(path = path, color = Color(body.argb))
                        } else {
                            drawPath(
                                path = path,
                                brush = Brush.linearGradient(
                                    colorStops = body.gradient
                                        .map { it.offset to Color(it.argb) }
                                        .toTypedArray(),
                                    start = Offset(body.x1, body.y1),
                                    end = Offset(body.x2, body.y2),
                                ),
                            )
                        }
                    }

                    // 6 条鱼没有 DS 那种手写的阴影 / 高光 path，就在**身体轮廓内**补两团柔光：
                    // 左上提亮、右下压暗。这是把 DS 的"贴纸体积感"用通用方式做给自绘形状 ——
                    // 不必给 6 条鱼各推两条 shadow/light 曲线（那 12 条曲线才是最难维护的部分）。
                    // ⚠️ 必须 clip 在身体 path 内：放射渐变填充的是整块画布，
                    // 不裁的话鳍外面会多出一圈雾。
                    if (shape.body == null) {
                        clipPath(path) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = GLOSS_ALPHA * volume), Color.Transparent),
                                    center = Offset(VIEWPORT * 0.36f, VIEWPORT * 0.20f),
                                    radius = VIEWPORT * 0.55f,
                                ),
                            )
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.Black.copy(alpha = SHADE_ALPHA * volume), Color.Transparent),
                                    center = Offset(VIEWPORT * 0.78f, VIEWPORT * 0.90f),
                                    radius = VIEWPORT * 0.62f,
                                ),
                            )
                        }
                    }

                    // 阴影 / 高光 / 肚皮：DS Bot 的吉祥物靠这几层叠出体积感，
                    // **顺序必须与数据里的列表一致**（肚皮在下、阴影压上、高光最上），
                    // 否则看到的是一张贴纸而不是一只立体的形象。
                    shape.layers.forEachIndexed { index, layer ->
                        drawPath(
                            path = layerPaths[index],
                            color = Color(layer.argb).copy(alpha = layer.alpha),
                        )
                    }

                    // 一眼"深色身体 vs 深色底"的问题：睡猫(#141416)与月亮(#2D2D2D)是 DS Bot 的
                    // 原版底色，在深色主题上只比背景亮一丝 —— 实测（98 那张基准图）只剩白眼和 z。
                    // 处理办法**不改原版配色**：只在两者亮度差真的不够时，沿身体描一圈 28% 白边。
                    // 浅色底上这条线永远不会出现，所以 96 / 97 两张对比图一个像素都不受影响。
                    val bodyColor = shape.body?.let { body ->
                        Color(if (body.gradient.isEmpty()) body.argb else body.gradient.last().argb)
                    }
                    if (bodyColor != null &&
                        abs(bodyColor.luminance() - surfaceColor.luminance()) < RIM_CONTRAST_THRESHOLD
                    ) {
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = RIM_ALPHA),
                            style = Stroke(width = RIM_WIDTH),
                        )
                    }

                    if (pathEyes.isEmpty()) {
                        // 眨眼只压纵向：横向不动，眼睛才是"合上"而不是"缩小"。
                        // 眯眼（PONDER 的 `bt-squint`）叠在同一个系数上；两只眼的系数可以不同
                        //（npface 那种"一只眼挑起来"），所以按 index 取左 / 右。
                        // 视线（`bt-seek` / Grok 的 gaze）只挪眼睛、不挪身体。
                        shape.eyes.forEachIndexed { index, eye ->
                            val eyeSquint = if (index == 0) pose.squintLeft else pose.squintRight
                            drawEye(
                                eye = eye.copy(
                                    x = eye.x + pose.gazeXUnits,
                                    y = eye.y + pose.gazeYUnits,
                                ),
                                scaleY = eyeScaleY * eyeSquint,
                            )
                        }
                    } else {
                        // 路径眼：Grok Ball 的 48 点眼环（32 个表情现算）、npface 的圆角方眼。
                        // 它们是填色路径、没有"眼皮"可画 —— 于是绕**这一只眼的包围盒中心**压纵向
                        // 来模拟眨眼 / 眯眼（原版是整块换一套眼形）。视线同样只平移眼睛。
                        pathEyes.forEachIndexed { index, (eyePath, eyeColor) ->
                            val eyeSquint =
                                (if (index == 0) pose.squintLeft else pose.squintRight) * eyeScaleY
                            val center = eyeLayerBounds[index].center
                            scale(scaleX = 1f, scaleY = eyeSquint, pivot = center) {
                                translate(left = pose.gazeXUnits, top = pose.gazeYUnits) {
                                    drawPath(path = eyePath, color = eyeColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一只眼睛：深色薄边 + 白色椭圆，按 `eye.tilt` 微微外斜。
 *
 * ⚠️ 2026-10 试过改成 DS Bot 那种圆角竖条（`corner`），在 32dp 头像上变成两道白缝，
 * 用户反馈"眼睛不好看了"后整体撤回 —— 别再往"统一成竖条"的方向改。
 */
private fun DrawScope.drawEye(eye: MascotEye, scaleY: Float) {
    val ry = eye.ry * scaleY
    rotate(degrees = eye.tilt, pivot = Offset(eye.x, eye.y)) {
        val rimRx = eye.rx * EYE_RIM_SCALE
        val rimRy = ry * EYE_RIM_SCALE
        drawOval(
            color = Color.Black.copy(alpha = EYE_RIM_ALPHA),
            topLeft = Offset(eye.x - rimRx, eye.y - rimRy),
            size = Size(rimRx * 2, rimRy * 2),
        )
        drawOval(
            color = Color.White,
            topLeft = Offset(eye.x - eye.rx, eye.y - ry),
            size = Size(eye.rx * 2, ry * 2),
        )
    }
}

/**
 * 眼睛的纵向缩放（1 = 睁开，[EYES_CLOSED] = 闭上）。
 *
 * ⚠️ **不要改成 `while (true) { delay(…); … }`** —— 调研稿给的就是那个写法，
 * 但它会让 Compose 测试的 `waitForIdle` 永远等不到空闲（无限挂起的协程一直"有待办"），
 * 35 张快照会集体挂死。`rememberInfiniteTransition` 是项目里已验证可行的做法
 * （`AiAvatar` 的光环脉冲就是它，且已有基线）。
 *
 * 关键帧：睁 →（末尾 240ms 内）闭 → 睁。见本文件 KDoc 第 1 条。
 */
@Composable
private fun rememberBlinkScaleY(blinking: Boolean): Float {
    // ⚠️ 系统要求"减少动画"时**不眨眼**：眨眼是每 ~3.2 秒一次的无限循环，
    // 对前庭敏感 / 注意力易分散的用户是持续的视觉干扰，而他关不掉。
    // 与 `blinking = false`（快照测试用）走同一条早返回，行为一致、实现只有一处。
    if (!blinking || reduceMotion()) return 1f
    val transition = rememberInfiniteTransition(label = "mascotBlink")
    val scaleY by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = BLINK_PERIOD_MS
                1f at 0
                1f at BLINK_PERIOD_MS - 2 * Motion.exit
                EYES_CLOSED at BLINK_PERIOD_MS - Motion.exit
                1f at BLINK_PERIOD_MS
            },
        ),
        label = "mascotEyeScaleY",
    )
    return scaleY
}

/** 形状数据与眼位坐标共用的视口边长（见 [MascotShape] 的坐标口径）。 */
private const val VIEWPORT = 100f

/**
 * 眨眼的**周期**（不是缓动时长，所以不进 [Motion]）。
 *
 * 3.6 秒：比"每 3~5 秒随机一次"的调研稿取中值略短 —— 随机会让同一屏里的多个头像
 * 各自漂移，也让基准图更不可控；固定周期配上"每只鱼在自己被组合时开始计时"，
 * 实际观感已经是错落的。
 */
private const val BLINK_PERIOD_MS = 3600

/** 闭眼时压到的纵向比例（不是 0：完全压成一条线会显得是"坏了"，10% 才像眨眼）。 */
private const val EYES_CLOSED = 0.1f
/** 深色身体兜底白边：身体与底色亮度差小于这个值就补边（≈"轮廓分不出来"）。 */
private const val RIM_CONTRAST_THRESHOLD = 0.10f

/** 补边的白，22% 不透明：够看出轮廓，又不至于像描了个漫画线稿。 */
private const val RIM_ALPHA = 0.22f

/** 小于这个尺寸的头像（消息气泡 32dp、底栏）不打体积光：小图上轮廓优先。 */
private val SMALL_AVATAR_THRESHOLD = 56.dp

/** 鱼身左上那团高光的白，30% —— 再高就成塑料了。 */
private const val GLOSS_ALPHA = 0.30f

/** 鱼身右下压暗的黑，18%：DS 的阴影层是 14%，鱼的身体小、要略重一点才看得出来。 */
private const val SHADE_ALPHA = 0.18f

/** 边宽，视口单位（100 的 1.2%）。 */
private const val RIM_WIDTH = 1.0f

/** 顶部高光：本色往白里提多少。 */
private const val MASCOT_TOP_LIGHTEN = 0.40f

/** 眼睛的深色薄边：比白眼大一圈、压在一层 18% 的黑上。 */
private const val EYE_RIM_SCALE = 1.28f
private const val EYE_RIM_ALPHA = 0.18f

/** 本色往白里提 [fraction] —— 只动 RGB，保留 alpha。 */
private fun Color.lightened(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha,
)

/**
 * 形象此刻的**姿态**：歪多少、整体挪多少、眼睛眯多少。
 *
 * 三者合成一个不可变值，是为了让"这一帧摆成什么样"只有一个出入口 —— 早先的写法是
 * 在 Canvas 里各自读三条动画，结果 `rotate` 的枢轴点和 `translate` 的单位各写一遍，
 * 改起来要同时改三处。
 */
private data class MascotPose(
    val tiltDeg: Float = 0f,
    /** 纵向位移，**视口单位**（负数 = 往上）。 */
    val liftUnits: Float = 0f,
    /** 横向位移，**视口单位**（负数 = 往左，鱼头朝左）。 */
    val slideUnits: Float = 0f,
    /**
     * 视线偏移，**只作用于眼睛**（视口单位）。抄的是 Grok Ball 的 `gaze`：
     * 它的 32 个表情全靠两只眼环在球面上滑动来表达，身体几乎不动。
     */
    val gazeXUnits: Float = 0f,
    val gazeYUnits: Float = 0f,
    /** 左眼 / 右眼各自纵向压多少（1 = 正常）。拆成两只，是为了 npface 的 `waiting` 那种"一只眼挑起来"。 */
    val squintLeft: Float = 1f,
    val squintRight: Float = 1f,
) {
    /** 叠加：循环姿态 + 一次性动作。逐项相加，眼睛的缩放相**乘**。 */
    fun plus(other: MascotPose) = MascotPose(
        tiltDeg = tiltDeg + other.tiltDeg,
        liftUnits = liftUnits + other.liftUnits,
        slideUnits = slideUnits + other.slideUnits,
        gazeXUnits = gazeXUnits + other.gazeXUnits,
        gazeYUnits = gazeYUnits + other.gazeYUnits,
        squintLeft = squintLeft * other.squintLeft,
        squintRight = squintRight * other.squintRight,
    )
}

/**
 * 状态动效。取值直接照 DS Bot 的类名取，方便回头对照它 `styles.js` 里的 keyframes。
 */
enum class MascotMotion {
    /** 静息：身上只有眨眼。 */
    IDLE,

    /**
     * 思考中：抄 DS 的 `bt-ponder`（2.2s，rotate 4°→7°、translateY -3%）+
     * `bt-squint`（同一周期把眼睛压到 55%）。
     *
     * 为什么是"歪头 + 眯眼"而不是常见的"上下呼吸"：呼吸会跟 [AiAvatar] 光环的
     * 脉冲撞节奏，两个不同周期的循环叠在同一个头像上会互相打架。歪头是横向的，
     * 和光环的纵向脉冲不冲突 —— 这也是 DS 把它分成 ponder / bob 两套的原因。
     */
    PONDER,

    /** 查工具中：抄 DS 的 `bt-bob`（1.6s，上下颠一颠），比思考更"忙"。 */
    WORKING,

    /**
     * 出错了：抄 DS 的 `bt-hop`（1.3s 连跳两下）—— **只跳一轮就停**。
     *
     * 错误态会一直停在 `AgentPhase.ERROR` 等用户处理（见它的 KDoc），若挂无限循环，
     * 头像是会一直跳下去 —— 比"没反应"更烦人。
     */
    HOP,

    /**
     * 查资料：抄 DS 的 `bt-seek`（1s）——**只有眼睛在扫**，身体几乎不动。
     *
     * 单独一档而不是并进 [PONDER]，是因为"想"和"翻资料"在原版里就是两套：
     * 想是歪头（`bt-ponder`），翻是眼珠左右跑（`bt-seek`）。Grok Ball 也一样，
     * 它的 32 个表情里"视线往哪看"是独立于身体姿态的一个量（`setGaze`）。
     */
    SEARCH,

    /**
     * 等你答话：抄 npface 的 `waiting` —— 歪头 + **一只眼挑起来**（左右眼各自眯的程度不同）。
     *
     * 这是"不对称眯眼"唯一的用处：两眼同步眯是"困 / 想"，一只眼眯才是"瞅着你等你说话"。
     */
    WAITING,

    /**
     * 收尾（一轮答完）：抄 DS 的 `bt-done`（1.8s）——**只演一遍**。
     *
     * 与 [HOP] 的区别是方向与情绪：出错是往上跳两下（惊），干完是往上一提 + 眯一下眼（满意）。
     */
    PROUD,
    ;

    /**
     * 设置页「动作」那一行的中文名。
     *
     * ⚠️ 新增取值必须同时加进 [MASCOT_MOTION_CATALOG]，否则设置页少一个按钮
     *（`MascotMotionTest` 会红：它逐项断言"每个取值都能被试演"）。
     */
    internal val previewLabel: String
        get() = when (this) {
            IDLE -> "静息"
            PONDER -> "思考"
            SEARCH -> "查资料"
            WORKING -> "干活"
            WAITING -> "等你"
            PROUD -> "收工"
            HOP -> "出岔子"
        }

    /** 是不是"只演一遍"的动作（见 [rememberLeapPose]）；循环动作算 `false`。 */
    internal val isOneShot: Boolean
        get() = this == PROUD || this == HOP
}

/** 设置页「动作试演」那一行的顺序：先静息，再按一轮对话的时间线走。 */
internal val MASCOT_MOTION_CATALOG = listOf(
    MascotMotion.IDLE,
    MascotMotion.PONDER,
    MascotMotion.SEARCH,
    MascotMotion.WORKING,
    MascotMotion.WAITING,
    MascotMotion.PROUD,
    MascotMotion.HOP,
)

/**
 * 按 [motion] 算出这一帧的姿态。
 *
 * 静息 / "减少动态效果" 时**不挂任何无限动画**：直接返回常量。这不只是无障碍要求 ——
 * 快照测试里若挂了 `rememberInfiniteTransition`，抓到的相位取决于虚拟时间，
 * 基线就变成了"有时红有时绿"。见 `MotionTokenUsageTest` 的 KDoc 里同一类教训。
 */
@Composable
private fun rememberMascotPose(motion: MascotMotion): MascotPose {
    if (motion == MascotMotion.IDLE || reduceMotion()) return MascotPose()

    return rememberLoopPose(motion).plus(rememberLeapPose(motion))
}

@Composable
private fun rememberLoopPose(motion: MascotMotion): MascotPose {
    val transition = rememberInfiniteTransition(label = "mascotMotion")
    return when (motion) {
        MascotMotion.PONDER -> {
            val tilt by transition.animateFloat(
                initialValue = PONDER_TILT_REST,
                targetValue = PONDER_TILT_REST,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = PONDER_PERIOD_MS
                        PONDER_TILT_REST at 0
                        PONDER_TILT_PEAK at PONDER_PERIOD_MS / 2
                        PONDER_TILT_REST at PONDER_PERIOD_MS
                    },
                ),
                label = "mascotPonderTilt",
            )
            val lift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = PONDER_PERIOD_MS
                        0f at 0
                        PONDER_LIFT_UNITS at PONDER_PERIOD_MS / 2
                        0f at PONDER_PERIOD_MS
                    },
                ),
                label = "mascotPonderLift",
            )
            val squint by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = PONDER_PERIOD_MS
                        1f at 0
                        PONDER_SQUINT at PONDER_PERIOD_MS / 2
                        1f at PONDER_PERIOD_MS
                    },
                ),
                label = "mascotPonderSquint",
            )
            MascotPose(tiltDeg = tilt, liftUnits = lift, squintLeft = squint, squintRight = squint)
        }

        MascotMotion.WORKING -> {
            val lift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = BOB_PERIOD_MS
                        0f at 0
                        BOB_LIFT_UNITS at BOB_PERIOD_MS * 35 / 100
                        0f at BOB_PERIOD_MS * 55 / 100
                        BOB_LIFT_UNITS * 0.4f at BOB_PERIOD_MS * 72 / 100
                        0f at BOB_PERIOD_MS
                    },
                ),
                label = "mascotBobLift",
            )
            MascotPose(liftUnits = lift)
        }

        MascotMotion.SEARCH -> {
            // 眼睛左右扫：身体只跟着倾一点点（DS 的 `bt-seek` 就是"头不动、眼在找"）。
            // 视线范围取 ±2.4 个视口单位 —— 眼球直径约 7，挪这么多已经是"看向一边"了。
            val gaze by transition.animateFloat(
                initialValue = -SEARCH_GAZE_UNITS,
                targetValue = -SEARCH_GAZE_UNITS,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = SEEK_PERIOD_MS
                        -SEARCH_GAZE_UNITS at 0
                        SEARCH_GAZE_UNITS at SEEK_PERIOD_MS / 2
                        -SEARCH_GAZE_UNITS at SEEK_PERIOD_MS
                    },
                ),
                label = "mascotSeekGaze",
            )
            val tilt by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = SEEK_PERIOD_MS
                        0f at 0
                        SEEK_TILT_DEG at SEEK_PERIOD_MS / 2
                        0f at SEEK_PERIOD_MS
                    },
                ),
                label = "mascotSeekTilt",
            )
            MascotPose(tiltDeg = tilt, gazeXUnits = gaze, gazeYUnits = SEARCH_GAZE_LIFT_UNITS)
        }

        MascotMotion.WAITING -> {
            // 歪头 + 慢慢喘一口气；两只眼眯的程度**不一样**（左眼 85%、右眼 45%），
            // 于是读起来是"挑起一边眉毛瞅着你"，而不是"困了"。
            val lift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = WAIT_PERIOD_MS
                        0f at 0
                        WAIT_LIFT_UNITS at WAIT_PERIOD_MS / 2
                        0f at WAIT_PERIOD_MS
                    },
                ),
                label = "mascotWaitLift",
            )
            MascotPose(
                tiltDeg = WAIT_TILT_DEG,
                liftUnits = lift,
                squintLeft = WAIT_SQUINT_LEFT,
                squintRight = WAIT_SQUINT_RIGHT,
            )
        }

        MascotMotion.HOP, MascotMotion.PROUD, MascotMotion.IDLE -> MascotPose()
    }
}

/**
 * 一次性动作：**只在 `motion` 发生变化时播一遍**。
 *
 * 为什么是"变化时"而不是"组合时"—— 这是快照能稳定的关键：
 * 基准图是**直接以 THINKING / ERROR 组合**出来的，第一帧 `played == motion`，
 * 所以一张图都碰不到动画；真机上则是"欢迎页那个头像从 IDLE 变 THINKING"
 * 或"消息头像 THINKING → TOOL → ERROR"，变化发生了才会播。
 *
 * 试过"组合即播"：41 / 51 / 消息区那三张基线会在动画中途被抓到，
 * 同一份代码连跑两次，一次绿一次红（实测）。
 */
@Composable
private fun rememberLeapPose(motion: MascotMotion): MascotPose {
    val progress = remember { Animatable(0f) }
    // 初值 = 首帧的 motion，于是"首次组合"不触发动画
    var played by remember { mutableStateOf(motion) }
    LaunchedEffect(motion) {
        if (played == motion) return@LaunchedEffect
        played = motion
        when (motion) {
            MascotMotion.PONDER -> progress.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = DASH_PERIOD_MS
                    0f at 0
                    1f at DASH_PERIOD_MS * 38 / 100
                    0f at DASH_PERIOD_MS
                },
            )

            MascotMotion.HOP -> progress.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = HOP_PERIOD_MS
                    0f at 0
                    1f at HOP_PERIOD_MS * 15 / 100
                    0f at HOP_PERIOD_MS * 30 / 100
                    1f at HOP_PERIOD_MS * 55 / 100
                    0f at HOP_PERIOD_MS * 70 / 100
                    0f at HOP_PERIOD_MS
                },
            )

            MascotMotion.PROUD -> progress.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = MASCOT_DONE_PERIOD_MS
                    0f at 0
                    1f at MASCOT_DONE_PERIOD_MS * 22 / 100
                    0f at MASCOT_DONE_PERIOD_MS * 55 / 100
                    0.45f at MASCOT_DONE_PERIOD_MS * 72 / 100
                    0f at MASCOT_DONE_PERIOD_MS
                },
            )

            MascotMotion.SEARCH, MascotMotion.WAITING,
            MascotMotion.WORKING, MascotMotion.IDLE,
            -> Unit
        }
    }
    return when (motion) {
        MascotMotion.PONDER -> MascotPose(slideUnits = DASH_SLIDE_UNITS * progress.value)
        MascotMotion.HOP -> MascotPose(liftUnits = HOP_LIFT_UNITS * progress.value)
        // 收工：往上一提 + 眯一下眼（满意），提完落回原地
        MascotMotion.PROUD -> MascotPose(
            tiltDeg = PROUD_TILT_DEG * progress.value,
            liftUnits = PROUD_LIFT_UNITS * progress.value,
            squintLeft = 1f - (1f - PROUD_SQUINT) * progress.value,
            squintRight = 1f - (1f - PROUD_SQUINT) * progress.value,
        )

        else -> MascotPose()
    }
}

/** 歪头的静息角度：DS 的 `bt-ponder` 是 4°↔7°，不是从 0° 起摆。 */
private const val PONDER_TILT_REST = 4f
private const val PONDER_TILT_PEAK = 7f

/** 思考时整体往上浮的量（视口单位，-3 ≈ DS 的 translateY(-3%)）。 */
private const val PONDER_LIFT_UNITS = -3f

/** `bt-squint`：眼睛压到 55%。 */
private const val PONDER_SQUINT = 0.55f

/** `bt-ponder` 的周期。 */
private const val PONDER_PERIOD_MS = 2200

/** `bt-dash`：发送瞬间往鱼头方向窜多少（视口单位，负数 = 往左）、周期。 */
private const val DASH_SLIDE_UNITS = -6f
private const val DASH_PERIOD_MS = 1100

/** `bt-hop`：错误态跳多高、一轮多久。 */
private const val HOP_LIFT_UNITS = -10f
private const val HOP_PERIOD_MS = 1300

/** `bt-bob` 的周期与振幅（-7 个视口单位 ≈ 鱼身高度的 12%）。 */
private const val BOB_PERIOD_MS = 1600
private const val BOB_LIFT_UNITS = -7f

/**
 * `bt-seek`：眼睛左右扫的范围与周期。
 *
 * 周期照原版 1s；范围取 ±2.4 个视口单位 —— Grok Ball 的 gaze 是"球面上滑"，
 * 这里没有球面可投，用**平移眼球**近似（眼睛直径约 7，挪 2.4 ≈ 看向一侧）。
 */
private const val SEEK_PERIOD_MS = 1000
private const val SEARCH_GAZE_UNITS = 2.4f

/** 扫视时视线整体略抬，外加身体跟着倾 1.2°：太小看不出来，太大会像在摇头。 */
private const val SEARCH_GAZE_LIFT_UNITS = -0.6f
private const val SEEK_TILT_DEG = 1.2f

/** npface 的 `waiting`：歪 5°、慢慢沉一下，两只眼分别眯到 85% / 45%。 */
private const val WAIT_PERIOD_MS = 2400
private const val WAIT_TILT_DEG = 5f
private const val WAIT_LIFT_UNITS = -1.5f
private const val WAIT_SQUINT_LEFT = 0.85f
private const val WAIT_SQUINT_RIGHT = 0.55f

/** `bt-done`：收工的周期、往上提多少、眯到多少（72% 是"笑眼"，再低就成闭眼了）。 */
internal const val MASCOT_DONE_PERIOD_MS = 1800
private const val PROUD_LIFT_UNITS = -4f
private const val PROUD_TILT_DEG = 2.5f
private const val PROUD_SQUINT = 0.72f

