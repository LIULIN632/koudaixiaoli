package com.pocket.xiaoli.ui.components.infocard

import androidx.compose.runtime.Immutable
import com.pocket.xiaoli.data.entity.SavedGradeEntity

/**
 * 成绩分布 —— 把一堆分数压成几个档位，用于画分布条。
 *
 * ## 为什么值得做
 *
 * 项目里已有平均分/平均绩点/通过率这些**单一数值**，但看不出**结构**：
 * 平均分 78 可能是"全都 78"，也可能是"一半 95 一半 60" —— 这两种情况的
 * 学习状态完全不同，而后者的风险大得多。
 *
 * 分布条让这种结构一眼可见。
 *
 * ## 为什么是纯函数
 *
 * 分档逻辑（几分算优秀）是**业务规则**，与渲染无关。放在纯函数里可以单测，
 * 也能保证图与文字用的是同一套判断（避免"图上显示优秀、标签写着及格"）。
 */
@Immutable
data class GradeBucket(
    val label: String,
    val count: Int,
    val band: ScoreBand,
)

@Immutable
data class GradeDistribution(
    val buckets: List<GradeBucket>,
    /** 参与统计的有效分数个数（无法解析的分数不计入） */
    val scored: Int,
) {
    val isEmpty: Boolean get() = scored == 0

    /** 最大桶的计数，用于算相对高度。至少为 1，避免除零。 */
    val maxCount: Int get() = buckets.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    companion object {
        /** 成绩档案那一侧的入口（[SavedGradeEntity.totalScore] 是一列文本分数）。 */
        fun of(grades: List<SavedGradeEntity>): GradeDistribution =
            ofScores(grades.map { it.totalScore })

        /**
         * 从**一列分数文本**分档 —— 分数从哪来（成绩档案的 `totalScore`、教务明细的「最大成绩」）
         * 不关分档的事，所以这一层才是规则本体，[of] 只是它的一个取数适配器。
         *
         * 非数字（空串、"—"、"合格"/"优秀"这类等级制）一律跳过：
         * 硬猜成 60 或 85 都会在分布条上画出一门不存在的课。
         */
        fun ofScores(rawScores: List<String>): GradeDistribution {
            val scores = rawScores.mapNotNull { it.trim().toFloatOrNull() }
            val buckets = SPECS.map { spec ->
                GradeBucket(
                    label = spec.label,
                    count = scores.count { spec.matches(it) },
                    band = spec.band,
                )
            }
            return GradeDistribution(buckets = buckets, scored = scores.size)
        }
    }
}

/** 一个分档规则：标签 + 语义档位 + 命中判断。抽出来是为了让 `of` 保持可读。 */
private class BucketSpec(
    val label: String,
    val band: ScoreBand,
    val matches: (Float) -> Boolean,
)

/**
 * 四档规则。档位与 [ScoreBand] 对齐（优秀 / 及格 / 不及格），但把 60–89 再拆成
 * 「80-89」与「60-79」 —— 分布图上合成一档会看不出中段的情况。
 */
private val SPECS = listOf(
    BucketSpec("90+", ScoreBand.EXCELLENT) { it >= 90f },
    BucketSpec("80-89", ScoreBand.PASS) { it in 80f..89.99f },
    BucketSpec("60-79", ScoreBand.PASS) { it in 60f..79.99f },
    BucketSpec("<60", ScoreBand.FAIL) { it < 60f },
)
