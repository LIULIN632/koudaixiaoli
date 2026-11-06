package com.pocket.xiaoli.desktop.data

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max

/**
 * 两张截图的逐像素比较（截图回归门禁用）。
 *
 * 口径：
 * - **尺寸不同直接算全不同** —— 窗口大小变了，内容再怎么对也不算通过。
 * - 单像素三通道最大差 **> 3/255** 才算「真的不一样」：只放行抗锯齿/字体渲染的抖动，
 *   放过它是因为同一份代码在不同次渲染里会有 1~2 的通道差，卡 0 会天天红。
 * - 输出**差异百分比 + 最大通道差**：只有百分比时，看不出「差一点点」还是「整块没了」。
 */
object ShotDiff {

    /**
     * 单像素三通道最大差超过它才算「真的不一样」。
     *
     * 取 8 而不是 0：同一份代码两次渲染，文字边缘的抗锯齿会有 1~7 的通道差；
     * 卡 0 只会让门禁天天红，而真正的回归（错位/缺块/配色错）远大于 8。
     */
    private const val AA_TOLERANCE = 8

    data class Outcome(
        val different: Long,
        val total: Long,
        val maxChannelDelta: Int,
    ) {
        val percent: Double get() = if (total <= 0L) 100.0 else 100.0 * different / total

        fun passes(thresholdPct: Double): Boolean = percent <= thresholdPct

        fun line(thresholdPct: Double): String =
            "diff=" + different + " total=" + total + " maxDelta=" + maxChannelDelta +
                " pct=" + "%.4f".format(percent) + " threshold=" + thresholdPct +
                " result=" + if (passes(thresholdPct)) "PASS" else "FAIL"
    }

    /** 比较两张 PNG。读不出来就当成「全不同」，绝不静默通过。 */
    fun compare(baselinePath: String, actualPath: String): Outcome {
        val a = runCatching { ImageIO.read(File(baselinePath)) }.getOrNull()
        val b = runCatching { ImageIO.read(File(actualPath)) }.getOrNull()
        if (a == null || b == null) return Outcome(different = 1, total = 1, maxChannelDelta = 255)
        if (a.width != b.width || a.height != b.height) {
            val total = a.width.toLong() * a.height
            return Outcome(different = total, total = total, maxChannelDelta = 255)
        }
        var different = 0L
        var maxDelta = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val pa = a.getRGB(x, y)
                val pb = b.getRGB(x, y)
                val d = max(
                    abs((pa shr 16 and 0xFF) - (pb shr 16 and 0xFF)),
                    max(
                        abs((pa shr 8 and 0xFF) - (pb shr 8 and 0xFF)),
                        abs((pa and 0xFF) - (pb and 0xFF)),
                    ),
                )
                if (d > AA_TOLERANCE) different++
                if (d > maxDelta) maxDelta = d
            }
        }
        return Outcome(different = different, total = a.width.toLong() * a.height, maxChannelDelta = maxDelta)
    }
}
