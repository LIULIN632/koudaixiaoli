package com.pocket.xiaoli.ui.components.infocard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors

/**
 * 成绩档位 —— 把「分数 → 颜色」这件事从**视觉判断**提升为**业务语义**。
 *
 * ## 为什么需要它
 *
 * 原实现（`ui/zhengfang/ZhengfangLoginUi.kt` 的 `scoreColor`）直接返回硬编码颜色：
 *
 * ```kotlin
 * v < 60  -> Color(0xFFE57373)   // 红
 * v >= 85 -> Color(0xFF66BB6A)   // 绿
 * else    -> Color(0xFF43A047)   // 深绿
 * ```
 *
 * 问题：这些固定色**不随配色主题与深浅模式变化**。在深色模式下，
 * 那个"深绿"压在深色卡片上对比度明显不足 —— 而这恰恰是用户最需要看清的数字。
 *
 * 改为先判**档位**、再由主题给颜色，这样：
 * - 深色模式自动用更亮的语义色
 * - 换配色主题时成绩色仍然协调（而不是永远那一抹绿）
 * - 判断逻辑（几分算优秀）可以单独测试，与渲染解耦
 */
enum class ScoreBand {
    /** 未评分 / 无法解析 */
    UNKNOWN,

    /** 不及格（< 60） */
    FAIL,

    /** 及格（60–84） */
    PASS,

    /** 优秀（≥ 85） */
    EXCELLENT,
}

/** 把分数文本解析为档位。解析失败返回 [ScoreBand.UNKNOWN]。 */
fun scoreBandOf(score: String): ScoreBand {
    val v = score.trim().toFloatOrNull() ?: return ScoreBand.UNKNOWN
    return when {
        v < 60f -> ScoreBand.FAIL
        v >= 85f -> ScoreBand.EXCELLENT
        else -> ScoreBand.PASS
    }
}

/** 档位 → 主题语义色。 */
@Composable
fun ScoreBand.color(): Color {
    val colors = LocalPocketXiaoliColors.current
    return when (this) {
        ScoreBand.UNKNOWN -> colors.textTertiary
        ScoreBand.FAIL -> colors.danger
        ScoreBand.PASS -> colors.success
        ScoreBand.EXCELLENT -> colors.accentMain
    }
}

/** 档位对应的短标签（成绩卡右上角的胶囊）。 */
fun ScoreBand.label(): String? = when (this) {
    ScoreBand.FAIL -> "不及格"
    ScoreBand.PASS -> "已通过"
    ScoreBand.EXCELLENT -> "优秀"
    ScoreBand.UNKNOWN -> null
}
