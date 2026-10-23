package com.pocket.xiaoli.data.knowledge

/**
 * 一次工具执行的结果。
 *
 * ## 为什么不是一个 `String`
 *
 * 工具结果原本只有一个字符串（例如"已存档 41 门课程，平均分 78.3，平均绩点 3.42"）。
 * 那句话是给**模型**读的散文，而界面想要的是那三个数字与明细行 —— 于是界面的选择只有
 * "原样铺一句散文"或者"去正则解析那句散文"。后者是两套口径的开始：
 * 文案一改，解析就静默失效，而且没人会收到通知。
 *
 * [text] 与 [card] 因此**必须来自同一次取数**（都在 [AssistantFormatters] 里生成）：
 * - [text] 照旧回灌给模型，它的措辞是模型行为的一部分，**不要为了排版去改它**；
 * - [card] 只给界面用，不落库（与思考链同一策略：重启后旧回复只剩文字，数字仍在文字里）。
 */
data class ToolOutcome(
    val text: String,
    val card: AssistantCard? = null,
)

/**
 * 回答里可以夹带的**数据卡片**。
 *
 * 只有真正"有数可列"的工具才产出它；其余工具 [ToolOutcome.card] 为 null，
 * 界面就当它不存在（不渲染空卡片）。
 */
sealed interface AssistantCard {

    /**
     * 成绩。
     *
     * 两种形态共用一张卡：
     * - **问整体**（"我绩点多少"）→ [stats] 有值、[rows] 为空 → 渲染三个并列 stat；
     * - **问某门课**（"高数多少分"）→ [stats] 为空、[rows] 有值 → 渲染明细表。
     *
     * 刻意**不同时给**：问某门课时把"全库均分"摆在旁边，用户会以为那是这门课的均分。
     */
    data class Grades(
        val stats: Stats? = null,
        val rows: List<GradeRow> = emptyList(),
    ) : AssistantCard {

        /** 整体统计。**挂科数**是用户最在意的那个数字，所以与均分、绩点并列。 */
        data class Stats(
            val total: Int,
            val averageScore: Double?,
            val averageGpa: Double?,
            val failedCount: Int,
        )
    }

    /** 一条成绩明细。[score]/[gpa] 用字符串而不是数字：教务返回的本就是字符串（可能是"—"）。 */
    data class GradeRow(
        val course: String,
        val term: String,
        val score: String,
        val gpa: String,
    )

    /**
     * 培养方案进度（修课情况快照）。
     *
     * 同样两种形态、同样**互斥**（理由见 [Grades] 的注释）：
     * - 问整体（"我还差多少学分"）→ [stats] 有值、[rows] 是**缺学分的类型**；
     * - 问某门课（"形势与政策修了没"）→ 用 [rows] 列课程，[stats] 仍给（它说的是总进度，不会误读）。
     *
     * [updatedLabel] 必须显示：培养方案**每学期会调**，不写"更新于"用户会把旧要求当现行要求。
     */
    data class AcademicProgress(
        val stats: Stats? = null,
        val rows: List<Row> = emptyList(),
        val updatedLabel: String = "",
    ) : AssistantCard {

        /** [missedCredit] 是"还差多少学分"——用户问"还差多少"时唯一要的那个数 */
        data class Stats(
            val requiredCredit: String,
            val earnedCredit: String,
            val missedCredit: String,
            val shortTypeCount: Int,
            val untakenCourseCount: Int,
        )

        /**
         * 一行：[title] 是类型名或课名，[detail] 是那行的关键数字，
         * [tag] 是右侧小标签（如「缺 6.0 学分」/「未修」）。
         */
        data class Row(
            val title: String,
            val detail: String = "",
            val tag: String = "",
        )
    }
}
