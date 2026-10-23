package com.pocket.xiaoli.ui.theme

import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Grok Ball 一个身体的「脸部参数」，照抄它 `SHAPES[*].face`。
 *
 * 这四个数决定"眼睛整体往哪挪、缩多少"：`wedge`（楔子）的 `y = 24 / sx = sy = 0.7 / eye = 0.79`
 * 不是装饰 —— 少了它们，楔子这种上尖下宽的三角就会有一只眼飘到轮廓外（2026-10 第一版实测）。
 */
data class MascotFaceBox(
    val x: Float,
    val y: Float,
    val sx: Float,
    val sy: Float,
    val eye: Float,
)

/**
 * 把 Grok Ball 的 48 点眼环投影到我们的 100 视口里。
 *
 * ## 为什么不是"简单缩放"
 *
 * 原作者把眼睛当**贴在球面上**的东西画：同一只眼在球心附近大、靠近轮廓时被压扁并往两侧走。
 * 它 `createBall().setEye()` 里的那一串公式就是这件事（距离球心越远，`theta` 越大 →
 * 横向压得越狠 `eye * cos(theta)`、纵向再乘一个 `fy` 的球面因子）。照抄这条公式，
 * 32 个表情才能在球 / 楔 / 宝石三种身体上都落在正确位置。
 *
 * 这里只做**静态**版：`pose` 各项取 0、`open = 1`、`yaw = 0`，也就是"表情的基准姿态"。
 *
 * 坐标：输入是原作者那套 viewBox `-15 -15 259 259`（`HEAD_C = 114.2705`），
 * 输出是 100 视口 —— 与 `Mascot.kt` 里身体 path 同一套 `fit` 变换（包围盒居中、留 2 边距）。
 */
class MascotProjection(
    /** 身体原始轮廓，x/y 交替。 */
    private val ring: FloatArray,
    val face: MascotFaceBox,
) {

    private val headC = 114.2705f
    private val eyeHalf = 21f

    private val minX: Float
    private val maxX: Float
    private val minY: Float
    private val maxY: Float

    /** 身体 bbox → 100 视口的等比变换（与生成 path 的脚本同一口径：留 2 的边距）。 */
    private val fitScale: Float
    private val fitCx: Float
    private val fitCy: Float

    init {
        require(ring.size % 2 == 0 && ring.size >= 6) { "身体轮廓至少 3 个点" }
        var x0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE
        var y0 = Float.MAX_VALUE
        var y1 = -Float.MAX_VALUE
        var i = 0
        while (i < ring.size) {
            val x = ring[i]
            val y = ring[i + 1]
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
            i += 2
        }
        minX = x0
        maxX = x1
        minY = y0
        maxY = y1
        val span = maxOf(x1 - x0, y1 - y0)
        fitScale = (VIEWPORT - 2f * FIT_PADDING) / span
        fitCx = (x0 + x1) / 2f
        fitCy = (y0 + y1) / 2f
    }

    /**
     * 一组眼环 → 100 视口里的 path。
     *
     * @param eyeRing x/y 交替的 48 点（原坐标系）。
     */
    fun eyePath(eyeRing: FloatArray): Path {
        val n = eyeRing.size / 2
        var baseX = 0f
        var baseY = 0f
        for (i in 0 until n) {
            baseX += eyeRing[2 * i]
            baseY += eyeRing[2 * i + 1]
        }
        baseX /= n
        baseY /= n

        // 纵向：先按 face 拉开，再夹进身体里（原作者用 "该处轮廓高度 - 半个眼高" 夹）
        val sy = face.eye
        val halfH = eyeHalf * sy + 2f
        var ey0 = headC + face.y + (baseY - headC) * face.sy
        val low = minY + halfH
        val high = maxY - halfH
        ey0 = if (low <= high) ey0.coerceIn(low, high) else (minY + maxY) / 2f

        // 横向：量出这一行身体的左右边界，眼心按 "球面" 往两侧推
        val sil = silhouetteAt(ey0)
        val cx0 = (sil.first + sil.second) / 2f
        val hw = maxOf((sil.second - sil.first) / 2f, 12f)
        val ox = face.x + (baseX - headC) * face.sx
        val theta = (ox / hw).coerceIn(-1.15f, 1.15f)
        val ex = cx0 + hw * sin(theta) * 0.985f

        // 球面因子：越靠近上下边缘越扁
        val dyN = (ey0 - headC) / 130f
        val fy = sqrt((1f - dyN * dyN * 0.22f).coerceAtLeast(0.01f))

        val sxT = face.eye * cos(theta)
        val syT = sy * fy

        val path = Path()
        for (i in 0 until n) {
            val px = ex + (eyeRing[2 * i] - baseX) * sxT
            val py = ey0 + (eyeRing[2 * i + 1] - baseY) * syT
            val fx = (px - fitCx) * fitScale + VIEWPORT / 2f
            val fyPx = (py - fitCy) * fitScale + VIEWPORT / 2f
            if (i == 0) path.moveTo(fx, fyPx) else path.lineTo(fx, fyPx)
        }
        path.close()
        return path
    }

    /** 身体轮廓在 `y` 这一行的左右边界（多边形与水平线的交点取最小 / 最大）。 */
    private fun silhouetteAt(y: Float): Pair<Float, Float> {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        val n = ring.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val y1 = ring[2 * i + 1]
            val y2 = ring[2 * j + 1]
            if (abs(y2 - y1) < 1e-4f) continue
            if (y < minOf(y1, y2) || y > maxOf(y1, y2)) continue
            val t = (y - y1) / (y2 - y1)
            val x = ring[2 * i] + t * (ring[2 * j] - ring[2 * i])
            if (x < lo) lo = x
            if (x > hi) hi = x
        }
        // 理论上不会空；真空了就退回包围盒，宁可眼位略偏也不要崩
        if (lo > hi) return minX to maxX
        return lo to hi
    }

    private companion object {
        /** 与生成身体 path 的脚本同一个边距（`fit(..., pad = 2.0)`）。 */
        const val FIT_PADDING = 2f
        const val VIEWPORT = 100f
    }
}

/**
 * 动作 → 表情：没手动选表情时，Grok 那三个身体跟着**动作**换脸。
 *
 * 映射的依据是原作者给每个表情标的 `pool` 第一组（见 [MascotFaces]），
 * 这里挑语义最接近的那一个；`IDLE` 用「待机放空」而不是「睡眠」——
 * 前者是睁眼的，后者是闭眼细缝，静止时看着像坏图（2026-10 踩过）。
 */
internal fun MascotFaces.forMotion(motion: MascotMotion): MascotFace = when (motion) {
    MascotMotion.IDLE -> byId("02")
    MascotMotion.PONDER -> byId("30")
    MascotMotion.SEARCH -> byId("40")
    MascotMotion.WORKING -> byId("32")
    MascotMotion.WAITING -> byId("35")
    MascotMotion.PROUD -> byId("33")
    MascotMotion.HOP -> byId("34")
}

